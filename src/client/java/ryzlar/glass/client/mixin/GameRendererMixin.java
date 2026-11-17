package ryzlar.glass.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import ryzlar.glass.client.render.SpyViewRenderer;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin
{
    @Shadow
    @Final
    private Camera mainCamera;

    /**
     * Draw the terminal views right when vanilla has finished the world projection matrix (which by then includes view
     * bobbing, the hurt shake and the nausea warp), so the views are drawn with exactly the same projection as the
     * world they are shown in. Nothing of the main frame (projection, culling, fog) has been set up yet at this point.
     */
    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/PerspectiveProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
    private GpuBufferSlice glass$renderSpyViews(PerspectiveProjectionMatrixBuffer buffer, Matrix4f projection, Operation<GpuBufferSlice> original, DeltaTracker deltaTracker)
    {
        SpyViewRenderer.INSTANCE.renderViews((GameRenderer)(Object)this, mainCamera, deltaTracker, new Matrix4f(projection));
        return original.call(buffer, projection);
    }
}
