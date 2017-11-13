package me.ichun.mods.glass.common;

import me.ichun.mods.glass.common.core.EventHandler;
import me.ichun.mods.glass.common.core.ProxyCommon;
import me.ichun.mods.ichunutil.common.core.Logger;
import me.ichun.mods.ichunutil.common.iChunUtil;
import me.ichun.mods.ichunutil.common.module.update.UpdateChecker;
import net.minecraft.block.Block;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = GeneralLaymansAestheticSpyingScreen.MOD_ID, name = GeneralLaymansAestheticSpyingScreen.MOD_NAME,
        version = GeneralLaymansAestheticSpyingScreen.VERSION,
        guiFactory = iChunUtil.GUI_CONFIG_FACTORY,
        dependencies = "required-after:ichunutil@[" + iChunUtil.VERSION_MAJOR + ".1.0," + (iChunUtil.VERSION_MAJOR + 1) + ".0.0)",
        acceptableRemoteVersions = "[" + iChunUtil.VERSION_MAJOR + ".0.0," + iChunUtil.VERSION_MAJOR + ".1.0)",
        acceptedMinecraftVersions = iChunUtil.MC_VERSION_RANGE
)
public class GeneralLaymansAestheticSpyingScreen
{
    public static final String MOD_NAME = "GeneralLaymansAestheticSpyingScreen";
    public static final String MOD_ID = "generallaymansaestheticspyingscreen";
    public static final String VERSION = iChunUtil.VERSION_MAJOR + ".0.0";

    @Mod.Instance
    public static GeneralLaymansAestheticSpyingScreen instance;

    @SidedProxy(clientSide = "me.ichun.mods.glass.client.core.ProxyClient", serverSide = "me.ichun.mods.glass.common.core.ProxyCommon")
    public static ProxyCommon proxy;

    public static final Logger LOGGER = Logger.createLogger(MOD_NAME);

    public static EventHandler eventHandler;

    public static Block blockGlass;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        proxy.preInit();

        UpdateChecker.registerMod(new UpdateChecker.ModVersionInfo(MOD_NAME, iChunUtil.VERSION_OF_MC, VERSION, false));
    }
}
