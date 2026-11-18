package ryzlar.glass.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The ground of one side of a cloak, rebuilt from its surroundings ("content-aware fill").
 * <p>
 * The clip plane cuts away everything between the screen and the back of the hidden part, the ground included. What
 * would be there if the hidden blocks were not, nobody knows, so it is made up from what is around it: the height of
 * the ground around the hidden part carries on smoothly across it, its top is the block of the nearest column around it
 * (grass, sand, snow...), and grass and flowers grow on it as densely as around it. Beside the hidden part (also cut
 * away) the real ground is drawn back in, and just behind it the real ground is drawn again without the dark edges the
 * hidden blocks left in its baked-in shading.
 * <p>
 * The blocks are drawn by the game's own block renderer through a {@link Terrain} view of the world with the hidden part
 * replaced, so faces, smooth lighting and ambient occlusion come out like the real terrain around it. The result is
 * kept and replayed every frame, and rebuilt once a second (the world around it may change).
 */
public final class CloakFill
{
    /** Ground beside the hidden part drawn back in, in blocks (a ray through the glass at an angle runs far sideways). */
    private static final int SPREAD = 24;
    /** Rows of ground just behind the hidden part that are drawn again. */
    private static final int RING = 2;
    /** Rows of ground around the hidden part its new ground is worked out from. */
    private static final int SAMPLE = 3;
    /** How far up and down the ground is looked for, from the screen's bottom, in blocks. */
    private static final int SCAN = 12;
    /** The made-up ground stays this close to the height of the ground in front of the screen, in blocks. */
    private static final int MAX_RISE = 5;
    private static final int REFRESH_TICKS = 20;
    /** How far above the real ground the ground behind the hidden part is drawn again. */
    private static final float RING_LIFT = 0.004F;

    /**
     * Where a cloak side is, in its own terms: {@code along} counts block rows from the screen's outer glass back into
     * the hidden part (0 is the glass itself), {@code across} runs sideways along the screen.
     */
    public record Area(Direction side, int groundY, int minAcross, int maxAcross, int outerBlock, int depth, AABB hidden, BlockPos lightPos)
    {
        BlockPos column(int along, int across, int y)
        {
            Direction right = side.getClockWise();
            int a = across * (right.getStepX() + right.getStepZ());
            int l = outerBlock - along * (side.getStepX() + side.getStepZ());
            return side.getAxis() == Direction.Axis.X ? new BlockPos(l, y, a) : new BlockPos(a, y, l);
        }

        boolean isHiddenColumn(int along, int across)
        {
            return along >= 0 && along < depth && across >= minAcross && across <= maxAcross;
        }
    }

    private final Area area;
    private long builtAt = Long.MIN_VALUE;
    private final Mesh fill = new Mesh();
    private final Mesh ring = new Mesh();

    public CloakFill(Area area)
    {
        this.area = area;
    }

    // ---- Drawing ----

    /** Rebuilds the ground if it is a second old (or new). */
    public void refresh(ClientLevel level)
    {
        long now = level.getGameTime();
        if(builtAt != Long.MIN_VALUE && now - builtAt < REFRESH_TICKS && now >= builtAt)
        {
            return;
        }
        builtAt = now;
        build(level);
    }

    /** The ground under and beside the hidden part (drawn without the clip plane, over a cleared depth buffer). */
    public void drawFill(VertexConsumer consumer, Vec3 camera)
    {
        fill.replay(consumer, camera, 0F);
    }

    /** The ground just behind the hidden part (drawn with the view's own depth, a hair above the real ground). */
    public void drawRing(VertexConsumer consumer, Vec3 camera)
    {
        ring.replay(consumer, camera, RING_LIFT);
    }

    // ---- Building ----

    /** One column of ground: the height of its top block, the top, what is under it, and a plant on it (or null). */
    private record Column(int height, BlockState top, BlockState filler, @Nullable BlockState plant) {}

