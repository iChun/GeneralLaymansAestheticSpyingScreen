package ryzlar.glass.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import ryzlar.glass.blockentity.GlassTerminalBlockEntity;

/**
 * Draws the terminal (an obsidian block with a small tilted glass cube showing which way it faces).
 * It is skipped while rendering a terminal's own view, so it does not block the camera. Port of TileEntityGlassTerminalRenderer.
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
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        dispatcher.renderSingleBlock(Blocks.OBSIDIAN.defaultBlockState(), poseStack, buffers, light, overlay);

        poseStack.pushPose();
        float scale = 0.25F;
        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.mulPose(new Quaternionf().rotationAxis((float)Math.toRadians(45F), te.facing.getStepX(), te.facing.getStepY(), te.facing.getStepZ()));
        poseStack.translate(-0.5D, -0.5D, -0.5D);

        poseStack.translate(0.375D, 0.375D, 0.375D);
        poseStack.translate(te.facing.getStepX() * -0.4D, te.facing.getStepY() * -0.4D, te.facing.getStepZ() * -0.4D);
        poseStack.scale(scale, scale, scale);
        dispatcher.renderSingleBlock(Blocks.GLASS.defaultBlockState(), poseStack, buffers, light, overlay);
        poseStack.popPose();
    }
}
