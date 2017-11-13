package me.ichun.mods.glass.common.tileentity;

import net.minecraft.nbt.NBTTagCompound;

public class TileEntityGlassMaster extends TileEntityGlassBase
{
    public boolean powered;

    @Override
    public void update()
    {
        super.update(); //Glass master is also a glass base. Let it do it's job.
    }

    public void changeRedstoneState(boolean newState)
    {

    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag)
    {
        super.writeToNBT(tag);
        tag.setBoolean("powered", this.powered);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag)
    {
        super.readFromNBT(tag);
        this.powered = tag.getBoolean("powered");
    }

}
