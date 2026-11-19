package ryzlar.glass.client.gadget;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ryzlar.glass.gadget.HoloDecoyEntity;
import ryzlar.glass.gadget.HoloMapTableBlock;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws a holo map table's map: the land {@link #RADIUS} blocks round the table as a little relief of map colours,
 * floating over it with a blue cast and a scan line running over it, and a dot for every player (cyan), monster (red)
 * and animal (green) on it. The land is looked at again every two seconds; the dots every frame.
 */
public class HoloMapRenderer implements BlockEntityRenderer<HoloMapTableBlock.HoloMapTableBlockEntity>
{
    private static final int RADIUS = 24;
    /** How wide the map is, how high one block of land is on it, and how far over the table it floats (blocks). */
    private static final float SIZE = 2.2F;
    private static final float CELL = SIZE / (RADIUS * 2 + 1);
    private static final float STEP = CELL * 0.8F;
    private static final float FLOAT = 0.92F;
    private static final int DEPTH = 12;
    private static final int REBUILD_TICKS = 40;

    /** A map's relief: quads (x, y, z per corner) and their colours. */
    private static final class Relief
    {
        final FloatArrayList corners = new FloatArrayList();
        final IntArrayList colours = new IntArrayList();
        boolean built;
        long builtAt;
    }

    private final Map<BlockPos, Relief> reliefs = new HashMap<>();

    public HoloMapRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(HoloMapTableBlock.HoloMapTableBlockEntity table, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay, Vec3 cameraPos)
    {
        Level level = table.getLevel();
        if(level == null || !table.getBlockState().getValue(HoloMapTableBlock.LIT))
        {
            table.clientOnSince = Long.MIN_VALUE / 2;
            return;
        }
        long now = level.getGameTime();
        if(table.clientOnSince < Long.MIN_VALUE / 4)
        {
            table.clientOnSince = now;
        }
        //The map rises out of the table and spreads out when switched on.
        float grow = Math.clamp((now - table.clientOnSince + partialTick) / 30F, 0F, 1F);
        float eased = grow * grow * (3F - 2F * grow);
        Relief relief = reliefs.computeIfAbsent(table.getBlockPos().immutable(), p -> new Relief());
        if(!relief.built || now - relief.builtAt >= REBUILD_TICKS || now < relief.builtAt)
        {
            build(level, table.getBlockPos(), relief);
            relief.built = true;
            relief.builtAt = now;
        }
        float time = now + partialTick;
        float sweep = (time * 0.02F) % 1F;

        poseStack.pushPose();
        poseStack.translate(0.5D, FLOAT, 0.5D);
        poseStack.scale(eased, eased, eased);
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
        for(int i = 0, q = 0; i + 12 <= relief.corners.size(); i += 12, q++)
        {
            int colour = relief.colours.getInt(q);
            //The scan line: a band across the map, lighting what it passes.
            float z = relief.corners.getFloat(i + 2);
            float band = 1F - Math.min(1F, Math.abs((z / SIZE + 0.5F) - sweep) * 14F);
            colour = brighten(colour, Math.max(0F, band) * 0.5F);
            for(int c = 0; c < 4; c++)
            {
                quads.addVertex(pose, relief.corners.getFloat(i + c * 3), relief.corners.getFloat(i + c * 3 + 1), relief.corners.getFloat(i + c * 3 + 2)).setColor(colour);
            }
        }
        drawDots(level, table.getBlockPos(), pose, quads, time);
        drawFrame(buffers.getBuffer(RenderType.lines()), pose, time);
        poseStack.popPose();
    }

    /** The land round the table: a column for each block of the map, as high as the ground there, in its map colour. */
    private static void build(Level level, BlockPos centre, Relief relief)
    {
        relief.corners.clear();
        relief.colours.clear();
        int n = RADIUS * 2 + 1;
        int[] heights = new int[n * n];
        int[] colours = new int[n * n];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for(int dz = -RADIUS; dz <= RADIUS; dz++)
        {
            for(int dx = -RADIUS; dx <= RADIUS; dx++)
            {
                int x = centre.getX() + dx, z = centre.getZ() + dz;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                pos.set(x, top, z);
                MapColor map = level.getBlockState(pos).getMapColor(level, pos);
                int rgb = map == MapColor.NONE ? 0x202830 : map.col;
                int i = (dz + RADIUS) * n + (dx + RADIUS);
                heights[i] = Math.clamp(top - centre.getY(), -DEPTH, DEPTH);
                //A blue cast, as a hologram.
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                r = (int)(r * 0.72F + 0x3C * 0.28F);
                g = (int)(g * 0.72F + 0xE6 * 0.28F);
                b = (int)(b * 0.72F + 0xFF * 0.28F);
                float edge = Math.max(Math.abs(dx), Math.abs(dz)) / (float)RADIUS;
                int alpha = (int)(230 * (1F - Math.max(0F, edge - 0.75F) * 3F));
                colours[i] = Math.max(0, alpha) << 24 | r << 16 | g << 8 | b;
            }
        }
        float half = SIZE / 2F;
        for(int iz = 0; iz < n; iz++)
        {
            for(int ix = 0; ix < n; ix++)
            {
                int i = iz * n + ix;
                float x0 = ix * CELL - half, x1 = x0 + CELL, z0 = iz * CELL - half, z1 = z0 + CELL;
                float y = (heights[i] + DEPTH) * STEP * 0.5F;
                int colour = colours[i];
                quad(relief, colour, x0, y, z1, x1, y, z1, x1, y, z0, x0, y, z0);
                //Sides down to lower neighbours, a little darker.
                int side = darken(colour, 0.75F);
                if(ix + 1 < n && heights[i + 1] < heights[i])
                {
                    float low = (heights[i + 1] + DEPTH) * STEP * 0.5F;
                    quad(relief, side, x1, low, z1, x1, low, z0, x1, y, z0, x1, y, z1);
                }
                if(ix > 0 && heights[i - 1] < heights[i])
                {
                    float low = (heights[i - 1] + DEPTH) * STEP * 0.5F;
                    quad(relief, side, x0, low, z0, x0, low, z1, x0, y, z1, x0, y, z0);
                }
                if(iz + 1 < n && heights[i + n] < heights[i])
                {
                    float low = (heights[i + n] + DEPTH) * STEP * 0.5F;
                    quad(relief, side, x0, low, z1, x1, low, z1, x1, y, z1, x0, y, z1);
                }
                if(iz > 0 && heights[i - n] < heights[i])
                {
                    float low = (heights[i - n] + DEPTH) * STEP * 0.5F;
                    quad(relief, side, x1, low, z0, x0, low, z0, x0, y, z0, x1, y, z0);
                }
            }
        }
    }

