package ryzlar.glass.client.tome;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.Glass;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The GLASS Tome's contents, read from the mod's assets (tome/index.json and tome/pages/*.json), so a resource pack
 * can change them. Read once, and again after a resource reload.
 */
public final class TomeBook
{
    /** A paragraph of text (with [[page|text]] links) or a recipe. */
    public sealed interface Block permits Text, Recipe {}

    public record Text(String text) implements Block {}

    public record Recipe(ResourceLocation item) implements Block {}

    public record Page(String id, String title, String chapter, List<ResourceLocation> items, @Nullable String scene, List<Block> body)
    {
        /** All its words, for searching. */
        public String searchText()
        {
            StringBuilder text = new StringBuilder(title).append(' ').append(chapter);
            for(Block block : body)
            {
                if(block instanceof Text t)
                {
                    text.append(' ').append(t.text().replaceAll("\\[\\[[^|\\]]*\\|([^\\]]*)]]", "$1"));
                }
            }
            return text.toString().toLowerCase(Locale.ROOT);
        }
    }

    public record Chapter(String title, List<Page> pages) {}

    private static TomeBook loaded;

    public final List<Chapter> chapters;
    private final Map<String, Page> pages = new LinkedHashMap<>();

    private TomeBook(List<Chapter> chapters)
    {
        this.chapters = chapters;
        for(Chapter chapter : chapters)
        {
            for(Page page : chapter.pages())
            {
                pages.put(page.id(), page);
            }
        }
    }

    public static TomeBook get()
    {
        if(loaded == null)
        {
            loaded = load();
        }
        return loaded;
    }

    /** Forget what was read (resources reloaded). */
    public static void reset()
    {
        loaded = null;
    }

    @Nullable
    public Page page(String id)
    {
        return pages.get(id);
    }

    public List<Page> pages()
    {
        return List.copyOf(pages.values());
    }

    @Nullable
    public Page first()
    {
        return pages.isEmpty() ? null : pages.values().iterator().next();
    }

    /** The page about an item (the first one listing it), for the look-up key. */
    @Nullable
    public Page pageFor(ResourceLocation item)
    {
        for(Page page : pages.values())
        {
            if(page.items().contains(item))
            {
                return page;
            }
        }
        return null;
    }

    /** Pages whose words contain every word of the query. */
    public List<Page> search(String query)
    {
        String[] words = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
        List<Page> found = new ArrayList<>();
        for(Page page : pages.values())
        {
            String text = page.searchText();
            boolean all = true;
            for(String word : words)
            {
                if(!word.isEmpty() && !text.contains(word))
                {
                    all = false;
                    break;
                }
            }
            if(all)
            {
                found.add(page);
            }
        }
        return found;
    }

    private static TomeBook load()
    {
        List<Chapter> chapters = new ArrayList<>();
        JsonObject index = read(Glass.id("tome/index.json"));
        if(index == null)
        {
            return new TomeBook(chapters);
        }
        for(JsonElement c : index.getAsJsonArray("chapters"))
        {
            JsonObject chapter = c.getAsJsonObject();
            String chapterTitle = chapter.get("title").getAsString();
            List<Page> pages = new ArrayList<>();
            for(JsonElement p : chapter.getAsJsonArray("pages"))
            {
                String id = p.getAsString();
                JsonObject page = read(Glass.id("tome/pages/" + id + ".json"));
                if(page != null)
                {
                    pages.add(parsePage(id, chapterTitle, page));
                }
            }
            chapters.add(new Chapter(chapterTitle, List.copyOf(pages)));
        }
        return new TomeBook(List.copyOf(chapters));
    }

    private static Page parsePage(String id, String chapter, JsonObject json)
    {
        List<ResourceLocation> items = new ArrayList<>();
        if(json.has("items"))
        {
            for(JsonElement item : json.getAsJsonArray("items"))
            {
                items.add(ResourceLocation.parse(item.getAsString()));
            }
        }
        List<Block> body = new ArrayList<>();
        JsonArray blocks = json.has("body") ? json.getAsJsonArray("body") : new JsonArray();
        for(JsonElement block : blocks)
        {
            if(block.isJsonPrimitive())
            {
                body.add(new Text(block.getAsString()));
            }
            else if(block.getAsJsonObject().has("recipe"))
            {
                body.add(new Recipe(ResourceLocation.parse(block.getAsJsonObject().get("recipe").getAsString())));
            }
        }
        String scene = json.has("scene") ? json.get("scene").getAsString() : null;
        return new Page(id, json.get("title").getAsString(), chapter, List.copyOf(items), scene, List.copyOf(body));
    }

    @Nullable
    private static JsonObject read(ResourceLocation location)
    {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
        if(resource.isEmpty())
        {
            Glass.LOGGER.warn("GLASS Tome: missing {}", location);
            return null;
        }
        try(Reader reader = resource.get().openAsReader())
        {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
        catch(Exception e)
        {
            Glass.LOGGER.error("GLASS Tome: could not read {}", location, e);
            return null;
        }
    }
}
