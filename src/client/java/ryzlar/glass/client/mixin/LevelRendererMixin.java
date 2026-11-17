package ryzlar.glass.client.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ryzlar.glass.client.render.SpyViewRenderer;

/**
 * Keeps a terminal view's render pass from disturbing the main one: the loaded sections stay centred on the player,
 * compiled geometry stays sorted for the player, and section updates reach every view's visibility graph.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin
{
    @Shadow
    @Final
    private SectionOcclusionGraph sectionOcclusionGraph;

    @WrapWithCondition(method = "setupRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ViewArea;repositionCamera(Lnet/minecraft/core/SectionPos;)V"))
    private boolean glass$keepViewAreaOnPlayer(ViewArea area, SectionPos pos)
    {
        return !SpyViewRenderer.INSTANCE.isRendering();
    }

    @ModifyArg(method = "setupRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;setCameraPosition(Lnet/minecraft/world/phys/Vec3;)V"))
    private Vec3 glass$sortForPlayer(Vec3 pos)
    {
        Vec3 main = SpyViewRenderer.INSTANCE.mainCameraPosition();
        return main != null ? main : pos;
    }

    @Inject(method = "scheduleTranslucentSectionResort", at = @At("HEAD"), cancellable = true)
    private void glass$noResortForSpyView(Vec3 pos, CallbackInfo ci)
    {
        if(SpyViewRenderer.INSTANCE.isRendering())
        {
            ci.cancel();
        }
    }

    @Inject(method = "shouldShowEntityOutlines", at = @At("HEAD"), cancellable = true)
    private void glass$noOutlinesInSpyView(CallbackInfoReturnable<Boolean> cir)
    {
        if(SpyViewRenderer.INSTANCE.isRendering())
        {
            cir.setReturnValue(false);
        }
    }

    @WrapOperation(method = { "addRecentlyCompiledSection", "onSectionBecomingNonEmpty" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SectionOcclusionGraph;schedulePropagationFrom(Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;)V"))
    private void glass$shareSectionUpdate(SectionOcclusionGraph graph, SectionRenderDispatcher.RenderSection section, Operation<Void> original)
    {
        original.call(graph, section);
        SpyViewRenderer.INSTANCE.forEachOtherGraph(graph, other -> other.schedulePropagationFrom(section));
    }

    @Inject(method = "onChunkReadyToRender", at = @At("TAIL"))
    private void glass$shareReadyChunk(ChunkPos chunkPos, CallbackInfo ci)
    {
        SpyViewRenderer.INSTANCE.forEachOtherGraph(sectionOcclusionGraph, graph -> graph.onChunkReadyToRender(chunkPos));
    }

    @Inject(method = "needsUpdate", at = @At("TAIL"))
    private void glass$shareNeedsUpdate(CallbackInfo ci)
    {
        SpyViewRenderer.INSTANCE.forEachOtherGraph(sectionOcclusionGraph, SectionOcclusionGraph::invalidate);
    }
}
