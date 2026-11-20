package ryzlar.glass.client.render;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.client.RendererMods;

import java.lang.reflect.Method;

/**
 * Which world renderer the screens and cloaks draw their views with, picked once at startup from the mods installed.
 * <p>
 * A view draws the world a second time from another camera. {@link #VANILLA}: with vanilla's chunk rendering, borrowing
 * its section visibility per view (VanillaSectionsMixin). {@link #SODIUM}: with Sodium (and Iris without a shader pack),
 * through Sodium's own renderer (SodiumWorldRendererMixin). {@link #UNSUPPORTED}: a renderer views cannot use (another
 * Sodium version, a fork): views are not drawn, screens show their white glow and cloaks plain glass. With an Iris
 * shader pack on, views are not drawn either (the shader pipeline draws from the player's camera only).
 */
public enum ViewBackend
{
    VANILLA,
    SODIUM,
    UNSUPPORTED;

    private static final ViewBackend CURRENT = pick();
    @Nullable
    private static Method irisShaderPackInUse;
    @Nullable
    private static Object irisApi;
    private static boolean irisLookedUp;

    public static ViewBackend current()
    {
        return CURRENT;
    }

    private static ViewBackend pick()
    {
        if(RendererMods.replacing() == null)
        {
            return VANILLA;
        }
        return RendererMods.sodiumBackend() ? SODIUM : UNSUPPORTED;
    }

    /** True if screens and cloaks can draw their views now. */
    public boolean drawsViews()
    {
        return switch(this)
        {
            case VANILLA -> true;
            case SODIUM -> !irisShadersOn();
            case UNSUPPORTED -> false;
        };
    }

    /** True if Iris is installed and a shader pack is in use (asked through Iris's API, without depending on Iris). */
    public static boolean irisShadersOn()
    {
        if(!irisLookedUp)
        {
            irisLookedUp = true;
            if(FabricLoader.getInstance().isModLoaded("iris"))
            {
                try
                {
                    Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    irisApi = api.getMethod("getInstance").invoke(null);
                    irisShaderPackInUse = api.getMethod("isShaderPackInUse");
                }
                catch(ReflectiveOperationException | RuntimeException e)
                {
                    irisApi = null;
                    irisShaderPackInUse = null;
                }
            }
        }
        if(irisApi == null || irisShaderPackInUse == null)
        {
            return false;
        }
        try
        {
            return (Boolean)irisShaderPackInUse.invoke(irisApi);
        }
        catch(ReflectiveOperationException | RuntimeException e)
        {
            return false;
        }
    }

    /** What to tell the player once when they join, or null. */
    @Nullable
    public Component message()
    {
        if(this == UNSUPPORTED)
        {
            String mod = RendererMods.replacing();
            String name = mod == null ? "?" : FabricLoader.getInstance().getModContainer(mod)
                    .map(c -> c.getMetadata().getName() + " " + c.getMetadata().getVersion().getFriendlyString()).orElse(mod);
            return Component.translatable("glass.msg.viewsUnsupported", name);
        }
        if(this == SODIUM && irisShadersOn())
        {
            return Component.translatable("glass.msg.viewsShaders");
        }
        return null;
    }
}
