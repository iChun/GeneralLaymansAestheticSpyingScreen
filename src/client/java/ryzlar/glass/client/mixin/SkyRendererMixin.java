package ryzlar.glass.client.mixin;

import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ryzlar.glass.client.render.SpyViewRenderer;

/**
 * In a terminal or cloak view the near plane is tilted onto the clip plane, which cuts into the sky dome too (looking
 * steeply up over a cloak field you saw a band of plain fog colour). The sky is infinitely far away, so draw it with
 * the view's projection without the clip plane.
 */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin
{
    @Inject(method = { "renderSkyDisc", "renderDarkDisc", "renderSunMoonAndStars", "renderSunriseAndSunset", "renderEndSky" }, at = @At("HEAD"))
    private void glass$unclippedSky(CallbackInfo ci)
    {
        SpyViewRenderer.INSTANCE.useSkyProjection(true);
    }

    @Inject(method = { "renderSkyDisc", "renderDarkDisc", "renderSunMoonAndStars", "renderSunriseAndSunset", "renderEndSky" }, at = @At("RETURN"))
    private void glass$clippedAgain(CallbackInfo ci)
    {
        SpyViewRenderer.INSTANCE.useSkyProjection(false);
    }
}
