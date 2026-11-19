package ryzlar.glass.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import ryzlar.glass.blockentity.GlassProjectorBlockEntity;
import ryzlar.glass.client.GlassClientState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The back of a floating (wireless) screen and the inside of a cloak field. The image only works from the front, so
 * from behind you would see nothing at all and not know a screen is there. Instead: its edges glow, with marks in the
 * corners; coming close, a nano grid fades in, brightest where you are nearest; and walking through it (or brushing
 * against it) sends a ripple across it. The look is in the glass_back shader.
 */
public final class ScreenBack
{
    /** How wide the edges and corner marks are, and how long the marks run along the edges, in blocks. */
    private static final float EDGE = 0.045F;
    private static final float MARK = 0.11F;
    private static final float MARK_LENGTH = 0.5F;
    /** How long a ripple lasts (ticks) and how fast its ring travels (blocks per tick). */
    private static final float RIPPLE_TICKS = 34F;
    private static final float RIPPLE_SPEED = 0.32F;
    /** Closer than this to the back (or the inside of a field) counts as touching it, and touching ripples this often. */
    private static final double TOUCH = 0.4D;
    private static final long TOUCH_REPEAT = 16L;

    private record Ripple(Vec3 point, long time) {}

    private static final Map<String, Ripple> RIPPLES = new HashMap<>();
    private static final Map<String, Vec3> LAST_BODY = new HashMap<>();
    /** Simulation barriers drawn lately (world boxes), so walking through them ripples them too. */
    private record Barrier(AABB box, long seenAt) {}
    private static final Map<String, Barrier> BARRIERS = new HashMap<>();
    /** How the vertices that follow look: a barrier (seen from far, always faintly there) and how far faded in. */
    private static boolean barrierStyle;
    private static float styleFade = 1F;

    private ScreenBack() {}

    // ---- Ripples: walking through or touching ----

    public static void tick(Minecraft mc)
    {
        if(mc.level == null || mc.player == null)
        {
            RIPPLES.clear();
            LAST_BODY.clear();
            return;
        }
        long now = mc.level.getGameTime();
        //The middle of the body: what goes through the screen.
        Vec3 body = mc.player.position().add(0D, mc.player.getBbHeight() * 0.55D, 0D);
        Set<String> active = new HashSet<>();
        for(GlassProjectorBlockEntity projector : GlassClientState.INSTANCE.loadedProjectors)
        {
            String key = projector.screenKey();
            if(!projector.active || projector.wirelessPos.isEmpty() || key.isEmpty() || !key.equals(projector.channel))
            {
                continue;
            }
            active.add(key);
            Vec3 last = LAST_BODY.put(key, body);
            AABB box = projector.cloakFieldBox();
            if(box != null)
            {
                boolean in = box.contains(body);
                Vec3 onWall = nearestOnBoxSurface(box, body);
                if(last != null && in != box.contains(last))
                {
                    ripple(key, onWall, now, true);
                }
                else if(in && onWall.distanceTo(body) < TOUCH)
                {
                    ripple(key, onWall, now, false);
                }
                continue;
            }
            Plane plane = Plane.of(projector);
            if(plane == null)
            {
                continue;
            }
            double side = plane.distance(body);
            if(!plane.covers(body))
            {
                continue;
            }
            if(last != null && Math.signum(side) != Math.signum(plane.distance(last)))
            {
                ripple(key, plane.project(body), now, true);
                GlassClientState.INSTANCE.addPunch(key, plane.project(body));
            }
            else if(side < 0D && -side < TOUCH)
            {
                ripple(key, plane.project(body), now, false);
            }
        }
        BARRIERS.values().removeIf(b -> now - b.seenAt() > 20L);
        for(Map.Entry<String, Barrier> e : BARRIERS.entrySet())
        {
            String key = e.getKey();
            AABB box = e.getValue().box();
            active.add(key);
            Vec3 last = LAST_BODY.put(key, body);
            Vec3 onWall = nearestOnBoxSurface(box, body);
            boolean in = box.contains(body);
            if(last != null && in != box.contains(last))
            {
                ripple(key, onWall, now, true);
            }
            else if(onWall.distanceTo(body) < TOUCH && Math.abs(body.y - Mth.clamp(body.y, box.minY, box.maxY)) < 1.0E-6D)
            {
                ripple(key, onWall, now, false);
            }
        }
        LAST_BODY.keySet().retainAll(active);
        RIPPLES.keySet().retainAll(active);
    }

