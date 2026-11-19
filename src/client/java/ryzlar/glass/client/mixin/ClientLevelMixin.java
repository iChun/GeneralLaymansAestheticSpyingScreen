package ryzlar.glass.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ryzlar.glass.client.holo.SimulatedSky;

/** Inside a holo cube's simulation, the world is lit as bright as it was where (and when) the room was scanned. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin
{
    @Inject(method = "getSkyDarken", at = @At("RETURN"), cancellable = true)
    private void glass$simulatedDaylight(float partialTick, CallbackInfoReturnable<Float> cir)
    {
        cir.setReturnValue(SimulatedSky.skyDarken(cir.getReturnValueF()));
    }
}
