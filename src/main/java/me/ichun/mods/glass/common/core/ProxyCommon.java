package me.ichun.mods.glass.common.core;

import me.ichun.mods.glass.common.GeneralLaymansAestheticSpyingScreen;
import me.ichun.mods.glass.common.packet.PacketSetChannel;
import me.ichun.mods.glass.common.packet.PacketSetProjector;
import me.ichun.mods.glass.common.packet.PacketWirelessOrder;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassBase;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassMaster;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassTerminal;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassWireless;
import me.ichun.mods.ichunutil.common.core.network.PacketChannel;
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
        GameRegistry.registerTileEntity(TileEntityGlassWireless.class, "GLASS_TEWireless");
        GameRegistry.registerTileEntity(TileEntityGlassTerminal.class, "GLASS_TETerminal");

        GeneralLaymansAestheticSpyingScreen.eventHandlerServer = new EventHandlerServer();
        MinecraftForge.EVENT_BUS.register(GeneralLaymansAestheticSpyingScreen.eventHandlerServer);

        GeneralLaymansAestheticSpyingScreen.channel = new PacketChannel("GLASS", PacketSetChannel.class, PacketSetProjector.class, PacketWirelessOrder.class);
    }
}