    private static void ripple(String key, Vec3 point, long now, boolean through)
    {
        Ripple old = RIPPLES.get(key);
        if(!through && old != null && now - old.time() < TOUCH_REPEAT)
        {
            return;
        }
        RIPPLES.put(key, new Ripple(point, now));
        Minecraft mc = Minecraft.getInstance();
        if(mc.level != null)
        {
            mc.level.playLocalSound(point.x, point.y, point.z, ryzlar.glass.GlassSounds.RIPPLE, net.minecraft.sounds.SoundSource.BLOCKS,
                    through ? 0.7F : 0.35F, 0.9F + mc.level.random.nextFloat() * 0.2F, false);
        }
    }

    private static Vec3 nearestOnBoxSurface(AABB box, Vec3 p)
    {
        //Pushed out onto the nearest wall (or roof) if inside, clamped onto the box if outside. Not the floor: there is none.
        if(box.contains(p))
        {
            double[] gaps = { p.x - box.minX, box.maxX - p.x, p.z - box.minZ, box.maxZ - p.z, box.maxY - p.y };
            int nearest = 0;
            for(int i = 1; i < gaps.length; i++)
            {
                if(gaps[i] < gaps[nearest])
                {
                    nearest = i;
                }
            }
            return switch(nearest)
            {
                case 0 -> new Vec3(box.minX, p.y, p.z);
                case 1 -> new Vec3(box.maxX, p.y, p.z);
                case 2 -> new Vec3(p.x, p.y, box.minZ);
                case 3 -> new Vec3(p.x, p.y, box.maxZ);
                default -> new Vec3(p.x, box.maxY, p.z);
            };
        }
        return new Vec3(Mth.clamp(p.x, box.minX, box.maxX), Mth.clamp(p.y, box.minY, box.maxY), Mth.clamp(p.z, box.minZ, box.maxZ));
    }

    /** The ripple running on a screen, as the shader wants it: radius (1/64 blocks) and strength (0..1000). Null: none. */
    @Nullable
    private static Ripple running(String key, float partialTick, int[] ring)
    {
        Ripple ripple = RIPPLES.get(key);
        Minecraft mc = Minecraft.getInstance();
        if(ripple == null || mc.level == null)
        {
            return null;
        }
        float age = mc.level.getGameTime() - ripple.time() + partialTick;
        float strength = 1F - age / RIPPLE_TICKS;
        if(strength <= 0F || age < 0F)
        {
            return null;
        }
        ring[0] = Mth.clamp((int)(age * RIPPLE_SPEED * 64F), 0, 32767);
        ring[1] = Mth.clamp((int)(strength * strength * 1000F), 0, 1000);
        return ripple;
    }

    // ---- A floating screen ----

    /** A floating screen's plane: through its corners, facing the way the projector was placed. */
    private record Plane(Vec3 origin, Vec3 normal, Vec3 u, Vec3 v, List<float[]> hull)
    {
        @Nullable
        static Plane of(GlassProjectorBlockEntity projector)
        {
            List<Vector3f> points = GlassBlockEntityRenderer.wirelessPoints(projector);
            if(points.size() < 3)
            {
                return null;
            }
            Direction face = projector.placingFace;
            Vector3f n = new Vector3f(face.getStepX(), face.getStepY(), face.getStepZ());
            Vector3f u = GlassBlockEntityRenderer.wirelessU(face);
            Vector3f v = new Vector3f(n).cross(u);
            Vec3 centre = Vec3.atCenterOf(projector.getBlockPos());
            Vector3f first = points.getFirst();
            Vec3 origin = centre.add(n.x * first.dot(n), n.y * first.dot(n), n.z * first.dot(n));
            List<float[]> flat = new ArrayList<>();
            for(Vector3f p : points)
            {
                flat.add(new float[] { p.dot(u), p.dot(v) });
            }
            return new Plane(origin, new Vec3(n), new Vec3(u), new Vec3(v), ScreenBack.hull(flat));
        }

