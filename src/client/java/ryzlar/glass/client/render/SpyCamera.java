package ryzlar.glass.client.render;

import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A camera placed at a terminal. It can have any rotation, including roll (a vanilla camera only has yaw and pitch),
 * which happens when a wall screen shows a terminal that faces up or down.
 */
public class SpyCamera extends Camera
{
    public void configure(BlockGetter level, Entity entity, Vec3 position, Quaternionf rotation, float partialTick)
    {
        //Detached, so the player is drawn in the terminal's view.
        setup(level, entity, true, false, partialTick);
        setPosition(position);

        Vector3f forwards = rotation.transform(new Vector3f(0F, 0F, -1F));
        float yaw = (float)(Mth.atan2(-forwards.x, forwards.z) * Mth.RAD_TO_DEG);
        float pitch = (float)(Math.asin(Mth.clamp(-forwards.y, -1F, 1F)) * Mth.RAD_TO_DEG);
        setRotation(yaw, pitch);

        ((GlassCamera)this).glass$setFullRotation(rotation);
    }
}
