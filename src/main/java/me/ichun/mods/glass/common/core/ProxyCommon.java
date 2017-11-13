package me.ichun.mods.glass.common.core;

import me.ichun.mods.glass.common.GeneralLaymansAestheticSpyingScreen;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassBase;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassMaster;
import me.ichun.mods.ichunutil.common.module.worldportals.common.WorldPortals;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.registry.GameRegistry;

public class ProxyCommon
{
    public void preInit()
    {
        WorldPortals.init();

        GameRegistry.registerTileEntity(TileEntityGlassMaster.class, "GLASS_TEMaster");
        GameRegistry.registerTileEntity(TileEntityGlassBase.class, "GLASS_TEBase");

        GeneralLaymansAestheticSpyingScreen.eventHandler = new EventHandler();
        MinecraftForge.EVENT_BUS.register(GeneralLaymansAestheticSpyingScreen.eventHandler);
    }
}