    private void build(ClientLevel level)
    {
        fill.clear();
        ring.clear();

        //The ground around the hidden part.
        List<int[]> samplePos = new ArrayList<>();
        List<Column> samples = new ArrayList<>();
        List<Integer> frontHeights = new ArrayList<>();
        for(int along = -SAMPLE; along < area.depth() + SAMPLE; along++)
        {
            for(int across = area.minAcross() - SAMPLE; across <= area.maxAcross() + SAMPLE; across++)
            {
                if(area.isHiddenColumn(along, across))
                {
                    continue;
                }
                Column column = realColumn(level, area.column(along, across, 0));
                if(column != null)
                {
                    samplePos.add(new int[] { along, across });
                    samples.add(column);
                    if(along < 0)
                    {
                        frontHeights.add(column.height());
                    }
                }
            }
        }
        if(samples.isEmpty())
        {
            return;
        }
        //The ground in front of the screen is certainly ground (the viewer stands on it); around the sides and back there
        //may be more of the base. Keep the made-up ground near the front's height.
        int reference = frontHeights.isEmpty() ? area.groundY() : median(frontHeights);
        int plants = 0;
        for(Column sample : samples)
        {
            if(sample.plant() != null)
            {
                plants++;
            }
        }
        float plantChance = (float)plants / samples.size();

        //Made-up ground for the hidden columns.
        Long2ObjectOpenHashMap<Column> made = new Long2ObjectOpenHashMap<>();
        for(int along = 0; along < area.depth(); along++)
        {
            for(int across = area.minAcross(); across <= area.maxAcross(); across++)
            {
                double weights = 0D;
                double heights = 0D;
                Column nearest = null;
                double nearestDistance = Double.MAX_VALUE;
                for(int i = 0; i < samples.size(); i++)
                {
                    int[] p = samplePos.get(i);
                    double d2 = (p[0] - along) * (p[0] - along) + (p[1] - across) * (p[1] - across);
                    double w = 1D / (d2 * d2);
                    weights += w;
                    heights += w * samples.get(i).height();
                    if(d2 < nearestDistance)
                    {
                        nearestDistance = d2;
                        nearest = samples.get(i);
                    }
                }
                int height = Math.clamp(Math.round(heights / weights), reference - MAX_RISE, reference + MAX_RISE);
                BlockPos pos = area.column(along, across, height);
                BlockState plant = null;
                if(hash(pos, 17) < plantChance)
                {
                    plant = pickPlant(samples, hash(pos, 31));
                }
                made.put(columnKey(pos), new Column(height, nearest.top(), nearest.filler(), plant));
            }
        }

        Terrain terrain = new Terrain(level, area, made);
        Minecraft mc = Minecraft.getInstance();
        PoseStack pose = new PoseStack();
        RandomSource random = RandomSource.create();
        List<BlockModelPart> parts = new ArrayList<>();
        BlockPos origin = area.lightPos();

        for(int along = 0; along < area.depth() + RING; along++)
        {
            boolean behind = along >= area.depth();
            for(int across = area.minAcross() - SPREAD; across <= area.maxAcross() + SPREAD; across++)
            {
                BlockPos base = area.column(along, across, 0);
                Column column = made.get(columnKey(base));
                if(column == null)
                {
                    column = realColumn(level, base);
                }
                if(column == null)
                {
                    continue;
                }
                Mesh mesh = behind ? ring : fill;
                //Down to the lowest neighbour, so the sides of steps are drawn too (not behind: just the top again).
                int bottom = column.height();
                if(!behind)
                {
                    for(Direction direction : Direction.Plane.HORIZONTAL)
                    {
                        BlockPos next = base.relative(direction);
                        Column neighbour = made.get(columnKey(next));
                        if(neighbour == null)
                        {
                            neighbour = realColumn(level, next);
                        }
                        if(neighbour != null)
                        {
                            bottom = Math.min(bottom, neighbour.height());
                        }
                    }
                    bottom = Math.max(bottom, column.height() - 3);
                }
                int top = behind || column.plant() == null ? column.height() : column.height() + 1;
                for(int y = bottom; y <= top; y++)
                {
                    BlockPos pos = new BlockPos(base.getX(), y, base.getZ());
                    BlockState state = terrain.getBlockState(pos);
                    if(state.isAir() || state.getRenderShape() != RenderShape.MODEL)
                    {
                        continue;
                    }
                    parts.clear();
                    random.setSeed(state.getSeed(pos));
                    mc.getBlockRenderer().getBlockModel(state).collectParts(random, parts);
                    pose.pushPose();
                    pose.translate(pos.getX() - origin.getX(), pos.getY() - origin.getY(), pos.getZ() - origin.getZ());
                    mesh.origin = origin;
                    mc.getBlockRenderer().renderBatched(state, pos, terrain, pose, mesh, true, parts);
                    pose.popPose();
                }
            }
        }
    }

