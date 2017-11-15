package me.ichun.mods.glass.common.core;

import me.ichun.mods.glass.common.GeneralLaymansAestheticSpyingScreen;
import me.ichun.mods.glass.common.block.BlockGlass;
import me.ichun.mods.glass.common.block.BlockGlassTerminal;
import me.ichun.mods.glass.common.item.ItemGlass;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class EventHandlerServer
{
    @SubscribeEvent
    public void onRegisterBlock(RegistryEvent.Register<Block> event)
    {
        GeneralLaymansAestheticSpyingScreen.blockGlass = (new BlockGlass(Material.GLASS, false)).setRegistryName(GeneralLaymansAestheticSpyingScreen.MOD_ID, "block_glass").setUnlocalizedName("glass.block.glass").setHardness(0.8F);
        GeneralLaymansAestheticSpyingScreen.blockGlassTerminal = (new BlockGlassTerminal()).setRegistryName(GeneralLaymansAestheticSpyingScreen.MOD_ID, "block_glass_terminal").setUnlocalizedName("glass.block.glass_terminal").setHardness(50.0F).setResistance(2000.0F);

        event.getRegistry().register(GeneralLaymansAestheticSpyingScreen.blockGlass);
        event.getRegistry().register(GeneralLaymansAestheticSpyingScreen.blockGlassTerminal);
    }

    @SubscribeEvent
    public void onRegisterItem(RegistryEvent.Register<Item> event)
    {
        event.getRegistry().register(new ItemGlass(GeneralLaymansAestheticSpyingScreen.blockGlass).setRegistryName(GeneralLaymansAestheticSpyingScreen.blockGlass.getRegistryName()));
        event.getRegistry().register(new ItemBlock(GeneralLaymansAestheticSpyingScreen.blockGlassTerminal).setRegistryName(GeneralLaymansAestheticSpyingScreen.blockGlassTerminal.getRegistryName()));
    }

    @SubscribeEvent
    public void onRegisterSound(RegistryEvent.Register<SoundEvent> event)
    {
        GeneralLaymansAestheticSpyingScreen.soundAmb = new SoundEvent(new ResourceLocation("generallaymansaestheticspyingscreen", "amb")).setRegistryName(new ResourceLocation("generallaymansaestheticspyingscreen", "amb"));

        event.getRegistry().register(GeneralLaymansAestheticSpyingScreen.soundAmb);
    }
}
