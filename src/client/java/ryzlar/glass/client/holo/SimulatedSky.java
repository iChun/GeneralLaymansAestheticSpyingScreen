package ryzlar.glass.client.holo;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.holo.CubeScan;

import java.util.HashMap;
import java.util.Map;

/**
 * The sky inside a holo cube's simulation: a scan keeps the time of day and weather it was made in, and standing inside
 * a running simulation the whole sky is as it was then (sun, moon, stars and colour where they were, rain if it
 * rained), and the world is lit as brightly as it was (dark for a scan made at night), the real world seen through the
 * bubble's wall too: a bubble of another time. It changes over as you walk in and as the bubble grows. Only this client
 * sees the change; the world's real time and weather go on as they were.
 */
public final class SimulatedSky
{
    /** Over how many blocks inside the bubble the scanned sky takes over (blocks). */
    private static final float FADE_DEPTH = 1.5F;
    /** A cube not drawn for this long (ticks) is gone, or too far to be standing in. */
    private static final long STALE_TICKS = 10L;

    /** A running simulation as its renderer last drew it: its bubble (centre and radius), how far grown, and its scan. */
    private record Room(Vec3 centre, float radius, float built, CubeScan scan, long seenAt) {}

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
    public static void offer(int entityId, Vec3 centre, float radius, float built, CubeScan scan, long gameTime)
    {
        if(scan.hasSky())
        {
            ROOMS.put(entityId, new Room(centre, radius, built, scan, gameTime));
        }
    }

    /** How bright daylight was when this scan was made (0.2 at night .. 1 at noon), as ClientLevel.getSkyDarken works it out. */
    public static float scannedDaylight(CubeScan scan)
    {
        Minecraft mc = Minecraft.getInstance();
        if(!scan.hasSky() || mc.level == null)
        {
            return 1F;
        }
        float timeOfDay = mc.level.dimensionType().timeOfDay(scan.skyTime());
        float light = 1F - Math.clamp(1F - ((float)Math.cos(timeOfDay * Math.PI * 2D) * 2F + 0.2F), 0F, 1F);
        light *= 1F - scan.rain() * 5F / 16F;
        light *= 1F - scan.thunder() * 5F / 16F;
        return light * 0.8F + 0.2F;
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
            double inside = room.radius - eye.distanceTo(room.centre);
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
        return real + (scannedDaylight(sky) - real) * fade;
    }

    /**
     * The time of day the sky shows (0..1, as DimensionType.timeOfDay gives it): the scanned one inside a simulation,
     * turning over from the real one the short way round as you walk in.
     */
    public static float timeOfDay(float real)
    {
        Minecraft mc = Minecraft.getInstance();
        if(!active() || mc.level == null)
        {
            return real;
        }
        float scanned = mc.level.dimensionType().timeOfDay(sky.skyTime());
        float delta = scanned - real;
        delta -= (float)Math.floor(delta + 0.5F);
        float time = real + delta * fade;
        return time - (float)Math.floor(time);
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
