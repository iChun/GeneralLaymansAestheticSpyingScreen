package ryzlar.glass.client.tome;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.Glass;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The crafting recipes the tome shows, read straight from the mod's own recipe files: since 1.21.2 a client is not sent
 * every recipe, and these are always the ones the mod ships.
 */
public final class TomeRecipes
{
    /** A 3x3 crafting grid (each slot: the items that fit, cycled through) and what it makes. */
    public record Grid(List<List<ItemStack>> slots, ItemStack result, boolean shapeless) {}

    private static final Map<ResourceLocation, Optional<Grid>> CACHE = new HashMap<>();

    private TomeRecipes() {}

    @Nullable
    public static Grid of(ResourceLocation item)
    {
        return CACHE.computeIfAbsent(item, TomeRecipes::read).orElse(null);
    }

    public static void reset()
    {
        CACHE.clear();
    }

    private static Optional<Grid> read(ResourceLocation item)
    {
        Optional<Path> path = FabricLoader.getInstance().getModContainer(Glass.MOD_ID)
                .flatMap(mod -> mod.findPath("data/" + item.getNamespace() + "/recipe/" + item.getPath() + ".json"));
        if(path.isEmpty())
        {
            return Optional.empty();
        }
        try(Reader reader = Files.newBufferedReader(path.get()))
        {
            return Optional.ofNullable(parse(JsonParser.parseReader(reader).getAsJsonObject()));
        }
        catch(Exception e)
        {
            Glass.LOGGER.error("GLASS Tome: could not read the recipe of {}", item, e);
            return Optional.empty();
        }
    }

    @Nullable
    private static Grid parse(JsonObject json)
    {
        String type = json.get("type").getAsString();
        List<List<ItemStack>> slots = new ArrayList<>();
        for(int i = 0; i < 9; i++)
        {
            slots.add(List.of());
        }
        boolean shapeless = type.endsWith("crafting_shapeless");
        if(shapeless)
        {
            JsonArray ingredients = json.getAsJsonArray("ingredients");
            for(int i = 0; i < Math.min(9, ingredients.size()); i++)
            {
                slots.set(i, ingredient(ingredients.get(i)));
            }
        }
        else if(type.endsWith("crafting_shaped"))
        {
            JsonArray pattern = json.getAsJsonArray("pattern");
            JsonObject key = json.getAsJsonObject("key");
            for(int row = 0; row < Math.min(3, pattern.size()); row++)
            {
                String line = pattern.get(row).getAsString();
                for(int col = 0; col < Math.min(3, line.length()); col++)
                {
                    String symbol = String.valueOf(line.charAt(col));
                    if(key.has(symbol))
                    {
                        slots.set(row * 3 + col, ingredient(key.get(symbol)));
                    }
                }
            }
        }
        else
        {
            return null;
        }
        JsonObject result = json.getAsJsonObject("result");
        Item resultItem = BuiltInRegistries.ITEM.getValue(ResourceLocation.parse(result.get("id").getAsString()));
        int count = result.has("count") ? result.get("count").getAsInt() : 1;
        return new Grid(List.copyOf(slots), new ItemStack(resultItem, count), shapeless);
    }

    /** "minecraft:glass", "#minecraft:planks" or a list of those. */
    private static List<ItemStack> ingredient(JsonElement json)
    {
        List<ItemStack> stacks = new ArrayList<>();
        if(json.isJsonArray())
        {
            for(JsonElement option : json.getAsJsonArray())
            {
                stacks.addAll(ingredient(option));
            }
            return stacks;
        }
        String id = json.isJsonObject() ? json.getAsJsonObject().has("tag") ? "#" + json.getAsJsonObject().get("tag").getAsString()
                : json.getAsJsonObject().get("item").getAsString() : json.getAsString();
        if(id.startsWith("#"))
        {
            for(Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, ResourceLocation.parse(id.substring(1)))))
            {
                stacks.add(new ItemStack(holder));
            }
        }
        else
        {
            stacks.add(new ItemStack(BuiltInRegistries.ITEM.getValue(ResourceLocation.parse(id))));
        }
        return stacks;
    }
}
