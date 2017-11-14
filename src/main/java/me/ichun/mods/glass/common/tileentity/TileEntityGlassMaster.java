package me.ichun.mods.glass.common.tileentity;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

public class TileEntityGlassMaster extends TileEntityGlassBase
{
    public boolean powered;
    public String setChannel = "public:Channel 1";
    public EnumFacing placingFace = EnumFacing.NORTH;

    @Override
    public void update()
    {
        super.update(); //Glass master is also a glass base. Let it do it's job.
    }

    public void changeRedstoneState(boolean newState)
    {
        if(!setChannel.isEmpty() && (!active || channel.equalsIgnoreCase(setChannel)))
        {
            if(newState)
            {
                active = true;
                channel = setChannel;
                distance = 1;
                activeFaces.add(placingFace);
            }
            else
            {
                active = false;
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
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag)
    {
        super.readFromNBT(tag);
        powered = tag.getBoolean("powered");
        setChannel = tag.getString("setChannel");
        placingFace = EnumFacing.getFront(tag.getInteger("placingFace"));
    }

}
