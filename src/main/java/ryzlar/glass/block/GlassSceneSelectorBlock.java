package ryzlar.glass.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.GlassClientBridge;
import ryzlar.glass.blockentity.GlassSceneSelectorBlockEntity;

/** The scene selector block: right-click for its panel, a redstone pulse shows the next scene. */
public class GlassSceneSelectorBlock extends BaseEntityBlock
{
    public static final MapCodec<GlassSceneSelectorBlock> CODEC = simpleCodec(GlassSceneSelectorBlock::new);

    public GlassSceneSelectorBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec()
    {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new GlassSceneSelectorBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult)
    {
        if(level.isClientSide && level.getBlockEntity(pos) instanceof GlassSceneSelectorBlockEntity selector)
        {
            GlassClientBridge.get().openSceneSelectorScreen(selector);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston)
    {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        if(!level.isClientSide && level.getBlockEntity(pos) instanceof GlassSceneSelectorBlockEntity selector)
        {
            boolean signal = level.hasNeighborSignal(pos);
            if(signal && !selector.powered)
            {
                selector.nextScene();
            }
            if(signal != selector.powered)
            {
                selector.powered = signal;
                selector.setChanged();
            }
        }
    }
}
