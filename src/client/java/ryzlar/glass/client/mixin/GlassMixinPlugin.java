package ryzlar.glass.client.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import ryzlar.glass.client.RendererMods;

import java.util.List;
import java.util.Set;

/**
 * Leaves out the mixins of the vanilla view backend when another mod (Sodium and the like) replaces vanilla's chunk
 * rendering: they hook into methods that mod overwrites, and the game would not start (see the client's ViewBackend).
 */
public class GlassMixinPlugin implements IMixinConfigPlugin
{
    private static final String PACKAGE = "ryzlar.glass.client.mixin.";
    /** Mixins only for vanilla's chunk rendering. */
    private static final Set<String> VANILLA_ONLY = Set.of("VanillaSectionsMixin", "LevelRendererAccessor", "SectionOcclusionGraphAccessor");

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
        if(mixinClassName.startsWith(PACKAGE) && VANILLA_ONLY.contains(mixinClassName.substring(PACKAGE.length())))
        {
            return RendererMods.replacing() == null;
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
