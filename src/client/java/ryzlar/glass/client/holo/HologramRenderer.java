package ryzlar.glass.client.holo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import ryzlar.glass.GlassRegistry;
import ryzlar.glass.client.render.GlassRenderTypes;
import ryzlar.glass.holo.HoloProjectorBlockEntity;
import ryzlar.glass.holo.ProjectionRef;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a holo projector's hologram.
 * <p>
 * The projection's blocks are drawn by the game's own block renderer (so faces between blocks are left out and the
 * shading is right), then placed: scaled, turned and set between the emitters (or on the projector). Each side of each
 * block is kept only if the projector or one of its emitters can see it, the way light from a real projector would
 * only land on the sides facing it: with one emitter you get half a model, with emitters all round a whole one.
 * The result is kept and rebuilt only when something about it changes.
 */
public class HologramRenderer implements BlockEntityRenderer<HoloProjectorBlockEntity>
{
    /** Fit with no emitter box: the longest side of the model becomes this many blocks. */
    private static final float DEFAULT_FIT = 3F;

    public HologramRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(HoloProjectorBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay, Vec3 cameraPos)
    {
        if(!(be.getLevel() instanceof ClientLevel level))
        {
            return;
        }
        //On: the nano wave grows out of the projector. Off: it shrinks back into it, then the hologram is gone.
        Hologram hologram;
        float radius;
        if(be.active)
        {
            ProjectionRef ref = be.projection();
            HoloClient.ClientProjection projection = ref == null ? null : HoloClient.projection(ref.id());
            if(projection == null)
            {
                return;
            }
            hologram = HologramCache.get(be, projection, () -> build(level, be, projection));
            be.clientReach = hologram.reach;
            radius = be.onRadius(partialTick);
        }
        else
        {
            radius = be.offWaveRadius(partialTick);
            hologram = radius > 0F ? HologramCache.peek(be) : null;
            if(hologram == null)
            {
                return;
            }
        }
        int look = be.look.ordinal();
        //Solid: lit like a block on the projector's spot (day, night, torches); holograms glow on their own.
        float brightness = 1F;
        if(be.look == HoloProjectorBlockEntity.Look.SOLID)
        {
            brightness = 0.25F + 0.75F * level.getMaxLocalRawBrightness(be.getBlockPos().above()) / 15F;
        }
        hologram.replay(buffers.getBuffer(GlassRenderTypes.hologram(look)), poseStack.last(), radius, brightness);
    }

    @Override
    public boolean shouldRenderOffScreen()
    {
        return true;
    }

    @Override
    public int getViewDistance()
    {
        return 128;
    }

    // ---- Building ----

    /** Where the hologram goes, in the projector's own coordinates: the anchor (bottom centre) and the scale. */
    private record Layout(Vec3 anchor, float scale) {}

    private static Layout layout(HoloProjectorBlockEntity be, HoloClient.ClientProjection projection)
    {
        boolean quarter = (be.rotation & 1) == 1;
        float dx = quarter ? projection.sizeZ() : projection.sizeX();
        float dy = projection.sizeY();
        float dz = quarter ? projection.sizeX() : projection.sizeZ();
        BlockPos origin = be.getBlockPos();
        Vec3 anchor = new Vec3(0.5D, 1D, 0.5D);
        float fit = DEFAULT_FIT / Math.max(dx, Math.max(dy, dz));
        if(be.emitters.size() >= 2)
        {
            //The space the emitters span (their centres).
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
            for(BlockPos e : be.emitters)
            {
                double x = e.getX() - origin.getX() + 0.5D, y = e.getY() - origin.getY() + 0.5D, z = e.getZ() - origin.getZ() + 0.5D;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                maxZ = Math.max(maxZ, z);
            }
            anchor = new Vec3((minX + maxX) / 2D, minY, (minZ + maxZ) / 2D);
            float best = Float.MAX_VALUE;
            if(maxX - minX > 0.5D)
            {
                best = Math.min(best, (float)(maxX - minX) / dx);
            }
            if(maxY - minY > 0.5D)
            {
                best = Math.min(best, (float)(maxY - minY) / dy);
            }
            if(maxZ - minZ > 0.5D)
            {
                best = Math.min(best, (float)(maxZ - minZ) / dz);
            }
            if(best != Float.MAX_VALUE)
            {
                fit = best;
            }
        }
        float scale = be.scale == 0 ? fit : HoloProjectorBlockEntity.SCALES[be.scale];
        return new Layout(anchor, scale);
    }

