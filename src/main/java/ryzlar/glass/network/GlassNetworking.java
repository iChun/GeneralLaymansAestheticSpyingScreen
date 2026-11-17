package ryzlar.glass.network;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import ryzlar.glass.Glass;
import ryzlar.glass.blockentity.GlassProjectorBlockEntity;
import ryzlar.glass.blockentity.GlassTerminalBlockEntity;
import ryzlar.glass.blockentity.GlassWirelessBlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-to-server packets sent by the GLASS screens. Ports of PacketSetChannel, PacketSetProjector and PacketWirelessOrder.
 * Unlike 1.12, the server checks that the player can reach the block and owns the channel name they send.
 */
public final class GlassNetworking
{
    public static final int MAX_CHANNEL_NAME = 15;
    private static final double REACH_LEEWAY = 4.0D;

    private GlassNetworking() {}

    /** Sets a terminal's channel, or the channel a (not active) projector will show. */
    public record SetChannel(BlockPos pos, String channel) implements CustomPacketPayload
    {
        public static final Type<SetChannel> TYPE = new Type<>(Glass.id("set_channel"));
        public static final StreamCodec<ByteBuf, SetChannel> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SetChannel::pos,
                ByteBufCodecs.stringUtf8(64), SetChannel::channel,
                SetChannel::new);

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
        PayloadTypeRegistry.playC2S().register(SetChannel.TYPE, SetChannel.CODEC);
        PayloadTypeRegistry.playC2S().register(SetProjector.TYPE, SetProjector.CODEC);
        PayloadTypeRegistry.playC2S().register(WirelessOrder.TYPE, WirelessOrder.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(SetChannel.TYPE, (payload, context) -> handleSetChannel(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(SetProjector.TYPE, (payload, context) -> handleSetProjector(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(WirelessOrder.TYPE, (payload, context) -> handleWirelessOrder(context.player(), payload));
    }

    /** Channels are "public:name" or "playername:name". */
    public static String channelPrefix(boolean isPublic, String playerName)
    {
        return (isPublic ? "public" : playerName) + ":";
    }

    private static boolean canUseChannel(ServerPlayer player, String channel)
    {
        String name;
        if(channel.startsWith(channelPrefix(true, "")))
        {
            name = channel.substring(channelPrefix(true, "").length());
        }
        else if(channel.startsWith(channelPrefix(false, player.getGameProfile().getName())))
        {
            name = channel.substring(channelPrefix(false, player.getGameProfile().getName()).length());
        }
        else
        {
            return false;
        }
        return name.length() <= MAX_CHANNEL_NAME;
    }

    private static boolean canReach(ServerPlayer player, BlockPos pos)
    {
        return player.level().isLoaded(pos) && player.canInteractWithBlock(pos, REACH_LEEWAY);
    }

    private static void handleSetChannel(ServerPlayer player, SetChannel payload)
    {
        ServerLevel level = player.level();
        if(!canReach(player, payload.pos()))
        {
            return;
        }
        if(level.getBlockEntity(payload.pos()) instanceof GlassTerminalBlockEntity terminal)
        {
            if(canUseChannel(player, payload.channel()))
            {
                terminal.channelName = payload.channel();
                terminal.sync();
            }
        }
        else if(level.getBlockEntity(payload.pos()) instanceof GlassProjectorBlockEntity projector && !projector.active)
        {
            //Projectors may tune in to any public channel or the player's own ones (that is all the screen offers).
            if(canUseChannel(player, payload.channel()))
            {
                projector.setChannel = payload.channel();
                projector.sync();
            }
        }
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
                && !projector.wirelessPos.contains(payload.wirelessPos())
                && projector.wirelessPos.size() < GlassProjectorBlockEntity.MAX_WIRELESS)
        {
            projector.wirelessPos.add(payload.wirelessPos());
            projector.sync();
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
        }
    }
}
