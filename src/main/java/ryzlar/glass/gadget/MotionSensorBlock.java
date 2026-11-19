package ryzlar.glass.gadget;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A motion sensor, for a GLASS terminal: put it right next to one. When someone (a player or a monster) comes into the
 * terminal's view, the sensor gives a redstone signal and the channel's screens flash ALERT.
 */
public class MotionSensorBlock extends BaseEntityBlock
{
    public static final MapCodec<MotionSensorBlock> CODEC = simpleCodec(MotionSensorBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 9, 13);

    public MotionSensorBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec()
    {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(POWERED);
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
    protected boolean isSignalSource(BlockState state)
    {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction)
    {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction)
    {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new MotionSensorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return level.isClientSide ? null : createTickerHelper(type, Gadgets.MOTION_SENSOR_BE, MotionSensorBlockEntity::serverTick);
    }
}