    /** Where light for the hologram comes from: the projector's top, and the tip of each emitter. */
    private static List<Vec3> sources(ClientLevel level, HoloProjectorBlockEntity be)
    {
        List<Vec3> sources = new ArrayList<>();
        sources.add(new Vec3(0.5D, 1.05D, 0.5D));
        BlockPos origin = be.getBlockPos();
        for(BlockPos e : be.emitters)
        {
            BlockState state = level.getBlockState(e);
            Direction facing = state.is(GlassRegistry.HOLO_EMITTER) ? state.getValue(net.minecraft.world.level.block.RodBlock.FACING) : Direction.UP;
            sources.add(new Vec3(e.getX() - origin.getX() + 0.5D + facing.getStepX() * 0.45D,
                    e.getY() - origin.getY() + 0.5D + facing.getStepY() * 0.45D,
                    e.getZ() - origin.getZ() + 0.5D + facing.getStepZ() * 0.45D));
        }
        return sources;
    }

    private static Hologram build(ClientLevel level, HoloProjectorBlockEntity be, HoloClient.ClientProjection projection)
    {
        Layout layout = layout(be, projection);
        Placement placement = new Placement(projection.sizeX() / 2F, 0F, projection.sizeZ() / 2F, be.rotation, layout.scale(), layout.anchor());
        return buildMesh(level, projection, placement, sources(level, be), new Vec3(0.5D, 1D, 0.5D), be.getBlockPos(), false);
    }

    /**
     * How a projection is laid out: the model point {@code pivot} goes to {@code anchor}, turned by {@code turn} quarter
     * turns around it and scaled.
     */
    record Placement(float pivotX, float pivotY, float pivotZ, int turn, float scale, Vec3 anchor) {}

    /**
     * Builds a hologram. {@code sources}: keep only the sides they can see (null keeps every side). {@code waveOrigin}:
     * where the nano wave starts. Everything is in the same local coordinates as the anchor. {@code simulationEdge}: the
     * room's outer edge falls apart into blue nano pixels (each corner carries how far it is from the edge).
     */
    static Hologram buildMesh(ClientLevel level, HoloClient.ClientProjection projection, Placement placement, @Nullable List<Vec3> sources, Vec3 waveOrigin, BlockPos tintPos,
                              boolean simulationEdge)
    {
        return buildMesh(level, projection, placement, sources, waveOrigin, tintPos, simulationEdge, 0F, -1F);
    }

