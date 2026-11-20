package ryzlar.glass.network;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import ryzlar.glass.Glass;
import ryzlar.glass.blockentity.GlassProjectorBlockEntity;
import ryzlar.glass.blockentity.GlassSceneSelectorBlockEntity;
import ryzlar.glass.blockentity.GlassTerminalBlockEntity;
import ryzlar.glass.blockentity.GlassWirelessBlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Packets between the GLASS screens and the server. The server owns the channels; clients get every channel's public
 * details ({@link NetworkState}) and a short reply to each action ({@link ActionResult}).
 */
public final class GlassNetworking
{
    private static final double REACH_LEEWAY = 4.0D;
    private static final StreamCodec<ByteBuf, String> SHORT_STRING = ByteBufCodecs.stringUtf8(128);

    private GlassNetworking() {}

    // ---- Server to client ----

    /** A channel as players see it: no password, just whether you own it, may use it, and whether its camera is up. */
    public record ChannelInfo(String id, String name, String ownerName, boolean owner, boolean access, boolean online)
    {
        public static final StreamCodec<ByteBuf, ChannelInfo> CODEC = StreamCodec.composite(
                SHORT_STRING, ChannelInfo::id,
                SHORT_STRING, ChannelInfo::name,
                SHORT_STRING, ChannelInfo::ownerName,
                ByteBufCodecs.BOOL, ChannelInfo::owner,
                ByteBufCodecs.BOOL, ChannelInfo::access,
                ByteBufCodecs.BOOL, ChannelInfo::online,
                ChannelInfo::new);
    }

