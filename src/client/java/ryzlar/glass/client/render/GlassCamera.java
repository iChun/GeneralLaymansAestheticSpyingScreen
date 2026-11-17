package ryzlar.glass.client.render;

import org.joml.Quaternionf;

/** Added to {@link net.minecraft.client.Camera} by CameraMixin. */
public interface GlassCamera
{
    void glass$setFullRotation(Quaternionf rotation);
}