    /**
     * The same, for a holo cube's ball: {@code ballRadius} (if over 0) puts the edge that falls apart round the ball (from
     * the pivot block's centre) instead of round the box; {@code daylight} (if 0 or more) lights it with the light it was
     * scanned in (each block's own, the sky's at that daylight), baked into its colours.
     */
    static Hologram buildMesh(ClientLevel level, HoloClient.ClientProjection projection, Placement placement, @Nullable List<Vec3> sources, Vec3 waveOrigin, BlockPos tintPos,
                              boolean simulationEdge, float ballRadius, float daylight)
    {
        boolean scannedLight = daylight >= 0F && projection.light().length > 0;
        ModelView view = new ModelView(level, projection, tintPos, 0xF000F0, scannedLight);
        Capture capture = new Capture();
        Hologram hologram = new Hologram(waveOrigin);
        Minecraft mc = Minecraft.getInstance();
        PoseStack pose = new PoseStack();
        RandomSource random = RandomSource.create();
        List<BlockModelPart> parts = new ArrayList<>();
        double turn = placement.turn() * Math.PI / 2D;
        double cos = Math.cos(turn), sin = Math.sin(turn);
        float scale = placement.scale();
        Vec3 anchor = placement.anchor();
        //The projection's box, placed (turns are quarter turns, so it stays a box).
        for(int corner = 0; corner < 2; corner++)
        {
            float mx = (corner == 0 ? 0F : projection.sizeX()) - placement.pivotX();
            float my = (corner == 0 ? 0F : projection.sizeY()) - placement.pivotY();
            float mz = (corner == 0 ? 0F : projection.sizeZ()) - placement.pivotZ();
            float rx = (float)(mx * cos - mz * sin);
            float rz = (float)(mx * sin + mz * cos);
            Vector3f p = new Vector3f((float)(anchor.x + rx * scale), (float)(anchor.y + my * scale), (float)(anchor.z + rz * scale));
            hologram.boxMin.min(p);
            hologram.boxMax.max(p);
        }

        for(int y = 0; y < projection.sizeY(); y++)
        {
            for(int z = 0; z < projection.sizeZ(); z++)
            {
                for(int x = 0; x < projection.sizeX(); x++)
                {
                    BlockState state = projection.get(x, y, z);
                    if(state.isAir() || state.getRenderShape() != RenderShape.MODEL)
                    {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x, y, z);
                    parts.clear();
                    random.setSeed(state.getSeed(pos));
                    mc.getBlockRenderer().getBlockModel(state).collectParts(random, parts);
                    capture.clear();
                    pose.pushPose();
                    pose.translate(x, y, z);
                    mc.getBlockRenderer().renderBatched(state, pos, view, pose, capture, true, parts);
                    pose.popPose();
                    //Place each quad, and keep it only if a source can see its front.
                    for(int q = 0; q + 4 <= capture.count(); q += 4)
                    {
                        Vector3f[] corners = new Vector3f[4];
                        for(int c = 0; c < 4; c++)
                        {
                            float mx = capture.x(q + c) - placement.pivotX();
                            float my = capture.y(q + c) - placement.pivotY();
                            float mz = capture.z(q + c) - placement.pivotZ();
                            float rx = (float)(mx * cos - mz * sin);
                            float rz = (float)(mx * sin + mz * cos);
                            corners[c] = new Vector3f((float)(anchor.x + rx * scale), (float)(anchor.y + my * scale), (float)(anchor.z + rz * scale));
                        }
                        Vector3f normal = new Vector3f(corners[1]).sub(corners[0]).cross(new Vector3f(corners[2]).sub(corners[0]));
                        if(normal.lengthSquared() < 1.0E-12F)
                        {
                            continue;
                        }
                        Vector3f centre = new Vector3f(corners[0]).add(corners[1]).add(corners[2]).add(corners[3]).mul(0.25F);
                        if(sources != null && !seen(centre, normal, sources))
                        {
                            continue;
                        }
                        for(int c = 0; c < 4; c++)
                        {
                            int alpha = 255;
                            float cx = capture.x(q + c), cy = capture.y(q + c), cz = capture.z(q + c);
                            if(simulationEdge && ballRadius > 0F)
                            {
                                //How far in from the ball's surface, as 0..254 over the edge band (255: no edge here).
                                float dx = cx - placement.pivotX(), dy = cy - placement.pivotY() - 0.5F, dz = cz - placement.pivotZ();
                                float edge = (ballRadius - (float)Math.sqrt(dx * dx + dy * dy + dz * dz)) * scale;
                                alpha = Math.clamp(Math.round(edge / EDGE_BAND * 254F), 0, 255);
                            }
                            else if(simulationEdge)
                            {
                                //How far in from the room's walls, as 0..254 over the edge band (255: no edge here).
                                float edge = Math.min(Math.min(cx, projection.sizeX() - cx), Math.min(cz, projection.sizeZ() - cz)) * scale;
                                alpha = Math.clamp(Math.round(edge / EDGE_BAND * 254F), 0, 255);
                            }
                            int colour = capture.colour(q + c);
                            if(scannedLight)
                            {
                                //The light it had: its block light, or the sky's at the scanned daylight, as the block renderer
                                //smoothed it over the face.
                                int light = capture.light(q + c);
                                float block = (light & 0xFFFF) / 240F, sky = (light >>> 16) / 240F;
                                float shade = 0.12F + 0.88F * Math.clamp(Math.max(block, sky * daylight), 0F, 1F);
                                colour = (int)(((colour >> 16) & 0xFF) * shade) << 16 | (int)(((colour >> 8) & 0xFF) * shade) << 8 | (int)((colour & 0xFF) * shade);
                            }
                            hologram.add(corners[c], capture.u(q + c), capture.v(q + c), (colour & 0xFFFFFF) | alpha << 24, c);
                        }
                    }
                }
            }
        }
        return hologram;
    }

