package me.ichun.mods.glass.client.core;

import me.ichun.mods.glass.common.GeneralLaymansAestheticSpyingScreen;
import me.ichun.mods.glass.common.block.TerminalPlacement;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassBase;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassMaster;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassTerminal;
import me.ichun.mods.ichunutil.client.model.item.ModelEmpty;
import me.ichun.mods.ichunutil.common.module.worldportals.common.WorldPortals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

public class EventHandlerClient
{
    public BlockPos clickedPos = BlockPos.ORIGIN;
    public HashMap<String, BlockPos> terminalLocations = new HashMap<>();
    public HashMap<String, HashSet<TileEntityGlassBase>> activeGLASS = new HashMap<>();
    public HashSet<String> drawnChannels = new HashSet<>();
    public HashMap<String, TerminalPlacement> terminalPlacements = new HashMap<>();
    public HashMap<String, Integer> terminalPlacementCreationTimeout = new HashMap<>();

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event)
    {
        if(event.phase == TickEvent.Phase.START)
        {
            drawnChannels.clear();
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event)
    {
        if(event.phase == TickEvent.Phase.END)
        {
            Minecraft mc = Minecraft.getMinecraft();
            if(mc.world != null)
            {
                if(!clickedPos.equals(BlockPos.ORIGIN))
                {
                    TileEntity te = mc.world.getTileEntity(clickedPos);
                    if(te instanceof TileEntityGlassBase && ((TileEntityGlassBase)te).active && mc.playerController.getIsHittingBlock())
                    {

                        TileEntityGlassBase base = (TileEntityGlassBase)te;
                        if(base.fadeoutTime < TileEntityGlassBase.FADEOUT_TIME - TileEntityGlassBase.PROPAGATE_TIME)
                        {
                            base.fadeoutTime = TileEntityGlassBase.FADEOUT_TIME;
                            base.fadePropagate = TileEntityGlassBase.PROPAGATE_TIME;
                            base.fadeDistance = 2;
                            base.fadePropagate();
                        }
                    }
                    else
                    {
                        clickedPos = BlockPos.ORIGIN;
                    }
                }
                Iterator<Map.Entry<String, Integer>> ite = terminalPlacementCreationTimeout.entrySet().iterator();
                while(ite.hasNext())
                {
                    Map.Entry<String, Integer> e = ite.next();
                    e.setValue(e.getValue() - 1);
                    if(e.getValue() < 0)
                    {
                        ite.remove();
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onModelRegistry(ModelRegistryEvent event)
    {
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(GeneralLaymansAestheticSpyingScreen.blockGlass), 0, new ModelResourceLocation("generallaymansaestheticspyingscreen:block_glass", "inventory"));
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(GeneralLaymansAestheticSpyingScreen.blockGlass), 1, new ModelResourceLocation("generallaymansaestheticspyingscreen:block_glass", "inventory"));
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(GeneralLaymansAestheticSpyingScreen.blockGlass), 2, new ModelResourceLocation("generallaymansaestheticspyingscreen:block_glass", "inventory"));

        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(GeneralLaymansAestheticSpyingScreen.blockGlassTerminal), 0, new ModelResourceLocation("generallaymansaestheticspyingscreen:block_glass_terminal", "inventory"));
    }

    @SubscribeEvent
    public void onModelBake(ModelBakeEvent event)
    {
        event.getModelRegistry().putObject(new ModelResourceLocation("generallaymansaestheticspyingscreen:block_glass_terminal", "normal"), ModelEmpty.INSTANCE);
    }

    @SubscribeEvent
    public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event)
    {
        Minecraft.getMinecraft().addScheduledTask(this::disconnectFromServer);
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event)
    {
        if(event.getWorld().isRemote)
        {
            Minecraft mc = Minecraft.getMinecraft();
            if(mc.world != null)
            {
                Iterator<Map.Entry<String, BlockPos>> ite = terminalLocations.entrySet().iterator();
                while(ite.hasNext())
                {
                    Map.Entry<String, BlockPos> e = ite.next();
                    TileEntity te = mc.world.getTileEntity(e.getValue());
                    if(!(te instanceof TileEntityGlassTerminal))
                    {
                        ite.remove();
                    }
                }

                Iterator<Map.Entry<String, HashSet<TileEntityGlassBase>>> ite1 = activeGLASS.entrySet().iterator();
                while(ite1.hasNext())
                {
                    Map.Entry<String, HashSet<TileEntityGlassBase>> e = ite1.next();
                    e.getValue().removeIf(base -> base.getWorld() != mc.world || !base.active);
                    if(e.getValue().isEmpty())
                    {
                        ite1.remove();
                    }
                }
            }
            else
            {
                terminalLocations.clear();
                activeGLASS.clear();
                terminalPlacements.forEach((k, v) -> WorldPortals.eventHandlerClient.renderGlobalProxy.releaseViewFrustum(v.getPair()));
                terminalPlacements.clear();
                terminalPlacementCreationTimeout.clear();
            }
        }
    }

    public TerminalPlacement getTerminalPlacement(String channel) //this is called in render, only from active bases.
    {
        if(!terminalPlacementCreationTimeout.containsKey(channel))
        {
            if(terminalPlacements.containsKey(channel))
            {
                return terminalPlacements.get(channel);
            }
            if(terminalLocations.containsKey(channel))
            {
                Minecraft mc = Minecraft.getMinecraft();
                TileEntity te = mc.world.getTileEntity(terminalLocations.get(channel));
                if(te instanceof TileEntityGlassTerminal)
                {
                    TileEntityGlassTerminal terminal = (TileEntityGlassTerminal)te;

                    //we have the terminal. now find the projector
                    TileEntityGlassMaster master = null;
                    HashSet<TileEntityGlassBase> activeGlasses = activeGLASS.get(channel);
                    for(TileEntityGlassBase base : activeGlasses)
                    {
                        if(base.active && base.distance == 1 && base.channel.equalsIgnoreCase(channel) && base instanceof TileEntityGlassMaster)
                        {
                            master = (TileEntityGlassMaster)base;
                            break;
                        }
                    }

                    if(master != null) //we have the projector. Now generate the terminal placement
                    {
                        TerminalPlacement placement = new TerminalPlacement(mc.world, master, terminal, getActiveGlass(channel));
                        terminalPlacements.put(channel, placement);
                        return placement;
                    }
                }
                else
                {
                    terminalLocations.remove(channel);
                }
            }

            terminalPlacementCreationTimeout.put(channel, 13); //13 tick wait before trying again.
        }
        return null;
    }

    public HashSet<TileEntityGlassBase> getActiveGlass(String channel)
    {
        if(!channel.isEmpty() && activeGLASS.containsKey(channel))
        {
            return activeGLASS.get(channel);
        }
        return new HashSet<>();
    }

    public void addActiveGlass(TileEntityGlassBase base, String channel)
    {
        HashSet<TileEntityGlassBase> bases = activeGLASS.computeIfAbsent(channel, v -> new HashSet<>());
        bases.add(base);

        if(terminalPlacements.containsKey(channel))
        {
            terminalPlacements.get(channel).addActiveGlass(base);
        }
    }

    public void removeActiveGlass(TileEntityGlassBase base, String channel)
    {
        HashSet<TileEntityGlassBase> bases = activeGLASS.get(channel);
        if(bases != null)
        {
            bases.remove(base);
            if(bases.isEmpty())
            {
                activeGLASS.remove(channel);
                if(terminalPlacements.containsKey(channel))
                {
                    WorldPortals.eventHandlerClient.renderGlobalProxy.releaseViewFrustum(terminalPlacements.get(channel).getPair());
                }
                terminalPlacements.remove(channel);
                terminalPlacementCreationTimeout.remove(channel);
            }
            else if(terminalPlacements.containsKey(channel))
            {
                terminalPlacements.get(channel).removeActiveGlass(base);
            }
        }
    }


    public void disconnectFromServer()
    {
        terminalLocations.clear();
        activeGLASS.clear();
        terminalPlacements.forEach((k, v) -> WorldPortals.eventHandlerClient.renderGlobalProxy.releaseViewFrustum(v.getPair()));
        terminalPlacements.clear();
        terminalPlacementCreationTimeout.clear();
    }
}
