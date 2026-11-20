package ryzlar.glass.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.world.phys.Vec3;
import ryzlar.glass.blockentity.GlassTerminalBlockEntity;

/**
 * Draws the terminal: a camera (its Blockbench model, glass_terminal), its lens towards the way it films. Its chunk
 * model is invisible and this draws it instead, so it can be skipped while rendering a terminal's own view and does
 * not block the camera. Port of TileEntityGlassTerminalRenderer.
 */
public class GlassTerminalRenderer implements BlockEntityRenderer<GlassTerminalBlockEntity>
{
    public GlassTerminalRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(GlassTerminalBlockEntity te, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay, Vec3 cameraPos)
    {
        if(SpyViewRenderer.INSTANCE.isRendering())
        {
            return;
        }
        //The camera (its model looks north), turned the way the terminal films.
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        switch(te.facing)
        {
            case SOUTH -> poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180F));
            case WEST -> poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90F));
            case EAST -> poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-90F));
            case UP -> poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90F));
            case DOWN -> poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90F));
            default -> {}
        }
        poseStack.translate(-0.5D, -0.5D, -0.5D);
        net.minecraft.client.renderer.block.ModelBlockRenderer.renderModel(poseStack.last(), buffers.getBuffer(net.minecraft.client.renderer.RenderType.cutout()),
                dispatcher.getBlockModel(te.getBlockState()), 1F, 1F, 1F, light, overlay);
        poseStack.popPose();
    }
}
