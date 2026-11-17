package ryzlar.glass.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.GlassClientBridge;
import ryzlar.glass.GlassRegistry;

/**
 * The GLASS terminal: the "camera" whose view is shown on every GLASS screen tuned to its channel.
 * Port of TileEntityGlassTerminal.
 */
public class GlassTerminalBlockEntity extends BlockEntity
{
    public String channelName = "";
    public Direction facing = Direction.UP;

    public GlassTerminalBlockEntity(BlockPos pos, BlockState state)
    {
        super(GlassRegistry.GLASS_TERMINAL_BE, pos, state);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, GlassTerminalBlockEntity terminal)
    {
        GlassClientBridge.get().syncTerminal(terminal);
    }

    @Override
    public void setRemoved()
    {
        super.setRemoved();
        if(level != null && level.isClientSide)
        {
            GlassClientBridge.get().removeTerminal(this);
        }
    }

    public void sync()
    {
        setChanged();
        if(level != null && !level.isClientSide)
        {
            BlockState state = getBlockState();
            level.sendBlockUpdated(getBlockPos(), state, state, 3);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        super.saveAdditional(output);
        output.putString("channelName", channelName);
        output.store("facing", Direction.CODEC, facing);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        channelName = input.getStringOr("channelName", "");
        facing = input.read("facing", Direction.CODEC).orElse(Direction.UP);
        if(level != null && level.isClientSide)
        {
            GlassClientBridge.get().syncTerminal(this);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries)
    {
        return saveCustomOnly(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
