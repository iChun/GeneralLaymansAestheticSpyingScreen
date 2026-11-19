package ryzlar.glass.gadget;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.GlassSounds;

/**
 * A holo map table: over it floats a live miniature of the land around it, with dots for the players (cyan), monsters
 * (red) and animals (green) about. Right-click to switch it on or off.
 */
public class HoloMapTableBlock extends BaseEntityBlock
{
    public static final MapCodec<HoloMapTableBlock> CODEC = simpleCodec(HoloMapTableBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 13, 16);

    public HoloMapTableBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, true));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec()
    {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(LIT);
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult)
    {
        if(!level.isClientSide)
        {
            boolean lit = !state.getValue(LIT);
            level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_ALL);
            level.playSound(null, pos, lit ? GlassSounds.NANO_ASSEMBLE : GlassSounds.NANO_DISSOLVE, SoundSource.BLOCKS, 0.7F, 1.1F);
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new HoloMapTableBlockEntity(pos, state);
    }

    /** Only there so the client can draw the map (see the client's HoloMapRenderer). */
    public static class HoloMapTableBlockEntity extends BlockEntity
    {
        /** Client: when it was last switched on (the map builds up from then). */
        public long clientOnSince = Long.MIN_VALUE / 2;

        public HoloMapTableBlockEntity(BlockPos pos, BlockState state)
        {
            super(Gadgets.HOLO_MAP_TABLE_BE, pos, state);
        }
    }
}
