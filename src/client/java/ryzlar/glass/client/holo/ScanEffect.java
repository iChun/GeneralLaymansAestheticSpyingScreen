package ryzlar.glass.client.holo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import ryzlar.glass.holo.HoloSphere;

/**
 * What a holo cube's scan looks like on the real world ("digitizing"): a holo grid ball grows out of the cube, and as
 * it passes, every visible face in the scanned ball breaks up into glowing pixels that drift off it and fall back into
 * place, until the whole ball has been taken apart and put back together. The pixels are drawn with the glass_scan
 * shader (which lights them as the front passes), the grid ball with lines.
 */
final class ScanEffect
{
    /** Pixels per face edge (so this squared per face). */
    private static final int PIXELS = 4;
    /** How far over a real face its pixels lie at rest, in blocks (with the pipeline's depth bias, enough not to flicker). */
    private static final float LIFT = 0.004F;
    /** Of the scan's time: the grid ball grows through the scanned ball in the first part... */
    private static final float GROW = 0.55F;
    /** ...and each face is apart for this long after the front has passed it. */
    private static final float APART = 0.35F;
    /** How far the pixels drift off their face at most, in blocks. */
    private static final float DRIFT = 0.55F;
    /** Rings of the grid ball (latitude lines) and its meridians. */
    private static final int RINGS = 7;
    private static final int MERIDIANS = 12;
    private static final int SEGMENTS = 32;

    /** Per face: its corner, the two directions along it, its normal (each x, y, z, from the anchor block's corner). */
    private final FloatArrayList faces = new FloatArrayList();
    /** Per face: its distance from the cube. */
    private final FloatArrayList distances = new FloatArrayList();
    private final Vector3f origin;
    final long builtFor;

    ScanEffect(LongList surfaces, BlockPos anchor, Vector3f origin, long builtFor)
    {
        this.origin = origin;
        this.builtFor = builtFor;
        for(int i = 0; i + 1 < surfaces.size(); i += 2)
        {
            BlockPos pos = BlockPos.of(surfaces.getLong(i));
            Direction direction = Direction.values()[(int)surfaces.getLong(i + 1)];
            float x = pos.getX() - anchor.getX(), y = pos.getY() - anchor.getY(), z = pos.getZ() - anchor.getZ();
            //The face's corner and its two edges, so that u x v points out of the block.
            Vector3f n = new Vector3f(direction.getStepX(), direction.getStepY(), direction.getStepZ());
            Vector3f u, v, corner;
            switch(direction)
            {
                case NORTH -> { corner = new Vector3f(x + 1, y, z); u = new Vector3f(-1, 0, 0); v = new Vector3f(0, 1, 0); }
                case SOUTH -> { corner = new Vector3f(x, y, z + 1); u = new Vector3f(1, 0, 0); v = new Vector3f(0, 1, 0); }
                case WEST -> { corner = new Vector3f(x, y, z); u = new Vector3f(0, 0, 1); v = new Vector3f(0, 1, 0); }
                case EAST -> { corner = new Vector3f(x + 1, y, z + 1); u = new Vector3f(0, 0, -1); v = new Vector3f(0, 1, 0); }
                case UP -> { corner = new Vector3f(x, y + 1, z + 1); u = new Vector3f(1, 0, 0); v = new Vector3f(0, 0, -1); }
                default -> { corner = new Vector3f(x, y, z); u = new Vector3f(1, 0, 0); v = new Vector3f(0, 0, 1); }
            }
            corner.add(new Vector3f(n).mul(LIFT));
            faces.add(corner.x); faces.add(corner.y); faces.add(corner.z);
            faces.add(u.x); faces.add(u.y); faces.add(u.z);
            faces.add(v.x); faces.add(v.y); faces.add(v.z);
            faces.add(n.x); faces.add(n.y); faces.add(n.z);
            Vector3f centre = new Vector3f(corner).add(new Vector3f(u).mul(0.5F)).add(new Vector3f(v).mul(0.5F));
            distances.add(centre.distance(origin));
        }
    }

    /** How far the grid ball has grown at this point of the scan (0..1), in blocks. */
    static float front(float progress)
    {
        return Math.min(progress / GROW, 1F) * (HoloSphere.SCAN_RADIUS + 1.2F);
    }

    /**
     * The pixels of every face: at rest on their face before the front gets there and after they have come back, apart
     * in between. {@code progress}: how far into the scan (0..1); {@code fade}: 1..0 once it is done; {@code flash}:
     * when it finishes.
     */
    void draw(VertexConsumer consumer, PoseStack.Pose pose, float progress, float fade, float flash)
    {
        float front = front(progress);
        int encodedRadius = Mth.clamp(Math.round(front * 64F), 0, 32767);
        int alpha = Mth.clamp(Math.round(fade * 255F), 0, 255);
        int green = Mth.clamp(Math.round(flash * 255F), 0, 255);
        float speed = (HoloSphere.SCAN_RADIUS + 1.2F) / GROW;
        float size = 1F / PIXELS;
        for(int f = 0, k = 0; k < distances.size(); f += 12, k++)
        {
            float distance = distances.getFloat(k);
            if(distance > front + 0.5F)
            {
                continue; //the front has not got there yet: the real face is still whole
            }
            //How far through being apart (0..1, then back together), from when the front passed this face.
            float since = progress - distance / speed;
            float apart = Math.clamp(since / APART, 0F, 1F);
            float out = (float)Math.sin(apart * Math.PI);
            float cx = faces.getFloat(f), cy = faces.getFloat(f + 1), cz = faces.getFloat(f + 2);
            float ux = faces.getFloat(f + 3), uy = faces.getFloat(f + 4), uz = faces.getFloat(f + 5);
            float vx = faces.getFloat(f + 6), vy = faces.getFloat(f + 7), vz = faces.getFloat(f + 8);
            float nx = faces.getFloat(f + 9), ny = faces.getFloat(f + 10), nz = faces.getFloat(f + 11);
            for(int py = 0; py < PIXELS; py++)
            {
                for(int px = 0; px < PIXELS; px++)
                {
                    int seed = k * 131 + py * PIXELS + px;
                    //Each pixel drifts off its own way and a little sideways, and shrinks while it is away.
                    float drift = out * DRIFT * (0.25F + random(seed, 1) * 0.75F);
                    float side = out * 0.15F;
                    float su = side * (random(seed, 2) - 0.5F), sv = side * (random(seed, 3) - 0.5F);
                    float shrink = 1F - out * 0.35F;
                    float u0 = px * size + size * (1F - shrink) / 2F + su, v0 = py * size + size * (1F - shrink) / 2F + sv;
                    float u1 = u0 + size * shrink, v1 = v0 + size * shrink;
                    float ox = nx * drift, oy = ny * drift, oz = nz * drift;
                    pixel(consumer, pose, cx + ox, cy + oy, cz + oz, ux, uy, uz, vx, vy, vz, u0, v0, u1, v1, green, alpha, encodedRadius);
                }
            }
        }
    }

