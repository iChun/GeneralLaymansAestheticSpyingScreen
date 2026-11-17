package ryzlar.glass.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.GlassRegistry;
import ryzlar.glass.network.GlassNetworkData;
import ryzlar.glass.network.GlassNetworkManager;

/**
 * The GLASS terminal: the camera of a GLASS channel. Each channel has one camera; the channel is made at the terminal.
 */
public class GlassTerminalBlockEntity extends BlockEntity
{
    private static final int VALIDATE_INTERVAL = 40;

    public String channelId = "";
    public Direction facing = Direction.UP;

    private int validateTimer;

    public GlassTerminalBlockEntity(BlockPos pos, BlockState state)
    {
        super(GlassRegistry.GLASS_TERMINAL_BE, pos, state);
    }

    public boolean isLinked()
    {
        return !channelId.isEmpty();
    }

    /** Server: drop the link if the channel was deleted or moved its camera elsewhere (or this is a copy of a terminal). */
    public static void serverTick(Level level, BlockPos pos, BlockState state, GlassTerminalBlockEntity terminal)
    {
        if(!terminal.isLinked() || ++terminal.validateTimer < VALIDATE_INTERVAL)
        {
            return;
        }
        terminal.validateTimer = 0;
        GlassNetworkData.Channel channel = GlassNetworkManager.data(((ServerLevel)level).getServer()).channels.get(terminal.channelId);
        GlassNetworkData.Camera camera = channel == null ? null : channel.camera;
        if(camera == null || !camera.pos().equals(pos) || !camera.dimension().equals(level.dimension()))
        {
            terminal.channelId = "";
            terminal.sync();
        }
    }

    /** The terminal was broken (not just unloaded): take its camera off the network. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        super.preRemoveSideEffects(pos, state);
        if(level instanceof ServerLevel serverLevel && isLinked())
        {
            GlassNetworkManager.forgetCamera(serverLevel.getServer(), channelId, serverLevel, pos);
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
        output.putString("channelId", channelId);
        output.store("facing", Direction.CODEC, facing);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        channelId = input.getStringOr("channelId", "");
        facing = input.read("facing", Direction.CODEC).orElse(Direction.UP);
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
