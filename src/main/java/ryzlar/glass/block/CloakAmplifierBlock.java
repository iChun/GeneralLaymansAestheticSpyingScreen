package ryzlar.glass.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A cloak amplifier. Next to a GLASS projector it lets the projector's cloak be bigger: each one (two at most) raises
 * the limits of a cloak field, a glass cloak and their depth and height (see GlassProjectorBlockEntity.cloakLimits).
 */
public class CloakAmplifierBlock extends Block
{
    public static final MapCodec<CloakAmplifierBlock> CODEC = simpleCodec(CloakAmplifierBlock::new);
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);

    public CloakAmplifierBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec()
    {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }
}
