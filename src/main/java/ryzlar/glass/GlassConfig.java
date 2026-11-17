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

    /** GLASS accounts one Minecraft player may create. */
    public int maxAccountsPerPlayer = 3;
    /** Channels one GLASS account may own. */
    public int maxChannelsPerAccount = 10;
    /** Cameras (terminals) one channel may have. */
    public int maxCamerasPerChannel = 16;
    /** Failed logins or channel joins a player may make per minute before being made to wait. */
    public int failedLoginsPerMinute = 5;
    /** Shortest auto-cycle interval for projectors, in seconds. */
    public int minCycleSeconds = 3;

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
