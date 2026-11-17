package ryzlar.glass.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import ryzlar.glass.blockentity.GlassBaseBlockEntity;
import ryzlar.glass.client.GlassClientState;
import ryzlar.glass.client.TerminalPlacement;

/**
 * A quiet hum that follows the GLASS block closest to the player. Port of SoundGlassAmbience.
 */
public class GlassAmbienceSound extends AbstractTickableSoundInstance
{
    private final TerminalPlacement placement;

    public GlassAmbienceSound(SoundEvent sound, SoundSource source, float volume, float pitch, TerminalPlacement placement)
    {
        super(sound, source, SoundInstance.createUnseededRandom());
        this.volume = volume;
        this.pitch = pitch;
        this.looping = true;
        this.delay = 0;
        this.placement = placement;

        BlockPos pos = placement.master.getBlockPos();
        this.x = pos.getX() + 0.5D;
        this.y = pos.getY() + 0.5D;
        this.z = pos.getZ() + 0.5D;
    }

    @Override
    public void tick()
    {
        if(GlassClientState.INSTANCE.terminalPlacements.get(placement.channel) != placement)
        {
            stop();
            return;
        }
        Entity ent = Minecraft.getInstance().getCameraEntity();
        if(ent != null)
        {
            Vec3 eye = ent.getEyePosition();
            BlockPos closest = null;
            double dist = Double.MAX_VALUE;
            for(GlassBaseBlockEntity base : placement.activeBlocks())
            {
                double d = eye.distanceToSqr(Vec3.atCenterOf(base.getBlockPos()));
                if(d < dist)
                {
                    dist = d;
                    closest = base.getBlockPos();
                }
            }
            if(closest != null)
            {
                this.x = closest.getX() + 0.5D;
                this.y = closest.getY() + 0.5D;
                this.z = closest.getZ() + 0.5D;
            }
        }
    }
}
