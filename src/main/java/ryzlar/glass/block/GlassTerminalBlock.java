package ryzlar.glass.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.GlassClientBridge;
import ryzlar.glass.GlassRegistry;
import ryzlar.glass.GlassUtil;
import ryzlar.glass.blockentity.GlassTerminalBlockEntity;

/**
 * The GLASS terminal. Like the 1.12 version, its chunk model is invisible and the block entity renderer draws it,
 * so it can be left out of the terminal's own camera view.
 */
public class GlassTerminalBlock extends BaseEntityBlock
{
    public static final MapCodec<GlassTerminalBlock> CODEC = simpleCodec(GlassTerminalBlock::new);

    public GlassTerminalBlock(Properties properties)
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
        return RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new GlassTerminalBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return level.isClientSide ? createTickerHelper(type, GlassRegistry.GLASS_TERMINAL_BE, GlassTerminalBlockEntity::clientTick) : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult)
    {
        if(level.isClientSide && level.getBlockEntity(pos) instanceof GlassTerminalBlockEntity terminal)
        {
            GlassClientBridge.get().openTerminalScreen(terminal);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(level, pos, state, placer, stack);
        if(!level.isClientSide && placer != null && level.getBlockEntity(pos) instanceof GlassTerminalBlockEntity terminal)
        {
            terminal.facing = GlassUtil.directionFromEntity(pos, placer);
            terminal.sync();
        }
    }
}
