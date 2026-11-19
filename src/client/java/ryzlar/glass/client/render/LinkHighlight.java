package ryzlar.glass.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.blockentity.GlassProjectorBlockEntity;

import java.util.List;

/**
 * Shows in the world what a wireless extension is (or would be) connected to, for a few seconds: a box round the
 * projector and the extension, a line between them, and, when connected, the screen's outline through all its
 * extensions. Asked for by the wireless extension's screen (LOCATE, or picking a projector in its lists).
 */
public final class LinkHighlight
{
    private static final int CONNECTED = 0xFF3CE6FF;
    private static final int CANDIDATE = 0xFFFFC857;

    @Nullable
    private static BlockPos extension;
    @Nullable
    private static BlockPos projector;
    private static boolean connected;
    private static long untilMillis;

    private LinkHighlight() {}

    public static void init()
    {
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(context -> {
            if(extension == null || projector == null || Util.getMillis() > untilMillis || context.consumers() == null)
            {
                return;
            }
            draw(context.matrixStack(), context.consumers(), context.camera().getPosition());
        });
    }

    /** Shows the link between an extension and a projector for this many seconds (connected: blue, could connect: amber). */
    public static void show(BlockPos extensionPos, BlockPos projectorPos, boolean isConnected, float seconds)
    {
        extension = extensionPos.immutable();
        projector = projectorPos.immutable();
        connected = isConnected;
        untilMillis = Util.getMillis() + (long)(seconds * 1000F);
    }

    public static void reset()
    {
        extension = null;
        projector = null;
    }

    private static void draw(PoseStack poseStack, MultiBufferSource buffers, Vec3 camera)
    {
        Minecraft mc = Minecraft.getInstance();
        if(mc.level == null)
        {
            return;
        }
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        PoseStack.Pose pose = poseStack.last();
        float pulse = 0.6F + 0.4F * (float)Math.sin(Util.getMillis() / 180.0D);
        int colour = connected ? CONNECTED : CANDIDATE;
        int faint = withAlpha(colour, 0.35F + 0.35F * pulse);
        box(lines, pose, new AABB(projector).inflate(0.06D + 0.04D * pulse), colour);
        box(lines, pose, new AABB(extension).inflate(0.04D), colour);
        Vec3 a = Vec3.atCenterOf(extension);
        Vec3 b = Vec3.atCenterOf(projector);
        line(lines, pose, a, b, faint);
        //Connected: the whole screen's outline, through its extensions in order.
        if(connected && mc.level.getBlockEntity(projector) instanceof GlassProjectorBlockEntity master)
        {
            List<BlockPos> order = master.wirelessPos;
            Vec3 last = b;
            for(BlockPos pos : order)
            {
                Vec3 next = Vec3.atCenterOf(pos);
                line(lines, pose, last, next, withAlpha(colour, 0.3F));
                if(!pos.equals(extension))
                {
                    box(lines, pose, new AABB(pos).inflate(0.02D), withAlpha(colour, 0.45F));
                }
                last = next;
            }
        }
        poseStack.popPose();
    }

    private static void box(VertexConsumer lines, PoseStack.Pose pose, AABB box, int colour)
    {
        double[] xs = { box.minX, box.maxX };
        double[] ys = { box.minY, box.maxY };
        double[] zs = { box.minZ, box.maxZ };
        for(double y : ys)
        {
            for(double z : zs)
            {
                line(lines, pose, new Vec3(box.minX, y, z), new Vec3(box.maxX, y, z), colour);
            }
        }
        for(double x : xs)
        {
            for(double z : zs)
            {
                line(lines, pose, new Vec3(x, box.minY, z), new Vec3(x, box.maxY, z), colour);
            }
            for(double y : ys)
            {
                line(lines, pose, new Vec3(x, y, box.minZ), new Vec3(x, y, box.maxZ), colour);
            }
        }
    }

    private static void line(VertexConsumer consumer, PoseStack.Pose pose, Vec3 a, Vec3 b, int colour)
    {
        Vec3 n = b.subtract(a);
        double length = n.length();
        if(length < 1.0E-6D)
        {
            return;
        }
        n = n.scale(1D / length);
        consumer.addVertex(pose, (float)a.x, (float)a.y, (float)a.z).setColor(colour).setNormal(pose, (float)n.x, (float)n.y, (float)n.z);
        consumer.addVertex(pose, (float)b.x, (float)b.y, (float)b.z).setColor(colour).setNormal(pose, (float)n.x, (float)n.y, (float)n.z);
    }

    private static int withAlpha(int colour, float alpha)
    {
        return Math.clamp((int)(alpha * 255F), 0, 255) << 24 | (colour & 0xFFFFFF);
    }
}
