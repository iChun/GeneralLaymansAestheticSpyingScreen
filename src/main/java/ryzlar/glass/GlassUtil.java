package ryzlar.glass;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

public final class GlassUtil
{
    private GlassUtil() {}

    /** Port of 1.12's EnumFacing.getDirectionFromEntityLiving: the side of the block that faces the placer. */
    public static Direction directionFromEntity(BlockPos pos, LivingEntity placer)
    {
        if(Math.abs(placer.getX() - (pos.getX() + 0.5D)) < 2.0D && Math.abs(placer.getZ() - (pos.getZ() + 0.5D)) < 2.0D)
        {
            double eyeY = placer.getEyeY();
            if(eyeY - pos.getY() > 2.0D)
            {
                return Direction.UP;
            }
            if(pos.getY() - eyeY > 0.0D)
            {
                return Direction.DOWN;
            }
        }
        return placer.getDirection().getOpposite();
    }

    public static float interpolateRotation(float prev, float current, float partialTick)
    {
        return prev + Mth.wrapDegrees(current - prev) * partialTick;
    }
}
