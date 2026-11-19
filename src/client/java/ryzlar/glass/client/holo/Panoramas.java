package ryzlar.glass.client.holo;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.Glass;
import ryzlar.glass.holo.CubeScan;
import ryzlar.glass.holo.HoloCubeEntity;
import ryzlar.glass.holo.HoloNetworking;
import ryzlar.glass.holo.PanoramaStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * The 360 degree panoramas of holo cube scans, on the client. When a cube this client watched finishes a scan, the world
 * is drawn six times from the cube (front, right, back, left, up, down, like the game's own panorama screenshots) into
 * one picture, three views across and two down, kept in a cache folder and sent to the server for everyone else. A
 * cube projecting that scan shows it as a dome round the room (see {@link HoloCubeRenderer}).
 */
public final class Panoramas
{
    /** Each view is drawn this big and kept this big (drawing it bigger and shrinking it smooths it). */
    private static final int RENDER = 512;
    public static final int FACE = 256;
    private static final long RETRY_MILLIS = 10_000L;

    private static boolean capturing;
    @Nullable
    private static HoloCubeEntity pending;
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();
    private static final Map<String, Long> REQUESTED = new HashMap<>();
    private static final Map<String, byte[][]> DOWNLOADS = new HashMap<>();

    private Panoramas() {}

    public static void init()
    {
        HoloCubeEntity.onClientScanDone = cube -> pending = cube;
        ClientTickEvents.END_CLIENT_TICK.register(Panoramas::tick);
        ClientPlayNetworking.registerGlobalReceiver(HoloNetworking.PanoramaDownload.TYPE, (payload, context) -> receive(payload));
    }

    public static void reset()
    {
        Minecraft mc = Minecraft.getInstance();
        for(ResourceLocation texture : TEXTURES.values())
        {
            mc.getTextureManager().release(texture);
        }
        TEXTURES.clear();
        REQUESTED.clear();
        DOWNLOADS.clear();
        pending = null;
    }

    /** True while the panorama is being drawn (the cube and its effects stay out of it). */
    public static boolean capturing()
    {
        return capturing;
    }

    /** A scan's panorama, or null while there is none (asked for now and then). */
    @Nullable
    public static ResourceLocation texture(String id)
    {
        ResourceLocation texture = TEXTURES.get(id);
        if(texture != null || !PanoramaStore.isValidId(id))
        {
            return texture;
        }
        Path cached = cacheFile(id);
        if(Files.isRegularFile(cached))
        {
            try
            {
                return register(id, NativeImage.read(Files.readAllBytes(cached)));
            }
            catch(IOException e)
            {
                Glass.LOGGER.warn("Could not read the cached panorama {}", id, e);
            }
        }
        long now = Util.getMillis();
        Long last = REQUESTED.get(id);
        if(last == null || now - last > RETRY_MILLIS)
        {
            REQUESTED.put(id, now);
            ClientPlayNetworking.send(new HoloNetworking.RequestPanorama(id));
        }
        return null;
    }

    private static Path cacheFile(String id)
    {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("glass_panoramas").resolve(id + ".png");
    }

    private static ResourceLocation register(String id, NativeImage image)
    {
        ResourceLocation location = Glass.id("panorama/" + id);
        DynamicTexture texture = new DynamicTexture(() -> "GLASS panorama " + id, image);
        Minecraft.getInstance().getTextureManager().register(location, texture);
        TEXTURES.put(id, location);
        return location;
    }

    private static void receive(HoloNetworking.PanoramaDownload part)
    {
        String id = part.id();
        if(!PanoramaStore.isValidId(id) || TEXTURES.containsKey(id))
        {
            return;
        }
        if(part.total() <= 0 || part.index() < 0 || part.index() >= part.total())
        {
            return; //none yet: asked again later
        }
        byte[][] parts = DOWNLOADS.computeIfAbsent(id, k -> new byte[part.total()][]);
        if(parts.length != part.total())
        {
            DOWNLOADS.remove(id);
            return;
        }
        parts[part.index()] = part.data();
        int size = 0;
        for(byte[] bytes : parts)
        {
            if(bytes == null)
            {
                return;
            }
            size += bytes.length;
        }
        DOWNLOADS.remove(id);
        byte[] all = new byte[size];
        int at = 0;
        for(byte[] bytes : parts)
        {
            System.arraycopy(bytes, 0, all, at, bytes.length);
            at += bytes.length;
        }
        try
        {
            Path cached = cacheFile(id);
            Files.createDirectories(cached.getParent());
            Files.write(cached, all);
            register(id, NativeImage.read(all));
        }
        catch(IOException e)
        {
            Glass.LOGGER.warn("Could not read the panorama {}", id, e);
        }
    }

    // ---- Taking one ----

    private static void tick(Minecraft mc)
    {
        HoloCubeEntity cube = pending;
        pending = null;
        if(cube == null || mc.level == null || mc.player == null || cube.isRemoved() || cube.level() != mc.level
                || cube.distanceToSqr(mc.player) > 64D * 64D)
        {
            return;
        }
        CubeScan scan = cube.scan();
        if(scan != null && !TEXTURES.containsKey(scan.id()))
        {
            capture(mc, cube, scan.id());
        }
    }

    /** Draws the six views from the cube (as Minecraft.grabPanoramixScreenshot does from the player). */
    private static void capture(Minecraft mc, HoloCubeEntity cube, String id)
    {
        Window window = mc.getWindow();
        RenderTarget target = mc.getMainRenderTarget();
        int width = window.getWidth(), height = window.getHeight();
        Entity camera = mc.getCameraEntity();
        float yRot = cube.getYRot(), xRot = cube.getXRot(), yRotO = cube.yRotO, xRotO = cube.xRotO;
        boolean invisible = mc.player.isInvisible();
        NativeImage atlas = new NativeImage(FACE * 3, FACE * 2, false);
        int[] done = { 0 };
        capturing = true;
        try
        {
            mc.gameRenderer.setRenderBlockOutline(false);
            mc.gameRenderer.setPanoramicMode(true);
            window.setWidth(RENDER);
            window.setHeight(RENDER);
            target.resize(RENDER, RENDER);
            mc.setCameraEntity(cube);
            mc.player.setInvisible(true);
            for(int face = 0; face < 6; face++)
            {
                //Front (south), right (west), back, left, up, down: the views the dome shader looks up.
                float yaw = switch(face)
                {
                    case 1 -> 90F;
                    case 2 -> 180F;
                    case 3 -> -90F;
                    default -> 0F;
                };
                float pitch = face == 4 ? -90F : face == 5 ? 90F : 0F;
                cube.setYRot(yaw);
                cube.setXRot(pitch);
                cube.yRotO = yaw;
                cube.xRotO = pitch;
                mc.gameRenderer.renderLevel(DeltaTracker.ONE);
                int cellX = face % 3 * FACE, cellY = face / 3 * FACE;
                Screenshot.takeScreenshot(target, RENDER / FACE, image -> {
                    try
                    {
                        int w = Math.min(FACE, image.getWidth()), h = Math.min(FACE, image.getHeight());
                        for(int y = 0; y < h; y++)
                        {
                            for(int x = 0; x < w; x++)
                            {
                                atlas.setPixel(cellX + x, cellY + y, image.getPixel(x, y) | 0xFF000000);
                            }
                        }
                    }
                    finally
                    {
                        image.close();
                    }
                    if(++done[0] == 6)
                    {
                        finish(id, atlas);
                    }
                });
            }
        }
        finally
        {
            cube.setYRot(yRot);
            cube.setXRot(xRot);
            cube.yRotO = yRotO;
            cube.xRotO = xRotO;
            mc.setCameraEntity(camera);
            mc.player.setInvisible(invisible);
            mc.gameRenderer.setRenderBlockOutline(true);
            window.setWidth(width);
            window.setHeight(height);
            target.resize(width, height);
            mc.gameRenderer.setPanoramicMode(false);
            capturing = false;
        }
    }

    /** All six views are in: keep it, show it, and send it to the server. */
    private static void finish(String id, NativeImage atlas)
    {
        byte[] bytes;
        try
        {
            Path cached = cacheFile(id);
            Files.createDirectories(cached.getParent());
            atlas.writeToFile(cached);
            bytes = Files.readAllBytes(cached);
        }
        catch(IOException e)
        {
            Glass.LOGGER.warn("Could not keep the panorama {}", id, e);
            atlas.close();
            return;
        }
        register(id, atlas);
        if(bytes.length > PanoramaStore.MAX_BYTES)
        {
            return;
        }
        int total = Math.max(1, (bytes.length + PanoramaStore.UPLOAD_PART - 1) / PanoramaStore.UPLOAD_PART);
        for(int i = 0; i < total; i++)
        {
            int from = i * PanoramaStore.UPLOAD_PART;
            int to = Math.min(bytes.length, from + PanoramaStore.UPLOAD_PART);
            byte[] part = new byte[to - from];
            System.arraycopy(bytes, from, part, 0, part.length);
            ClientPlayNetworking.send(new HoloNetworking.PanoramaUpload(id, i, total, part));
        }
    }
}
