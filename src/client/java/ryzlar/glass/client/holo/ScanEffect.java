package ryzlar.glass.client.holo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import ryzlar.glass.holo.RoomScan;

/**
 * What a holo cube's scan looks like on the real room: every face of the room's walls, floor and ceiling that faces the
 * open space gets a quad just over it, which the glass_scan shader lights as the scan's sphere of light runs past (a
 * bright front, then a grid that dims). Laser lines from the cube to spots on the front go with it.
 */
final class ScanEffect
{
    /** How far over a real face its quad lies, in blocks (with the pipeline's depth bias, enough not to flicker). */
    private static final float LIFT = 0.004F;
    private static final float[][] UV = { { 0F, 0F }, { 1F, 0F }, { 1F, 1F }, { 0F, 1F } };

    /** Corners (x, y, z, u, v), four per face, from the anchor block's corner. */
    private final FloatArrayList corners = new FloatArrayList();
    /** Face centres from the anchor block's corner, nearest first, and their distance from the cube. */
    private final float[] centres;
    private final float[] distances;
    private final Vector3f origin;
    final long builtFor;

    ScanEffect(RoomScan.Room room, BlockPos anchor, Vector3f origin, long builtFor)
    {
        this.origin = origin;
        this.builtFor = builtFor;
        FloatArrayList faceCentres = new FloatArrayList();
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
        for(long key : room.surfaces())
        {
            BlockPos pos = BlockPos.of(key);
            float x0 = pos.getX() - anchor.getX(), y0 = pos.getY() - anchor.getY(), z0 = pos.getZ() - anchor.getZ();
            float x1 = x0 + 1F, y1 = y0 + 1F, z1 = z0 + 1F;
            for(Direction direction : Direction.values())
            {
                next.setWithOffset(pos, direction);
                if(!room.open().contains(next.asLong()))
                {
                    continue;
                }
                float[][] face = switch(direction)
                {
                    case NORTH -> new float[][] { { x1, y0, z0 - LIFT }, { x0, y0, z0 - LIFT }, { x0, y1, z0 - LIFT }, { x1, y1, z0 - LIFT } };
                    case SOUTH -> new float[][] { { x0, y0, z1 + LIFT }, { x1, y0, z1 + LIFT }, { x1, y1, z1 + LIFT }, { x0, y1, z1 + LIFT } };
                    case WEST -> new float[][] { { x0 - LIFT, y0, z0 }, { x0 - LIFT, y0, z1 }, { x0 - LIFT, y1, z1 }, { x0 - LIFT, y1, z0 } };
                    case EAST -> new float[][] { { x1 + LIFT, y0, z1 }, { x1 + LIFT, y0, z0 }, { x1 + LIFT, y1, z0 }, { x1 + LIFT, y1, z1 } };
                    case UP -> new float[][] { { x0, y1 + LIFT, z1 }, { x1, y1 + LIFT, z1 }, { x1, y1 + LIFT, z0 }, { x0, y1 + LIFT, z0 } };
                    case DOWN -> new float[][] { { x0, y0 - LIFT, z0 }, { x1, y0 - LIFT, z0 }, { x1, y0 - LIFT, z1 }, { x0, y0 - LIFT, z1 } };
                };
                float cx = 0F, cy = 0F, cz = 0F;
                for(int c = 0; c < 4; c++)
                {
                    corners.add(face[c][0]);
                    corners.add(face[c][1]);
                    corners.add(face[c][2]);
                    corners.add(UV[c][0]);
                    corners.add(UV[c][1]);
                    cx += face[c][0] / 4F;
                    cy += face[c][1] / 4F;
                    cz += face[c][2] / 4F;
                }
                faceCentres.add(cx);
                faceCentres.add(cy);
                faceCentres.add(cz);
            }
        }
        //Sorted by distance from the cube, so the spots on the front can be found quickly.
        int count = faceCentres.size() / 3;
        Integer[] order = new Integer[count];
        float[] distance = new float[count];
        for(int i = 0; i < count; i++)
        {
            order[i] = i;
            distance[i] = new Vector3f(faceCentres.getFloat(i * 3), faceCentres.getFloat(i * 3 + 1), faceCentres.getFloat(i * 3 + 2)).distance(origin);
        }
        java.util.Arrays.sort(order, (a, b) -> Float.compare(distance[a], distance[b]));
        centres = new float[count * 3];
        distances = new float[count];
        for(int i = 0; i < count; i++)
        {
            int j = order[i];
            centres[i * 3] = faceCentres.getFloat(j * 3);
            centres[i * 3 + 1] = faceCentres.getFloat(j * 3 + 1);
            centres[i * 3 + 2] = faceCentres.getFloat(j * 3 + 2);
            distances[i] = distance[j];
        }
    }

    /** The light on the room, run out this far; {@code fade} 1..0 once the scan is done, {@code flash} when it finishes. */
    void draw(VertexConsumer consumer, PoseStack.Pose pose, float radius, float fade, float flash)
    {
        int encodedRadius = Mth.clamp(Math.round(radius * 64F), 0, 32767);
        int alpha = Mth.clamp(Math.round(fade * 255F), 0, 255);
        int green = Mth.clamp(Math.round(flash * 255F), 0, 255);
        for(int i = 0; i + 5 <= corners.size(); i += 5)
        {
            float x = corners.getFloat(i), y = corners.getFloat(i + 1), z = corners.getFloat(i + 2);
            consumer.addVertex(pose, x, y, z)
                    .setColor(255, green, 255, alpha)
                    .setUv(corners.getFloat(i + 3), corners.getFloat(i + 4))
                    .setUv1(toShort((x - origin.x) * 32F), toShort((z - origin.z) * 32F))
                    .setUv2(toShort((y - origin.y) * 32F), encodedRadius)
                    .setNormal(pose, 0F, 1F, 0F);
        }
    }

    /** Laser lines from the cube to spots on the front, changing every couple of ticks. */
    void beams(VertexConsumer lines, PoseStack.Pose pose, Vector3f from, float radius, int seed, int count)
    {
        int lo = lowerBound(radius - 0.9F);
        int hi = lowerBound(radius + 0.3F);
        if(hi <= lo)
        {
            return;
        }
        for(int i = 0; i < count; i++)
        {
            int pick = lo + (int)(random(seed, i) * (hi - lo));
            float tx = centres[pick * 3], ty = centres[pick * 3 + 1], tz = centres[pick * 3 + 2];
            float nx = tx - from.x, ny = ty - from.y, nz = tz - from.z;
            float length = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
            if(length < 1.0E-4F)
            {
                continue;
            }
            nx /= length;
            ny /= length;
            nz /= length;
            int colour = (int)(150 + random(seed, i + 97) * 105) << 24 | 0x6CF0FF;
            lines.addVertex(pose, from.x, from.y, from.z).setColor(colour).setNormal(pose, nx, ny, nz);
            lines.addVertex(pose, tx, ty, tz).setColor(colour).setNormal(pose, nx, ny, nz);
        }
    }

    /** The first face at least this far from the cube. */
    private int lowerBound(float distance)
    {
        int lo = 0, hi = distances.length;
        while(lo < hi)
        {
            int mid = (lo + hi) >>> 1;
            if(distances[mid] < distance)
            {
                lo = mid + 1;
            }
            else
            {
                hi = mid;
            }
        }
        return lo;
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
