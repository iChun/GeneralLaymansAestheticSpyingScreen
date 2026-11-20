package ryzlar.glass.holo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
     * Most a holo cube scan keeps of chest contents, signs, mobs and so on, in bytes (all of it goes to every client that
     * sees the simulation). One block entity or entity may hold at most {@link #MAX_EXTRA_BYTES}: a chest full of shulker
     * boxes full of books is kept as an empty chest, and a mob carrying that much is left out.
     */
    public static final int MAX_EXTRAS_BYTES = 2 * 1024 * 1024;
    public static final int MAX_EXTRA_BYTES = 64 * 1024;

    /**
     * Blocks of a scanned area: {@code blocks} holds a palette index per block, x fastest, then z, then y. Palette entry 0
     * is air.
     * <p>
     * A holo cube's scan keeps more (a scanner's crystal only the blocks): {@code blockEntities}, what is in its chests,
     * on its signs and so on (each with its place in the area as {@link #LOCAL_POS}); {@code entities}, the animals,
     * mobs, item frames, armor stands and paintings in it (each with its place as {@link #LOCAL_X} and on); and
     * {@code light}, the light of each block as it was (block light times 16 plus sky light, in block order), so the
     * simulation is lit as the place was.
     */
    public record Projection(String id, String name, int sizeX, int sizeY, int sizeZ, List<BlockState> palette, int[] blocks,
                             List<CompoundTag> blockEntities, List<CompoundTag> entities, int[] light)
    {
        public static final String LOCAL_POS = "glass_local";
        public static final String LOCAL_X = "glass_x";
        public static final String LOCAL_Y = "glass_y";
        public static final String LOCAL_Z = "glass_z";

        public static final Codec<Projection> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Projection::id),
                Codec.STRING.fieldOf("name").forGetter(Projection::name),
                Codec.INT.fieldOf("sizeX").forGetter(Projection::sizeX),
                Codec.INT.fieldOf("sizeY").forGetter(Projection::sizeY),
                Codec.INT.fieldOf("sizeZ").forGetter(Projection::sizeZ),
                BlockState.CODEC.listOf().fieldOf("palette").forGetter(Projection::palette),
                Codec.INT_STREAM.xmap(IntStream::toArray, Arrays::stream).fieldOf("blocks").forGetter(Projection::blocks),
                CompoundTag.CODEC.listOf().optionalFieldOf("blockEntities", List.of()).forGetter(Projection::blockEntities),
                CompoundTag.CODEC.listOf().optionalFieldOf("entities", List.of()).forGetter(Projection::entities),
                Codec.INT_STREAM.xmap(IntStream::toArray, Arrays::stream).optionalFieldOf("light", new int[0]).forGetter(Projection::light)
        ).apply(i, Projection::new));

        /** Just blocks (a scanner's crystal). */
        public Projection(String id, String name, int sizeX, int sizeY, int sizeZ, List<BlockState> palette, int[] blocks)
        {
            this(id, name, sizeX, sizeY, sizeZ, palette, blocks, List.of(), List.of(), new int[0]);
        }

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

    /** Forgets a projection (its scan was wiped), so the saved projections do not grow with every scan. */
    public static void remove(MinecraftServer server, String id)
    {
        HoloProjectionData data = get(server);
        if(data.projections.remove(id) != null)
        {
            data.setDirty();
        }
    }

    /**
     * A block entity's saved data as a scan keeps it: whole if it is small enough and there is room left, else only what
     * it is (contents dropped, see {@link #MAX_EXTRA_BYTES}). Returns the bytes it takes.
     */
    private static int keep(CompoundTag tag, List<CompoundTag> into, int used)
    {
        int size = tag.sizeInBytes();
        if(size > MAX_EXTRA_BYTES || used + size > MAX_EXTRAS_BYTES)
        {
            CompoundTag bare = new CompoundTag();
            for(String key : new String[] { "id", "x", "y", "z", Projection.LOCAL_POS })
            {
                net.minecraft.nbt.Tag value = tag.get(key);
                if(value != null)
                {
                    bare.put(key, value.copy());
                }
            }
            tag = bare;
            size = tag.sizeInBytes();
        }
        into.add(tag);
        return size;
    }

    /**
     * Stores the ball round a holo cube (see {@link HoloSphere}) as a new projection: every block in it, what is in its
     * chests and on its signs, the animals, mobs, frames, stands and paintings in it, and its light. The cube's block
     * is the middle of the projection's box.
     */
    public static Projection storeBall(ServerLevel level, BlockPos centre, String name)
    {
        int size = HoloSphere.SIZE;
        int r = HoloSphere.SCAN_RADIUS;
        BlockPos corner = centre.offset(-r, -r, -r);
        List<BlockState> palette = new ArrayList<>();
        palette.add(Blocks.AIR.defaultBlockState());
        Map<BlockState, Integer> indices = new HashMap<>();
        indices.put(Blocks.AIR.defaultBlockState(), 0);
        int[] blocks = new int[size * size * size];
        int[] light = new int[size * size * size];
        List<CompoundTag> blockEntities = new ArrayList<>();
        int used = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for(int y = 0; y < size; y++)
        {
            for(int z = 0; z < size; z++)
            {
                for(int x = 0; x < size; x++)
                {
                    if(!HoloSphere.inBall(x - r, y - r, z - r))
                    {
                        continue;
                    }
                    pos.setWithOffset(corner, x, y, z);
                    int i = (y * size + z) * size + x;
                    light[i] = level.getBrightness(LightLayer.BLOCK, pos) << 4 | level.getBrightness(LightLayer.SKY, pos);
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
                    blocks[i] = index;
                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    if(blockEntity != null)
                    {
                        CompoundTag tag = blockEntity.saveWithFullMetadata(level.registryAccess());
                        tag.putIntArray(Projection.LOCAL_POS, new int[] { x, y, z });
                        used += keep(tag, blockEntities, used);
                    }
                }
            }
        }
        //Animals, mobs and the things hung or stood in it (not players, not the cube, not dropped items).
        List<CompoundTag> entities = new ArrayList<>();
        Vec3 middle = Vec3.atCenterOf(centre);
        AABB box = new AABB(corner).expandTowards(size - 1, size - 1, size - 1);
        for(Entity entity : level.getEntities((Entity)null, box, e -> e.isAlive() && !(e instanceof Player) && !(e instanceof HoloCubeEntity)
                && !(e instanceof net.minecraft.world.entity.item.ItemEntity) && !(e instanceof net.minecraft.world.entity.ExperienceOrb) && !e.isPassenger()))
        {
            if(entity.position().distanceToSqr(middle) > (r + 0.5D) * (r + 0.5D))
            {
                continue;
            }
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            if(!entity.save(output))
            {
                continue;
            }
            CompoundTag tag = output.buildResult();
            int tagSize = tag.sizeInBytes();
            if(tagSize > MAX_EXTRA_BYTES || used + tagSize > MAX_EXTRAS_BYTES)
            {
                continue;
            }
            used += tagSize;
            tag.putDouble(Projection.LOCAL_X, entity.getX() - corner.getX());
            tag.putDouble(Projection.LOCAL_Y, entity.getY() - corner.getY());
            tag.putDouble(Projection.LOCAL_Z, entity.getZ() - corner.getZ());
            entities.add(tag);
        }
        Projection projection = new Projection(UUID.randomUUID().toString(), name, size, size, size, List.copyOf(palette), blocks,
                List.copyOf(blockEntities), List.copyOf(entities), light);
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
