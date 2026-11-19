package ryzlar.glass.client.holo;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import ryzlar.glass.holo.CubeScan;

import java.util.HashMap;
import java.util.Map;

/**
 * The light and weather inside a holo cube's simulation. The sky and land round it are the scan's panorama (the dome,
 * see {@link HoloCubeRenderer}); this makes the rest match it: a scan keeps the time of day and weather it was made in,
 * and standing inside a running simulation the world is lit as brightly as it was then (dark for a scan made at night)
 * and rains as it did. It changes over as you walk in and as the nano wave builds the room. The real sky is left alone
 * (the dome hides it), and only this client sees the change; the world's real time and weather go on as they were.
 */
public final class SimulatedSky
{
    /** Over how many blocks inside the room the scanned sky takes over (blocks). */
    private static final float FADE_DEPTH = 1.5F;
    /** A cube not drawn for this long (ticks) is gone, or too far to be standing in. */
    private static final long STALE_TICKS = 10L;

    /** A running simulation as its renderer last drew it: its box (from its anchor), how far built, and its scan. */
    private record Room(BlockPos anchor, Vector3f min, Vector3f max, float built, CubeScan scan, long seenAt) {}

    private static final Map<Integer, Room> ROOMS = new HashMap<>();
    @Nullable
    private static CubeScan sky;
    private static float fade;

    private SimulatedSky() {}

    public static void init()
    {
        ClientTickEvents.END_CLIENT_TICK.register(SimulatedSky::tick);
    }

    public static void reset()
    {
        ROOMS.clear();
        sky = null;
        fade = 0F;
    }

    /** Called by the cube's renderer each frame it draws a running simulation that has a scanned sky. */
    public static void offer(int entityId, BlockPos anchor, Vector3f min, Vector3f max, float built, CubeScan scan, long gameTime)
    {
        if(scan.hasSky())
        {
            ROOMS.put(entityId, new Room(anchor, new Vector3f(min), new Vector3f(max), built, scan, gameTime));
        }
    }

    /** Picks the simulation the camera stands deepest in (if any) and how far its sky has taken over. */
    private static void tick(Minecraft mc)
    {
        sky = null;
        fade = 0F;
        if(mc.level == null)
        {
            ROOMS.clear();
            return;
        }
        long now = mc.level.getGameTime();
        ROOMS.values().removeIf(room -> now - room.seenAt > STALE_TICKS || room.seenAt > now);
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        for(Room room : ROOMS.values())
        {
            double x = eye.x - room.anchor.getX(), y = eye.y - room.anchor.getY(), z = eye.z - room.anchor.getZ();
            double inside = Math.min(Math.min(Math.min(x - room.min.x, room.max.x - x), Math.min(z - room.min.z, room.max.z - z)),
                    Math.min(y - room.min.y, room.max.y - y));
            float f = Math.clamp((float)inside / FADE_DEPTH, 0F, 1F) * Math.clamp(room.built, 0F, 1F);
            if(f > fade)
            {
                fade = f;
                sky = room.scan;
            }
        }
    }

    /** True if this is the client's own thread, so the integrated server's world is never touched. */
    private static boolean active()
    {
        return sky != null && fade > 0F && Minecraft.getInstance().isSameThread();
    }

    /** How bright daylight is (as ClientLevel.getSkyDarken works it out), at the scanned time and weather. */
    public static float skyDarken(float real)
    {
        Minecraft mc = Minecraft.getInstance();
        if(!active() || mc.level == null)
        {
            return real;
        }
        float timeOfDay = mc.level.dimensionType().timeOfDay(sky.skyTime());
        float light = 1F - Math.clamp(1F - ((float)Math.cos(timeOfDay * Math.PI * 2D) * 2F + 0.2F), 0F, 1F);
        light *= 1F - sky.rain() * 5F / 16F;
        light *= 1F - sky.thunder() * 5F / 16F;
        float scanned = light * 0.8F + 0.2F;
        return real + (scanned - real) * fade;
    }

    public static float rain(float real)
    {
        return active() ? real + (sky.rain() - real) * fade : real;
    }

    public static float thunder(float real)
    {
        return active() ? real + (sky.thunder() - real) * fade : real;
    }
}
