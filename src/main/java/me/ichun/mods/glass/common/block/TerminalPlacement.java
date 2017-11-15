package me.ichun.mods.glass.common.block;

import me.ichun.mods.ichunutil.common.module.worldportals.common.portal.WorldPortal;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class TerminalPlacement extends WorldPortal
{
    public TerminalPlacement(World world)
    {
        super(world);
    }

    public TerminalPlacement(World world, Vec3d position, EnumFacing faceOn, EnumFacing upDir, float width, float height)
    {
        super(world, position, faceOn, upDir, width, height);
    }

    @Override
    public float getPlaneOffset()
    {
        return 0F;
    }

    @Override
    public boolean canCollideWithBorders()
    {
        return false;
    }

    @Override
    public String owner()
    {
        return null;
    }

    @Override
    public void drawPlane(float partialTick)
    {

    }

    @Override
    public <T extends WorldPortal> T createFakeInstance(NBTTagCompound tag)
    {
//        TerminalPlacement terminalPlacement = new TerminalPlacement(world);
        return null;
    }
}
