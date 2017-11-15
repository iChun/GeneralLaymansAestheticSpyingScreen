package me.ichun.mods.glass.common.tileentity;

import me.ichun.mods.glass.common.GeneralLaymansAestheticSpyingScreen;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;

public class TileEntityGlassMaster extends TileEntityGlassBase
{
    public boolean powered;
    public String setChannel = "";
    public EnumFacing placingFace = EnumFacing.NORTH;
    public ArrayList<BlockPos> wirelessPos = new ArrayList<>();

    public float rotationBeacon, rotationBeaconPrev;

    @Override
    public void update()
    {
        super.update(); //Glass master is also a glass base. Let it do it's job.
        float rotationFactor = active && channel.equalsIgnoreCase(setChannel) ? (1.0F - (1.0F * (float)fadeoutTime / FADEOUT_TIME)) : (1.0F * (float)fadeoutTime / FADEOUT_TIME);
        rotationBeacon += 20F * rotationFactor;
        rotationBeaconPrev = rotationBeacon;
    }

    public void changeRedstoneState(boolean newState)
    {
        if(!setChannel.isEmpty() && (!active || channel.equalsIgnoreCase(setChannel)))
        {
            if(newState)
            {
                active = true;
                GeneralLaymansAestheticSpyingScreen.eventHandlerClient.addActiveGlass(this, channel);
                channel = setChannel;
                distance = 1;
                activeFaces.add(placingFace);
            }
            else
            {
                active = false;
                GeneralLaymansAestheticSpyingScreen.eventHandlerClient.removeActiveGlass(this, channel);
            }
            fadeoutTime = TileEntityGlassBase.FADEOUT_TIME;
            propagateTime = TileEntityGlassBase.PROPAGATE_TIME;
            IBlockState state = getWorld().getBlockState(getPos());
            getWorld().notifyBlockUpdate(getPos(), state, state, 3);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag)
    {
        super.writeToNBT(tag);
        tag.setBoolean("powered", powered);
        tag.setString("setChannel", setChannel);
        tag.setInteger("placingFace", placingFace.getIndex());
        tag.setInteger("wirelessPos", wirelessPos.size());
        for(int i = 0; i < wirelessPos.size(); i++)
        {
            BlockPos pos = wirelessPos.get(i);
            tag.setInteger("wPx_" + i, pos.getX());
            tag.setInteger("wPy_" + i, pos.getY());
            tag.setInteger("wPz_" + i, pos.getZ());
        }
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag)
    {
        super.readFromNBT(tag);
        powered = tag.getBoolean("powered");
        setChannel = tag.getString("setChannel");
        placingFace = EnumFacing.getFront(tag.getInteger("placingFace"));
        int pos = tag.getInteger("wirelessPos");
        for(int i = 0; i < pos; i++)
        {
            wirelessPos.add(new BlockPos(tag.getInteger("wPx_" + i), tag.getInteger("wPy_" + i), tag.getInteger("wPz_" + i)));
        }

    }

}
