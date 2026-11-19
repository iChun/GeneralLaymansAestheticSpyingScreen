package ryzlar.glass.holo;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.Glass;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The 360 degree panoramas holo cubes take when they finish a room scan: the six views from the cube, side by side in one
 * PNG (three across, two down). The server cannot draw, so the client that saw the scan finish sends it in, in parts;
 * it is kept in the world's folder next to the scan and sent, in parts, to whoever projects the room.
 */
public final class PanoramaStore
{
    /** The biggest panorama accepted, and the size of the parts it travels in each way (bytes). */
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    public static final int UPLOAD_PART = 30000;
    public static final int DOWNLOAD_PART = 512 * 1024;
    private static final byte[] PNG = { (byte)0x89, 'P', 'N', 'G' };

    private record Upload(String id, byte[][] parts) {}

    private static final Map<UUID, Upload> UPLOADS = new HashMap<>();

    private PanoramaStore() {}

    private static Path folder(MinecraftServer server)
    {
        return server.getWorldPath(LevelResource.ROOT).resolve("glass_panoramas");
    }

    /** Scan ids are UUIDs: nothing else may name a file. */
    public static boolean isValidId(String id)
    {
        return id.length() == 36 && id.matches("[0-9a-f-]{36}");
    }

    public static Path file(MinecraftServer server, String id)
    {
        return folder(server).resolve(id + ".png");
    }

    public static boolean has(MinecraftServer server, String id)
    {
        return isValidId(id) && Files.isRegularFile(file(server, id));
    }

    /** One part of a panorama a client sends in. Once all parts are there, it is kept (if the scan has none yet). */
    public static void receive(ServerPlayer player, HoloNetworking.PanoramaUpload part)
    {
        MinecraftServer server = player.level().getServer();
        String id = part.id();
        if(!isValidId(id) || HoloProjectionData.find(server, id) == null || has(server, id)
                || part.total() <= 0 || part.total() * (long)UPLOAD_PART > MAX_BYTES || part.index() < 0 || part.index() >= part.total())
        {
            return;
        }
        Upload upload = UPLOADS.get(player.getUUID());
        if(upload == null || !upload.id().equals(id) || upload.parts().length != part.total())
        {
            upload = new Upload(id, new byte[part.total()][]);
            UPLOADS.put(player.getUUID(), upload);
        }
        upload.parts()[part.index()] = part.data();
        int size = 0;
        for(byte[] bytes : upload.parts())
        {
            if(bytes == null)
            {
                return;
            }
            size += bytes.length;
        }
        UPLOADS.remove(player.getUUID());
        byte[] all = new byte[size];
        int at = 0;
        for(byte[] bytes : upload.parts())
        {
            System.arraycopy(bytes, 0, all, at, bytes.length);
            at += bytes.length;
        }
        if(all.length < PNG.length || all[0] != PNG[0] || all[1] != PNG[1] || all[2] != PNG[2] || all[3] != PNG[3])
        {
            return;
        }
        try
        {
            Files.createDirectories(folder(server));
            Files.write(file(server, id), all);
        }
        catch(IOException e)
        {
            Glass.LOGGER.warn("Could not keep the panorama of scan {}", id, e);
        }
    }

    /** Sends a scan's panorama to a player, in parts (or says there is none yet: zero parts). */
    public static void send(ServerPlayer player, String id)
    {
        MinecraftServer server = player.level().getServer();
        byte[] all = read(server, id);
        if(all == null)
        {
            ServerPlayNetworking.send(player, new HoloNetworking.PanoramaDownload(id, 0, 0, new byte[0]));
            return;
        }
        int total = Math.max(1, (all.length + DOWNLOAD_PART - 1) / DOWNLOAD_PART);
        for(int i = 0; i < total; i++)
        {
            int from = i * DOWNLOAD_PART;
            int to = Math.min(all.length, from + DOWNLOAD_PART);
            byte[] bytes = new byte[to - from];
            System.arraycopy(all, from, bytes, 0, bytes.length);
            ServerPlayNetworking.send(player, new HoloNetworking.PanoramaDownload(id, i, total, bytes));
        }
    }

    @Nullable
    private static byte[] read(MinecraftServer server, String id)
    {
        if(!has(server, id))
        {
            return null;
        }
        try
        {
            return Files.readAllBytes(file(server, id));
        }
        catch(IOException e)
        {
            return null;
        }
    }

    public static void forget(ServerPlayer player)
    {
        UPLOADS.remove(player.getUUID());
    }
}
