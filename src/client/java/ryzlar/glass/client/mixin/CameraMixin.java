package ryzlar.glass.client.mixin;

import net.minecraft.client.Camera;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import ryzlar.glass.client.render.GlassCamera;

/** Lets a terminal camera have any rotation, including roll. */
@Mixin(Camera.class)
public abstract class CameraMixin implements GlassCamera
{
    @Shadow
    @Final
    private Quaternionf rotation;

    @Shadow
    @Final
    private Vector3f forwards;

    @Shadow
    @Final
    private Vector3f up;

    @Shadow
    @Final
    private Vector3f left;

    @Unique
    @Override
    public void glass$setFullRotation(Quaternionf newRotation)
    {
        rotation.set(newRotation);
        newRotation.transform(0F, 0F, -1F, forwards);
        newRotation.transform(0F, 1F, 0F, up);
        newRotation.transform(-1F, 0F, 0F, left);
    }
}