    /**
     * The real ground of a column: the highest solid block (with room above it) near the screen's bottom height, outside
     * the hidden part. Null if there is none in range.
     */
    @Nullable
    private Column realColumn(ClientLevel level, BlockPos column)
    {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for(int y = area.groundY() + SCAN; y >= area.groundY() - SCAN; y--)
        {
            pos.set(column.getX(), y, column.getZ());
            if(area.hidden().contains(Vec3.atCenterOf(pos)))
            {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if(!isGround(state, level, pos))
            {
                continue;
            }
            BlockPos above = pos.above();
            BlockState over = area.hidden().contains(Vec3.atCenterOf(above)) ? Blocks.AIR.defaultBlockState() : level.getBlockState(above);
            if(isGround(over, level, above))
            {
                continue;
            }
            return new Column(y, state, fillerFor(state), isPlant(over, level, above) ? over : null);
        }
        return null;
    }

    private static boolean isGround(BlockState state, BlockAndTintGetter level, BlockPos pos)
    {
        return !state.isAir() && state.getRenderShape() == RenderShape.MODEL && state.isFaceSturdy(level, pos, Direction.UP);
    }

    /** Grass, flowers and the like: one block tall, nothing to bump into. */
    private static boolean isPlant(BlockState state, BlockAndTintGetter level, BlockPos pos)
    {
        return !state.isAir() && state.getRenderShape() == RenderShape.MODEL && state.getFluidState().isEmpty()
                && !(state.getBlock() instanceof DoublePlantBlock) && state.getCollisionShape(level, pos).isEmpty();
    }

    /** What lies under a top block: dirt under grass and the like, the same block otherwise. */
    private static BlockState fillerFor(BlockState top)
    {
        if(top.is(Blocks.GRASS_BLOCK) || top.is(Blocks.PODZOL) || top.is(Blocks.MYCELIUM) || top.is(Blocks.DIRT_PATH) || top.is(Blocks.FARMLAND))
        {
            return Blocks.DIRT.defaultBlockState();
        }
        return top;
    }

    @Nullable
    private static BlockState pickPlant(List<Column> samples, float pick)
    {
        List<BlockState> plants = new ArrayList<>();
        for(Column sample : samples)
        {
            if(sample.plant() != null)
            {
                plants.add(sample.plant());
            }
        }
        return plants.isEmpty() ? null : plants.get(Math.min(plants.size() - 1, (int)(pick * plants.size())));
    }

    private static int median(List<Integer> values)
    {
        List<Integer> sorted = new ArrayList<>(values);
        sorted.sort(Integer::compare);
        return sorted.get(sorted.size() / 2);
    }

    /** A steady pseudo-random number in [0, 1) for a column, so made-up plants stay where they are. */
    private static float hash(BlockPos pos, int salt)
    {
        long h = pos.getX() * 3129871L ^ pos.getZ() * 116129781L ^ salt * 0x9E3779B97F4A7C15L;
        h = h * h * 42317861L + h * 11L;
        return ((h >> 16) & 0xFFFFFF) / (float)0x1000000;
    }

    private static long columnKey(BlockPos pos)
    {
        return BlockPos.asLong(pos.getX(), 0, pos.getZ());
    }

    /** The world as the block renderer sees it here: the hidden part swapped for the made-up ground. */
    private static final class Terrain implements BlockAndTintGetter
    {
        private final ClientLevel level;
        private final Area area;
        private final Long2ObjectOpenHashMap<Column> made;
        private final int outsideBlockLight;
        private final int outsideSkyLight;

        Terrain(ClientLevel level, Area area, Long2ObjectOpenHashMap<Column> made)
        {
            this.level = level;
            this.area = area;
            this.made = made;
            this.outsideBlockLight = level.getBrightness(LightLayer.BLOCK, area.lightPos());
            this.outsideSkyLight = level.getBrightness(LightLayer.SKY, area.lightPos());
        }

        @Override
        public BlockState getBlockState(BlockPos pos)
        {
            Column column = made.get(columnKey(pos));
            if(column != null && pos.getY() >= column.height() - 3 && pos.getY() <= Math.max(area.hidden().maxY, column.height() + 1))
            {
                if(pos.getY() == column.height())
                {
                    return column.top();
                }
                if(pos.getY() < column.height())
                {
                    return column.filler();
                }
                if(pos.getY() == column.height() + 1 && column.plant() != null)
                {
                    return column.plant();
                }
                return Blocks.AIR.defaultBlockState();
            }
            if(area.hidden().contains(Vec3.atCenterOf(pos)))
            {
                return Blocks.AIR.defaultBlockState();
            }
            return level.getBlockState(pos);
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

        /** Where the hidden blocks were, the light just outside the cloak (they made it dark there). */
        @Override
        public int getBrightness(LightLayer layer, BlockPos pos)
        {
            Column column = made.get(columnKey(pos));
            if(column != null && pos.getY() > column.height() || area.hidden().contains(Vec3.atCenterOf(pos)))
            {
                int outside = layer == LightLayer.SKY ? outsideSkyLight : outsideBlockLight;
                return Math.max(outside, level.getBrightness(layer, pos));
            }
            return level.getBrightness(layer, pos);
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
            return level.getBlockTint(pos, resolver);
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

    /** Vertices kept from the block renderer (relative to an origin block), replayed every frame. */
    private static final class Mesh implements VertexConsumer
    {
        private final FloatArrayList floats = new FloatArrayList(); //x, y, z, u, v
        private final IntArrayList ints = new IntArrayList(); //colour, light
        BlockPos origin = BlockPos.ZERO;
        private BlockPos replayOrigin = BlockPos.ZERO;

        void clear()
        {
            floats.clear();
            ints.clear();
        }

        void replay(VertexConsumer consumer, Vec3 camera, float lift)
        {
            float ox = (float)(replayOrigin.getX() - camera.x);
            float oy = (float)(replayOrigin.getY() - camera.y) + lift;
            float oz = (float)(replayOrigin.getZ() - camera.z);
            for(int v = 0, f = 0, i = 0; v < ints.size() / 2; v++, f += 5, i += 2)
            {
                consumer.addVertex(floats.getFloat(f) + ox, floats.getFloat(f + 1) + oy, floats.getFloat(f + 2) + oz)
                        .setColor(ints.getInt(i))
                        .setUv(floats.getFloat(f + 3), floats.getFloat(f + 4))
                        .setLight(ints.getInt(i + 1));
            }
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z)
        {
            replayOrigin = origin;
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
            return this; //overlay: not used
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
