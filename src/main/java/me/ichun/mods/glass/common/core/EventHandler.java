package me.ichun.mods.glass.common.core;

import me.ichun.mods.glass.common.GeneralLaymansAestheticSpyingScreen;
import me.ichun.mods.glass.common.block.BlockGlass;
import me.ichun.mods.glass.common.item.ItemGlass;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class EventHandler
{
    @SubscribeEvent
    public void onRegisterBlock(RegistryEvent.Register<Block> event)
    {
        GeneralLaymansAestheticSpyingScreen.blockGlass = (new BlockGlass(Material.GLASS, false)).setRegistryName(GeneralLaymansAestheticSpyingScreen.MOD_ID, "block_glass").setUnlocalizedName("glass.block.glass").setHardness(0.5F);

        event.getRegistry().register(GeneralLaymansAestheticSpyingScreen.blockGlass);
    }

    @SubscribeEvent
    public void onRegisterItem(RegistryEvent.Register<Item> event)
    {
        event.getRegistry().register(new ItemGlass(GeneralLaymansAestheticSpyingScreen.blockGlass).setRegistryName(GeneralLaymansAestheticSpyingScreen.blockGlass.getRegistryName()));
    }


    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onModelRegistry(ModelRegistryEvent event)
    {
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(GeneralLaymansAestheticSpyingScreen.blockGlass), 0, new ModelResourceLocation("generallaymansaestheticspyingscreen:block_glass", "inventory"));
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(GeneralLaymansAestheticSpyingScreen.blockGlass), 1, new ModelResourceLocation("generallaymansaestheticspyingscreen:block_glass", "inventory"));
    }
}
