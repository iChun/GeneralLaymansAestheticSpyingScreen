package ryzlar.glass.holo;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
    /**
     * Largest projection packet. A scan is bounded (see {@link HoloProjectionData#MAX_EXTRAS_BYTES}), but it can still
     * be more than vanilla's 1 MiB for one packet, so it is sent split up.
     */
    public static final int MAX_PROJECTION_BYTES = 8 * 1024 * 1024;
    /** How soon the same player gets the same projection again (the client asks again after 5 s if it got nothing). */
    private static final long RESEND_MILLIS = 4000L;
    private static final java.util.Map<java.util.UUID, java.util.Map<String, Long>> SENT = new java.util.HashMap<>();

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


    /**
     * A holo cube scan's extras (none for a scanner's crystal): its block entities and entities as saved, each with its
     * place in the scan's box, and each block's light (see {@link HoloProjectionData.Projection}).
     */
    public record ProjectionExtras(List<CompoundTag> blockEntities, List<CompoundTag> entities, List<Integer> light)
    {
        private static final int MAX_LIGHT = HoloSphere.SIZE * HoloSphere.SIZE * HoloSphere.SIZE;
        public static final StreamCodec<ByteBuf, ProjectionExtras> CODEC = StreamCodec.composite(
                ByteBufCodecs.COMPOUND_TAG.apply(ByteBufCodecs.list(4096)), ProjectionExtras::blockEntities,
                ByteBufCodecs.COMPOUND_TAG.apply(ByteBufCodecs.list(1024)), ProjectionExtras::entities,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MAX_LIGHT)), ProjectionExtras::light,
                ProjectionExtras::new);
    }

    /** The blocks of a projection: a palette of block state ids, and a palette index per block; and a cube scan's extras. */
    public record ProjectionBlocks(String id, int sizeX, int sizeY, int sizeZ, List<Integer> palette, List<Integer> blocks, ProjectionExtras extras)
            implements CustomPacketPayload
    {
        public static final Type<ProjectionBlocks> TYPE = new Type<>(Glass.id("projection_blocks"));
        public static final StreamCodec<ByteBuf, ProjectionBlocks> CODEC = StreamCodec.composite(
                SHORT_STRING, ProjectionBlocks::id,
                ByteBufCodecs.VAR_INT, ProjectionBlocks::sizeX,
                ByteBufCodecs.VAR_INT, ProjectionBlocks::sizeY,
                ByteBufCodecs.VAR_INT, ProjectionBlocks::sizeZ,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(65536)), ProjectionBlocks::palette,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(HoloProjectionData.MAX_SIZE * HoloProjectionData.MAX_SIZE * HoloProjectionData.MAX_SIZE)), ProjectionBlocks::blocks,
                ProjectionExtras.CODEC, ProjectionBlocks::extras,
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
        PayloadTypeRegistry.playS2C().registerLarge(ProjectionBlocks.TYPE, ProjectionBlocks.CODEC, MAX_PROJECTION_BYTES);

        ServerPlayNetworking.registerGlobalReceiver(ScannerConfig.TYPE, (payload, context) -> handleScannerConfig(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(ScannerScan.TYPE, (payload, context) -> handleScan(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(ProjectorConfig.TYPE, (payload, context) -> handleProjectorConfig(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(ProjectorAction.TYPE, (payload, context) -> handleProjectorAction(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(RequestProjection.TYPE, (payload, context) -> handleRequest(context.player(), payload));
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SENT.remove(handler.getPlayer().getUUID()));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> SENT.clear());
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
            GlassNetworking.tell(player, GlassNetworkManager.Result.fail("glass.msg.scanNeedsShard"));
            return;
        }
        String name = scanner.name.isBlank() ? Component.translatable("glass.ui.scan.defaultName").getString() : scanner.name.trim();
        HoloProjectionData.Projection projection = HoloProjectionData.scan(level, scanner.regionOrigin(), scanner.sizeX, scanner.sizeY, scanner.sizeZ, name);
        if(projection == null)
        {
            GlassNetworking.tell(player, GlassNetworkManager.Result.fail("glass.msg.scanNotLoaded"));
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
        GlassNetworking.tell(player, GlassNetworkManager.Result.ok("glass.msg.scanned", projection.name()));
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
            case LINK_EMITTERS -> GlassNetworking.tell(player, GlassNetworkManager.Result.ok("glass.msg.emittersLinked", Integer.toString(projector.linkEmitters())));
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

    /**
     * Sends a projection's blocks to the player who asked. A big one is a lot to build and send, so the same player gets
     * the same one at most once per {@link #RESEND_MILLIS} (a client asking over and over cannot make the server work).
     */
    private static void handleRequest(ServerPlayer player, RequestProjection payload)
    {
        HoloProjectionData.Projection projection = HoloProjectionData.find(player.level().getServer(), payload.id());
        if(projection == null)
        {
            return;
        }
        long now = net.minecraft.Util.getMillis();
        java.util.Map<String, Long> sent = SENT.computeIfAbsent(player.getUUID(), k -> new java.util.HashMap<>());
        Long last = sent.get(projection.id());
        if(last != null && now - last < RESEND_MILLIS)
        {
            return;
        }
        sent.values().removeIf(time -> now - time >= RESEND_MILLIS);
        sent.put(projection.id(), now);
        ServerPlayNetworking.send(player, blocksOf(projection));
    }

    /** The packet carrying a projection's blocks (and a cube scan's extras) to a client. */
    public static ProjectionBlocks blocksOf(HoloProjectionData.Projection projection)
    {
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
        List<Integer> light = new ArrayList<>(projection.light().length);
        for(int value : projection.light())
        {
            light.add(value);
        }
        ProjectionExtras extras = new ProjectionExtras(projection.blockEntities(), projection.entities(), light);
        return new ProjectionBlocks(projection.id(), projection.sizeX(), projection.sizeY(), projection.sizeZ(), palette, blocks, extras);
    }
}
