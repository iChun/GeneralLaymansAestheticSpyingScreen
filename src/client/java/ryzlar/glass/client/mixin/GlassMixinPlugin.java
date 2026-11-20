package ryzlar.glass.client.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import ryzlar.glass.client.RendererMods;

import java.util.List;
import java.util.Set;

/**
 * Picks the mixins of the view backend in use (see the client's ViewBackend): the vanilla ones are left out when another
 * mod (Sodium and the like) replaces vanilla's chunk rendering, as they hook into methods that mod overwrites and the
 * game would not start; the Sodium ones only go in with a Sodium version they were made for.
 */
public class GlassMixinPlugin implements IMixinConfigPlugin
{
    private static final String PACKAGE = "ryzlar.glass.client.mixin.";
    /** Mixins only for vanilla's chunk rendering. */
    private static final Set<String> VANILLA_ONLY = Set.of("VanillaSectionsMixin", "LevelRendererAccessor", "SectionOcclusionGraphAccessor");
    /** Mixins into Sodium itself (the Sodium view backend), only with a Sodium version they were made for. */
    private static final Set<String> SODIUM_ONLY = Set.of("SodiumWorldRendererMixin");

    @Override
    public void onLoad(String mixinPackage)
    {
    }

    @Override
    public String getRefMapperConfig()
    {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName)
    {
        String name = mixinClassName.startsWith(PACKAGE) ? mixinClassName.substring(PACKAGE.length()) : mixinClassName;
        if(VANILLA_ONLY.contains(name))
        {
            return RendererMods.replacing() == null;
        }
        if(SODIUM_ONLY.contains(name))
        {
            return RendererMods.sodiumBackend();
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets)
    {
    }

    @Override
    public List<String> getMixins()
    {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo)
    {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo)
    {
    }
}