    private void pixel(VertexConsumer consumer, PoseStack.Pose pose, float cx, float cy, float cz, float ux, float uy, float uz, float vx, float vy, float vz,
                       float u0, float v0, float u1, float v1, int green, int alpha, int encodedRadius)
    {
        float[][] uv = { { u0, v0 }, { u1, v0 }, { u1, v1 }, { u0, v1 } };
        float[][] tex = { { 0F, 0F }, { 1F, 0F }, { 1F, 1F }, { 0F, 1F } };
        for(int c = 0; c < 4; c++)
        {
            float x = cx + ux * uv[c][0] + vx * uv[c][1];
            float y = cy + uy * uv[c][0] + vy * uv[c][1];
            float z = cz + uz * uv[c][0] + vz * uv[c][1];
            consumer.addVertex(pose, x, y, z)
                    .setColor(255, green, 255, alpha)
                    .setUv(tex[c][0], tex[c][1])
                    .setUv1(toShort((x - origin.x) * 32F), toShort((z - origin.z) * 32F))
                    .setUv2(toShort((y - origin.y) * 32F), encodedRadius)
                    .setNormal(pose, 0F, 1F, 0F);
        }
    }

    /** The holo grid ball growing out of the cube: rings and meridians, pulsing, fading with the scan. */
    void grid(VertexConsumer lines, PoseStack.Pose pose, Vector3f centre, float progress, float fade, float time)
    {
        float radius = front(progress);
        if(radius <= 0.05F || fade <= 0F)
        {
            return;
        }
        float pulse = 0.75F + 0.25F * (float)Math.sin(time * 0.6F);
        int colour = Mth.clamp(Math.round(200F * pulse * fade), 0, 255) << 24 | 0x6CF0FF;
        float spin = time * 0.02F;
        for(int r = 1; r < RINGS; r++)
        {
            double lat = Math.PI * r / RINGS - Math.PI / 2D;
            float ringRadius = radius * (float)Math.cos(lat), y = radius * (float)Math.sin(lat);
            for(int s = 0; s < SEGMENTS; s++)
            {
                double a0 = Math.PI * 2D * s / SEGMENTS + spin, a1 = Math.PI * 2D * (s + 1) / SEGMENTS + spin;
                segment(lines, pose, centre, ringRadius * (float)Math.cos(a0), y, ringRadius * (float)Math.sin(a0),
                        ringRadius * (float)Math.cos(a1), y, ringRadius * (float)Math.sin(a1), colour);
            }
        }
        for(int m = 0; m < MERIDIANS; m++)
        {
            double a = Math.PI * 2D * m / MERIDIANS + spin;
            float cos = (float)Math.cos(a), sin = (float)Math.sin(a);
            for(int s = 0; s < SEGMENTS / 2; s++)
            {
                double l0 = Math.PI * s / (SEGMENTS / 2D) - Math.PI / 2D, l1 = Math.PI * (s + 1) / (SEGMENTS / 2D) - Math.PI / 2D;
                float r0 = radius * (float)Math.cos(l0), r1 = radius * (float)Math.cos(l1);
                segment(lines, pose, centre, r0 * cos, radius * (float)Math.sin(l0), r0 * sin, r1 * cos, radius * (float)Math.sin(l1), r1 * sin, colour);
            }
        }
    }

    private static void segment(VertexConsumer lines, PoseStack.Pose pose, Vector3f centre, float ax, float ay, float az, float bx, float by, float bz, int colour)
    {
        float nx = bx - ax, ny = by - ay, nz = bz - az;
        float length = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
        if(length < 1.0E-5F)
        {
            return;
        }
        nx /= length;
        ny /= length;
        nz /= length;
        lines.addVertex(pose, centre.x + ax, centre.y + ay, centre.z + az).setColor(colour).setNormal(pose, nx, ny, nz);
        lines.addVertex(pose, centre.x + bx, centre.y + by, centre.z + bz).setColor(colour).setNormal(pose, nx, ny, nz);
    }

    private static int toShort(float value)
    {
        return Mth.clamp(Math.round(value), -32767, 32767);
    }

    private static float random(int seed, int salt)
    {
        int h = seed * 0x9E3779B1 + salt * 0x85EBCA6B;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return (h >>> 8) / (float)(1 << 24);
    }
}
