package ryzlar.glass.client.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ryzlar.glass.client.gadget.GadgetsClient;

/**
 * Optical camo: someone wearing it is invisible, but while they move they shimmer. The game already draws an invisible
 * body faintly see-through for those it is not hidden from (team mates); a moving camo wearer is drawn that way for all.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin
{
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void glass$camoShimmer(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci)
    {
        if(state.isInvisible && GadgetsClient.shimmers(entity))
        {
            state.isInvisibleToPlayer = false;
        }
    }
}
