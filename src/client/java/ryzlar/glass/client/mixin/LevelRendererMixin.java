package ryzlar.glass.client.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ryzlar.glass.client.render.SpyViewRenderer;

/** No glowing entity outlines inside a terminal's view (with any renderer; the rest is in VanillaSectionsMixin). */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin
{
    @Inject(method = "shouldShowEntityOutlines", at = @At("HEAD"), cancellable = true)
    private void glass$noOutlinesInSpyView(CallbackInfoReturnable<Boolean> cir)
    {
        if(SpyViewRenderer.INSTANCE.isRendering())
        {
            cir.setReturnValue(false);
        }
    }
}