    /** A lit frame round the map, and the light from the table's lens spreading up to its corners. */
    private static void drawFrame(VertexConsumer lines, PoseStack.Pose pose, float time)
    {
        float half = SIZE / 2F;
        float y = DEPTH * STEP * 0.5F;
        float lens = 13.5F / 16F - FLOAT;
        int frame = (int)(170 + 60 * Math.sin(time * 0.15F)) << 24 | 0x3CE6FF;
        int beam = 70 << 24 | 0x3CE6FF;
        float[][] corners = { { -half, -half }, { half, -half }, { half, half }, { -half, half } };
        for(int i = 0; i < 4; i++)
        {
            float[] a = corners[i], b = corners[(i + 1) % 4];
            line(lines, pose, a[0], y, a[1], b[0], y, b[1], frame);
            line(lines, pose, 0F, lens, 0F, a[0], y, a[1], beam);
        }
    }

    private static void line(VertexConsumer lines, PoseStack.Pose pose, float ax, float ay, float az, float bx, float by, float bz, int colour)
    {
        float nx = bx - ax, ny = by - ay, nz = bz - az;
        float length = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
        lines.addVertex(pose, ax, ay, az).setColor(colour).setNormal(pose, nx / length, ny / length, nz / length);
        lines.addVertex(pose, bx, by, bz).setColor(colour).setNormal(pose, nx / length, ny / length, nz / length);
    }

    /** Players, monsters and animals on the map, as little glowing cubes over the land. */
    private static void drawDots(Level level, BlockPos centre, PoseStack.Pose pose, VertexConsumer quads, float time)
    {
        AABB area = new AABB(centre).inflate(RADIUS, DEPTH * 2, RADIUS);
        float half = SIZE / 2F;
        float pulse = 0.75F + 0.25F * (float)Math.sin(time * 0.3F);
        for(LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive))
        {
            int colour = entity instanceof Player || entity instanceof HoloDecoyEntity ? 0x3CE6FF : entity instanceof Enemy ? 0xFF4050 : 0x5CFF8A;
            colour = (int)(255 * pulse) << 24 | colour;
            float x = (float)(entity.getX() - centre.getX() - 0.5D) * CELL + CELL / 2F;
            float z = (float)(entity.getZ() - centre.getZ() - 0.5D) * CELL + CELL / 2F;
            if(Math.abs(x) > half || Math.abs(z) > half)
            {
                continue;
            }
            float y = (float)(Math.clamp(entity.getY() - centre.getY(), -DEPTH, DEPTH) + DEPTH + 1) * STEP * 0.5F + CELL;
            float s = entity instanceof Player ? CELL * 2.4F : CELL * 1.7F;
            cube(quads, pose, colour, x, y, z, s);
        }
    }

    private static void cube(VertexConsumer quads, PoseStack.Pose pose, int colour, float x, float y, float z, float s)
    {
        float a = x - s / 2F, b = x + s / 2F, c = y, d = y + s, e = z - s / 2F, f = z + s / 2F;
        float[][] faces = {
                { a, d, f, b, d, f, b, d, e, a, d, e }, { a, c, e, b, c, e, b, c, f, a, c, f },
                { a, c, f, b, c, f, b, d, f, a, d, f }, { b, c, e, a, c, e, a, d, e, b, d, e },
                { a, c, e, a, c, f, a, d, f, a, d, e }, { b, c, f, b, c, e, b, d, e, b, d, f },
        };
        for(float[] face : faces)
        {
            for(int k = 0; k < 4; k++)
            {
                quads.addVertex(pose, face[k * 3], face[k * 3 + 1], face[k * 3 + 2]).setColor(colour);
            }
        }
    }

    private static void quad(Relief relief, int colour, float... corners)
    {
        for(float v : corners)
        {
            relief.corners.add(v);
        }
        relief.colours.add(colour);
    }

    private static int darken(int argb, float by)
    {
        int r = (int)(((argb >> 16) & 0xFF) * by), g = (int)(((argb >> 8) & 0xFF) * by), b = (int)((argb & 0xFF) * by);
        return (argb & 0xFF000000) | r << 16 | g << 8 | b;
    }

    private static int brighten(int argb, float by)
    {
        if(by <= 0F)
        {
            return argb;
        }
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        r = (int)(r + (0x9C - r * 0.4F) * by);
        g = (int)(g + (0xF6 - g * 0.4F) * by);
        b = (int)(b + (0xFF - b * 0.4F) * by);
        return (argb & 0xFF000000) | Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, b);
    }

    @Override
    public int getViewDistance()
    {
        return 64;
    }

    @Override
    public boolean shouldRenderOffScreen()
    {
        return true;
    }
}
