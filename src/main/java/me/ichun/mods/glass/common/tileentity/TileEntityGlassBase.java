package me.ichun.mods.glass.common.tileentity;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;

public class TileEntityGlassBase extends TileEntity implements ITickable
{
    public static final int FADEOUT_TIME = 10;
    public static final int PROPAGATE_TIME = 4;

    public int fadeoutTime = 0;

    public EnumFacing activeFace = EnumFacing.UP;
    public boolean active = false;
    public String channel = "";
    public int distance = 0;
    public int propagateTime = 0;

    @Override
    public void update()
    {
        if(fadeoutTime > 0)
        {
            fadeoutTime--;
        }
        if(propagateTime > 0)
        {
            propagateTime--;
            if(propagateTime == 0)
            {
                //DO STUFF
            }
        }
    }

    public void propagate() //do I need to send active state, channel, online/offline, block change/init propagation?
    {
        //DO STUFF
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag)
    {
        super.writeToNBT(tag);
        tag.setInteger("activeFace", activeFace.getIndex());
        tag.setBoolean("active", active);
        tag.setString("channel", channel);
        tag.setInteger("distance", distance);
        tag.setInteger("propagateTime", propagateTime);
        return tag;
    }

    @Override
    public NBTTagCompound getUpdateTag()
    {
        return this.writeToNBT(new NBTTagCompound());
    }

    @Override
    public void readFromNBT(NBTTagCompound tag)
    {
        super.readFromNBT(tag);
        activeFace = EnumFacing.getFront(tag.getInteger("activeFace"));
        active = tag.getBoolean("active");
        channel = tag.getString("channel");
        distance = tag.getInteger("distance");
        propagateTime = tag.getInteger("propagateTime");
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket()
    {
        return new SPacketUpdateTileEntity(getPos(), 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt)
    {
        readFromNBT(pkt.getNbtCompound());
    }
}
