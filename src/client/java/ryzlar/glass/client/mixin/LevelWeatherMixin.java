package ryzlar.glass.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ryzlar.glass.client.holo.SimulatedSky;

/** Inside a holo cube's simulation, the client's world rains (and thunders) as it did where the room was scanned. */
@Mixin(Level.class)
public abstract class LevelWeatherMixin
{
    @Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
    private void glass$simulatedRain(float partialTick, CallbackInfoReturnable<Float> cir)
    {
        if((Object)this instanceof ClientLevel)
        {
            cir.setReturnValue(SimulatedSky.rain(cir.getReturnValueF()));
        }
    }

    @Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
    private void glass$simulatedThunder(float partialTick, CallbackInfoReturnable<Float> cir)
    {
        if((Object)this instanceof ClientLevel)
        {
            cir.setReturnValue(SimulatedSky.thunder(cir.getReturnValueF()));
        }
    }
}
