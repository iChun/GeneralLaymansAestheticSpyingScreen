package ryzlar.glass.gadget;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import ryzlar.glass.Glass;
import ryzlar.glass.network.GlassNetworkData;
import ryzlar.glass.network.GlassNetworkManager;

/** The gadgets' packets: tuning a tablet and finding its camera, motion alerts, and who wears optical camo. */
public final class GadgetNetworking
{
    private static final StreamCodec<ByteBuf, String> SHORT_STRING = ByteBufCodecs.stringUtf8(128);

    private GadgetNetworking() {}

    /** Tunes the tablet in this hand to a channel. */
    public record LinkTablet(boolean offhand, String channelId) implements CustomPacketPayload
    {
        public static final Type<LinkTablet> TYPE = new Type<>(Glass.id("link_tablet"));
        public static final StreamCodec<ByteBuf, LinkTablet> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, LinkTablet::offhand, SHORT_STRING, LinkTablet::channelId, LinkTablet::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /** Asks where a channel's camera is (for a tablet tuned to it). */
    public record RequestCamera(String channelId) implements CustomPacketPayload
    {
        public static final Type<RequestCamera> TYPE = new Type<>(Glass.id("request_camera"));
        public static final StreamCodec<ByteBuf, RequestCamera> CODEC = SHORT_STRING.map(RequestCamera::new, RequestCamera::channelId);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /** Where a channel's camera is: its dimension, block and the way it looks (or {@code ok} false: none, or no access). */
    public record CameraAt(String channelId, boolean ok, String dimension, BlockPos pos, Direction facing) implements CustomPacketPayload
    {
        public static final Type<CameraAt> TYPE = new Type<>(Glass.id("camera_at"));
        public static final StreamCodec<ByteBuf, CameraAt> CODEC = StreamCodec.composite(
                SHORT_STRING, CameraAt::channelId,
                ByteBufCodecs.BOOL, CameraAt::ok,
                SHORT_STRING, CameraAt::dimension,
                BlockPos.STREAM_CODEC, CameraAt::pos,
                Direction.STREAM_CODEC, CameraAt::facing,
                CameraAt::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /** A motion sensor saw someone through this channel's camera: its screens flash ALERT for a moment. */
    public record ChannelAlert(String channelId, int ticks) implements CustomPacketPayload
    {
        public static final Type<ChannelAlert> TYPE = new Type<>(Glass.id("channel_alert"));
        public static final StreamCodec<ByteBuf, ChannelAlert> CODEC = StreamCodec.composite(
                SHORT_STRING, ChannelAlert::channelId, ByteBufCodecs.VAR_INT, ChannelAlert::ticks, ChannelAlert::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /** Someone wears optical camo for this many ticks (0: it broke). */
    public record CamoState(int entityId, int ticks) implements CustomPacketPayload
    {
        public static final Type<CamoState> TYPE = new Type<>(Glass.id("camo_state"));
        public static final StreamCodec<ByteBuf, CamoState> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, CamoState::entityId, ByteBufCodecs.VAR_INT, CamoState::ticks, CamoState::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    public static void init()
    {
        PayloadTypeRegistry.playC2S().register(LinkTablet.TYPE, LinkTablet.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestCamera.TYPE, RequestCamera.CODEC);
        PayloadTypeRegistry.playS2C().register(CameraAt.TYPE, CameraAt.CODEC);
        PayloadTypeRegistry.playS2C().register(ChannelAlert.TYPE, ChannelAlert.CODEC);
        PayloadTypeRegistry.playS2C().register(CamoState.TYPE, CamoState.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(LinkTablet.TYPE, (payload, context) -> linkTablet(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(RequestCamera.TYPE, (payload, context) -> sendCamera(context.player(), payload.channelId()));
    }

    private static void linkTablet(ServerPlayer player, LinkTablet payload)
    {
        ItemStack stack = player.getItemInHand(payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if(!stack.is(Gadgets.GLASS_TABLET))
        {
            return;
        }
        GlassNetworkData.Channel channel = GlassNetworkManager.data(player.level().getServer()).channels.get(payload.channelId());
        if(channel == null || !channel.hasAccess(player.getUUID()))
        {
            return;
        }
        stack.set(Gadgets.TABLET_LINK, new TabletLink(channel.id, channel.name));
        sendCamera(player, channel.id);
    }

    private static void sendCamera(ServerPlayer player, String channelId)
    {
        GlassNetworkData.Channel channel = GlassNetworkManager.data(player.level().getServer()).channels.get(channelId);
        if(channel == null || !channel.hasAccess(player.getUUID()) || channel.camera == null)
        {
            ServerPlayNetworking.send(player, new CameraAt(channelId, false, "", BlockPos.ZERO, Direction.NORTH));
            return;
        }
        GlassNetworkData.Camera camera = channel.camera;
        ServerPlayNetworking.send(player, new CameraAt(channelId, true, camera.dimension().location().toString(), camera.pos(), camera.facing()));
    }

    /** Tells everyone in the world a channel's camera saw someone (their client knows which screens show it). */
    public static void sendAlert(ServerLevel level, String channelId)
    {
        for(ServerPlayer player : level.getServer().getPlayerList().getPlayers())
        {
            ServerPlayNetworking.send(player, new ChannelAlert(channelId, MotionSensorBlockEntity.ALERT_TICKS));
        }
    }

    /** Tells the player and everyone who can see them that their camo is on (for this long) or off. */
    public static void sendCamo(ServerPlayer player, int ticks)
    {
        CamoState state = new CamoState(player.getId(), ticks);
        ServerPlayNetworking.send(player, state);
        for(ServerPlayer watcher : PlayerLookup.tracking(player))
        {
            ServerPlayNetworking.send(watcher, state);
        }
    }
}
