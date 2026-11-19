package ryzlar.glass.holo;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The room a holo cube scans: the open space around it (flooded out to the walls, within a box), and the blocks that
 * make it a room: the solid blocks around that space, and the small things standing in it (torches, flowers, carpet).
 * Worked out the same way on the server (what goes in the scan) and the client (where the scan's light runs).
 */
public final class RoomScan
{
    /** The box a room is looked for in, around the cube's block. */
    public static final int HALF_WIDTH = 16;
    public static final int BELOW = 4;
    public static final int ABOVE = 12;

    /** {@code surfaces}: solid blocks touching the open space; {@code open}: the space; {@code blocks}: what is scanned. */
    public record Room(BlockPos centre, BlockPos min, BlockPos max, LongSet surfaces, LongSet open, LongSet blocks)
    {
        /** How far the farthest scanned block is from the cube, in blocks. */
        public float reach()
        {
            float reach = 0F;
            for(long key : blocks)
            {
                float dx = BlockPos.getX(key) - centre.getX(), dy = BlockPos.getY(key) - centre.getY(), dz = BlockPos.getZ(key) - centre.getZ();
                reach = Math.max(reach, (float)Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
            return reach + 1F;
        }
    }

    private RoomScan() {}

    /** The room around this block (the cube's), or null if it is not in an open space. */
    @Nullable
    public static Room find(Level level, BlockPos from)
    {
        BlockPos start = isOpen(level, from) ? from : from.above();
        if(!isOpen(level, start))
        {
            return null;
        }
        BlockPos min = from.offset(-HALF_WIDTH, -BELOW, -HALF_WIDTH);
        BlockPos max = from.offset(HALF_WIDTH - 1, ABOVE, HALF_WIDTH - 1);
        LongSet open = new LongOpenHashSet();
        LongSet surfaces = new LongOpenHashSet();
        LongSet blocks = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        open.add(start.asLong());
        queue.enqueue(start.asLong());
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
        while(!queue.isEmpty())
        {
            long at = queue.dequeueLong();
            BlockPos pos = BlockPos.of(at);
            if(!level.getBlockState(pos).isAir())
            {
                blocks.add(at); //a torch, a flower, carpet: no collision, but part of the room
            }
            for(Direction direction : Direction.values())
            {
                next.setWithOffset(pos, direction);
                if(next.getX() < min.getX() || next.getY() < min.getY() || next.getZ() < min.getZ()
                        || next.getX() > max.getX() || next.getY() > max.getY() || next.getZ() > max.getZ())
                {
                    continue;
                }
                long key = next.asLong();
                if(open.contains(key) || surfaces.contains(key))
                {
                    continue;
                }
                if(isOpen(level, next))
                {
                    open.add(key);
                    queue.enqueue(key);
                }
                else
                {
                    surfaces.add(key);
                    blocks.add(key);
                }
            }
        }
        if(surfaces.isEmpty())
        {
            return null;
        }
        return new Room(from.immutable(), min, max, surfaces, open, blocks);
    }

    private static boolean isOpen(Level level, BlockPos pos)
    {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }
}
