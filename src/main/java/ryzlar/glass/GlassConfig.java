package ryzlar.glass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server settings, in config/generallaymansaestheticspyingscreen.json. Missing values get their defaults and the file
 * is rewritten with every setting, so new options show up in it.
 */
public final class GlassConfig
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static GlassConfig instance = new GlassConfig();

    /** Channels one player may own. */
    public int maxChannelsPerPlayer = 10;
    /** Wrong channel passwords a player may enter per minute before being made to wait. */
    public int failedPasswordsPerMinute = 5;
    /** New channels plus changed passwords a player may make per minute (each one is slow for the server to hash). */
    public int newPasswordsPerMinute = 5;

    public static GlassConfig get()
    {
        return instance;
    }

    public static void load()
    {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(Glass.MOD_ID + ".json");
        GlassConfig config = new GlassConfig();
        if(Files.exists(path))
        {
            try(Reader reader = Files.newBufferedReader(path))
            {
                GlassConfig read = GSON.fromJson(reader, GlassConfig.class);
                if(read != null)
                {
                    config = read;
                }
            }
            catch(IOException | RuntimeException e)
            {
                Glass.LOGGER.error("Could not read {}, using defaults", path, e);
            }
        }
        instance = config;
        try
        {
            Files.createDirectories(path.getParent());
            try(Writer writer = Files.newBufferedWriter(path))
            {
                GSON.toJson(config, writer);
            }
        }
        catch(IOException e)
        {
            Glass.LOGGER.error("Could not write {}", path, e);
        }
    }
}
