package ryzlar.glass.client.holo;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.Glass;
import ryzlar.glass.holo.CubeScan;
import ryzlar.glass.holo.HoloCubeEntity;
import ryzlar.glass.holo.HoloNetworking;
import ryzlar.glass.holo.PanoramaStore;
import ryzlar.glass.client.render.SpyViewRenderer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The 360 degree panoramas of holo cube scans, on the client. When a cube this client watched finishes a scan, the world
 * is drawn six times from the cube (front, right, back, left, up, down, like the game's own panorama screenshots) into
 * one picture, three views across and two down, kept in a cache folder and sent to the server for everyone else. A
 * cube projecting that scan shows it as a dome round the room (see {@link HoloCubeRenderer}).
 * <p>
 * None of it may hold up a frame: the views are drawn one per tick into a target of their own (not the window's, which
 * would have to be resized), and read back with a plain copy; shrinking them, putting them together, writing the PNG and
 * reading panoramas back in all happen on background threads. Only handing the finished picture to the GPU is left
 * for the render thread.
 */
public final class Panoramas
{
    /** Each view is drawn this big and kept this big (drawing it bigger and shrinking it smooths it). */
    private static final int RENDER = 2048;
    public static final int FACE = 1024;
    private static final long RETRY_MILLIS = 10_000L;

    private static boolean capturing;
    @Nullable
    private static HoloCubeEntity pending;
    @Nullable
    private static Capture capture;
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();
    /** Panoramas being read in (from the cache or the server) on a background thread. */
    private static final Set<String> LOADING = new HashSet<>();
    private static final Map<String, Long> REQUESTED = new HashMap<>();
    private static final Map<String, byte[][]> DOWNLOADS = new HashMap<>();

    private Panoramas() {}

    public static void init()
    {
        //Not taken any more: the simulation's dome is a black void now, so nothing shows them. Kept to read old ones.
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
        LOADING.clear();
        pending = null;
        if(capture != null)
        {
            capture.abort();
            capture = null;
        }
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
        if(texture != null || !PanoramaStore.isValidId(id) || LOADING.contains(id))
        {
            return texture;
        }
        Path cached = cacheFile(id);
        if(Files.isRegularFile(cached))
        {
            LOADING.add(id);
            Util.ioPool().execute(() -> {
                try
                {
                    decodeAndRegister(id, Files.readAllBytes(cached));
                }
                catch(IOException e)
                {
                    Glass.LOGGER.warn("Could not read the cached panorama {}", id, e);
                    Minecraft.getInstance().execute(() -> LOADING.remove(id));
                }
            });
            return null;
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

    /** Background thread: decodes a panorama, then hands it to the render thread. */
    private static void decodeAndRegister(String id, byte[] png)
    {
        try
        {
            NativeImage image = NativeImage.read(png);
            Minecraft.getInstance().execute(() -> {
                LOADING.remove(id);
                if(TEXTURES.containsKey(id))
                {
                    image.close();
                }
                else
                {
                    register(id, image);
                }
            });
        }
        catch(IOException e)
        {
            Glass.LOGGER.warn("Could not read the panorama {}", id, e);
            Minecraft.getInstance().execute(() -> LOADING.remove(id));
        }
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
        LOADING.add(id);
        Util.ioPool().execute(() -> {
            try
            {
                Path cached = cacheFile(id);
                Files.createDirectories(cached.getParent());
                Files.write(cached, all);
            }
            catch(IOException e)
            {
                Glass.LOGGER.warn("Could not keep the panorama {}", id, e);
            }
            decodeAndRegister(id, all);
        });
    }

    // ---- Taking one ----

    /** A panorama being taken: one view drawn per tick; each read back, shrunk and put in place in the background. */
    private static final class Capture
    {
        final HoloCubeEntity cube;
        final String id;
        final TextureTarget target = new TextureTarget("GLASS panorama", RENDER, RENDER, true);
        final NativeImage atlas = new NativeImage(FACE * 3, FACE * 2, false);
        int face;
        /** Views read back and put in the atlas (on background threads). */
        final AtomicInteger placed = new AtomicInteger();
        boolean aborted;

        Capture(HoloCubeEntity cube, String id)
        {
            this.cube = cube;
            this.id = id;
        }

        /** Stops: frees the target now, and the atlas once no view still being put in it needs it. */
        void abort()
        {
            if(!aborted)
            {
                aborted = true;
                target.destroyBuffers();
                if(placed.get() >= face)
                {
                    atlas.close();
                }
            }
        }
    }

    private static void tick(Minecraft mc)
    {
        if(capture != null)
        {
            step(mc, capture);
            return;
        }
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
            capture = new Capture(cube, scan.id());
        }
    }

    /** Draws the next view, or stops if the cube went away. */
    private static void step(Minecraft mc, Capture c)
    {
        if(c.cube.isRemoved() || mc.level == null || c.cube.level() != mc.level || mc.player == null)
        {
            c.abort();
            capture = null;
            return;
        }
        drawFace(mc, c, c.face);
        c.face++;
        if(c.face == 6)
        {
            capture = null; //the last views are still on their way back; the target is freed when they are in
        }
    }

    /** Draws one of the six views from the cube into the capture's own target (as grabPanoramixScreenshot does). */
    private static void drawFace(Minecraft mc, Capture c, int face)
    {
        Window window = mc.getWindow();
        int width = window.getWidth(), height = window.getHeight();
        HoloCubeEntity cube = c.cube;
        Entity camera = mc.getCameraEntity();
        float yRot = cube.getYRot(), xRot = cube.getXRot(), yRotO = cube.yRotO, xRotO = cube.xRotO;
        boolean invisible = mc.player.isInvisible();
        capturing = true;
        try
        {
            mc.gameRenderer.setRenderBlockOutline(false);
            mc.gameRenderer.setPanoramicMode(true);
            //Square, for the projection; the window itself is not resized, the world draws into the capture's target.
            window.setWidth(RENDER);
            window.setHeight(RENDER);
            SpyViewRenderer.INSTANCE.beginCapture(c.target);
            mc.setCameraEntity(cube);
            mc.player.setInvisible(true);
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
            readBack(c, face, face == 5);
        }
        finally
        {
            SpyViewRenderer.INSTANCE.endCapture();
            cube.setYRot(yRot);
            cube.setXRot(xRot);
            cube.yRotO = yRotO;
            cube.xRotO = xRotO;
            mc.setCameraEntity(camera);
            mc.player.setInvisible(invisible);
            mc.gameRenderer.setRenderBlockOutline(true);
            window.setWidth(width);
            window.setHeight(height);
            mc.gameRenderer.setPanoramicMode(false);
            capturing = false;
        }
    }

    /**
     * Copies a view off the GPU (when it is ready, a plain bulk copy on the render thread), then shrinks it and puts it in
     * its place in the atlas on a background thread. After the last view, the atlas is written out and sent.
     */
    private static void readBack(Capture c, int face, boolean last)
    {
        GpuTexture texture = c.target.getColorTexture();
        if(texture == null)
        {
            c.abort();
            return;
        }
        int width = c.target.width, height = c.target.height;
        int pixelSize = texture.getFormat().pixelSize();
        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "GLASS panorama view", GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, width * height * pixelSize);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToBuffer(texture, buffer, 0, () -> {
            int[] pixels = new int[width * height];
            try(GpuBuffer.MappedView view = encoder.mapBuffer(buffer, true, false))
            {
                view.data().asIntBuffer().get(pixels);
            }
            buffer.close();
            if(last)
            {
                c.target.destroyBuffers();
            }
            Util.backgroundExecutor().execute(() -> place(c, face, pixels, width, height));
        }, 0);
    }

    /** Background thread: a view, shrunk (averaging each square of pixels) and turned the right way up, into its cell. */
    private static void place(Capture c, int face, int[] pixels, int width, int height)
    {
        if(c.aborted)
        {
            if(c.placed.incrementAndGet() >= c.face)
            {
                c.atlas.close();
            }
            return;
        }
        int scale = width / FACE;
        int cellX = face % 3 * FACE, cellY = face / 3 * FACE;
        int samples = scale * scale;
        for(int y = 0; y < FACE; y++)
        {
            for(int x = 0; x < FACE; x++)
            {
                //Each byte lane (red, green, blue) averaged on its own; the GPU's rows run bottom up.
                int r = 0, g = 0, b = 0;
                for(int sy = 0; sy < scale; sy++)
                {
                    int row = (height - 1 - (y * scale + sy)) * width;
                    for(int sx = 0; sx < scale; sx++)
                    {
                        int p = pixels[row + x * scale + sx];
                        r += p & 0xFF;
                        g += (p >> 8) & 0xFF;
                        b += (p >> 16) & 0xFF;
                    }
                }
                c.atlas.setPixelABGR(cellX + x, cellY + y, 0xFF000000 | (b / samples) << 16 | (g / samples) << 8 | (r / samples));
            }
        }
        if(c.placed.incrementAndGet() == 6)
        {
            finish(c.id, c.atlas);
        }
    }

    /** Background thread: all six views are in: keep the picture, then show it and send it to the server. */
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
        Minecraft.getInstance().execute(() -> {
            if(TEXTURES.containsKey(id))
            {
                atlas.close();
            }
            else
            {
                register(id, atlas);
            }
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
        });
    }
}
