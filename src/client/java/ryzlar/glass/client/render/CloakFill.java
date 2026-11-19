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
 * away) the real world is drawn back in, houses, crops and water included, and just behind it the real ground is drawn
 * again without the dark edges the hidden blocks left in its baked-in shading.
 * <p>
 * The blocks are drawn by the game's own block renderer through a {@link Terrain} view of the world with the hidden part
 * replaced, so faces, smooth lighting and ambient occlusion come out like the real terrain around it. The result is
 * kept and replayed every frame, and rebuilt once a second (the world around it may change).
 */
public final class CloakFill
{
    /** The world beside the hidden part drawn back in, in blocks (a ray through the glass at an angle runs far sideways). */
    public static final int SPREAD = 32;
    /** Rows of ground just behind the hidden part that are drawn again. */
    public static final int RING = 2;
    /** How far above the hidden part the world beside it is drawn back in (a ray up through the glass sees trees there). */
    private static final int ABOVE = 4;
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
     * the hidden part (0 is the glass itself), {@code across} runs sideways along the screen. The world is drawn back in
     * from {@code before} rows in front of the glass to {@code after} rows behind the hidden part and {@code spread}
     * columns to either side of it, and {@code ring} rows of ground behind that are drawn again.
     */
    public record Area(Direction side, int groundY, int minAcross, int maxAcross, int outerBlock, int depth, AABB hidden, BlockPos lightPos,
                       int before, int after, int spread, int ring)
    {
        /** A screen's side: the world beside the hidden part is drawn back in, and the ground just behind it. */
        public static Area side(Direction side, int groundY, int minAcross, int maxAcross, int outerBlock, int depth, AABB hidden, BlockPos lightPos)
        {
            return new Area(side, groundY, minAcross, maxAcross, outerBlock, depth, hidden, lightPos, 0, 0, SPREAD, RING);
        }

        /** Seen from above: the world all around the hidden part is drawn back in. */
        public Area allAround(int margin)
        {
            return new Area(side, groundY, minAcross, maxAcross, outerBlock, depth, hidden, lightPos, margin, margin, margin, 0);
        }

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
        //Later rebuilds of a cloak's sides are spread over the second instead of all landing on the same frame.
        builtAt = builtAt == Long.MIN_VALUE ? now - area.side().ordinal() * 3L % REFRESH_TICKS : now;
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
        Long2ObjectOpenHashMap<Column> realColumns = new Long2ObjectOpenHashMap<>();

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
                Column column = realColumn(level, area.column(along, across, 0), realColumns);
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
        //The ground in front of the screen is certainly ground (the viewer stands on it); around the sides and back there
        //may be more of the base. Keep the made-up ground near the front's height.
        int reference = frontHeights.isEmpty() ? area.groundY() : median(frontHeights);
        //What the ground is like is told by the natural ground around it (grass, sand, stone...), not by a house or a lamp
        //post that happens to stand beside the hidden part. Only if there is nothing else, everything counts.
        List<int[]> groundPos = new ArrayList<>();
        List<Column> ground = new ArrayList<>();
        for(int i = 0; i < samples.size(); i++)
        {
            Column sample = samples.get(i);
            if(isNatural(sample.top()) && Math.abs(sample.height() - reference) <= MAX_RISE)
            {
                groundPos.add(samplePos.get(i));
                ground.add(sample);
            }
        }
        if(ground.isEmpty())
        {
            groundPos = samplePos;
            ground = samples;
        }
        int plants = 0;
        for(Column sample : ground)
        {
            if(sample.plant() != null)
            {
                plants++;
            }
        }
        float plantChance = ground.isEmpty() ? 0F : (float)plants / ground.size();

        //Made-up ground for the hidden columns.
        Long2ObjectOpenHashMap<Column> made = new Long2ObjectOpenHashMap<>();
        if(!ground.isEmpty())
        {
            for(int along = 0; along < area.depth(); along++)
            {
                for(int across = area.minAcross(); across <= area.maxAcross(); across++)
                {
                    double weights = 0D;
                    double heights = 0D;
                    Column nearest = null;
                    double nearestDistance = Double.MAX_VALUE;
                    for(int i = 0; i < ground.size(); i++)
                    {
                        int[] p = groundPos.get(i);
                        double d2 = (p[0] - along) * (p[0] - along) + (p[1] - across) * (p[1] - across);
                        double w = 1D / (d2 * d2);
                        weights += w;
                        heights += w * ground.get(i).height();
                        if(d2 < nearestDistance)
                        {
                            nearestDistance = d2;
                            nearest = ground.get(i);
                        }
                    }
                    int height = Math.clamp(Math.round(heights / weights), reference - MAX_RISE, reference + MAX_RISE);
                    BlockPos pos = area.column(along, across, height);
                    BlockState plant = null;
                    if(hash(pos, 17) < plantChance)
                    {
                        plant = pickPlant(ground, nearest.top(), hash(pos, 31));
                    }
                    made.put(columnKey(pos), new Column(height, nearest.top(), nearest.filler(), plant));
                }
            }
        }

        Terrain terrain = new Terrain(level, area, made);
        Drawer drawer = new Drawer(terrain, area.lightPos());
        int aboveHidden = (int)Math.ceil(area.hidden().maxY);
        int top = aboveHidden + ABOVE;
        int lowest = area.groundY() - SCAN;
        int lastFillRow = area.depth() + area.after();
        for(int along = -area.before(); along < lastFillRow + area.ring(); along++)
        {
            boolean behind = along >= lastFillRow;
            for(int across = area.minAcross() - area.spread(); across <= area.maxAcross() + area.spread(); across++)
            {
                BlockPos base = area.column(along, across, 0);
                Column column = made.get(columnKey(base));
                if(behind)
                {
                    //Just behind the hidden part: the top of the real ground again.
                    column = realColumn(level, base, realColumns);
                    if(column != null)
                    {
                        drawer.draw(ring, new BlockPos(base.getX(), column.height(), base.getZ()));
                    }
                    continue;
                }
                if(column != null)
                {
                    //Made-up ground: down to the lowest neighbour, so the sides of steps are drawn too.
                    int bottom = column.height();
                    for(Direction direction : Direction.Plane.HORIZONTAL)
                    {
                        BlockPos next = base.relative(direction);
                        Column neighbour = made.get(columnKey(next));
                        if(neighbour == null)
                        {
                            neighbour = realColumn(level, next, realColumns);
                        }
                        if(neighbour != null)
                        {
                            bottom = Math.min(bottom, neighbour.height());
                        }
                    }
                    bottom = Math.max(bottom, column.height() - 3);
                    int plantTop = column.plant() == null ? column.height() : column.height() + 1;
                    for(int y = bottom; y <= plantTop; y++)
                    {
                        drawer.draw(fill, new BlockPos(base.getX(), y, base.getZ()));
                    }
                    //And whatever hangs over the hidden part (a tree's leaves).
                    for(int y = Math.max(aboveHidden, plantTop + 1); y <= top; y++)
                    {
                        drawer.draw(fill, new BlockPos(base.getX(), y, base.getZ()));
                    }
                    continue;
                }
                //The real world beside the hidden part (the clip plane cut it away too): everything from just under its
                //ground (or its lowest neighbour's) up, so houses, fences and crops come back as well as the ground.
                int low = area.groundY();
                Column real = realColumn(level, base, realColumns);
                if(real != null)
                {
                    low = Math.min(low, real.height());
                }
                for(Direction direction : Direction.Plane.HORIZONTAL)
                {
                    BlockPos next = base.relative(direction);
                    Column neighbour = made.get(columnKey(next));
                    if(neighbour == null)
                    {
                        neighbour = realColumn(level, next, realColumns);
                    }
                    if(neighbour != null)
                    {
                        low = Math.min(low, neighbour.height());
                    }
                }
                for(int y = Math.max(low - 1, lowest); y <= top; y++)
                {
                    drawer.draw(fill, new BlockPos(base.getX(), y, base.getZ()));
                }
            }
        }
    }

