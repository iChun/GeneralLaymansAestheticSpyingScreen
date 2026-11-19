package ryzlar.glass;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ryzlar.glass.network.GlassNetworking;

/**
 * GeneralLaymansAestheticSpyingScreen ("GLASS"), ported to Fabric.
 * Original mod by iChun: https://github.com/iChun/GeneralLaymansAestheticSpyingScreen (LGPL-3.0).
 */
public class Glass implements ModInitializer
{
    public static final String MOD_ID = "generallaymansaestheticspyingscreen";
    public static final String MOD_NAME = "GeneralLaymansAestheticSpyingScreen";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    public static ResourceLocation id(String path)
    {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize()
    {
        GlassConfig.load();
        GlassRegistry.init();
        GlassSounds.init();
        ryzlar.glass.gadget.Gadgets.init();
        GlassNetworking.init();
        ryzlar.glass.holo.HoloNetworking.init();
    }
}
