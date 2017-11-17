package me.ichun.mods.glass.common.tileentity;

import me.ichun.mods.glass.common.GeneralLaymansAestheticSpyingScreen;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class TileEntityGlassBase extends TileEntity implements ITickable
{
    public static final HashMap<EnumFacing, ArrayList<EnumFacing>> PROPAGATION_FACES = new HashMap<>();
    static
    {
        for(EnumFacing face : EnumFacing.VALUES)
        {
            ArrayList<EnumFacing> faces = PROPAGATION_FACES.computeIfAbsent(face, v -> new ArrayList<>());
            for(EnumFacing face1 : EnumFacing.VALUES)
            {
                if(!face1.getAxis().equals(face.getAxis()))
                {
                    faces.add(face1);
                }
            }
        }
    }

    public static int FADEOUT_TIME = 12;
    public static int PROPAGATE_TIME = 2;

    public int fadeoutTime = 0;

    public ArrayList<EnumFacing> activeFaces = new ArrayList<>();
    public boolean active = false;
    public String channel = "";
    public int distance = 0; //distance = 0 also means off
    public int propagateTime = 0;

    public int fadePropagate;
    public int fadeDistance;

    public int lastDraw;

    @Override
    public void onLoad()
    {
        if(getWorld().isRemote && active && !channel.isEmpty())
        {
            GeneralLaymansAestheticSpyingScreen.eventHandlerClient.addActiveGlass(this, channel);
        }
    }

    @Override
    public void onChunkUnload()
    {
        if(getWorld().isRemote && active && !channel.isEmpty())
        {
            GeneralLaymansAestheticSpyingScreen.eventHandlerClient.removeActiveGlass(this, channel);
        }
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState)
    {
        boolean flag = oldState != newState;
        if(flag && world.isRemote && active && !channel.isEmpty()) // new TE or removed
        {
            GeneralLaymansAestheticSpyingScreen.eventHandlerClient.removeActiveGlass(this, channel);
        }
        return flag;
    }

    @Override
    public void update()
    {
        if(fadeoutTime > 0)
        {
            fadeoutTime--;
            if(fadeoutTime == 0)
            {
                if(!active)
                {
                    activeFaces.clear();
                }
                if(fadeDistance > 0)
                {
                    fadeDistance = 0;
                }
            }
        }
        if(propagateTime > 0)
        {
            propagateTime--;
            if(!world.isRemote && propagateTime == 0)
            {
                propagate();
            }
        }
        if(fadePropagate > 0)
        {
            fadePropagate--;
            if(world.isRemote && fadePropagate == 0)
            {
                fadePropagate();
            }
        }
        if(lastDraw > 0)
        {
            lastDraw--;
        }
    }

    public void fadePropagate()
    {
        if(fadeDistance <= 0 || !active)
        {
            return;
        }
        HashSet<EnumFacing> propagationFaces = new HashSet<>();
        for(EnumFacing facing : activeFaces)
        {
            propagationFaces.addAll(PROPAGATION_FACES.get(facing));
        }
        for(EnumFacing facing : propagationFaces)
        {
            BlockPos pos = this.getPos().offset(facing);
            TileEntity te = getWorld().getTileEntity(pos);
            if(te instanceof TileEntityGlassBase)
            {
                TileEntityGlassBase base = (TileEntityGlassBase)te;
                if(base.active && base.channel.equalsIgnoreCase(channel) && base.fadeDistance <= fadeDistance)
                {
                    base.fadeoutTime = FADEOUT_TIME;
                    base.fadePropagate = PROPAGATE_TIME;
                    base.fadeDistance = fadeDistance - 1;
                }
            }
        }
    }

    public boolean canPropagate()
    {
        return distance < 40;
    }

    public void propagate() //do I need to send active state, channel, online/offline, block change/init propagation?
    {
        if(!canPropagate())
        {
            return;
        }
        HashSet<EnumFacing> propagationFaces = new HashSet<>();
        for(EnumFacing facing : activeFaces)
        {
            propagationFaces.addAll(PROPAGATION_FACES.get(facing));
        }
        for(EnumFacing facing : propagationFaces)
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
            IBlockState state = getWorld().getBlockState(getPos());
            getWorld().notifyBlockUpdate(getPos(), state, state, 3);
        }
    }

    public void bePropagatedTo(TileEntityGlassBase base, String newChannel, boolean activate)
    {
        boolean flag = false;
        if(active && activate && channel.equalsIgnoreCase(newChannel)) //same channel and both activated but this is further than the other from master.
        {
            if(distance > base.distance + 1)
            {
                distance = base.distance + 1;
                checkFacesToTurnOn(base);
                flag = true;
            }
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
                propagateTime = TileEntityGlassBase.PROPAGATE_TIME + 1;
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
            activeFaces.addAll(origin.activeFaces); //check origin location and remove that active face.
            if(activeFaces.size() > 1)
            {
                for(int i = activeFaces.size() - 1; i >= 0; i--)
                {
                    EnumFacing facing = activeFaces.get(i);
                    BlockPos facePos = getPos().offset(facing, -1);
                    TileEntity te = getWorld().getTileEntity(facePos);
                    if(te instanceof TileEntityGlassBase && ((TileEntityGlassBase)te).active && ((TileEntityGlassBase)te).channel.equalsIgnoreCase(channel) && ((TileEntityGlassBase)te).distance < distance)
                    {
                        activeFaces.remove(i);
                        continue;
                    }
                    facePos = getPos().offset(facing);
                    te = getWorld().getTileEntity(facePos);
                    if(te instanceof TileEntityGlassBase && ((TileEntityGlassBase)te).active && ((TileEntityGlassBase)te).channel.equalsIgnoreCase(channel) && ((TileEntityGlassBase)te).distance < distance)
                    {
                        activeFaces.remove(i);
                        continue;
                    }
                }
            }

            HashSet<EnumFacing> newFaces = new HashSet<>();
            for(EnumFacing facing : activeFaces)
            {
                BlockPos facePos = getPos().offset(facing);
                TileEntity te = getWorld().getTileEntity(facePos);
                if(te instanceof TileEntityGlassBase) //inner corner
                {
                    BlockPos originPos = origin.getPos().offset(facing);
                    EnumFacing newFace = EnumFacing.getFacingFromVector(originPos.getX() - facePos.getX(), originPos.getY() - facePos.getY(), originPos.getZ() - facePos.getZ());
                    newFaces.add(newFace);
                }
                else //outer corner
                {
                    facePos = getPos().offset(facing, -1);
                    te = getWorld().getTileEntity(facePos);
                    if(te instanceof TileEntityGlassBase)
                    {
                        if(origin.getPos().getY() != getPos().getY())
                        {
                            // maybe the origin is from below but we prefer horizontals
                            for(EnumFacing newFacing : PROPAGATION_FACES.get(facing))
                            {
                                if(newFacing.getAxis() != EnumFacing.Axis.Y)
                                {
                                    BlockPos newPos = getPos().offset(newFacing);
                                    TileEntity te1 = getWorld().getTileEntity(newPos);
                                    if(te1 instanceof TileEntityGlassBase && ((TileEntityGlassBase)te1).activeFaces.contains(facing))
                                    {
                                        origin = ((TileEntityGlassBase)te1);
                                    }
                                }
                            }
                        }
                        BlockPos originPos = origin.getPos().offset(facing, -1);
                        EnumFacing newFace = EnumFacing.getFacingFromVector(facePos.getX() - originPos.getX(), facePos.getY() - originPos.getY(), facePos.getZ() - originPos.getZ());
                        newFaces.add(newFace);
                    }
                }
            }
            activeFaces.addAll(newFaces);

            IBlockState state = getWorld().getBlockState(getPos());
            getWorld().notifyBlockUpdate(getPos(), state, state, 3);
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

    @SideOnly(Side.CLIENT)
    @Override
    public AxisAlignedBB getRenderBoundingBox()
    {
        return new AxisAlignedBB(getPos().add(-1, -1, -1), getPos().add(2, 2, 2));
    }
}
