package ryzlar.glass.client.mixin;

import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ryzlar.glass.client.render.SpyViewRenderer;

/** Clouds are as far away as the sky: in a terminal or cloak view, draw them without the clip plane too (see SkyRendererMixin). */
@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin
{
    @Inject(method = "render", at = @At("HEAD"))
    private void glass$unclippedClouds(CallbackInfo ci)
    {
        SpyViewRenderer.INSTANCE.useSkyProjection(true);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void glass$clippedAgain(CallbackInfo ci)
    {
        SpyViewRenderer.INSTANCE.useSkyProjection(false);
    }
}
