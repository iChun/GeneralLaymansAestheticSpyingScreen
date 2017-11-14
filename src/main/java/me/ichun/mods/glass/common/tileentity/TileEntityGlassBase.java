package me.ichun.mods.glass.common.tileentity;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;

public class TileEntityGlassBase extends TileEntity implements ITickable
{
    public static int FADEOUT_TIME = 12;
    public static int PROPAGATE_TIME = 2;

    public int fadeoutTime = 0;

    public ArrayList<EnumFacing> activeFaces = new ArrayList<>();
    public boolean active = false;
    public String channel = "";
    public int distance = 0; //distance = 0 also means off
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
                propagate();
            }
        }
    }

    public void propagate() //do I need to send active state, channel, online/offline, block change/init propagation?
    {
        //DO STUFF
        for(EnumFacing facing : EnumFacing.VALUES)
        {
            BlockPos pos = this.getPos().offset(facing);
            TileEntity te = getWorld().getTileEntity(pos);
            if(te instanceof TileEntityGlassBase)
            {
                ((TileEntityGlassBase)te).bePropagatedTo(this, channel, active);
            }
        }
        if(!active)
        {
            channel = "";
            distance = 0;
        }
    }

    public void bePropagatedTo(TileEntityGlassBase base, String newChannel, boolean activate)
    {
        boolean flag = false;
        if(active && activate && channel.equalsIgnoreCase(newChannel) && distance > base.distance + 1) //same channel and both activated but this is further than the other from master.
        {
            distance = base.distance + 1;
            checkFacesToTurnOn(base);
            flag = true;
        }
        if(activate && !active && (distance > base.distance || distance == 0)) //turn on
        {
            active = true;
            channel = newChannel;
            distance = base.distance + 1;
            checkFacesToTurnOn(base);
            flag = true;
        }
        if(!activate && active && channel.equalsIgnoreCase(newChannel)) //turn off
        {
            if(distance > base.distance || base == this)
            {
                active = false;
                flag = true;
            }
            else
            {
                propagateTime = TileEntityGlassBase.PROPAGATE_TIME + 2;
                IBlockState state = getWorld().getBlockState(getPos());
                getWorld().notifyBlockUpdate(getPos(), state, state, 3);
            }
            //do not set channel or distance as we're still propagating
        }
        if(flag)
        {
            fadeoutTime = TileEntityGlassBase.FADEOUT_TIME;
            propagateTime = TileEntityGlassBase.PROPAGATE_TIME;
            IBlockState state = getWorld().getBlockState(getPos());
            getWorld().notifyBlockUpdate(getPos(), state, state, 3);
        }
    }

    public void checkFacesToTurnOn(TileEntityGlassBase origin)
    {
        if(origin != this)
        {
            activeFaces.clear();
            activeFaces.addAll(origin.activeFaces);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag)
    {
        super.writeToNBT(tag);
        tag.setInteger("activeFaces", activeFaces.size());
        for(int i = 0; i < activeFaces.size(); i++)
        {
            tag.setInteger("activeFace_" + i, activeFaces.get(i).getIndex());
        }
        tag.setBoolean("active", active);
        tag.setString("channel", channel);
        tag.setInteger("distance", distance);
        tag.setInteger("propagateTime", propagateTime);
        tag.setInteger("fadeoutTime", fadeoutTime);
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
        activeFaces.clear();
        int faceCount = tag.getInteger("activeFaces");
        for(int i = 0; i < faceCount; i++)
        {
            activeFaces.add(EnumFacing.getFront(tag.getInteger("activeFace_" + i)));
        }
        active = tag.getBoolean("active");
        channel = tag.getString("channel");
        distance = tag.getInteger("distance");
        propagateTime = tag.getInteger("propagateTime");
        fadeoutTime = tag.getInteger("fadeoutTime");
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
