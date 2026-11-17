package ryzlar.glass.network;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import ryzlar.glass.GlassConfig;
import ryzlar.glass.blockentity.GlassProjectorBlockEntity;
import ryzlar.glass.blockentity.GlassTerminalBlockEntity;
import ryzlar.glass.blockentity.GlassWirelessBlockEntity;
import ryzlar.glass.Glass;

import java.util.ArrayList;
import java.util.List;

/**
 * Packets between the GLASS screens and the server. The server owns all network data; the client only gets the part
 * the logged-in account may see ({@link NetworkState}) and a short reply to each action ({@link ActionResult}).
 */
public final class GlassNetworking
{
    private static final double REACH_LEEWAY = 4.0D;
    private static final StreamCodec<ByteBuf, String> SHORT_STRING = ByteBufCodecs.stringUtf8(128);

    private GlassNetworking() {}

    // ---- Server to client ----

    public record CameraInfo(String id, String name, String dimension, BlockPos pos, Direction facing)
    {
        public static final StreamCodec<ByteBuf, CameraInfo> CODEC = StreamCodec.composite(
                SHORT_STRING, CameraInfo::id,
                SHORT_STRING, CameraInfo::name,
                SHORT_STRING, CameraInfo::dimension,
                BlockPos.STREAM_CODEC, CameraInfo::pos,
                Direction.STREAM_CODEC, CameraInfo::facing,
                CameraInfo::new);
    }

    public record ChannelInfo(String id, String name, String ownerName, boolean owner, List<String> members, List<CameraInfo> cameras)
    {
        public static final StreamCodec<ByteBuf, ChannelInfo> CODEC = StreamCodec.composite(
                SHORT_STRING, ChannelInfo::id,
                SHORT_STRING, ChannelInfo::name,
                SHORT_STRING, ChannelInfo::ownerName,
                ByteBufCodecs.BOOL, ChannelInfo::owner,
                SHORT_STRING.apply(ByteBufCodecs.list(1024)), ChannelInfo::members,
                CameraInfo.CODEC.apply(ByteBufCodecs.list(1024)), ChannelInfo::cameras,
                ChannelInfo::new);
    }

    /** What the player's GLASS account can see. An empty account name means not logged in. */
    public record NetworkState(String account, List<ChannelInfo> channels) implements CustomPacketPayload
    {
        public static final Type<NetworkState> TYPE = new Type<>(Glass.id("network_state"));
        public static final StreamCodec<ByteBuf, NetworkState> CODEC = StreamCodec.composite(
                SHORT_STRING, NetworkState::account,
                ChannelInfo.CODEC.apply(ByteBufCodecs.list(4096)), NetworkState::channels,
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
        REGISTER(3),       //username, password, remember (1/0)
        LOGIN(3),          //username, password, remember (1/0)
        LOGOUT(0),
        CREATE_CHANNEL(2), //name, password
        JOIN_CHANNEL(2),   //name, password
        LEAVE_CHANNEL(1),  //channel id
        DELETE_CHANNEL(1), //channel id
        CHANGE_PASSWORD(2),//channel id, new password
        KICK_MEMBER(2),    //channel id, account name
        REMOVE_CAMERA(2);  //channel id, camera id

        final int args;

        Action(int args)
        {
            this.args = args;
        }
    }

    /** An account or channel command. Arguments are plain strings; see {@link Action}. */
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

