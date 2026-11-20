package ryzlar.glass.client.render;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Which world renderer the screens and cloaks draw their views with, picked once at startup from the mods installed.
 * <p>
 * A view draws the world a second time from another camera, which leans on the renderer's own chunk visibility.
 * Vanilla's is used directly ({@link #VANILLA}). Sodium (and Iris, which needs Sodium) replaces that part with its own
 * renderer, which views cannot use yet: then the views are not drawn ({@link #UNSUPPORTED}), screens show their white
 * glow and cloaks plain glass, and everything else (channels, holograms, the holo cube) works as usual. A Sodium
 * backend can be added here later.
 */
public enum ViewBackend
{
    VANILLA(true, null),
    UNSUPPORTED(false, "glass.msg.viewsUnsupported");

    private static final ViewBackend CURRENT = pick();

    private final boolean drawsViews;
    @Nullable
    private final String messageKey;

    ViewBackend(boolean drawsViews, @Nullable String messageKey)
    {
        this.drawsViews = drawsViews;
        this.messageKey = messageKey;
    }

    public static ViewBackend current()
    {
        return CURRENT;
    }

    private static ViewBackend pick()
    {
        return ryzlar.glass.client.RendererMods.replacing() == null ? VANILLA : UNSUPPORTED;
    }

    /** True if screens and cloaks can draw their views. */
    public boolean drawsViews()
    {
        return drawsViews;
    }

    /** What to tell the player once when they join, or null. */
    @Nullable
    public Component message()
    {
        if(messageKey == null)
        {
            return null;
        }
        String mod = ryzlar.glass.client.RendererMods.replacing();
        String name = mod == null ? "?" : FabricLoader.getInstance().getModContainer(mod).map(c -> c.getMetadata().getName()).orElse(mod);
        return Component.translatable(messageKey, name);
    }
}