        /** In front (+) or behind (-), in blocks. */
        double distance(Vec3 p)
        {
            return p.subtract(origin).dot(normal);
        }

        Vec3 project(Vec3 p)
        {
            return p.subtract(normal.scale(distance(p)));
        }

        /** True if the point lies over the screen (with a little margin), seen square on. */
        boolean covers(Vec3 p)
        {
            Vec3 d = p.subtract(origin);
            //The hull's coordinates are from the projector's centre, which lies on the plane's normal through origin.
            float a = (float)d.dot(u), b = (float)d.dot(v);
            return inside(hull, a, b, 0.3F);
        }
    }

    /**
     * Draws the back of a floating screen. {@code points}: its corners from the projector's centre (where the pose is),
     * as the front is drawn.
     */
    public static void drawWireless(String key, GlassProjectorBlockEntity projector, List<Vector3f> points, PoseStack.Pose pose,
                                    MultiBufferSource buffers, float partialTick)
    {
        if(points.size() < 3)
        {
            return;
        }
        Direction face = projector.placingFace;
        Vector3f n = new Vector3f(face.getStepX(), face.getStepY(), face.getStepZ());
        Vector3f u = GlassBlockEntityRenderer.wirelessU(face);
        Vector3f v = new Vector3f(n).cross(u);
        int[] ring = { 0, 0 };
        int[] hit = { 32767, 32767 };
        Ripple ripple = running(key, partialTick, ring);
        if(ripple != null)
        {
            Vec3 p = ripple.point().subtract(Vec3.atCenterOf(projector.getBlockPos()));
            Vector3f local = new Vector3f((float)p.x, (float)p.y, (float)p.z);
            hit = new int[] { toShort(local.dot(u) * 32F), toShort(local.dot(v) * 32F) };
        }
        VertexConsumer consumer = buffers.getBuffer(GlassRenderTypes.BACK);
        //The surface, made of the same triangles as the front.
        for(int i = 0; i + 2 < points.size(); i++)
        {
            Vector3f[] tri = { points.get(i), points.get(i + 1), points.get(i + 2), points.get(i + 2) };
            for(Vector3f p : tri)
            {
                vertex(consumer, pose, p, p.dot(u), p.dot(v), false, hit, ring);
            }
        }
        //The outline: the hull of the corners, back in 3D on the plane.
        List<float[]> flat = new ArrayList<>();
        for(Vector3f p : points)
        {
            flat.add(new float[] { p.dot(u), p.dot(v) });
        }
        List<float[]> hull = hull(flat);
        float depth = points.getFirst().dot(n);
        List<Vector3f> outline = new ArrayList<>();
        for(float[] h : hull)
        {
            outline.add(new Vector3f(u).mul(h[0]).add(new Vector3f(v).mul(h[1])).add(new Vector3f(n).mul(depth)));
        }
        drawOutline(consumer, pose, outline, n, u, v, hit, ring);
    }

    // ---- A cloak field ----

    /**
     * Draws the inside of one side of a cloak field: a rectangle from {@code origin} along {@code along} and {@code up}
     * (the roof: along x and z), {@code width} by {@code height} blocks. {@code surfaceStart}: where it starts in the
     * field's surface coordinates (the walls run on round it). {@code centre}: the projector's centre in the world.
     */
    public static void drawFieldSide(String key, Vector3f origin, Vector3f along, Vector3f up, float width, float height, float surfaceStart,
                                     Vec3 centre, PoseStack.Pose pose, MultiBufferSource buffers, float partialTick)
    {
        if(width <= 0F || height <= 0F)
        {
            return;
        }
        Vector3f n = new Vector3f(along).cross(up);
        int[] ring = { 0, 0 };
        int[] hit = { 32767, 32767 };
        Ripple ripple = running(key, partialTick, ring);
        if(ripple != null)
        {
            Vec3 p = ripple.point().subtract(centre);
            Vector3f local = new Vector3f((float)p.x, (float)p.y, (float)p.z).sub(origin);
            //Only on the side it happened on (or right next to it).
            if(Math.abs(local.dot(n)) < 0.75F)
            {
                hit = new int[] { toShort((surfaceStart + local.dot(along)) * 32F), toShort(local.dot(up) * 32F) };
            }
            else
            {
                ring = new int[] { 0, 0 };
            }
        }
        VertexConsumer consumer = buffers.getBuffer(GlassRenderTypes.BACK);
        Vector3f[] corners = {
                new Vector3f(origin),
                new Vector3f(along).mul(width).add(origin),
                new Vector3f(along).mul(width).add(new Vector3f(up).mul(height)).add(origin),
                new Vector3f(up).mul(height).add(origin),
        };
        float[][] surface = { { surfaceStart, 0F }, { surfaceStart + width, 0F }, { surfaceStart + width, height }, { surfaceStart, height } };
        for(int i = 0; i < 4; i++)
        {
            vertex(consumer, pose, corners[i], surface[i][0], surface[i][1], false, hit, ring);
        }
        drawOutline(consumer, pose, List.of(corners), n, along, up, hit, ring, surfaceStart);
    }

