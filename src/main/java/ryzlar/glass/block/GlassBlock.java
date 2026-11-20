package ryzlar.glass.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.GlassClientBridge;
import ryzlar.glass.GlassUtil;
import ryzlar.glass.blockentity.GlassBaseBlockEntity;
import ryzlar.glass.blockentity.GlassProjectorBlockEntity;
import ryzlar.glass.blockentity.GlassWirelessBlockEntity;

/**
 * GLASS extension, projector and wireless extension blocks. Port of BlockGlass.
 */
public class GlassBlock extends TransparentBlock implements EntityBlock
{
    public enum Kind
    {
        EXTENSION,
        PROJECTOR,
        WIRELESS
    }

    private final Kind kind;
    private final MapCodec<GlassBlock> codec;

    public GlassBlock(Properties properties, Kind kind)
    {
        super(properties);
        this.kind = kind;
        this.codec = simpleCodec(p -> new GlassBlock(p, kind));
    }

    public Kind getKind()
    {
        return kind;
    }

    @Override
    protected MapCodec<? extends TransparentBlock> codec()
    {
        return codec;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return switch(kind)
        {
            case PROJECTOR -> new GlassProjectorBlockEntity(pos, state);
            case WIRELESS -> new GlassWirelessBlockEntity(pos, state);
            case EXTENSION -> new GlassBaseBlockEntity(pos, state);
        };
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return (lvl, pos, st, be) -> {
            if(be instanceof GlassBaseBlockEntity glass)
            {
                GlassBaseBlockEntity.tick(lvl, pos, st, glass);
            }
        };
    }

    /** The GLASS glass blocks join up seamlessly, like one block type did in 1.12 (not the wireless extension's pylon). */
    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacentState, Direction direction)
    {
        boolean glass = kind != Kind.WIRELESS && adjacentState.getBlock() instanceof GlassBlock other && other.kind != Kind.WIRELESS;
        return glass || super.skipRendering(state, adjacentState, direction);
    }

    /** The wireless extension is a relay pylon, not a whole block. */
    private static final net.minecraft.world.phys.shapes.VoxelShape PYLON = net.minecraft.world.level.block.Block.box(3, 0, 3, 13, 14, 13);

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos,
                                                                  net.minecraft.world.phys.shapes.CollisionContext context)
    {
        return kind == Kind.WIRELESS ? PYLON : super.getShape(state, level, pos, context);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult)
    {
        BlockEntity te = level.getBlockEntity(pos);
        if(te instanceof GlassProjectorBlockEntity projector)
        {
            if(level.isClientSide)
            {
                GlassClientBridge.get().openProjectorScreen(projector);
            }
            return InteractionResult.SUCCESS;
        }
        if(te instanceof GlassWirelessBlockEntity wireless && !wireless.active)
        {
            if(level.isClientSide)
            {
                GlassClientBridge.get().openWirelessScreen(wireless);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player)
    {
        //Punching a screen sends a ripple across it from where you hit (drawn on the client).
        if(level.isClientSide && level.getBlockEntity(pos) instanceof GlassBaseBlockEntity base && base.active)
        {
            GlassClientBridge.get().onGlassClicked(pos);
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(level, pos, state, placer, stack);
        if(placer != null && level.getBlockEntity(pos) instanceof GlassProjectorBlockEntity projector)
        {
            projector.placingFace = GlassUtil.directionFromEntity(pos, placer);
            projector.sync();
        }
    }

    // In 1.12 a new GLASS block was noticed by its neighbours through neighborChanged(fromPos). 1.21 no longer passes
    // fromPos, so the new block asks its neighbours itself, one tick after it is placed (its block entity exists by then).
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston)
    {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if(!level.isClientSide && !oldState.is(state.getBlock()))
        {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        if(!(level.getBlockEntity(pos) instanceof GlassBaseBlockEntity placed))
        {
            return;
        }
        for(Direction direction : Direction.values())
        {
            if(level.getBlockEntity(pos.relative(direction)) instanceof GlassBaseBlockEntity base)
            {
                onNeighbourGlassPlaced(base, placed);
            }
        }
    }

    /** {@code base} is next to {@code placed}, a newly placed GLASS block. */
    private static void onNeighbourGlassPlaced(GlassBaseBlockEntity base, GlassBaseBlockEntity placed)
    {
        if(!base.active)
        {
            return;
        }
        for(Direction facing : base.getPropagationFaces())
        {
            GlassBaseBlockEntity base2 = base.getGlass(base.getBlockPos().relative(facing, -1));
            if(base2 != null && base2.active && base2.channel.equals(base.channel) && base2.distance < base.distance) //this is the origin
            {
                base.checkFacesToTurnOn(base2);
                if(!placed.active || placed.channel.equals(base.channel))
                {
                    placed.bePropagatedTo(base, base.channel, base.active);
                }
            }
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston)
    {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        for(Direction direction : Direction.values())
        {
            if(level.getBlockEntity(pos.relative(direction)) instanceof GlassBaseBlockEntity base)
            {
                onNeighbourGlassRemoved(base);
            }
        }
    }

    /** A GLASS block next to {@code base} was removed: find another route to the projector, or turn off. */
    private static void onNeighbourGlassRemoved(GlassBaseBlockEntity base)
    {
        if(!base.active)
        {
            return;
        }
        BlockPos pos = base.getBlockPos();
        int distance = base.distance;
        for(Direction facing : base.getPropagationFaces())
        {
            GlassBaseBlockEntity base1 = base.getGlass(pos.relative(facing));
            if(base1 != null && base1.active && base1.channel.equalsIgnoreCase(base.channel) && base1.distance < distance)
            {
                distance = base1.distance;
            }
        }
        if(distance == base.distance)
        {
            base.bePropagatedTo(base, base.channel, false); //turn off if we can't find a close base.
        }
        else
        {
            for(Direction facing : base.getPropagationFaces())
            {
                GlassBaseBlockEntity base2 = base.getGlass(pos.relative(facing, -1));
                if(base2 != null && base2.active && base2.channel.equals(base.channel) && base2.distance < base.distance) //this is the origin
                {
                    base.checkFacesToTurnOn(base2);
                }
            }
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston)
    {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        if(!level.isClientSide && level.getBlockEntity(pos) instanceof GlassProjectorBlockEntity projector)
        {
            boolean flag = level.hasNeighborSignal(pos);
            if(projector.powered != flag)
            {
                projector.changeRedstoneState(flag);
                projector.powered = flag;
                projector.setChanged();
            }
        }
    }
}
