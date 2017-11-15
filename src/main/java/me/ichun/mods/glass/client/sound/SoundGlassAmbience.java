package me.ichun.mods.glass.client.sound;

import me.ichun.mods.glass.common.GeneralLaymansAestheticSpyingScreen;
import me.ichun.mods.glass.common.block.TerminalPlacement;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ITickableSound;
import net.minecraft.client.audio.PositionedSound;
import net.minecraft.entity.Entity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

public class SoundGlassAmbience extends PositionedSound implements ITickableSound
{
    public TerminalPlacement placement;

    public SoundGlassAmbience(SoundEvent soundIn, SoundCategory categoryIn, float volume, float pitch, TerminalPlacement placement)
    {
        super(soundIn, categoryIn);
        this.volume = volume;
        this.pitch = pitch;
        this.repeat = true;
        this.repeatDelay = 0;

        this.xPosF = (float)placement.master.getPos().getX();
        this.yPosF = (float)placement.master.getPos().getY();
        this.zPosF = (float)placement.master.getPos().getZ();

        this.placement = placement;
    }

    @Override
    public boolean isDonePlaying()
    {
        return !GeneralLaymansAestheticSpyingScreen.eventHandlerClient.terminalPlacements.containsValue(placement);
    }

    @Override
    public void update()
    {
        Entity ent = Minecraft.getMinecraft().getRenderViewEntity();
        if(ent != null)
        {
            BlockPos closest = BlockPos.ORIGIN;
            double dist = 1000000D;
            for(TileEntityGlassBase base : placement.activeBlocks)
            {
                double d0 = ent.posX - (base.getPos().getX() + 0.5D);
                double d1 = ent.posY + ent.getEyeHeight() - (base.getPos().getY() + 0.5D);
                double d2 = ent.posZ - (base.getPos().getZ() + 0.5D);
                double dista = (double)MathHelper.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
                if(dista < dist)
                {
                    dist = dista;
                    closest = base.getPos();
                }
            }
            if(dist != 1000000D)
            {
                this.xPosF = (float)closest.getX() + 0.5F;
                this.yPosF = (float)closest.getY() + 0.5F;
                this.zPosF = (float)closest.getZ() + 0.5F;
            }
        }
    }
}