    /** True if any source is in front of this face (the face's outside points towards it). */
    private static boolean seen(Vector3f centre, Vector3f normal, List<Vec3> sources)
    {
        for(Vec3 s : sources)
        {
            double d = (s.x - centre.x) * normal.x + (s.y - centre.y) * normal.y + (s.z - centre.z) * normal.z;
            if(d > 0D)
            {
                return true;
            }
        }
        return false;
    }

    /** How far in from a simulation's walls its edge falls apart, in blocks (the same as EDGE_BAND in glass_holo.fsh). */
    static final float EDGE_BAND = 1.75F;

    /** A built hologram: quads in the projector's coordinates, and how far the farthest corner is from the wave's origin. */
    static final class Hologram
    {
        /** The projection's box, placed. */
        final Vector3f boxMin = new Vector3f(Float.MAX_VALUE);
        final Vector3f boxMax = new Vector3f(-Float.MAX_VALUE);
        private final FloatArrayList floats = new FloatArrayList(); //x, y, z, u, v
        private final IntArrayList ints = new IntArrayList(); //colour, corner
        /** Where the nano wave starts. */
        private final float originX, originY, originZ;
        float reach;

        Hologram(Vec3 waveOrigin)
        {
            originX = (float)waveOrigin.x;
            originY = (float)waveOrigin.y;
            originZ = (float)waveOrigin.z;
        }

        void add(Vector3f p, float u, float v, int colour, int corner)
        {
            floats.add(p.x);
            floats.add(p.y);
            floats.add(p.z);
            floats.add(u);
            floats.add(v);
            ints.add(colour);
            ints.add(corner);
            float dx = p.x - originX, dy = p.y - originY, dz = p.z - originZ;
            reach = Math.max(reach, (float)Math.sqrt(dx * dx + dy * dy + dz * dz));
        }

        /**
         * Draws it with the wave at this radius. Each vertex carries its position from the wave's origin (UV1, UV2.x),
         * the radius (UV2.y) and its face corner (normal), see glass_holo.vsh.
         */
        void replay(VertexConsumer consumer, PoseStack.Pose pose, float radius, float brightness)
        {
            int encodedRadius = encodeRadius(radius);
            int vertices = ints.size() / 2;
            for(int i = 0; i < vertices; i++)
            {
                int f = i * 5;
                int k = i * 2;
                float x = floats.getFloat(f), y = floats.getFloat(f + 1), z = floats.getFloat(f + 2);
                int colour = ints.getInt(k);
                if(brightness != 1F)
                {
                    int r = (int)(((colour >> 16) & 0xFF) * brightness);
                    int g = (int)(((colour >> 8) & 0xFF) * brightness);
                    int b = (int)((colour & 0xFF) * brightness);
                    colour = (colour & 0xFF000000) | r << 16 | g << 8 | b;
                }
                vertex(consumer, pose, x, y, z, floats.getFloat(f + 3), floats.getFloat(f + 4), colour, ints.getInt(k + 1), encodedRadius);
            }
        }

        static int encodeRadius(float radius)
        {
            return Math.clamp(Math.round(radius * 64F), 0, 32767);
        }

        /** One hologram corner (see glass_holo.vsh); {@code corner} 0..3 goes round the face. */
        void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int colour, int corner, int encodedRadius)
        {
            consumer.addVertex(pose, x, y, z)
                    .setColor(colour)
                    .setUv(u, v)
                    .setUv1(toShort((x - originX) * 32F), toShort((z - originZ) * 32F))
                    .setUv2(toShort((y - originY) * 32F), encodedRadius)
                    .setNormal(pose, corner == 2 || corner == 3 ? 1F : -1F, corner == 1 || corner == 2 ? 1F : -1F, 0F);
        }

