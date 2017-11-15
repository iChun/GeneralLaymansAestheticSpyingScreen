package me.ichun.mods.glass.common.tileentity;

import me.ichun.mods.glass.common.GeneralLaymansAestheticSpyingScreen;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class TileEntityGlassTerminal extends TileEntity
{
    public String channelName = "";
    public EnumFacing facing = EnumFacing.UP;

    @Override
    public void onLoad()
    {
        if(getWorld().isRemote && !channelName.isEmpty())
        {
            GeneralLaymansAestheticSpyingScreen.eventHandlerClient.terminalLocations.put(channelName, getPos());
        }
    }

    @Override
    public void onChunkUnload()
    {
        if(getWorld().isRemote && !channelName.isEmpty())
        {
            GeneralLaymansAestheticSpyingScreen.eventHandlerClient.terminalLocations.remove(channelName);
        }
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState)
    {
        boolean flag = oldState != newState;
        if(flag && world.isRemote && !channelName.isEmpty()) // new TE or removed
        {
            GeneralLaymansAestheticSpyingScreen.eventHandlerClient.terminalLocations.remove(channelName);
        }
        return flag;
    }

    @Override
    public NBTTagCompound getUpdateTag()
    {
        return this.writeToNBT(new NBTTagCompound());
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag)
    {
        super.writeToNBT(tag);
        tag.setString("channelName", channelName);
        tag.setInteger("facing", facing.getIndex());
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag)
    {
        super.readFromNBT(tag);
        channelName = tag.getString("channelName");
        facing = EnumFacing.getFront(tag.getInteger("facing"));
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket()
    {
        return new SPacketUpdateTileEntity(getPos(), 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt)
    {
        onChunkUnload();
        readFromNBT(pkt.getNbtCompound());
        onLoad();
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag)
    {
        onChunkUnload();
        readFromNBT(tag);
        onLoad();
    }
}
