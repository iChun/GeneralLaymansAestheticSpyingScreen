package ryzlar.glass.gadget;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A hard-light emitter. Put it at the bottom of one side of an opening, its lens facing across: powered, it fills the
 * opening with hard light, across to the other side and up to the top (at most {@link HardLightEmitterBlockEntity#MAX_SPAN}
 * across and {@link HardLightEmitterBlockEntity#MAX_HEIGHT} up), a column at a time; unpowered, the light goes again.
 */
public class HardLightEmitterBlock extends BaseEntityBlock
{
    public static final MapCodec<HardLightEmitterBlock> CODEC = simpleCodec(HardLightEmitterBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);

    public HardLightEmitterBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec()
    {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING, POWERED);
    }

    /** The lens faces the way the player looks: across the opening. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection())
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return Shapes.block();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new HardLightEmitterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return level.isClientSide ? null : createTickerHelper(type, Gadgets.HARD_LIGHT_EMITTER_BE, HardLightEmitterBlockEntity::serverTick);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston)
    {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        boolean signal = level.hasNeighborSignal(pos);
        if(!level.isClientSide && signal != state.getValue(POWERED))
        {
            level.setBlock(pos, state.setValue(POWERED, signal), Block.UPDATE_CLIENTS);
        }
    }
}