        private static int toShort(float value)
        {
            return Math.clamp(Math.round(value), -32767, 32767);
        }
    }

    /** The projection as a little world for the block renderer: its blocks, lit evenly, tinted like the projector's spot. */
    private static final class ModelView implements BlockAndTintGetter
    {
        private final ClientLevel level;
        private final HoloClient.ClientProjection projection;
        private final BlockPos tintPos;
        private final int blockLight;
        private final int skyLight;
        /** Lit as scanned (a holo cube's ball): each block's own light, kept with the scan. */
        private final boolean scannedLight;

        ModelView(ClientLevel level, HoloClient.ClientProjection projection, BlockPos tintPos, int lightColour, boolean scannedLight)
        {
            this.level = level;
            this.projection = projection;
            this.tintPos = tintPos;
            this.blockLight = (lightColour & 0xFFFF) >> 4;
            this.skyLight = (lightColour >> 16 & 0xFFFF) >> 4;
            this.scannedLight = scannedLight;
        }

        @Override
        public BlockState getBlockState(BlockPos pos)
        {
            return projection.get(pos.getX(), pos.getY(), pos.getZ());
        }

        @Override
        public FluidState getFluidState(BlockPos pos)
        {
            return getBlockState(pos).getFluidState();
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos)
        {
            return null;
        }

        @Override
        public int getBrightness(LightLayer layer, BlockPos pos)
        {
            if(scannedLight)
            {
                int light = projection.light(pos.getX(), pos.getY(), pos.getZ());
                return layer == LightLayer.SKY ? light & 15 : light >> 4;
            }
            return layer == LightLayer.SKY ? skyLight : blockLight;
        }

        @Override
        public int getRawBrightness(BlockPos pos, int darkening)
        {
            return Math.max(getBrightness(LightLayer.SKY, pos) - darkening, getBrightness(LightLayer.BLOCK, pos));
        }

        @Override
        public float getShade(Direction direction, boolean shade)
        {
            return level.getShade(direction, shade);
        }

        @Override
        public LevelLightEngine getLightEngine()
        {
            return level.getLightEngine();
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver resolver)
        {
            return level.getBlockTint(tintPos, resolver);
        }

        @Override
        public int getHeight()
        {
            return level.getHeight();
        }

        @Override
        public int getMinY()
        {
            return level.getMinY();
        }
    }

    /** Takes the vertices of one block from the block renderer. */
    private static final class Capture implements VertexConsumer
    {
        private final FloatArrayList floats = new FloatArrayList(); //x, y, z, u, v
        private final IntArrayList ints = new IntArrayList(); //colour, light

        void clear()
        {
            floats.clear();
            ints.clear();
        }

        int count()
        {
            return ints.size() / 2;
        }

        float x(int i) { return floats.getFloat(i * 5); }
        float y(int i) { return floats.getFloat(i * 5 + 1); }
        float z(int i) { return floats.getFloat(i * 5 + 2); }
        float u(int i) { return floats.getFloat(i * 5 + 3); }
        float v(int i) { return floats.getFloat(i * 5 + 4); }
        int colour(int i) { return ints.getInt(i * 2); }
        int light(int i) { return ints.getInt(i * 2 + 1); }

        @Override
        public VertexConsumer addVertex(float x, float y, float z)
        {
            floats.add(x);
            floats.add(y);
            floats.add(z);
            floats.add(0F);
            floats.add(0F);
            ints.add(-1);
            ints.add(0);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a)
        {
            ints.set(ints.size() - 2, (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v)
        {
            floats.set(floats.size() - 2, u);
            floats.set(floats.size() - 1, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v)
        {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v)
        {
            ints.set(ints.size() - 1, (u & 0xFFFF) | (v & 0xFFFF) << 16);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z)
        {
            return this;
        }
    }
}