    // ---- A simulation's barrier ----

    /**
     * Draws a holo cube simulation's barrier: walls and a roof round the box from {@code min} to {@code max} (from the
     * anchor block's corner, where the pose is), always faintly there, glowing at the edges.
     */
    public static void drawBarrier(String key, Vector3f min, Vector3f max, BlockPos anchor, PoseStack.Pose pose, MultiBufferSource buffers,
                                   float partialTick, float fade)
    {
        Minecraft mc = Minecraft.getInstance();
        if(mc.level != null)
        {
            BARRIERS.put(key, new Barrier(new AABB(min.x, min.y, min.z, max.x, max.y, max.z).move(anchor.getX(), anchor.getY(), anchor.getZ()), mc.level.getGameTime()));
        }
        Vec3 centre = Vec3.atLowerCornerOf(anchor);
        float w = max.x - min.x, d = max.z - min.z, h = max.y - min.y;
        barrierStyle = true;
        styleFade = fade;
        try
        {
            drawFieldSide(key, new Vector3f(min.x, min.y, min.z), new Vector3f(1F, 0F, 0F), new Vector3f(0F, 1F, 0F), w, h, 0F, centre, pose, buffers, partialTick);
            drawFieldSide(key, new Vector3f(min.x, min.y, max.z), new Vector3f(1F, 0F, 0F), new Vector3f(0F, 1F, 0F), w, h, d, centre, pose, buffers, partialTick);
            drawFieldSide(key, new Vector3f(min.x, min.y, min.z), new Vector3f(0F, 0F, 1F), new Vector3f(0F, 1F, 0F), d, h, 0F, centre, pose, buffers, partialTick);
            drawFieldSide(key, new Vector3f(max.x, min.y, min.z), new Vector3f(0F, 0F, 1F), new Vector3f(0F, 1F, 0F), d, h, w, centre, pose, buffers, partialTick);
            drawFieldSide(key, new Vector3f(min.x, max.y, min.z), new Vector3f(1F, 0F, 0F), new Vector3f(0F, 0F, 1F), w, d, 0F, centre, pose, buffers, partialTick);
        }
        finally
        {
            barrierStyle = false;
            styleFade = 1F;
        }
    }

    // ---- Drawing ----

    private static void drawOutline(VertexConsumer consumer, PoseStack.Pose pose, List<Vector3f> outline, Vector3f n, Vector3f u, Vector3f v,
                                    int[] hit, int[] ring)
    {
        drawOutline(consumer, pose, outline, n, u, v, hit, ring, Float.NaN);
    }

    /** Glowing edges round a flat outline, and a thicker mark along both edges at each corner. */
    private static void drawOutline(VertexConsumer consumer, PoseStack.Pose pose, List<Vector3f> outline, Vector3f n, Vector3f u, Vector3f v,
                                    int[] hit, int[] ring, float surfaceStart)
    {
        int count = outline.size();
        Vector3f origin = outline.getFirst();
        for(int i = 0; i < count; i++)
        {
            Vector3f a = outline.get(i);
            Vector3f b = outline.get((i + 1) % count);
            float length = a.distance(b);
            if(length < 1.0E-4F)
            {
                continue;
            }
            line(consumer, pose, a, b, n, EDGE, u, v, hit, ring, surfaceStart, origin);
            float mark = Math.min(MARK_LENGTH, length * 0.3F);
            Vector3f dir = new Vector3f(b).sub(a).div(length);
            line(consumer, pose, a, new Vector3f(dir).mul(mark).add(a), n, MARK, u, v, hit, ring, surfaceStart, origin);
            line(consumer, pose, new Vector3f(dir).mul(-mark).add(b), b, n, MARK, u, v, hit, ring, surfaceStart, origin);
        }
    }

