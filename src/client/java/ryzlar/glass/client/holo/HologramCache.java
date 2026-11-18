package ryzlar.glass.client.holo;

import ryzlar.glass.holo.HoloProjectorBlockEntity;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Built holograms, one per projector, rebuilt only when what they show (or how) changes. */
final class HologramCache
{
    private record Entry(String key, HologramRenderer.Hologram hologram) {}

    private static final Map<HoloProjectorBlockEntity, Entry> CACHE = new WeakHashMap<>();

    private HologramCache() {}

    static HologramRenderer.Hologram get(HoloProjectorBlockEntity be, HoloClient.ClientProjection projection, Supplier<HologramRenderer.Hologram> build)
    {
        String key = projection.id() + "|" + be.look + "|" + be.scale + "|" + be.rotation + "|" + be.emitters;
        Entry entry = CACHE.get(be);
        if(entry == null || !entry.key().equals(key))
        {
            entry = new Entry(key, build.get());
            CACHE.put(be, entry);
        }
        return entry.hologram();
    }

    /** The last hologram built for this projector, if any (for the switch-off wave, when the crystal may be gone). */
    @org.jetbrains.annotations.Nullable
    static HologramRenderer.Hologram peek(HoloProjectorBlockEntity be)
    {
        Entry entry = CACHE.get(be);
        return entry == null ? null : entry.hologram();
    }
}
