package ryzlar.glass.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EndCrystalModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import ryzlar.glass.GlassUtil;
import ryzlar.glass.blockentity.GlassBaseBlockEntity;
import ryzlar.glass.blockentity.GlassProjectorBlockEntity;
import ryzlar.glass.blockentity.GlassWirelessBlockEntity;
import ryzlar.glass.client.GlassClientState;
import ryzlar.glass.client.TerminalPlacement;

import java.util.List;

/**
 * Draws a GLASS block's screen faces, the spinning beacon in a projector and the crystal in a wireless extension.
 * Port of TileEntityGlassRenderer.
 */
public class GlassBlockEntityRenderer implements BlockEntityRenderer<GlassBaseBlockEntity>
{
    private static final ResourceLocation END_CRYSTAL_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/end_crystal/end_crystal.png");
    private static final RenderType END_CRYSTAL_RENDER_TYPE = RenderType.entityCutoutNoCull(END_CRYSTAL_TEXTURE);

    private final EndCrystalModel crystalModel;
    private final EndCrystalRenderState crystalState = new EndCrystalRenderState();

    public GlassBlockEntityRenderer(BlockEntityRendererProvider.Context context)
    {
        crystalModel = new EndCrystalModel(context.bakeLayer(ModelLayers.END_CRYSTAL));
        crystalState.showsBottom = false;
    }

    @Override
    public void render(GlassBaseBlockEntity te, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay, Vec3 cameraPos)
    {
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);

        boolean isMaster = te instanceof GlassProjectorBlockEntity;
        if(te instanceof GlassProjectorBlockEntity master)
        {
            renderBeacon(master, partialTick, poseStack, buffers);
        }
        else if(te instanceof GlassWirelessBlockEntity wireless)
        {
            renderCrystal(wireless, partialTick, poseStack, buffers);
        }
        te.lastDraw = 8;

        if(te.active)
        {
            drawScene(te, poseStack, buffers);
        }

        boolean wirelessMaster = isMaster && !((GlassProjectorBlockEntity)te).wirelessPos.isEmpty();
        drawPlanes(te, poseStack, buffers.getBuffer(GlassRenderTypes.planes(!wirelessMaster)), 1F, 1F, 1F, -1F, wirelessMaster ? 0.501D : 0.502D, partialTick);