    /** A strip from a to b in the plane with normal n, this wide. */
    private static void line(VertexConsumer consumer, PoseStack.Pose pose, Vector3f a, Vector3f b, Vector3f n, float width, Vector3f u, Vector3f v,
                             int[] hit, int[] ring, float surfaceStart, Vector3f origin)
    {
        Vector3f side = new Vector3f(b).sub(a).cross(n);
        if(side.lengthSquared() < 1.0E-10F)
        {
            return;
        }
        side.normalize().mul(width / 2F);
        Vector3f[] corners = { new Vector3f(a).sub(side), new Vector3f(b).sub(side), new Vector3f(b).add(side), new Vector3f(a).add(side) };
        for(Vector3f c : corners)
        {
            float su, sv;
            if(Float.isNaN(surfaceStart))
            {
                su = c.dot(u);
                sv = c.dot(v);
            }
            else
            {
                Vector3f local = new Vector3f(c).sub(origin);
                su = surfaceStart + local.dot(u);
                sv = local.dot(v);
            }
            vertex(consumer, pose, c, su, sv, true, hit, ring);
        }
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Vector3f p, float su, float sv, boolean edge, int[] hit, int[] ring)
    {
        consumer.addVertex(pose, p.x, p.y, p.z)
                .setColor(edge ? 255 : 0, 0, barrierStyle ? 255 : 0, Mth.clamp(Math.round(styleFade * 255F), 0, 255))
                .setUv(su, sv)
                .setUv1(hit[0], hit[1])
                .setUv2(ring[0], ring[1])
                .setNormal(pose, 0F, 1F, 0F);
    }

    private static int toShort(float value)
    {
        return Mth.clamp(Math.round(value), -32767, 32767);
    }

    // ---- Flat geometry ----

    /** The convex hull of flat points, round in order (monotone chain). */
    private static List<float[]> hull(List<float[]> points)
    {
        List<float[]> sorted = new ArrayList<>(points);
        sorted.sort((p, q) -> p[0] != q[0] ? Float.compare(p[0], q[0]) : Float.compare(p[1], q[1]));
        if(sorted.size() < 3)
        {
            return sorted;
        }
        float[][] h = new float[sorted.size() * 2][];
        int k = 0;
        for(float[] p : sorted)
        {
            while(k >= 2 && cross(h[k - 2], h[k - 1], p) <= 0F)
            {
                k--;
            }
            h[k++] = p;
        }
        for(int i = sorted.size() - 2, lower = k + 1; i >= 0; i--)
        {
            float[] p = sorted.get(i);
            while(k >= lower && cross(h[k - 2], h[k - 1], p) <= 0F)
            {
                k--;
            }
            h[k++] = p;
        }
        List<float[]> result = new ArrayList<>();
        for(int i = 0; i < k - 1; i++)
        {
            result.add(h[i]);
        }
        return result;
    }

    private static float cross(float[] o, float[] a, float[] b)
    {
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
    }

    /** True if (x, y) is inside the hull (counter-clockwise), or within the margin of it. */
    private static boolean inside(List<float[]> hull, float x, float y, float margin)
    {
        if(hull.size() < 3)
        {
            return false;
        }
        for(int i = 0; i < hull.size(); i++)
        {
            float[] a = hull.get(i);
            float[] b = hull.get((i + 1) % hull.size());
            float ex = b[0] - a[0], ey = b[1] - a[1];
            float length = (float)Math.sqrt(ex * ex + ey * ey);
            if(length < 1.0E-6F)
            {
                continue;
            }
            //Distance to the left of the edge (inside for a counter-clockwise hull).
            float left = (ex * (y - a[1]) - ey * (x - a[0])) / length;
            if(left < -margin)
            {
                return false;
            }
        }
        return true;
    }
}
