package ryzlar.glass.holo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * The scanned projections of a world (data/glass_projections.dat). A projection crystal only carries the id of its
 * projection, so big scans do not bloat items (and the packets that carry them).
 */
public class HoloProjectionData extends SavedData
{
    /** Largest scan, each way, in blocks. */
    public static final int MAX_SIZE = 32;

    /**
     * Blocks of a scanned area: {@code blocks} holds a palette index per block, x fastest, then z, then y. Palette entry 0
     * is air.
     */
    public record Projection(String id, String name, int sizeX, int sizeY, int sizeZ, List<BlockState> palette, int[] blocks)
    {
        public static final Codec<Projection> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Projection::id),
                Codec.STRING.fieldOf("name").forGetter(Projection::name),
                Codec.INT.fieldOf("sizeX").forGetter(Projection::sizeX),
                Codec.INT.fieldOf("sizeY").forGetter(Projection::sizeY),
                Codec.INT.fieldOf("sizeZ").forGetter(Projection::sizeZ),
                BlockState.CODEC.listOf().fieldOf("palette").forGetter(Projection::palette),
                Codec.INT_STREAM.xmap(IntStream::toArray, Arrays::stream).fieldOf("blocks").forGetter(Projection::blocks)
        ).apply(i, Projection::new));

        public int index(int x, int y, int z)
        {
            return (y * sizeZ + z) * sizeX + x;
        }

        public BlockState get(int x, int y, int z)
        {
            if(x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ)
            {
                return Blocks.AIR.defaultBlockState();
            }
            int i = blocks[index(x, y, z)];
            return i >= 0 && i < palette.size() ? palette.get(i) : Blocks.AIR.defaultBlockState();
        }
    }

    public static final Codec<HoloProjectionData> CODEC = Projection.CODEC.listOf().optionalFieldOf("projections", List.of()).codec()
            .xmap(HoloProjectionData::new, data -> List.copyOf(data.projections.values()));
    public static final SavedDataType<HoloProjectionData> TYPE = new SavedDataType<>("glass_projections", HoloProjectionData::new, CODEC, null);

    public final Map<String, Projection> projections = new LinkedHashMap<>();

    public HoloProjectionData() {}

    private HoloProjectionData(List<Projection> list)
    {
        for(Projection projection : list)
        {
            projections.put(projection.id(), projection);
        }
    }

    public static HoloProjectionData get(MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    @Nullable
    public static Projection find(MinecraftServer server, String id)
    {
        return get(server).projections.get(id);
    }

    /** Stores the given blocks of an area (the rest is left empty) as a new projection: a room scan. */
    public static Projection store(ServerLevel level, BlockPos corner, int sizeX, int sizeY, int sizeZ, it.unimi.dsi.fastutil.longs.LongSet positions, String name)
    {
        List<BlockState> palette = new ArrayList<>();
        palette.add(Blocks.AIR.defaultBlockState());
        Map<BlockState, Integer> indices = new HashMap<>();
        indices.put(Blocks.AIR.defaultBlockState(), 0);
        int[] blocks = new int[sizeX * sizeY * sizeZ];
        for(long key : positions)
        {
            BlockPos pos = BlockPos.of(key);
            int x = pos.getX() - corner.getX(), y = pos.getY() - corner.getY(), z = pos.getZ() - corner.getZ();
            if(x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ)
            {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if(state.isAir())
            {
                continue;
            }
            Integer index = indices.get(state);
            if(index == null)
            {
                index = palette.size();
                palette.add(state);
                indices.put(state, index);
            }
            blocks[(y * sizeZ + z) * sizeX + x] = index;
        }
        Projection projection = new Projection(UUID.randomUUID().toString(), name, sizeX, sizeY, sizeZ, List.copyOf(palette), blocks);
        HoloProjectionData data = get(level.getServer());
        data.projections.put(projection.id(), projection);
        data.setDirty();
        return projection;
    }

    /**
     * Scans the blocks of an area and stores them as a new projection. Returns null if part of the area is not loaded.
     * Only blocks are kept (no block entity contents, no mobs): a projection is just a picture.
     */
    @Nullable
    public static Projection scan(ServerLevel level, BlockPos origin, int sizeX, int sizeY, int sizeZ, String name)
    {
        if(!level.isLoaded(origin) || !level.isLoaded(origin.offset(sizeX - 1, sizeY - 1, sizeZ - 1))
                || !level.isLoaded(origin.offset(sizeX - 1, 0, 0)) || !level.isLoaded(origin.offset(0, 0, sizeZ - 1)))
        {
            return null;
        }
        List<BlockState> palette = new ArrayList<>();
        palette.add(Blocks.AIR.defaultBlockState());
        Map<BlockState, Integer> indices = new HashMap<>();
        indices.put(Blocks.AIR.defaultBlockState(), 0);
        int[] blocks = new int[sizeX * sizeY * sizeZ];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int i = 0;
        for(int y = 0; y < sizeY; y++)
        {
            for(int z = 0; z < sizeZ; z++)
            {
                for(int x = 0; x < sizeX; x++)
                {
                    BlockState state = level.getBlockState(pos.setWithOffset(origin, x, y, z));
                    if(state.isAir())
                    {
                        state = Blocks.AIR.defaultBlockState();
                    }
                    Integer index = indices.get(state);
                    if(index == null)
                    {
                        index = palette.size();
                        palette.add(state);
                        indices.put(state, index);
                    }
                    blocks[i++] = index;
                }
            }
        }
        Projection projection = new Projection(UUID.randomUUID().toString(), name, sizeX, sizeY, sizeZ, List.copyOf(palette), blocks);
        HoloProjectionData data = get(level.getServer());
        data.projections.put(projection.id(), projection);
        data.setDirty();
        return projection;
    }
}
