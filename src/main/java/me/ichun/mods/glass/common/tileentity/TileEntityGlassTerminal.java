package me.ichun.mods.glass.common.tileentity;

import me.ichun.mods.glass.common.GeneralLaymansAestheticSpyingScreen;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

public class TileEntityGlassTerminal extends TileEntity
{
    public String channelName = "";

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
    public NBTTagCompound getUpdateTag()
    {
        return this.writeToNBT(new NBTTagCompound());
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag)
    {
        super.writeToNBT(tag);
        tag.setString("channelName", channelName);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag)
    {
        super.readFromNBT(tag);
        channelName = tag.getString("channelName");
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