    /** Draws blocks of the {@link Terrain} into a mesh: their models, and their water or lava. */
    private static final class Drawer
    {
        private final Minecraft mc = Minecraft.getInstance();
        private final Terrain terrain;
        private final BlockPos origin;
        private final PoseStack pose = new PoseStack();
        private final RandomSource random = RandomSource.create();
        private final List<BlockModelPart> parts = new ArrayList<>();

        Drawer(Terrain terrain, BlockPos origin)
        {
            this.terrain = terrain;
            this.origin = origin;
        }

        void draw(Mesh mesh, BlockPos pos)
        {
            BlockState state = terrain.getBlockState(pos);
            if(state.isAir())
            {
                return;
            }
            mesh.origin = origin;
            FluidState fluid = state.getFluidState();
            if(!fluid.isEmpty())
            {
                //The liquid renderer puts its corners relative to the section the block is in.
                mesh.offset((pos.getX() & ~15) - origin.getX(), (pos.getY() & ~15) - origin.getY(), (pos.getZ() & ~15) - origin.getZ());
                mc.getBlockRenderer().renderLiquid(pos, terrain, mesh, state, fluid);
                mesh.offset(0F, 0F, 0F);
            }
            if(state.getRenderShape() != RenderShape.MODEL)
            {
                return;
            }
            parts.clear();
            random.setSeed(state.getSeed(pos));
            mc.getBlockRenderer().getBlockModel(state).collectParts(random, parts);
            pose.pushPose();
            pose.translate(pos.getX() - origin.getX(), pos.getY() - origin.getY(), pos.getZ() - origin.getZ());
            mc.getBlockRenderer().renderBatched(state, pos, terrain, pose, mesh, true, parts);
            pose.popPose();
        }
    }

