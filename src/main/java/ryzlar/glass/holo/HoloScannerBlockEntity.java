package ryzlar.glass.holo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.GlassRegistry;
import ryzlar.glass.GlassUtil;

/** The projection scanner: the area it scans (an offset from the scanner and a size) and the name for the next scan. */
public class HoloScannerBlockEntity extends BlockEntity
{
    public static final int MAX_OFFSET = 32;
    public static final int MAX_NAME = 24;

    public int offsetX = 1;
    public int offsetY = 0;
    public int offsetZ = 0;
    public int sizeX = 5;
    public int sizeY = 5;
    public int sizeZ = 5;
    public String name = "";

    public HoloScannerBlockEntity(BlockPos pos, BlockState state)
    {
        super(GlassRegistry.HOLO_SCANNER_BE, pos, state);
    }

    public BlockPos regionOrigin()
    {
        return worldPosition.offset(offsetX, offsetY, offsetZ);
    }

    public AABB regionBox()
    {
        BlockPos o = regionOrigin();
        return new AABB(o.getX(), o.getY(), o.getZ(), o.getX() + sizeX, o.getY() + sizeY, o.getZ() + sizeZ);
    }

    /** Sets the area, clamped to what is allowed. */
    public void configure(int ox, int oy, int oz, int sx, int sy, int sz, String newName)
    {
        offsetX = Math.clamp(ox, -MAX_OFFSET, MAX_OFFSET);
        offsetY = Math.clamp(oy, -MAX_OFFSET, MAX_OFFSET);
        offsetZ = Math.clamp(oz, -MAX_OFFSET, MAX_OFFSET);
        sizeX = Math.clamp(sx, 1, HoloProjectionData.MAX_SIZE);
        sizeY = Math.clamp(sy, 1, HoloProjectionData.MAX_SIZE);
        sizeZ = Math.clamp(sz, 1, HoloProjectionData.MAX_SIZE);
        name = newName.length() > MAX_NAME ? newName.substring(0, MAX_NAME) : newName;
        sync();
    }

    public void sync()
    {
        setChanged();
        if(level != null && !level.isClientSide)
        {
            GlassUtil.sendToTracking(this);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        super.saveAdditional(output);
        output.putInt("offsetX", offsetX);
        output.putInt("offsetY", offsetY);
        output.putInt("offsetZ", offsetZ);
        output.putInt("sizeX", sizeX);
        output.putInt("sizeY", sizeY);
        output.putInt("sizeZ", sizeZ);
        output.putString("name", name);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        offsetX = input.getIntOr("offsetX", 1);
        offsetY = input.getIntOr("offsetY", 0);
        offsetZ = input.getIntOr("offsetZ", 0);
        sizeX = input.getIntOr("sizeX", 5);
        sizeY = input.getIntOr("sizeY", 5);
        sizeZ = input.getIntOr("sizeZ", 5);
        name = input.getStringOr("name", "");
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
