package me.ichun.mods.glass.common.block;

import me.ichun.mods.glass.client.render.TileEntityGlassRenderer;
import me.ichun.mods.glass.client.sound.SoundGlassAmbience;
import me.ichun.mods.glass.common.GeneralLaymansAestheticSpyingScreen;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassBase;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassMaster;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassTerminal;
import me.ichun.mods.ichunutil.common.module.worldportals.common.portal.WorldPortal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashSet;

public class TerminalPlacement extends WorldPortal
{
    public TileEntityGlassMaster master;
    public TileEntityGlassTerminal terminal;
    public HashSet<TileEntityGlassBase> activeBlocks;

    public TileEntityGlassBase renderCaller;

    public boolean playedAmbience;

    public TerminalPlacement(World world)
    {
        super(world);
        master = null;
        terminal = null;
        activeBlocks = null;
        //This should never NEVER NEVER be called except when creating a pair.
    }

    public TerminalPlacement(World world, TileEntityGlassMaster master, TileEntityGlassTerminal terminal, HashSet<TileEntityGlassBase> activeBlocks)
    {
        super(world, new Vec3d(master.getPos()).addVector(0.5D, 0.5D, 0.5D), master.placingFace, EnumFacing.UP, 0F, 0F);
        this.master = master;
        this.terminal = terminal;
        this.activeBlocks = activeBlocks;

        TerminalPlacement pair = new TerminalPlacement(world);
        pair.setPosition(new Vec3d(terminal.getPos().offset(terminal.facing, -1)).addVector(0.5D, 0.5D, 0.5D));
        pair.setFace(terminal.facing.getOpposite(), EnumFacing.UP);
        setPair(pair);
        pair.setPair(this);

        generateActiveFaces();
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
        return "GLASS";
    }

    @Override
    public void drawPlane(float partialTick)
    {
        if(!playedAmbience)
        {
            playedAmbience = true;
            Minecraft.getMinecraft().getSoundHandler().playSound(new SoundGlassAmbience(GeneralLaymansAestheticSpyingScreen.soundAmb, SoundCategory.BLOCKS, 0.015F, 0.6F, this));
        }

        for(TileEntityGlassBase base : activeBlocks)
        {
            if(base.active && base.lastDraw > 0)
            {
                GlStateManager.pushMatrix();
                GlStateManager.translate(base.getPos().getX() - renderCaller.getPos().getX(), base.getPos().getY() - renderCaller.getPos().getY(), base.getPos().getZ() - renderCaller.getPos().getZ());

                TileEntityGlassRenderer.drawPlanes(base, 1F, 1F, 1F, 1F, 0.5015D);

                GlStateManager.popMatrix();
            }
        }
    }

    public void generateActiveFaces()
    {
        WorldPortal pair = getPair();

        ArrayList<BlockPos> poses = pair.getPoses();
        ArrayList<EnumFacing> faces = pair.getFacesOn();
        for(TileEntityGlassBase base : activeBlocks)
        {
            if(base.active)
            {
                BlockPos differencePos = base.getPos().subtract(master.getPos());
                BlockPos referencePos = terminal.getPos().add(differencePos);
                for(EnumFacing activeFace : base.activeFaces)
                {
                    if(GeneralLaymansAestheticSpyingScreen.blockGlass.shouldSideBeRendered(base.getWorld().getBlockState(base.getPos()), base.getWorld(), base.getPos(), activeFace))
                    {
                        EnumFacing actualFace = activeFace.getOpposite();
                        BlockPos appliedPos = referencePos.offset(actualFace).add(differencePos);

                        boolean found = false;
                        for(int i = 0; i < faces.size(); i++)
                        {
                            EnumFacing face = faces.get(i);
                            BlockPos pos = poses.get(i);
                            if(face.equals(actualFace) && (face.getAxis() == EnumFacing.Axis.X && pos.getX() == appliedPos.getX() || face.getAxis() == EnumFacing.Axis.Y && pos.getY() == appliedPos.getY() || face.getAxis() == EnumFacing.Axis.Z && pos.getZ() == appliedPos.getZ()))
                            {
                                found = true;
                                break;
                            }
                        }
                        if(!found)
                        {
                            pair.addFace(actualFace, EnumFacing.UP, new Vec3d(appliedPos).addVector(0.5D, 0.5D, 0.5D));
                        }
                    }
                }
            }
        }
    }

    public void addActiveGlass(TileEntityGlassBase base)
    {
        generateActiveFaces();
    }

    public void removeActiveGlass(TileEntityGlassBase base)
    {
        generateActiveFaces();
    }

    @Override
    public boolean canTeleportEntities()
    {
        return false;
    }

    @Override
    public <T extends WorldPortal> T createFakeInstance(NBTTagCompound tag)
    {
        return null;
    }
}
