package ryzlar.glass.client.holo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.GlassRegistry;
import ryzlar.glass.holo.HoloNetworking;
import ryzlar.glass.holo.ProjectionRef;

import java.util.HashMap;
import java.util.Map;

/**
 * Client side of the hologram kit: the blocks of the projections in use (asked from the server once each), the scanner's
 * preview grid, and the crystal tooltip.
 */
public final class HoloClient
{
    /** A projection's blocks: x fastest, then z, then y. */
    public record ClientProjection(String id, int sizeX, int sizeY, int sizeZ, BlockState[] blocks)
    {
        public BlockState get(int x, int y, int z)
        {
            if(x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ)
            {
                return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            }
            return blocks[(y * sizeZ + z) * sizeX + x];
        }
    }

    private static final Map<String, ClientProjection> projections = new HashMap<>();
    private static final Map<String, Long> requested = new HashMap<>();
    private static final long REQUEST_RETRY_MILLIS = 5000L;

    /** The scanner preview: the area, and until when it shows (game time). */
    @Nullable
    private static AABB previewBox;
    private static long previewUntil;

    private HoloClient() {}

    public static void init()
    {
        ClientPlayNetworking.registerGlobalReceiver(HoloNetworking.ProjectionBlocks.TYPE, (payload, context) -> {
            BlockState[] palette = new BlockState[payload.palette().size()];
            for(int i = 0; i < palette.length; i++)
            {
                palette[i] = Block.stateById(payload.palette().get(i));
            }
            BlockState[] blocks = new BlockState[payload.blocks().size()];
            for(int i = 0; i < blocks.length; i++)
            {
                int index = payload.blocks().get(i);
                blocks[i] = index >= 0 && index < palette.length ? palette[index] : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            }
            projections.put(payload.id(), new ClientProjection(payload.id(), payload.sizeX(), payload.sizeY(), payload.sizeZ(), blocks));
        });

        BlockEntityRenderers.register(GlassRegistry.HOLO_PROJECTOR_BE, HologramRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(GlassRegistry.HOLO_CUBE_ENTITY, HoloCubeRenderer::new);

        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            ProjectionRef ref = stack.get(GlassRegistry.PROJECTION);
            if(ref != null)
            {
                lines.add(Component.translatable("glass.tooltip.projection", ref.name(), ref.sizeX(), ref.sizeY(), ref.sizeZ()).withStyle(ChatFormatting.AQUA));
            }
            if(stack.is(GlassRegistry.HOLO_CUBE))
            {
                ryzlar.glass.holo.CubeScan scan = stack.get(GlassRegistry.CUBE_SCAN);
                lines.add(scan == null ? Component.translatable("glass.tooltip.cube.empty").withStyle(ChatFormatting.GRAY)
                        : Component.translatable("glass.tooltip.cube.scan", scan.sizeX(), scan.sizeY(), scan.sizeZ()).withStyle(ChatFormatting.AQUA));
            }
        });

        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(context -> {
            Minecraft mc = Minecraft.getInstance();
            if(previewBox == null || mc.level == null || mc.level.getGameTime() > previewUntil || context.consumers() == null)
            {
                return;
            }
            drawGrid(context.matrixStack(), context.consumers(), context.camera().getPosition(), previewBox);
        });
    }

    public static void reset()
    {
        projections.clear();
        requested.clear();
        previewBox = null;
    }

    /** The blocks of a projection, or null while they are on their way (asked for now if need be). */
    @Nullable
    public static ClientProjection projection(String id)
    {
        ClientProjection projection = projections.get(id);
        if(projection == null)
        {
            long now = Util.getMillis();
            Long last = requested.get(id);
            if(last == null || now - last > REQUEST_RETRY_MILLIS)
            {
                requested.put(id, now);
                ClientPlayNetworking.send(new HoloNetworking.RequestProjection(id));
            }
        }
        return projection;
    }

    /** Shows the scanner's area for the given number of ticks from now. */
    public static void preview(AABB box, int ticks)
    {
        Minecraft mc = Minecraft.getInstance();
        previewBox = box;
        previewUntil = mc.level == null ? 0L : mc.level.getGameTime() + ticks;
    }

    /** The area as a glowing grid: its outline, and a line along every block on each of its faces. */
    private static void drawGrid(PoseStack poseStack, MultiBufferSource buffers, Vec3 camera, AABB box)
    {
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        PoseStack.Pose pose = poseStack.last();
        float pulse = 0.55F + 0.25F * (float)Math.sin(Util.getMillis() / 250.0D);
        int grid = colour(0.24F, 0.9F, 1F, pulse * 0.55F);
        int edge = colour(0.24F, 0.9F, 1F, 1F);
        double x0 = box.minX, y0 = box.minY, z0 = box.minZ, x1 = box.maxX, y1 = box.maxY, z1 = box.maxZ;
        //Grid lines on each face.
        for(double x = x0 + 1; x < x1; x++)
        {
            line(lines, pose, x, y0, z0, x, y1, z0, grid);
            line(lines, pose, x, y0, z1, x, y1, z1, grid);
            line(lines, pose, x, y0, z0, x, y0, z1, grid);
            line(lines, pose, x, y1, z0, x, y1, z1, grid);
        }
        for(double y = y0 + 1; y < y1; y++)
        {
            line(lines, pose, x0, y, z0, x1, y, z0, grid);
            line(lines, pose, x0, y, z1, x1, y, z1, grid);
            line(lines, pose, x0, y, z0, x0, y, z1, grid);
            line(lines, pose, x1, y, z0, x1, y, z1, grid);
        }
        for(double z = z0 + 1; z < z1; z++)
        {
            line(lines, pose, x0, y0, z, x0, y1, z, grid);
            line(lines, pose, x1, y0, z, x1, y1, z, grid);
            line(lines, pose, x0, y0, z, x1, y0, z, grid);
            line(lines, pose, x0, y1, z, x1, y1, z, grid);
        }
        //The outline.
        double[][] corners = { { x0, y0, z0 }, { x1, y0, z0 }, { x1, y0, z1 }, { x0, y0, z1 } };
        for(int i = 0; i < 4; i++)
        {
            double[] a = corners[i];
            double[] b = corners[(i + 1) % 4];
            line(lines, pose, a[0], y0, a[2], b[0], y0, b[2], edge);
            line(lines, pose, a[0], y1, a[2], b[0], y1, b[2], edge);
            line(lines, pose, a[0], y0, a[2], a[0], y1, a[2], edge);
        }
        poseStack.popPose();
    }

    private static int colour(float r, float g, float b, float a)
    {
        return (int)(a * 255F) << 24 | (int)(r * 255F) << 16 | (int)(g * 255F) << 8 | (int)(b * 255F);
    }

    private static void line(VertexConsumer consumer, PoseStack.Pose pose, double ax, double ay, double az, double bx, double by, double bz, int colour)
    {
        float nx = (float)(bx - ax);
        float ny = (float)(by - ay);
        float nz = (float)(bz - az);
        float length = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
        if(length > 0F)
        {
            nx /= length;
            ny /= length;
            nz /= length;
        }
        consumer.addVertex(pose, (float)ax, (float)ay, (float)az).setColor(colour).setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, (float)bx, (float)by, (float)bz).setColor(colour).setNormal(pose, nx, ny, nz);
    }
}
