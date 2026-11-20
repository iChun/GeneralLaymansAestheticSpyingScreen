package ryzlar.glass.holo;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The shape of a holo cube's scan and simulation: a ball of blocks round the cube's block. The scan takes every block
 * whose centre is within {@link #SCAN_RADIUS} of the cube's block centre (ground and all), fifteen blocks across; the
 * simulation shows it shrunk to {@link #SCALE}, ten blocks across, in a bubble of {@link #BUBBLE_RADIUS}, its glowing
 * wall just round it.
 */
public final class HoloSphere
{
    /** How far the scan reaches from the cube's block, in blocks (a ball fifteen blocks across). */
    public static final int SCAN_RADIUS = 7;
    /** A block is in the ball if its centre is this close (squared) to the cube's block centre: a little rounder than 7². */
    private static final int SCAN_RADIUS_SQ = 52;
    /** The scan's box, each way: the ball fits in it with the cube's block in the middle. */
    public static final int SIZE = SCAN_RADIUS * 2 + 1;
    /** The simulation shows the scan this size: fifteen blocks across become ten. */
    public static final float SCALE = 10F / 15F;
    /** The simulation's bubble: its wall, half a block round the shrunk ball. */
    public static final float BUBBLE_RADIUS = (SCAN_RADIUS + 0.5F) * SCALE + 0.5F;

    private HoloSphere() {}

    /** True if a block this far from the cube's block (in whole blocks each way) is in the scanned ball. */
    public static boolean inBall(int dx, int dy, int dz)
    {
        return dx * dx + dy * dy + dz * dz <= SCAN_RADIUS_SQ;
    }

    public static boolean inBall(BlockPos centre, BlockPos pos)
    {
        return inBall(pos.getX() - centre.getX(), pos.getY() - centre.getY(), pos.getZ() - centre.getZ());
    }

    /**
     * Ground nature made: what a simulation's visitors walk on (the rest of the scan, houses, trees, furniture, they walk
     * through). Grass, dirt, stone, sand, gravel, snow, clay, ice, terracotta, ores and the like.
     */
    public static boolean isNaturalGround(BlockState state)
    {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER)
                || state.is(BlockTags.NYLIUM) || state.is(BlockTags.SNOW) || state.is(BlockTags.TERRACOTTA) || state.is(BlockTags.ICE)
                || state.is(ConventionalBlockTags.ORES) || state.is(Blocks.GRAVEL) || state.is(Blocks.SUSPICIOUS_GRAVEL) || state.is(Blocks.SUSPICIOUS_SAND)
                || state.is(Blocks.DIRT_PATH) || state.is(Blocks.FARMLAND) || state.is(Blocks.CLAY) || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE)
                || state.is(Blocks.END_STONE) || state.is(Blocks.BEDROCK) || state.is(Blocks.OBSIDIAN) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL) || state.is(Blocks.CALCITE) || state.is(Blocks.DRIPSTONE_BLOCK);
    }

    /**
     * The scanned ball's faces that can be seen: every side of a block in it that is not air and has open space next to
     * it (in the ball or not), as the block's position and the side's ordinal ({@link Direction#ordinal()}) in turn.
     * For the scan's light running over the real world.
     */
    public static LongList surfaces(Level level, BlockPos centre)
    {
        LongList faces = new LongArrayList();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
        for(int dy = -SCAN_RADIUS; dy <= SCAN_RADIUS; dy++)
        {
            for(int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++)
            {
                for(int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++)
                {
                    if(!inBall(dx, dy, dz))
                    {
                        continue;
                    }
                    pos.setWithOffset(centre, dx, dy, dz);
                    if(level.getBlockState(pos).isAir())
                    {
                        continue;
                    }
                    for(Direction direction : Direction.values())
                    {
                        next.setWithOffset(pos, direction);
                        if(!level.getBlockState(next).isSolidRender())
                        {
                            faces.add(pos.asLong());
                            faces.add(direction.ordinal());
                        }
                    }
                }
            }
        }
        return faces;
    }
}