        poseStack.popPose();
    }

    private void renderBeacon(GlassProjectorBlockEntity master, float partialTick, PoseStack poseStack, MultiBufferSource buffers)
    {
        poseStack.pushPose();
        Direction face = master.placingFace;
        if(face == Direction.DOWN)
        {
            poseStack.mulPose(Axis.XP.rotationDegrees(180F));
        }
        else if(face.getAxis().isHorizontal())
        {
            poseStack.mulPose(Axis.YP.rotationDegrees(-face.get2DDataValue() * 90F));
            poseStack.mulPose(Axis.XP.rotationDegrees(90F));
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(GlassUtil.interpolateRotation(master.rotationBeaconPrev, master.rotationBeacon, partialTick)));
        poseStack.translate(-0.25D, -0.35D, -0.25D);
        poseStack.scale(0.5F, 0.5F, 0.5F);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.BEACON.defaultBlockState(), poseStack, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    private void renderCrystal(GlassWirelessBlockEntity wireless, float partialTick, PoseStack poseStack, MultiBufferSource buffers)
    {
        poseStack.pushPose();
        poseStack.translate(0F, -0.15F, 0F);
        poseStack.scale(0.3F, 0.3F, 0.3F);
        //Same transforms as the vanilla end crystal renderer.
        poseStack.scale(2.0F, 2.0F, 2.0F);
        poseStack.translate(0.0F, -0.5F, 0.0F);
        //1.12 spun the crystal by (ticks * speed) degrees; the 1.21 model spins 3 degrees per age tick.
        crystalState.ageInTicks = (wireless.ticks + partialTick) * (1.2F + wireless.users * 10F) / 3F;
        crystalModel.setupAnim(crystalState);
        crystalModel.renderToBuffer(poseStack, buffers.getBuffer(END_CRYSTAL_RENDER_TYPE), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    /** Draws the terminal's view on this block's faces (1.12 rendered the world through a stencil here). */
    private void drawScene(GlassBaseBlockEntity te, PoseStack poseStack, MultiBufferSource buffers)
    {
        boolean wirelessMaster = te instanceof GlassProjectorBlockEntity master && !master.wirelessPos.isEmpty();
        SpyViewRenderer spy = SpyViewRenderer.INSTANCE;
        //Like 1.12, glass seen from inside a terminal's view, or with no terminal to show, is drawn as plain glass.
        TerminalPlacement placement = spy.isRendering() ? null : GlassClientState.INSTANCE.getTerminalPlacement(te.channel);
        if(placement == null)
        {
            return;
        }

        placement.playAmbienceOnce();
        ResourceLocation texture = spy.request(placement);
        if(texture != null)
        {
            drawPlanes(te, poseStack, buffers.getBuffer(GlassRenderTypes.screen(texture, !wirelessMaster)), 1F, 1F, 1F, 1F, 0.501D, 0F);
        }
    }

    /**
     * Port of TileEntityGlassRenderer.drawPlanes. With alpha -1 the alpha is worked out from the fade timers
     * (the white flash when glass turns on or off, or is punched).
     */
    public static void drawPlanes(GlassBaseBlockEntity te, PoseStack poseStack, VertexConsumer consumer, float r, float g, float b, float alpha, double pushback, float partialTick)
    {
        boolean calcAlpha = alpha == -1F;
        if(calcAlpha)
        {
            alpha = (float)Math.pow(Mth.clamp((te.fadeoutTime - partialTick) / (float)GlassBaseBlockEntity.FADEOUT_TIME, 0F, 1F), 0.5D);
        }
        PoseStack.Pose pose = poseStack.last();

        if(te instanceof GlassProjectorBlockEntity master)
        {
            if(!master.wirelessPos.isEmpty() && master.setChannel.equalsIgnoreCase(master.channel))
            {
                if(master.wirelessPos.size() > 1)
                {
                    drawWirelessScreen(master, pose, consumer, r, g, b, alpha, calcAlpha, pushback, partialTick);
                }
                return;
            }
        }

        Level level = te.getLevel();
        BlockPos pos = te.getBlockPos();
        if(alpha <= 0F || level == null)
        {
            return;
        }
        float halfSize = 0.501F;
        for(Direction face : te.activeFaces)
        {
            if(!Block.shouldRenderFace(te.getBlockState(), level.getBlockState(pos.relative(face)), face))
            {
                continue;
            }
            Vector3f n = new Vector3f(face.getStepX(), face.getStepY(), face.getStepZ());
            Vector3f u = face.getAxis() == Direction.Axis.Y ? new Vector3f(1F, 0F, 0F) : new Vector3f(0F, 1F, 0F);
            Vector3f v = new Vector3f(n).cross(u);
            //u x v = n, so these corners go anticlockwise seen from outside the face.
            Vector3f c = new Vector3f(n).mul((float)pushback);
            vertex(consumer, pose, c, u, v, -halfSize, -halfSize, r, g, b, alpha);
            vertex(consumer, pose, c, u, v, halfSize, -halfSize, r, g, b, alpha);
            vertex(consumer, pose, c, u, v, halfSize, halfSize, r, g, b, alpha);
            vertex(consumer, pose, c, u, v, -halfSize, halfSize, r, g, b, alpha);
        }
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Vector3f c, Vector3f u, Vector3f v, float du, float dv, float r, float g, float b, float a)
    {
        consumer.addVertex(pose, c.x + u.x * du + v.x * dv, c.y + u.y * du + v.y * dv, c.z + u.z * du + v.z * dv).setColor(r, g, b, a);
    }

    /** The floating screen spanned by a projector's wireless extensions, drawn as a triangle strip in 1.12. */
    private static void drawWirelessScreen(GlassProjectorBlockEntity master, PoseStack.Pose pose, VertexConsumer consumer, float r, float g, float b, float alpha, boolean calcAlpha, double pushback, float partialTick)
    {
        Direction face = master.placingFace;
        List<BlockPos> wirelessPos = master.wirelessPos;
        BlockPos masterPos = master.getBlockPos();

        int count = wirelessPos.size() + 1;
        float[][] verts = new float[count][];
        verts[0] = new float[] { (float)(face.getStepX() * pushback), (float)(face.getStepY() * pushback), (float)(face.getStepZ() * pushback), alpha };
        for(int i = 0; i < wirelessPos.size(); i++)
        {
            float a;
            if(master.active && master.wirelessTime > (i - 1) * GlassBaseBlockEntity.PROPAGATE_TIME)
            {
                a = calcAlpha ? (float)Math.pow(Mth.clamp(1.0F - (master.wirelessTime + partialTick - ((i - 1) * GlassBaseBlockEntity.PROPAGATE_TIME)) / (float)GlassBaseBlockEntity.FADEOUT_TIME, 0F, 1F), 0.5D) : alpha;
            }
            else if(!master.active)
            {
                a = alpha;
            }
            else
            {
                a = 0F;
            }
            BlockPos pos = wirelessPos.get(i);
            float pX = (float)(face.getStepX() * pushback * (face.getStepX() > 0 && pos.getX() > masterPos.getX() ? -1D : 1D));
            float pY = (float)(face.getStepY() * pushback * (face.getStepY() > 0 && pos.getY() > masterPos.getY() ? -1D : 1D));
            float pZ = (float)(face.getStepZ() * pushback * (face.getStepZ() > 0 && pos.getZ() > masterPos.getZ() ? -1D : 1D));
            verts[i + 1] = new float[] { pos.getX() - masterPos.getX() + pX, pos.getY() - masterPos.getY() + pY, pos.getZ() - masterPos.getZ() + pZ, a };
        }

        //Triangle strip as quads (the last corner repeated): (0,1,2), (1,2,3), ...
        for(int i = 0; i + 2 < count; i++)
        {
            float[] v0 = verts[i];
            float[] v1 = verts[i + 1];
            float[] v2 = verts[i + 2];
            if(v0[3] <= 0F && v1[3] <= 0F && v2[3] <= 0F)
            {
                continue;
            }
            consumer.addVertex(pose, v0[0], v0[1], v0[2]).setColor(r, g, b, v0[3]);
            consumer.addVertex(pose, v1[0], v1[1], v1[2]).setColor(r, g, b, v1[3]);
            consumer.addVertex(pose, v2[0], v2[1], v2[2]).setColor(r, g, b, v2[3]);
            consumer.addVertex(pose, v2[0], v2[1], v2[2]).setColor(r, g, b, v2[3]);
        }
    }

    @Override
    public boolean shouldRenderOffScreen()
    {
        return true;
    }

    @Override
    public int getViewDistance()
    {
        return 256;
    }
}