    public record NetworkState(List<ChannelInfo> channels) implements CustomPacketPayload
    {
        public static final Type<NetworkState> TYPE = new Type<>(Glass.id("network_state"));
        public static final StreamCodec<ByteBuf, NetworkState> CODEC = StreamCodec.composite(
                ChannelInfo.CODEC.apply(ByteBufCodecs.list(65536)), NetworkState::channels,
                NetworkState::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /** The answer to an action, shown on the screen (a translation key and one argument). */
    public record ActionResult(boolean ok, String message, String arg) implements CustomPacketPayload
    {
        public static final Type<ActionResult> TYPE = new Type<>(Glass.id("action_result"));
        public static final StreamCodec<ByteBuf, ActionResult> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, ActionResult::ok,
                SHORT_STRING, ActionResult::message,
                SHORT_STRING, ActionResult::arg,
                ActionResult::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    // ---- Client to server ----

    public enum Action
    {
        REQUEST_STATE(0),
        UNLOCK(2),          //channel id, password
        DELETE_CHANNEL(1),  //channel id
        CHANGE_PASSWORD(2); //channel id, new password

        final int args;

        Action(int args)
        {
            this.args = args;
        }
    }

    /** A channel command. */
    public record NetAction(int action, List<String> args) implements CustomPacketPayload
    {
        public static final Type<NetAction> TYPE = new Type<>(Glass.id("net_action"));
        public static final StreamCodec<ByteBuf, NetAction> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, NetAction::action,
                SHORT_STRING.apply(ByteBufCodecs.list(4)), NetAction::args,
                NetAction::new);

        public static NetAction of(Action action, String... args)
        {
            return new NetAction(action.ordinal(), List.of(args));
        }

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    public enum TerminalCommand
    {
        CREATE,    //name, password: a new channel with this terminal as its camera
        MOVE_HERE, //channel id: make this terminal the camera of one of your channels
        DETACH     //this terminal stops being a camera
    }

    public record TerminalAction(BlockPos pos, int command, String a, String b) implements CustomPacketPayload
    {
        public static final Type<TerminalAction> TYPE = new Type<>(Glass.id("terminal_action"));
        public static final StreamCodec<ByteBuf, TerminalAction> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, TerminalAction::pos,
                ByteBufCodecs.VAR_INT, TerminalAction::command,
                SHORT_STRING, TerminalAction::a,
                SHORT_STRING, TerminalAction::b,
                TerminalAction::new);

        public static TerminalAction of(BlockPos pos, TerminalCommand command, String a, String b)
        {
            return new TerminalAction(pos, command.ordinal(), a, b);
        }

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /** Sets which channel a projector shows (empty: none). */
    public record ConfigureProjector(BlockPos pos, String channelId) implements CustomPacketPayload
    {
        public static final Type<ConfigureProjector> TYPE = new Type<>(Glass.id("configure_projector"));
        public static final StreamCodec<ByteBuf, ConfigureProjector> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, ConfigureProjector::pos,
                SHORT_STRING, ConfigureProjector::channelId,
                ConfigureProjector::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /** Turns a projector's cloak mode on or off, and sets how many blocks behind the screen it hides. */
    public record ConfigureCloak(BlockPos pos, boolean cloak, int depth, boolean field, int fieldHeight) implements CustomPacketPayload
    {
        public static final Type<ConfigureCloak> TYPE = new Type<>(Glass.id("configure_cloak"));
        public static final StreamCodec<ByteBuf, ConfigureCloak> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, ConfigureCloak::pos,
                ByteBufCodecs.BOOL, ConfigureCloak::cloak,
                ByteBufCodecs.VAR_INT, ConfigureCloak::depth,
                ByteBufCodecs.BOOL, ConfigureCloak::field,
                ByteBufCodecs.VAR_INT, ConfigureCloak::fieldHeight,
                ConfigureCloak::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /** What the scene selector panel asks for. */
    public enum SelectorOp { LINK, ADD, REMOVE, UP, DOWN, SHOW }

    public record SelectorAction(BlockPos pos, int op, int index, String channelId) implements CustomPacketPayload
    {
        public static final Type<SelectorAction> TYPE = new Type<>(Glass.id("selector_action"));
        public static final StreamCodec<ByteBuf, SelectorAction> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SelectorAction::pos,
                ByteBufCodecs.VAR_INT, SelectorAction::op,
                ByteBufCodecs.VAR_INT, SelectorAction::index,
                SHORT_STRING, SelectorAction::channelId,
                SelectorAction::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /** Links a wireless extension to a projector. */
    public record SetProjector(BlockPos wirelessPos, BlockPos projectorPos) implements CustomPacketPayload
    {
        public static final Type<SetProjector> TYPE = new Type<>(Glass.id("set_projector"));
        public static final StreamCodec<ByteBuf, SetProjector> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SetProjector::wirelessPos,
                BlockPos.STREAM_CODEC, SetProjector::projectorPos,
                SetProjector::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /** Disconnects a wireless extension from a projector (from the extension's screen). */
    public record UnlinkWireless(BlockPos wirelessPos, BlockPos projectorPos) implements CustomPacketPayload
    {
        public static final Type<UnlinkWireless> TYPE = new Type<>(Glass.id("unlink_wireless"));
        public static final StreamCodec<ByteBuf, UnlinkWireless> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, UnlinkWireless::wirelessPos,
                BlockPos.STREAM_CODEC, UnlinkWireless::projectorPos,
                UnlinkWireless::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /** Reorders (or removes) the wireless extensions linked to a projector. */
    public record WirelessOrder(BlockPos projectorPos, List<BlockPos> order) implements CustomPacketPayload
    {
        public static final Type<WirelessOrder> TYPE = new Type<>(Glass.id("wireless_order"));
        public static final StreamCodec<ByteBuf, WirelessOrder> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, WirelessOrder::projectorPos,
                BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(GlassProjectorBlockEntity.MAX_WIRELESS)), WirelessOrder::order,
                WirelessOrder::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    public static void init()
    {
        PayloadTypeRegistry.playS2C().register(NetworkState.TYPE, NetworkState.CODEC);
        PayloadTypeRegistry.playS2C().register(ActionResult.TYPE, ActionResult.CODEC);
        PayloadTypeRegistry.playC2S().register(NetAction.TYPE, NetAction.CODEC);
        PayloadTypeRegistry.playC2S().register(TerminalAction.TYPE, TerminalAction.CODEC);
        PayloadTypeRegistry.playC2S().register(ConfigureProjector.TYPE, ConfigureProjector.CODEC);
        PayloadTypeRegistry.playC2S().register(SetProjector.TYPE, SetProjector.CODEC);
        PayloadTypeRegistry.playC2S().register(ConfigureCloak.TYPE, ConfigureCloak.CODEC);
        PayloadTypeRegistry.playC2S().register(SelectorAction.TYPE, SelectorAction.CODEC);
        PayloadTypeRegistry.playC2S().register(WirelessOrder.TYPE, WirelessOrder.CODEC);
        PayloadTypeRegistry.playC2S().register(UnlinkWireless.TYPE, UnlinkWireless.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(NetAction.TYPE, (payload, context) -> handleAction(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(TerminalAction.TYPE, (payload, context) -> handleTerminal(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(ConfigureProjector.TYPE, (payload, context) -> handleConfigureProjector(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(ConfigureCloak.TYPE, (payload, context) -> handleConfigureCloak(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(SelectorAction.TYPE, (payload, context) -> handleSelector(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(SetProjector.TYPE, (payload, context) -> handleSetProjector(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(WirelessOrder.TYPE, (payload, context) -> handleWirelessOrder(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(UnlinkWireless.TYPE, (payload, context) -> handleUnlinkWireless(context.player(), payload));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> GlassNetworkManager.onJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> GlassNetworkManager.onLeave(handler.getPlayer()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> GlassNetworkManager.onServerStopped());
    }

    public static void reply(ServerPlayer player, GlassNetworkManager.Result result)
    {
        ServerPlayNetworking.send(player, new ActionResult(result.ok(), result.message(), result.arg()));
        if(result.ok())
        {
            GlassNetworkManager.syncAll(player.level().getServer());
        }
    }

    private static boolean canReach(ServerPlayer player, BlockPos pos)
    {
        return player.level().isLoaded(pos) && player.canInteractWithBlock(pos, REACH_LEEWAY);
    }

    private static void handleAction(ServerPlayer player, NetAction payload)
    {
        if(payload.action() < 0 || payload.action() >= Action.values().length)
        {
            return;
        }
        Action action = Action.values()[payload.action()];
        List<String> a = payload.args();
        if(a.size() != action.args)
        {
            return;
        }
        switch(action)
        {
            case REQUEST_STATE -> GlassNetworkManager.sendState(player);
            case UNLOCK -> reply(player, GlassNetworkManager.unlock(player, a.get(0), a.get(1)));
            case DELETE_CHANNEL -> reply(player, GlassNetworkManager.deleteChannel(player, a.get(0)));
            case CHANGE_PASSWORD -> reply(player, GlassNetworkManager.changePassword(player, a.get(0), a.get(1)));
        }
    }

    private static void handleTerminal(ServerPlayer player, TerminalAction payload)
    {
        ServerLevel level = player.level();
        if(payload.command() < 0 || payload.command() >= TerminalCommand.values().length
                || !canReach(player, payload.pos()) || !(level.getBlockEntity(payload.pos()) instanceof GlassTerminalBlockEntity terminal))
        {
            return;
        }
        reply(player, switch(TerminalCommand.values()[payload.command()])
        {
            case CREATE -> GlassNetworkManager.createChannel(player, terminal, payload.a(), payload.b());
            case MOVE_HERE -> GlassNetworkManager.moveCamera(player, terminal, payload.a());
            case DETACH -> GlassNetworkManager.detachCamera(player, terminal);
        });
    }

    private static void handleConfigureProjector(ServerPlayer player, ConfigureProjector payload)
    {
        ServerLevel level = player.level();
        if(!canReach(player, payload.pos()) || !(level.getBlockEntity(payload.pos()) instanceof GlassProjectorBlockEntity projector))
        {
            return;
        }
        GlassNetworkData data = GlassNetworkManager.data(level.getServer());
        GlassNetworkData.Channel channel = data.channels.get(payload.channelId());
        if(!payload.channelId().isEmpty() && (channel == null || !channel.hasAccess(player.getUUID())))
        {
            reply(player, GlassNetworkManager.Result.fail("glass.msg.noAccess"));
            return;
        }
        //Switching while the projector is on is fine: the screen stays up and the new feed wipes over the old one.
        showChannel(projector, data, payload.channelId(), channel == null ? "" : channel.name);
        reply(player, GlassNetworkManager.Result.ok("glass.msg.projectorSet", projector.channelName));
    }

    /** Points a projector at a channel (leaving cloak mode). An empty id clears it, which switches the screen off. */
    public static void showChannel(GlassProjectorBlockEntity projector, GlassNetworkData data, String channelId, String channelName)
    {
        projector.cloak = false;
        projector.channelId = channelId;
        projector.channelName = channelName;
        if(projector.active && channelId.isEmpty())
        {
            projector.turnOff();
        }
        projector.resolveCamera(data, true);
    }

    private static void handleSelector(ServerPlayer player, SelectorAction payload)
    {
        ServerLevel level = player.level();
        if(payload.op() < 0 || payload.op() >= SelectorOp.values().length || !canReach(player, payload.pos())
                || !(level.getBlockEntity(payload.pos()) instanceof GlassSceneSelectorBlockEntity selector))
        {
            return;
        }
        switch(SelectorOp.values()[payload.op()])
        {
            case LINK -> reply(player, selector.linkNearest()
                    ? GlassNetworkManager.Result.ok("glass.msg.selectorLinked", "")
                    : GlassNetworkManager.Result.fail("glass.msg.noProjectorNear"));
            case ADD -> {
                GlassNetworkData.Channel channel = GlassNetworkManager.data(level.getServer()).channels.get(payload.channelId());
                if(channel == null || !channel.hasAccess(player.getUUID()))
                {
                    reply(player, GlassNetworkManager.Result.fail("glass.msg.noAccess"));
                }
                else if(!selector.addScene(channel.id, channel.name))
                {
                    reply(player, GlassNetworkManager.Result.fail("glass.msg.sceneNotAdded"));
                }
                else
                {
                    reply(player, GlassNetworkManager.Result.ok("glass.msg.sceneAdded", channel.name));
                }
            }
            case REMOVE -> selector.removeScene(payload.index());
            case UP -> selector.moveScene(payload.index(), -1);
            case DOWN -> selector.moveScene(payload.index(), 1);
            case SHOW -> {
                if(!selector.showScene(payload.index()))
                {
                    reply(player, GlassNetworkManager.Result.fail(selector.projector() == null ? "glass.msg.selectorNoProjector" : "glass.msg.sceneGone"));
                }
            }
        }
    }

    private static void handleConfigureCloak(ServerPlayer player, ConfigureCloak payload)
    {
        ServerLevel level = player.level();
        if(!canReach(player, payload.pos()) || !(level.getBlockEntity(payload.pos()) instanceof GlassProjectorBlockEntity projector))
        {
            return;
        }
        GlassProjectorBlockEntity.CloakLimits limits = projector.cloakLimits();
        projector.cloak = payload.cloak();
        projector.cloakDepth = Math.clamp(payload.depth(), 1, limits.depth());
        projector.cloakField = payload.field();
        projector.fieldHeight = Math.clamp(payload.fieldHeight(), 1, limits.height());
        if(projector.active && !projector.hasFeed())
        {
            projector.turnOff();
        }
        projector.sync();
    }

    private static void handleSetProjector(ServerPlayer player, SetProjector payload)
    {
        ServerLevel level = player.level();
        if(!canReach(player, payload.wirelessPos()) || !level.isLoaded(payload.projectorPos()))
        {
            return;
        }
        if(level.getBlockEntity(payload.wirelessPos()) instanceof GlassWirelessBlockEntity
                && level.getBlockEntity(payload.projectorPos()) instanceof GlassProjectorBlockEntity projector
                && !projector.active
                && !projector.wirelessPos.contains(payload.wirelessPos())
                && projector.wirelessPos.size() < GlassProjectorBlockEntity.MAX_WIRELESS)
        {
            projector.wirelessPos.add(payload.wirelessPos());
            projector.sync();
            reply(player, GlassNetworkManager.Result.ok("glass.msg.wirelessLinked", ""));
        }
    }

    private static void handleUnlinkWireless(ServerPlayer player, UnlinkWireless payload)
    {
        ServerLevel level = player.level();
        if(!canReach(player, payload.wirelessPos()) || !level.isLoaded(payload.projectorPos()))
        {
            return;
        }
        if(level.getBlockEntity(payload.projectorPos()) instanceof GlassProjectorBlockEntity projector && projector.wirelessPos.contains(payload.wirelessPos()))
        {
            //Like reordering: the screen's shape is fixed while it is on.
            if(projector.active)
            {
                reply(player, GlassNetworkManager.Result.fail("glass.msg.switchOffFirst"));
                return;
            }
            projector.wirelessPos.remove(payload.wirelessPos());
            projector.sync();
            reply(player, GlassNetworkManager.Result.ok("glass.msg.wirelessUnlinked", ""));
        }
    }

    private static void handleWirelessOrder(ServerPlayer player, WirelessOrder payload)
    {
        ServerLevel level = player.level();
        if(!canReach(player, payload.projectorPos()))
        {
            return;
        }
        if(level.getBlockEntity(payload.projectorPos()) instanceof GlassProjectorBlockEntity projector && !projector.active)
        {
            //The screen only lets you reorder or remove extensions, so only accept positions the projector already has.
            List<BlockPos> order = new ArrayList<>();
            for(BlockPos pos : payload.order())
            {
                if(projector.wirelessPos.contains(pos) && !order.contains(pos))
                {
                    order.add(pos);
                }
            }
            projector.wirelessPos = order;
            projector.sync();
            reply(player, GlassNetworkManager.Result.ok("glass.msg.wirelessOrdered", ""));
        }
    }
}
