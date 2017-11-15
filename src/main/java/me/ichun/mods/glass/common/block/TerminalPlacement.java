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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

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

    }

    public void addActiveGlass(TileEntityGlassBase base)
    {

    }

    public void removeActiveGlass(TileEntityGlassBase base)
    {

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
