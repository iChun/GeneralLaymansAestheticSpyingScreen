package ryzlar.glass.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ryzlar.glass.client.render.SpyViewRenderer;

/** While a terminal view renders, everything that draws to "the screen" draws into that view's image instead. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin
{
    @Inject(method = "getMainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void glass$spyViewTarget(CallbackInfoReturnable<RenderTarget> cir)
    {
        RenderTarget target = SpyViewRenderer.INSTANCE.targetOverride();
        if(target != null)
        {
            cir.setReturnValue(target);
        }
    }
}
