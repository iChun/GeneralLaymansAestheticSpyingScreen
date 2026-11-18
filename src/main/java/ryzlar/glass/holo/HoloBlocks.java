package ryzlar.glass.holo;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.RodBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.GlassClientBridge;
import ryzlar.glass.GlassRegistry;

/** The blocks of the hologram kit: the scanner, the holo projector and its emitters. */
public final class HoloBlocks
{
    private HoloBlocks() {}

    /** Scans an area into a projection crystal. Right-click: its panel (area, name, SCAN). */
    public static class Scanner extends BaseEntityBlock
    {
        public static final MapCodec<Scanner> CODEC = simpleCodec(Scanner::new);

        public Scanner(Properties properties)
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
            return new HoloScannerBlockEntity(pos, state);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult)
        {
            if(level.isClientSide && level.getBlockEntity(pos) instanceof HoloScannerBlockEntity scanner)
            {
                GlassClientBridge.get().openHoloScannerScreen(scanner);
            }
            return InteractionResult.SUCCESS;
        }
    }

    /**
     * Projects the crystal it holds while powered. Right-click with a crystal: put it in. Sneak-right-click with an empty
     * hand: take it out. Right-click: its panel.
     */
    public static class Projector extends BaseEntityBlock
    {
        public static final MapCodec<Projector> CODEC = simpleCodec(Projector::new);

        public Projector(Properties properties)
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
            return new HoloProjectorBlockEntity(pos, state);
        }

        @Nullable
        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
        {
            return level.isClientSide ? null : createTickerHelper(type, GlassRegistry.HOLO_PROJECTOR_BE, HoloProjectorBlockEntity::serverTick);
        }

        @Override
        protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult)
        {
            if(stack.get(GlassRegistry.PROJECTION) != null && level.getBlockEntity(pos) instanceof HoloProjectorBlockEntity projector)
            {
                if(!level.isClientSide)
                {
                    ItemStack old = projector.crystal.isEmpty() ? ItemStack.EMPTY : projector.takeCrystal();
                    projector.putCrystal(stack);
                    stack.shrink(1);
                    if(!old.isEmpty() && !player.addItem(old))
                    {
                        player.drop(old, false);
                    }
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult)
        {
            if(!(level.getBlockEntity(pos) instanceof HoloProjectorBlockEntity projector))
            {
                return InteractionResult.PASS;
            }
            if(player.isSecondaryUseActive() && !projector.crystal.isEmpty())
            {
                if(!level.isClientSide)
                {
                    ItemStack out = projector.takeCrystal();
                    if(!player.addItem(out))
                    {
                        player.drop(out, false);
                    }
                }
                return InteractionResult.SUCCESS;
            }
            if(level.isClientSide)
            {
                GlassClientBridge.get().openHoloProjectorScreen(projector);
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston)
        {
            super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
            if(!level.isClientSide && level.getBlockEntity(pos) instanceof HoloProjectorBlockEntity projector)
            {
                boolean signal = level.hasNeighborSignal(pos);
                if(signal != projector.powered)
                {
                    projector.setPowered(signal);
                }
            }
        }
    }

    /** A holo emitter: a rod that lights up the sides of the hologram it can see. Placed like an end rod. */
    public static class Emitter extends RodBlock
    {
        public static final MapCodec<Emitter> CODEC = simpleCodec(Emitter::new);

        public Emitter(Properties properties)
        {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
        }

        @Override
        protected MapCodec<? extends RodBlock> codec()
        {
            return CODEC;
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context)
        {
            return defaultBlockState().setValue(FACING, context.getClickedFace());
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
        {
            builder.add(FACING);
        }
    }
}
