package ryzlar.glass.client.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.trigger.CameraMovement;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.client.Camera;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ryzlar.glass.client.render.SpyViewRenderer;

/**
 * The Sodium view backend: a screen's or cloak's view is drawn by Sodium's own renderer, from the view's camera. Sodium
 * works out what is visible right then (on this thread), so the view only has to keep Sodium from taking it for the
 * player having moved: while a view draws, no translucent re-sorting is set off and chunk builds stay ordered round the
 * player (and uploading them is left to the player's frame); afterwards Sodium's idea of the camera is put back, and
 * what is visible is worked out again for the player. Only applied with Sodium (see GlassMixinPlugin).
 */
@Mixin(value = SodiumWorldRenderer.class, remap = false)
public abstract class SodiumWorldRendererMixin
{
    @Shadow
    private Vector3d lastCameraPos;
    @Shadow
    private double lastCameraPitch;
    @Shadow
    private double lastCameraYaw;
    @Shadow
    private Matrix4f lastProjectionMatrix;
    @Shadow
    private FogParameters lastFogParameters;
    @Shadow
    private RenderSectionManager renderSectionManager;

    /** Sodium's camera as the player's frame left it, while a view draws. */
    @Unique
    private Vector3d glass$playerCameraPos;
    @Unique
    private double glass$playerPitch, glass$playerYaw;
    @Unique
    private Matrix4f glass$playerProjection;
    @Unique
    private FogParameters glass$playerFog;

    @Inject(method = "setupTerrain", at = @At("HEAD"))
    private void glass$viewStart(Camera camera, Viewport viewport, FogParameters fog, boolean spectator, boolean updateImmediately,
                                 ChunkRenderMatrices matrices, CallbackInfo ci)
    {
        if(!SpyViewRenderer.INSTANCE.isRendering())
        {
            return;
        }
        glass$playerCameraPos = lastCameraPos == null ? null : new Vector3d(lastCameraPos);
        glass$playerPitch = lastCameraPitch;
        glass$playerYaw = lastCameraYaw;
        glass$playerProjection = lastProjectionMatrix == null ? null : new Matrix4f(lastProjectionMatrix);
        glass$playerFog = lastFogParameters;
        if(renderSectionManager != null)
        {
            renderSectionManager.markGraphDirty(); //always work out what this view sees
        }
    }

    @Inject(method = "setupTerrain", at = @At("RETURN"))
    private void glass$viewEnd(Camera camera, Viewport viewport, FogParameters fog, boolean spectator, boolean updateImmediately,
                               ChunkRenderMatrices matrices, CallbackInfo ci)
    {
        if(!SpyViewRenderer.INSTANCE.isRendering())
        {
            return;
        }
        lastCameraPos = glass$playerCameraPos;
        lastCameraPitch = glass$playerPitch;
        lastCameraYaw = glass$playerYaw;
        lastProjectionMatrix = glass$playerProjection;
        lastFogParameters = glass$playerFog;
        if(renderSectionManager != null)
        {
            renderSectionManager.markGraphDirty(); //the player's frame works out what the player sees again
        }
    }

    @WrapWithCondition(method = "setupTerrain", at = @At(value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;processGFNIMovement(Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/trigger/CameraMovement;)V"))
    private boolean glass$noSortForView(RenderSectionManager manager, CameraMovement movement)
    {
        return !SpyViewRenderer.INSTANCE.isRendering();
    }

    @WrapWithCondition(method = "setupTerrain", at = @At(value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;prepareFrame(Lorg/joml/Vector3dc;)V"))
    private boolean glass$buildsForPlayer(RenderSectionManager manager, Vector3dc position)
    {
        return !SpyViewRenderer.INSTANCE.isRendering();
    }

    @WrapWithCondition(method = "setupTerrain", at = @At(value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;updateChunks(Z)V"))
    private boolean glass$noChunkUpdatesForView(RenderSectionManager manager, boolean updateImmediately)
    {
        return !SpyViewRenderer.INSTANCE.isRendering();
    }

    @WrapWithCondition(method = "setupTerrain", at = @At(value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;uploadChunks()V"))
    private boolean glass$noUploadsForView(RenderSectionManager manager)
    {
        return !SpyViewRenderer.INSTANCE.isRendering();
    }
}
