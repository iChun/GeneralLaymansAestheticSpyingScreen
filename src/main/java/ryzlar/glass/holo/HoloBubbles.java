package ryzlar.glass.holo;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The holo cube simulations running in each world, on the server and on the client. A simulation is a picture laid over
 * the real world, which stays as it is: the scanned ball, shrunk to {@link HoloSphere#SCALE}, round the cube. Only its
 * ground can be stood on, and only where the real world is empty (where there is real ground, that is what counts);
 * the rest of it is walked through. The same on both sides, so movement agrees (see the collision mixin).
 * <p>
 * Read on any thread: the state is an immutable map, swapped whole on change.
 */
public final class HoloBubbles
{
    /** The scanned blocks of a ball, by their place in the scan's box. */
    public interface ScannedBlocks
    {
        BlockState get(int x, int y, int z);
    }

    /**
     * A running simulation: the scan laid round {@code anchor} (the cube's block), its {@code origin} (the block the cube
     * scanned from, in the scan's box) on the anchor's floor, shrunk to {@link HoloSphere#SCALE} and turned by
     * {@code turn} quarter turns. {@code solid}: its ground can be stood on (while it is switched on; not while it
     * shrinks away on the client).
     */
    public static final class Bubble
    {
        public final int id;
        public final BlockPos anchor;
        public final int turn;
        public final int originX, originY, originZ;
        public final ScannedBlocks blocks;
        public final boolean solid;
        private final int[] cosSin;
        /** Per world block: the scanned ground's shape in it (from the block's corner), worked out once. */
        @Nullable
        private volatile Long2ObjectOpenHashMap<VoxelShape> ground;

        public Bubble(int id, BlockPos anchor, int turn, int originX, int originY, int originZ, ScannedBlocks blocks, boolean solid)
        {
            this.id = id;
            this.anchor = anchor.immutable();
            this.turn = turn & 3;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.blocks = blocks;
            this.solid = solid;
            this.cosSin = COS_SIN[this.turn];
        }

        /** The same simulation, the same way round (what is cached for it still holds). */
        public boolean samePlace(Bubble other)
        {
            return other.id == id && other.anchor.equals(anchor) && other.turn == turn && other.blocks == blocks
                    && other.originX == originX && other.originY == originY && other.originZ == originZ;
        }

        /** A point of the scan's box (in blocks) in the world. */
        public Vec3 toWorld(double x, double y, double z)
        {
            double mx = (x - originX - 0.5D) * HoloSphere.SCALE, mz = (z - originZ - 0.5D) * HoloSphere.SCALE;
            return new Vec3(anchor.getX() + 0.5D + mx * cosSin[0] - mz * cosSin[1],
                    anchor.getY() + (y - originY) * HoloSphere.SCALE,
                    anchor.getZ() + 0.5D + mx * cosSin[1] + mz * cosSin[0]);
        }

        /** A point of the world in the scan's box (in blocks). */
        public Vec3 toScan(double x, double y, double z)
        {
            double dx = (x - anchor.getX() - 0.5D) / HoloSphere.SCALE, dz = (z - anchor.getZ() - 0.5D) / HoloSphere.SCALE;
            return new Vec3(originX + 0.5D + dx * cosSin[0] + dz * cosSin[1],
                    originY + (y - anchor.getY()) / HoloSphere.SCALE,
                    originZ + 0.5D - dx * cosSin[1] + dz * cosSin[0]);
        }

        public Vec3 toScan(Vec3 world)
        {
            return toScan(world.x, world.y, world.z);
        }

        /** The scanned block at a place in the scan's box, turned as the simulation is. */
        public BlockState scanned(int x, int y, int z)
        {
            BlockState state = blocks.get(x, y, z);
            return turn == 0 || state.isAir() ? state : state.rotate(rotation(turn));
        }

        /** Whether a block of the scan is ground (stood on where the world is empty): nature made, or below the cube's floor. */
        public boolean isGround(int y, BlockState state)
        {
            return y < originY || HoloSphere.isNaturalGround(state);
        }

        /** The world box round the shrunk ball (with a block to spare). */
        public AABB box()
        {
            double r = (HoloSphere.SCAN_RADIUS + 1.5D) * HoloSphere.SCALE + 1D;
            Vec3 centre = toWorld(originX + 0.5D, originY + 0.5D, originZ + 0.5D);
            return new AABB(centre, centre).inflate(r);
        }

        /** The scanned ground's shape in this world block (from its corner), or null if there is none there. */
        @Nullable
        public VoxelShape groundIn(BlockPos pos)
        {
            Long2ObjectOpenHashMap<VoxelShape> map = ground;
            if(map == null)
            {
                map = buildGround();
                ground = map;
            }
            return map.get(pos.asLong());
        }

        /** Each ground block of the scan, shrunk and placed, cut up into the world blocks it covers. */
        private Long2ObjectOpenHashMap<VoxelShape> buildGround()
        {
            Map<Long, List<AABB>> parts = new java.util.HashMap<>();
            int size = HoloSphere.SIZE;
            for(int y = 0; y < size; y++)
            {
                for(int z = 0; z < size; z++)
                {
                    for(int x = 0; x < size; x++)
                    {
                        BlockState state = scanned(x, y, z);
                        if(state.isAir() || !isGround(y, state))
                        {
                            continue;
                        }
                        VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                        for(AABB part : shape.toAabbs())
                        {
                            Vec3 a = toWorld(x + part.minX, y + part.minY, z + part.minZ);
                            Vec3 b = toWorld(x + part.maxX, y + part.maxY, z + part.maxZ);
                            AABB world = new AABB(a, b);
                            for(BlockPos cell : BlockPos.betweenClosed(BlockPos.containing(world.minX, world.minY, world.minZ),
                                    BlockPos.containing(world.maxX - 1.0E-7D, world.maxY - 1.0E-7D, world.maxZ - 1.0E-7D)))
                            {
                                AABB in = world.intersect(new AABB(cell));
                                if(in.getXsize() > 1.0E-6D && in.getYsize() > 1.0E-6D && in.getZsize() > 1.0E-6D)
                                {
                                    parts.computeIfAbsent(cell.asLong(), k -> new ArrayList<>()).add(in.move(-cell.getX(), -cell.getY(), -cell.getZ()));
                                }
                            }
                        }
                    }
                }
            }
            Long2ObjectOpenHashMap<VoxelShape> map = new Long2ObjectOpenHashMap<>();
            for(Map.Entry<Long, List<AABB>> e : parts.entrySet())
            {
                VoxelShape shape = Shapes.empty();
                for(AABB box : e.getValue())
                {
                    shape = Shapes.or(shape, Shapes.create(box));
                }
                map.put(e.getKey().longValue(), shape.optimize());
            }
            return map;
        }
    }

    private static final int[][] COS_SIN = { { 1, 0 }, { 0, 1 }, { -1, 0 }, { 0, -1 } };

    /** How a block in the scan turns with the simulation (the scan's +x goes to the world's +z after one turn). */
    public static Rotation rotation(int turn)
    {
        return switch(turn & 3)
        {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private static volatile Map<Level, Bubble[]> bubbles = Map.of();
    private static final Bubble[] NONE = new Bubble[0];

    private HoloBubbles() {}

    /** Puts a simulation in (or replaces it, by its cube's id). */
    public static synchronized void put(Level level, Bubble bubble)
    {
        Bubble[] old = bubbles.getOrDefault(level, NONE);
        for(int i = 0; i < old.length; i++)
        {
            if(old[i].id == bubble.id)
            {
                Bubble[] next = old.clone();
                next[i] = bubble;
                set(level, next);
                return;
            }
        }
        Bubble[] next = java.util.Arrays.copyOf(old, old.length + 1);
        next[old.length] = bubble;
        set(level, next);
    }

    public static synchronized void remove(Level level, int id)
    {
        Bubble[] old = bubbles.get(level);
        if(old == null)
        {
            return;
        }
        Bubble[] next = java.util.Arrays.stream(old).filter(b -> b.id != id).toArray(Bubble[]::new);
        if(next.length != old.length)
        {
            set(level, next);
        }
    }

    /** Forgets every client world's simulations (the client left its world); the server's stay. */
    public static synchronized void clearClientSide()
    {
        for(Level level : List.copyOf(bubbles.keySet()))
        {
            if(level.isClientSide())
            {
                set(level, NONE);
            }
        }
    }

    private static void set(Level level, Bubble[] list)
    {
        Map<Level, Bubble[]> next = new IdentityHashMap<>(bubbles);
        if(list.length == 0)
        {
            next.remove(level);
        }
        else
        {
            next.put(level, list);
        }
        bubbles = next.isEmpty() ? Map.of() : next;
    }

    public static Bubble[] in(Level level)
    {
        Map<Level, Bubble[]> map = bubbles;
        return map.isEmpty() ? NONE : map.getOrDefault(level, NONE);
    }

    @Nullable
    public static Bubble byId(Level level, int id)
    {
        for(Bubble bubble : in(level))
        {
            if(bubble.id == id)
            {
                return bubble;
            }
        }
        return null;
    }

    /**
     * What to collide with in this world block, where the real one is empty: the scanned ground there (of a switched
     * on simulation), or null for none.
     */
    @Nullable
    public static VoxelShape groundAt(Level level, BlockPos pos)
    {
        for(Bubble bubble : in(level))
        {
            if(bubble.solid)
            {
                VoxelShape shape = bubble.groundIn(pos);
                if(shape != null)
                {
                    return shape;
                }
            }
        }
        return null;
    }
}
