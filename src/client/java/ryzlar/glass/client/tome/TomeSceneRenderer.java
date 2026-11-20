package ryzlar.glass.client.tome;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Draws a tome scene in a screen. A 1.21.6+ screen cannot draw 3D itself: the scene is drawn into its own image
 * ("picture in picture", registered with Fabric's SpecialGuiElementRegistry), which the screen then shows.
 */
public class TomeSceneRenderer extends PictureInPictureRenderer<TomeSceneRenderer.State>
{
    /** One scene at one moment, seen from {@code yaw}/{@code pitch}, in a box of the screen; {@code scale}: GUI pixels per block. */
    public record State(String scene, float time, float yaw, float pitch, int x0, int y0, int x1, int y1, float scale,
                        @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements PictureInPictureRenderState
    {
        public State(String scene, float time, float yaw, float pitch, int x0, int y0, int x1, int y1, float scale, @Nullable ScreenRectangle scissorArea)
        {
            this(scene, time, yaw, pitch, x0, y0, x1, y1, scale, scissorArea, PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
        }
    }

    public TomeSceneRenderer(MultiBufferSource.BufferSource buffers)
    {
        super(buffers);
    }

    @Override
    public Class<State> getRenderStateClass()
    {
        return State.class;
    }

    /** The scene's middle is the image's middle. */
    @Override
    protected float getTranslateY(int height, int guiScale)
    {
        return height / 2F;
    }

    @Override
    protected void renderToTexture(State state, PoseStack pose)
    {
        TomeScenes.Scene scene = TomeScenes.get(state.scene());
        if(scene == null)
        {
            return;
        }
        Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
        //The image's y runs down: turn the world (y up) the right way round.
        pose.scale(1F, -1F, -1F);
        pose.mulPose(Axis.XP.rotationDegrees(state.pitch()));
        pose.mulPose(Axis.YP.rotationDegrees(state.yaw()));
        Vec3 centre = scene.centre();
        pose.translate(-centre.x, -centre.y, -centre.z);
        SceneDraw draw = new SceneDraw(pose, bufferSource);
        scene.draw(draw, state.time() % scene.length());
        draw.finish();
    }

    @Override
    protected String getTextureLabel()
    {
        return "glass tome scene";
    }
}
