package ryzlar.glass.holo;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import ryzlar.glass.Glass;
import ryzlar.glass.GlassRegistry;
import ryzlar.glass.network.GlassNetworkManager;
import ryzlar.glass.network.GlassNetworking;

import java.util.ArrayList;
import java.util.List;

/** Packets of the hologram kit. */
public final class HoloNetworking
{
    private static final double REACH_LEEWAY = 4.0D;
    private static final StreamCodec<ByteBuf, String> SHORT_STRING = ByteBufCodecs.stringUtf8(128);

    private HoloNetworking() {}

    /** The scanner's area and the name for the next scan. */
    public record ScannerConfig(BlockPos pos, int offsetX, int offsetY, int offsetZ, int sizeX, int sizeY, int sizeZ, String name) implements CustomPacketPayload
    {
        public static final Type<ScannerConfig> TYPE = new Type<>(Glass.id("scanner_config"));
        public static final StreamCodec<ByteBuf, ScannerConfig> CODEC = new StreamCodec<>()
        {
            @Override
            public ScannerConfig decode(ByteBuf buf)
            {
                return new ScannerConfig(BlockPos.STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), SHORT_STRING.decode(buf));
            }

            @Override
            public void encode(ByteBuf buf, ScannerConfig c)
            {
                BlockPos.STREAM_CODEC.encode(buf, c.pos());
                ByteBufCodecs.VAR_INT.encode(buf, c.offsetX());
                ByteBufCodecs.VAR_INT.encode(buf, c.offsetY());
                ByteBufCodecs.VAR_INT.encode(buf, c.offsetZ());
                ByteBufCodecs.VAR_INT.encode(buf, c.sizeX());
                ByteBufCodecs.VAR_INT.encode(buf, c.sizeY());
                ByteBufCodecs.VAR_INT.encode(buf, c.sizeZ());
                SHORT_STRING.encode(buf, c.name());
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /** Scan the scanner's area now. */
    public record ScannerScan(BlockPos pos) implements CustomPacketPayload
    {
        public static final Type<ScannerScan> TYPE = new Type<>(Glass.id("scanner_scan"));
        public static final StreamCodec<ByteBuf, ScannerScan> CODEC = BlockPos.STREAM_CODEC.map(ScannerScan::new, ScannerScan::pos);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /** The holo projector's look, scale and rotation. */
    public record ProjectorConfig(BlockPos pos, int look, int scale, int rotation) implements CustomPacketPayload
    {
        public static final Type<ProjectorConfig> TYPE = new Type<>(Glass.id("holo_projector_config"));
        public static final StreamCodec<ByteBuf, ProjectorConfig> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, ProjectorConfig::pos,
                ByteBufCodecs.VAR_INT, ProjectorConfig::look,
                ByteBufCodecs.VAR_INT, ProjectorConfig::scale,
                ByteBufCodecs.VAR_INT, ProjectorConfig::rotation,
                ProjectorConfig::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    public enum ProjectorOp { LINK_EMITTERS, EJECT }

    public record ProjectorAction(BlockPos pos, int op) implements CustomPacketPayload
    {
        public static final Type<ProjectorAction> TYPE = new Type<>(Glass.id("holo_projector_action"));
        public static final StreamCodec<ByteBuf, ProjectorAction> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, ProjectorAction::pos,
                ByteBufCodecs.VAR_INT, ProjectorAction::op,
                ProjectorAction::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /** Client: send me the blocks of this projection. */
    public record RequestProjection(String id) implements CustomPacketPayload
    {
        public static final Type<RequestProjection> TYPE = new Type<>(Glass.id("request_projection"));
        public static final StreamCodec<ByteBuf, RequestProjection> CODEC = SHORT_STRING.map(RequestProjection::new, RequestProjection::id);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }


    /** The blocks of a projection: a palette of block state ids, and a palette index per block. */
    /** Part of a scan's panorama, from the client that took it (see PanoramaStore). */
    public record PanoramaUpload(String id, int index, int total, byte[] data) implements CustomPacketPayload
    {
        public static final Type<PanoramaUpload> TYPE = new Type<>(Glass.id("panorama_upload"));
        public static final StreamCodec<ByteBuf, PanoramaUpload> CODEC = StreamCodec.composite(
                SHORT_STRING, PanoramaUpload::id,
                ByteBufCodecs.VAR_INT, PanoramaUpload::index,
                ByteBufCodecs.VAR_INT, PanoramaUpload::total,
                ByteBufCodecs.byteArray(PanoramaStore.UPLOAD_PART), PanoramaUpload::data,
                PanoramaUpload::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /** Asks for a scan's panorama. */
    public record RequestPanorama(String id) implements CustomPacketPayload
    {
        public static final Type<RequestPanorama> TYPE = new Type<>(Glass.id("request_panorama"));
        public static final StreamCodec<ByteBuf, RequestPanorama> CODEC = SHORT_STRING.map(RequestPanorama::new, RequestPanorama::id);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /** Part of a scan's panorama, for a client that asked (zero parts: there is none yet). */
    public record PanoramaDownload(String id, int index, int total, byte[] data) implements CustomPacketPayload
    {
        public static final Type<PanoramaDownload> TYPE = new Type<>(Glass.id("panorama_download"));
        public static final StreamCodec<ByteBuf, PanoramaDownload> CODEC = StreamCodec.composite(
                SHORT_STRING, PanoramaDownload::id,
                ByteBufCodecs.VAR_INT, PanoramaDownload::index,
                ByteBufCodecs.VAR_INT, PanoramaDownload::total,
                ByteBufCodecs.byteArray(PanoramaStore.DOWNLOAD_PART), PanoramaDownload::data,
                PanoramaDownload::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    public record ProjectionBlocks(String id, int sizeX, int sizeY, int sizeZ, List<Integer> palette, List<Integer> blocks) implements CustomPacketPayload
    {
        public static final Type<ProjectionBlocks> TYPE = new Type<>(Glass.id("projection_blocks"));
        public static final StreamCodec<ByteBuf, ProjectionBlocks> CODEC = StreamCodec.composite(
                SHORT_STRING, ProjectionBlocks::id,
                ByteBufCodecs.VAR_INT, ProjectionBlocks::sizeX,
                ByteBufCodecs.VAR_INT, ProjectionBlocks::sizeY,
                ByteBufCodecs.VAR_INT, ProjectionBlocks::sizeZ,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(65536)), ProjectionBlocks::palette,
                //A scanner's box, or a holo cube's room (which reaches higher).
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(Math.max(HoloProjectionData.MAX_SIZE * HoloProjectionData.MAX_SIZE * HoloProjectionData.MAX_SIZE, RoomScan.MAX_VOLUME))), ProjectionBlocks::blocks,
                ProjectionBlocks::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    public static void init()
    {
        PayloadTypeRegistry.playC2S().register(ScannerConfig.TYPE, ScannerConfig.CODEC);
        PayloadTypeRegistry.playC2S().register(ScannerScan.TYPE, ScannerScan.CODEC);
        PayloadTypeRegistry.playC2S().register(ProjectorConfig.TYPE, ProjectorConfig.CODEC);
        PayloadTypeRegistry.playC2S().register(ProjectorAction.TYPE, ProjectorAction.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestProjection.TYPE, RequestProjection.CODEC);
        PayloadTypeRegistry.playS2C().register(ProjectionBlocks.TYPE, ProjectionBlocks.CODEC);
        PayloadTypeRegistry.playC2S().register(PanoramaUpload.TYPE, PanoramaUpload.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestPanorama.TYPE, RequestPanorama.CODEC);
        PayloadTypeRegistry.playS2C().register(PanoramaDownload.TYPE, PanoramaDownload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PanoramaUpload.TYPE, (payload, context) -> PanoramaStore.receive(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(RequestPanorama.TYPE, (payload, context) -> PanoramaStore.send(context.player(), payload.id()));
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PanoramaStore.forget(handler.getPlayer()));

        ServerPlayNetworking.registerGlobalReceiver(ScannerConfig.TYPE, (payload, context) -> handleScannerConfig(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(ScannerScan.TYPE, (payload, context) -> handleScan(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(ProjectorConfig.TYPE, (payload, context) -> handleProjectorConfig(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(ProjectorAction.TYPE, (payload, context) -> handleProjectorAction(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(RequestProjection.TYPE, (payload, context) -> handleRequest(context.player(), payload));
    }

    private static boolean canReach(ServerPlayer player, BlockPos pos)
    {
        return player.level().isLoaded(pos) && player.canInteractWithBlock(pos, REACH_LEEWAY);
    }

    private static void handleScannerConfig(ServerPlayer player, ScannerConfig payload)
    {
        if(canReach(player, payload.pos()) && player.level().getBlockEntity(payload.pos()) instanceof HoloScannerBlockEntity scanner)
        {
            scanner.configure(payload.offsetX(), payload.offsetY(), payload.offsetZ(), payload.sizeX(), payload.sizeY(), payload.sizeZ(), payload.name());
        }
    }

    /** A scan costs one amethyst shard (nothing in creative), and gives a crystal holding the projection. */
    private static void handleScan(ServerPlayer player, ScannerScan payload)
    {
        ServerLevel level = player.level();
        if(!canReach(player, payload.pos()) || !(level.getBlockEntity(payload.pos()) instanceof HoloScannerBlockEntity scanner))
        {
            return;
        }
        int shard = player.getInventory().findSlotMatchingItem(new ItemStack(Items.AMETHYST_SHARD));
        if(!player.hasInfiniteMaterials() && shard < 0)
        {
            GlassNetworking.reply(player, GlassNetworkManager.Result.fail("glass.msg.scanNeedsShard"));
            return;
        }
        String name = scanner.name.isBlank() ? Component.translatable("glass.ui.scan.defaultName").getString() : scanner.name.trim();
        HoloProjectionData.Projection projection = HoloProjectionData.scan(level, scanner.regionOrigin(), scanner.sizeX, scanner.sizeY, scanner.sizeZ, name);
        if(projection == null)
        {
            GlassNetworking.reply(player, GlassNetworkManager.Result.fail("glass.msg.scanNotLoaded"));
            return;
        }
        if(!player.hasInfiniteMaterials())
        {
            player.getInventory().getItem(shard).shrink(1);
        }
        ItemStack crystal = new ItemStack(GlassRegistry.PROJECTION_CRYSTAL);
        crystal.set(GlassRegistry.PROJECTION, new ProjectionRef(projection.id(), projection.name(), projection.sizeX(), projection.sizeY(), projection.sizeZ()));
        if(!player.addItem(crystal))
        {
            player.drop(crystal, false);
        }
        GlassNetworking.reply(player, GlassNetworkManager.Result.ok("glass.msg.scanned", projection.name()));
    }

    private static void handleProjectorConfig(ServerPlayer player, ProjectorConfig payload)
    {
        if(canReach(player, payload.pos()) && player.level().getBlockEntity(payload.pos()) instanceof HoloProjectorBlockEntity projector)
        {
            projector.configure(payload.look(), payload.scale(), payload.rotation());
        }
    }

    private static void handleProjectorAction(ServerPlayer player, ProjectorAction payload)
    {
        if(payload.op() < 0 || payload.op() >= ProjectorOp.values().length || !canReach(player, payload.pos())
                || !(player.level().getBlockEntity(payload.pos()) instanceof HoloProjectorBlockEntity projector))
        {
            return;
        }
        switch(ProjectorOp.values()[payload.op()])
        {
            case LINK_EMITTERS -> GlassNetworking.reply(player, GlassNetworkManager.Result.ok("glass.msg.emittersLinked", Integer.toString(projector.linkEmitters())));
            case EJECT -> {
                if(!projector.crystal.isEmpty())
                {
                    ItemStack out = projector.takeCrystal();
                    if(!player.addItem(out))
                    {
                        player.drop(out, false);
                    }
                }
            }
        }
    }

    private static void handleRequest(ServerPlayer player, RequestProjection payload)
    {
        HoloProjectionData.Projection projection = HoloProjectionData.find(player.level().getServer(), payload.id());
        if(projection == null)
        {
            return;
        }
        List<Integer> palette = new ArrayList<>(projection.palette().size());
        for(BlockState state : projection.palette())
        {
            palette.add(Block.getId(state));
        }
        List<Integer> blocks = new ArrayList<>(projection.blocks().length);
        for(int block : projection.blocks())
        {
            blocks.add(block);
        }
        ServerPlayNetworking.send(player, new ProjectionBlocks(projection.id(), projection.sizeX(), projection.sizeY(), projection.sizeZ(), palette, blocks));
    }
}
