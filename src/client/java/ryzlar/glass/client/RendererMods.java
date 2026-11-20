package ryzlar.glass.client;

import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

/**
 * Which installed mod (if any) replaces vanilla's chunk rendering. No Minecraft classes here: the mixin plugin asks this
 * before Minecraft is loaded.
 */
public final class RendererMods
{
    /** Mods whose renderer replaces vanilla's chunk rendering (Iris first: it needs Sodium, and is what the player put in; the others are Sodium forks). */
    private static final String[] REPLACING = { "iris", "sodium", "embeddium", "nvidium" };

    private RendererMods() {}

    /** The installed mod that replaces vanilla's chunk rendering, or null. */
    @Nullable
    public static String replacing()
    {
        for(String mod : REPLACING)
        {
            if(FabricLoader.getInstance().isModLoaded(mod))
            {
                return mod;
            }
        }
        return null;
    }
}
