package ryzlar.glass.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelTimeAccess;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ryzlar.glass.client.holo.SimulatedSky;
import ryzlar.glass.client.render.CloakFill;

/**
 * Inside a holo cube's simulation, the world is lit as bright as it was where (and when) the room was scanned, and the
 * sky shows that time of day: the sun or moon where they were, the sky's colour, the stars. The clouds go on moving.
 * <p>
 * The time of day is a default method of {@link LevelTimeAccess} that the client's world does not override; this mixin
 * implements that interface so its override is mapped like the game's own method.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin implements LevelTimeAccess
{
    @Inject(method = "getSkyDarken", at = @At("RETURN"), cancellable = true)
    private void glass$simulatedDaylight(float partialTick, CallbackInfoReturnable<Float> cir)
    {
        cir.setReturnValue(SimulatedSky.skyDarken(cir.getReturnValueF()));
    }

    /** A cloak's ground is built from the blocks around it: when one changes, it is built again. */
    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void glass$blockChanged(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci)
    {
        CloakFill.blockChanged(pos);
    }

    @Override
    public float getTimeOfDay(float partialTick)
    {
        return SimulatedSky.timeOfDay(dimensionType().timeOfDay(dayTime()));
    }
}
