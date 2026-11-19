package ryzlar.glass.gadget;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import ryzlar.glass.GlassSounds;

import java.util.ArrayList;
import java.util.List;

/**
 * Puts up and takes down a hard-light emitter's wall, a column at a time (nearest first), and remembers which blocks
 * of light are its own so it only ever removes those.
 */
public class HardLightEmitterBlockEntity extends BlockEntity
{
    /** How far across and how high a wall can be, in blocks. */
    public static final int MAX_SPAN = 12;
    public static final int MAX_HEIGHT = 8;

    /** The blocks of light it put up. */
    private final List<BlockPos> lit = new ArrayList<>();
    /** The wall it is putting up (columns, nearest first), and how many columns are up. */
    private List<List<BlockPos>> plan = List.of();
    private int columnsUp;

    public HardLightEmitterBlockEntity(BlockPos pos, BlockState state)
    {
        super(Gadgets.HARD_LIGHT_EMITTER_BE, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HardLightEmitterBlockEntity emitter)
    {
        boolean powered = state.getValue(HardLightEmitterBlock.POWERED);
        if(powered)
        {
            if(emitter.plan.isEmpty() && emitter.lit.isEmpty())
            {
                emitter.plan = emitter.measure(level, state.getValue(HardLightEmitterBlock.FACING));
                emitter.columnsUp = 0;
                if(!emitter.plan.isEmpty())
                {
                    level.playSound(null, pos, GlassSounds.NANO_ASSEMBLE, SoundSource.BLOCKS, 0.8F, 1.25F);
                }
            }
            if(emitter.columnsUp < emitter.plan.size())
            {
                for(BlockPos at : emitter.plan.get(emitter.columnsUp++))
                {
                    if(HardLightBlock.canFill(level, at))
                    {
                        level.setBlock(at, Gadgets.HARD_LIGHT.defaultBlockState(), Block.UPDATE_ALL);
                        emitter.lit.add(at);
                    }
                }
                emitter.setChanged();
            }
        }
        else if(!emitter.lit.isEmpty() || !emitter.plan.isEmpty())
        {
            //Down again, a column at a time, the far end first.
            if(emitter.columnsUp == emitter.plan.size() && !emitter.lit.isEmpty())
            {
                level.playSound(null, pos, GlassSounds.NANO_DISSOLVE, SoundSource.BLOCKS, 0.8F, 1.25F);
            }
            if(emitter.columnsUp > 0)
            {
                for(BlockPos at : emitter.plan.get(--emitter.columnsUp))
                {
                    emitter.clear(level, at);
                }
            }
            if(emitter.columnsUp <= 0)
            {
                for(BlockPos at : new ArrayList<>(emitter.lit))
                {
                    emitter.clear(level, at);
                }
                emitter.lit.clear();
                emitter.plan = List.of();
                emitter.columnsUp = 0;
            }
            emitter.setChanged();
        }
    }

    private void clear(Level level, BlockPos at)
    {
        if(lit.remove(at) && HardLightBlock.isHardLight(level.getBlockState(at)))
        {
            level.setBlock(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * The opening in front of the lens: across until something solid (or the span runs out), and up from each column
     * until something solid (or the height runs out). An open side (nothing to reach) gets a wall as wide as the span.
     */
    private List<List<BlockPos>> measure(Level level, Direction facing)
    {
        List<List<BlockPos>> columns = new ArrayList<>();
        int top = MAX_HEIGHT;
        //The height is the opening's height at its first column (a doorway is as high as its first step).
        BlockPos first = worldPosition.relative(facing);
        if(!HardLightBlock.canFill(level, first))
        {
            return columns;
        }
        for(int up = 1; up < MAX_HEIGHT; up++)
        {
            if(!HardLightBlock.canFill(level, first.above(up)))
            {
                top = up;
                break;
            }
        }
        for(int across = 1; across <= MAX_SPAN; across++)
        {
            BlockPos base = worldPosition.relative(facing, across);
            if(!HardLightBlock.canFill(level, base))
            {
                break;
            }
            List<BlockPos> column = new ArrayList<>();
            for(int up = 0; up < top; up++)
            {
                BlockPos at = base.above(up);
                if(!HardLightBlock.canFill(level, at))
                {
                    break;
                }
                column.add(at.immutable());
            }
            columns.add(column);
        }
        return columns;
    }

    /** Broken or removed: its light goes with it. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        super.preRemoveSideEffects(pos, state);
        if(level != null && !level.isClientSide)
        {
            for(BlockPos at : lit)
            {
                if(HardLightBlock.isHardLight(level.getBlockState(at)))
                {
                    level.setBlock(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            lit.clear();
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        super.saveAdditional(output);
        output.store("lit", BlockPos.CODEC.listOf(), List.copyOf(lit));
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        lit.clear();
        lit.addAll(input.read("lit", BlockPos.CODEC.listOf()).orElse(List.of()));
        //A wall that was up stays up as it was; the plan is only for putting up and taking down.
        plan = lit.isEmpty() ? List.of() : List.of(List.copyOf(lit));
        columnsUp = plan.size();
    }
}
