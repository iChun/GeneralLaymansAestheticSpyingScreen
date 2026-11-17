package ryzlar.glass.client.mixin;

import net.minecraft.client.renderer.SectionOcclusionGraph;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.Future;

@Mixin(SectionOcclusionGraph.class)
public interface SectionOcclusionGraphAccessor
{
    @Nullable
    @Accessor("fullUpdateTask")
    Future<?> glass$getFullUpdateTask();
}