    /** Puts a terminal's camera on a channel under a name. An empty channel id takes it off. */
    public record LinkCamera(BlockPos pos, String channelId, String name) implements CustomPacketPayload
    {
        public static final Type<LinkCamera> TYPE = new Type<>(Glass.id("link_camera"));
        public static final StreamCodec<ByteBuf, LinkCamera> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, LinkCamera::pos,
                SHORT_STRING, LinkCamera::channelId,
                SHORT_STRING, LinkCamera::name,
                LinkCamera::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /** Sets what a projector shows: a channel and one camera, or all of its cameras in turn. */
    public record ConfigureProjector(BlockPos pos, String channelId, String cameraId, boolean cycle, int cycleSeconds) implements CustomPacketPayload
    {
        public static final Type<ConfigureProjector> TYPE = new Type<>(Glass.id("configure_projector"));
        public static final StreamCodec<ByteBuf, ConfigureProjector> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, ConfigureProjector::pos,
                SHORT_STRING, ConfigureProjector::channelId,
                SHORT_STRING, ConfigureProjector::cameraId,
                ByteBufCodecs.BOOL, ConfigureProjector::cycle,
                ByteBufCodecs.VAR_INT, ConfigureProjector::cycleSeconds,
                ConfigureProjector::new);

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
        PayloadTypeRegistry.playC2S().register(LinkCamera.TYPE, LinkCamera.CODEC);
        PayloadTypeRegistry.playC2S().register(ConfigureProjector.TYPE, ConfigureProjector.CODEC);
        PayloadTypeRegistry.playC2S().register(SetProjector.TYPE, SetProjector.CODEC);
        PayloadTypeRegistry.playC2S().register(WirelessOrder.TYPE, WirelessOrder.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(NetAction.TYPE, (payload, context) -> handleAction(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(LinkCamera.TYPE, (payload, context) -> handleLinkCamera(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(ConfigureProjector.TYPE, (payload, context) -> handleConfigureProjector(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(SetProjector.TYPE, (payload, context) -> handleSetProjector(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(WirelessOrder.TYPE, (payload, context) -> handleWirelessOrder(context.player(), payload));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> GlassNetworkManager.onJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> GlassNetworkManager.onLeave(handler.getPlayer()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> GlassNetworkManager.onServerStopped());
    }

    private static void reply(ServerPlayer player, GlassNetworkManager.Result result)
    {
        ServerPlayNetworking.send(player, new ActionResult(result.ok(), result.message(), result.arg()));
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
        GlassNetworkManager.Result result = switch(action)
        {
            case REQUEST_STATE -> null;
            case REGISTER -> GlassNetworkManager.register(player, a.get(0), a.get(1), a.get(2).equals("1"));
            case LOGIN -> GlassNetworkManager.login(player, a.get(0), a.get(1), a.get(2).equals("1"));
            case LOGOUT -> GlassNetworkManager.logout(player);
            case CREATE_CHANNEL -> GlassNetworkManager.createChannel(player, a.get(0), a.get(1));
            case JOIN_CHANNEL -> GlassNetworkManager.joinChannel(player, a.get(0), a.get(1));
            case LEAVE_CHANNEL -> GlassNetworkManager.leaveChannel(player, a.get(0));
            case DELETE_CHANNEL -> GlassNetworkManager.deleteChannel(player, a.get(0));
            case CHANGE_PASSWORD -> GlassNetworkManager.changeChannelPassword(player, a.get(0), a.get(1));
            case KICK_MEMBER -> GlassNetworkManager.kickMember(player, a.get(0), a.get(1));
            case REMOVE_CAMERA -> GlassNetworkManager.removeCamera(player, a.get(0), a.get(1));
        };
        if(result == null)
        {
            GlassNetworkManager.sendState(player);
            return;
        }
        reply(player, result);
        if(result.ok())
        {
            GlassNetworkManager.syncAll(player.level().getServer());
        }
    }

    /** May this player change a block that is on {@code channelId}? Yes if it has none, it no longer exists, or they are a member. */
    private static boolean mayEdit(ServerPlayer player, String channelId)
    {
        if(channelId.isEmpty())
        {
            return true;
        }
        GlassNetworkData.Channel channel = GlassNetworkManager.data(player.level().getServer()).channels.get(channelId);
        return channel == null || channel.isMember(GlassNetworkManager.account(player));
    }

    private static void handleLinkCamera(ServerPlayer player, LinkCamera payload)
    {
        ServerLevel level = player.level();
        if(!canReach(player, payload.pos()) || !(level.getBlockEntity(payload.pos()) instanceof GlassTerminalBlockEntity terminal))
        {
            return;
        }
        if(GlassNetworkManager.account(player) == null)
        {
            reply(player, new GlassNetworkManager.Result(false, "glass.msg.notLoggedIn", ""));
            return;
        }
        if(!mayEdit(player, terminal.channelId))
        {
            reply(player, new GlassNetworkManager.Result(false, "glass.msg.noAccess", ""));
            return;
        }
        if(payload.channelId().isEmpty())
        {
            terminal.unlink(level.getServer());
            reply(player, new GlassNetworkManager.Result(true, "glass.msg.cameraUnlinked", ""));
        }
        else
        {
            GlassNetworkManager.Result result = GlassNetworkManager.linkCamera(player, payload.channelId(), terminal.cameraId.isEmpty() ? null : terminal.cameraId,
                    terminal.channelId.isEmpty() ? null : terminal.channelId, payload.name(), level.dimension(), terminal.getBlockPos(), terminal.facing);
            if(result.ok())
            {
                terminal.channelId = payload.channelId();
                terminal.cameraId = result.arg();
                terminal.cameraName = payload.name().trim();
                terminal.sync();
                result = new GlassNetworkManager.Result(true, "glass.msg.cameraLinked", terminal.cameraName);
            }
            reply(player, result);
        }
        GlassNetworkManager.syncAll(level.getServer());
    }

    private static void handleConfigureProjector(ServerPlayer player, ConfigureProjector payload)
    {
        ServerLevel level = player.level();
        if(!canReach(player, payload.pos()) || !(level.getBlockEntity(payload.pos()) instanceof GlassProjectorBlockEntity projector))
        {
            return;
        }
        if(GlassNetworkManager.account(player) == null)
        {
            reply(player, new GlassNetworkManager.Result(false, "glass.msg.notLoggedIn", ""));
            return;
        }
        if(!mayEdit(player, projector.channelId) || !mayEdit(player, payload.channelId()))
        {
            reply(player, new GlassNetworkManager.Result(false, "glass.msg.noAccess", ""));
            return;
        }
        GlassNetworkData data = GlassNetworkManager.data(level.getServer());
        GlassNetworkData.Channel channel = data.channels.get(payload.channelId());
        if(!payload.channelId().isEmpty() && channel == null)
        {
            reply(player, new GlassNetworkManager.Result(false, "glass.msg.noAccess", ""));
            return;
        }
        if(projector.active && !payload.channelId().equals(projector.channelId))
        {
            reply(player, new GlassNetworkManager.Result(false, "glass.msg.powerDown", ""));
            return;
        }
        projector.channelId = payload.channelId();
        projector.channelName = channel == null ? "" : channel.name;
        projector.cameraId = payload.cameraId();
        projector.cycle = payload.cycle();
        projector.cycleSeconds = Math.max(GlassConfig.get().minCycleSeconds, Math.min(600, payload.cycleSeconds()));
        projector.resolveCamera(data, true);
        projector.sync();
        reply(player, new GlassNetworkManager.Result(true, "glass.msg.projectorSet", projector.channelName));
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
                && projector.wirelessPos.size() < GlassProjectorBlockEntity.MAX_WIRELESS
                && mayEdit(player, projector.channelId))
        {
            projector.wirelessPos.add(payload.wirelessPos());
            projector.sync();
            reply(player, new GlassNetworkManager.Result(true, "glass.msg.wirelessLinked", ""));
        }
    }

    private static void handleWirelessOrder(ServerPlayer player, WirelessOrder payload)
    {
        ServerLevel level = player.level();
        if(!canReach(player, payload.projectorPos()))
        {
            return;
        }
        if(level.getBlockEntity(payload.projectorPos()) instanceof GlassProjectorBlockEntity projector && !projector.active && mayEdit(player, projector.channelId))
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
            reply(player, new GlassNetworkManager.Result(true, "glass.msg.wirelessOrdered", ""));
        }
    }
}