    /** {@link #realColumn(ClientLevel, BlockPos)}, each column looked up once per build. */
    @Nullable
    private Column realColumn(ClientLevel level, BlockPos column, Long2ObjectOpenHashMap<Column> known)
    {
        long key = columnKey(column);
        if(known.containsKey(key))
        {
            return known.get(key);
        }
        Column found = realColumn(level, column);
        known.put(key, found);
        return found;
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

    /**
     * Something to stand on: a block with a solid top, or one standing solidly on what is under it that is at least half
     * a block tall (paths and farmland are a little lower than a full block).
     */
    private static boolean isGround(BlockState state, BlockAndTintGetter level, BlockPos pos)
    {
        if(state.isAir() || state.getRenderShape() != RenderShape.MODEL)
        {
            return false;
        }
        if(state.isFaceSturdy(level, pos, Direction.UP))
        {
            return true;
        }
        if(!state.isFaceSturdy(level, pos, Direction.DOWN))
        {
            return false;
        }
        net.minecraft.world.phys.shapes.VoxelShape shape = state.getCollisionShape(level, pos);
        return !shape.isEmpty() && shape.max(Direction.Axis.Y) >= 0.5D;
    }

    /** Ground nature made (or a path or field on it), not a house or a lamp post. */
    private static boolean isNatural(BlockState state)
    {
        return state.is(net.minecraft.tags.BlockTags.DIRT) || state.is(net.minecraft.tags.BlockTags.SAND)
                || state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || state.is(net.minecraft.tags.BlockTags.BASE_STONE_NETHER)
                || state.is(net.minecraft.tags.BlockTags.NYLIUM) || state.is(net.minecraft.tags.BlockTags.SNOW)
                || state.is(net.minecraft.tags.BlockTags.TERRACOTTA) || state.is(Blocks.GRAVEL) || state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.FARMLAND) || state.is(Blocks.CLAY) || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE)
                || state.is(Blocks.END_STONE) || state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE);
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
    private static BlockState pickPlant(List<Column> samples, BlockState top, float pick)
    {
        //Only what grows on the same ground (no wheat on grass, no grass on sand).
        List<BlockState> plants = new ArrayList<>();
        for(Column sample : samples)
        {
            if(sample.plant() != null && sample.top().getBlock() == top.getBlock())
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
            if(column != null && pos.getY() >= column.height() - 3 && (pos.getY() <= column.height() + 1 || pos.getY() + 0.5D < area.hidden().maxY))
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
        private float offsetX, offsetY, offsetZ;

        /** Moves the vertices that follow (the liquid renderer works relative to a section). */
        void offset(float x, float y, float z)
        {
            offsetX = x;
            offsetY = y;
            offsetZ = z;
        }

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
            floats.add(x + offsetX);
            floats.add(y + offsetY);
            floats.add(z + offsetZ);
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
