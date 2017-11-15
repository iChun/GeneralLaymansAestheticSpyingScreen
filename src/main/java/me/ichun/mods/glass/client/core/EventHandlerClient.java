package me.ichun.mods.glass.client.core;

import me.ichun.mods.glass.common.GeneralLaymansAestheticSpyingScreen;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassBase;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassTerminal;
import me.ichun.mods.ichunutil.client.model.item.ModelEmpty;
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
import java.util.Iterator;
import java.util.Map;

public class EventHandlerClient
{
    public BlockPos clickedPos = BlockPos.ORIGIN;
    public HashMap<String, BlockPos> terminalLocations = new HashMap<>();

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
            }
            else
            {
                terminalLocations.clear();
            }
        }
    }


    public void disconnectFromServer()
    {
        terminalLocations.clear();
    }
}
