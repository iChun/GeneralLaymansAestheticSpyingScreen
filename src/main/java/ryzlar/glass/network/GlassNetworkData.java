package ryzlar.glass.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The GLASS channels of one world, saved with the overworld (data/glass_channels.dat) so every dimension shares them.
 * <p>
 * A channel is one camera (a terminal), owned by the player who made it and protected by a password. Players who
 * enter the password are remembered and do not need it again, until the owner changes it.
 */
public class GlassNetworkData extends SavedData
{
    public record Camera(ResourceKey<Level> dimension, BlockPos pos, Direction facing)
    {
        public static final Codec<Camera> CODEC = RecordCodecBuilder.create(i -> i.group(
                Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Camera::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(Camera::pos),
                Direction.CODEC.fieldOf("facing").forGetter(Camera::facing)
        ).apply(i, Camera::new));
    }

    public static final class Channel
    {
        public static final Codec<Channel> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(c -> c.id),
                Codec.STRING.fieldOf("name").forGetter(c -> c.name),
                UUIDUtil.CODEC.fieldOf("owner").forGetter(c -> c.owner),
                Codec.STRING.fieldOf("ownerName").forGetter(c -> c.ownerName),
                Codec.STRING.fieldOf("salt").forGetter(c -> c.salt),
                Codec.STRING.fieldOf("hash").forGetter(c -> c.hash),
                Codec.INT.fieldOf("iterations").forGetter(c -> c.iterations),
                UUIDUtil.CODEC.listOf().fieldOf("members").forGetter(c -> List.copyOf(c.members)),
                Camera.CODEC.optionalFieldOf("camera").forGetter(c -> Optional.ofNullable(c.camera))
        ).apply(i, Channel::new));

        public final String id;
        public final String name;
        public final UUID owner;
        /** The owner's username when last seen, for display. */
        public String ownerName;
        public String salt;
        public String hash;
        public int iterations;
        /** Players (besides the owner) who entered the password. */
        public final Set<UUID> members = new LinkedHashSet<>();
        @Nullable
        public Camera camera;

        public Channel(String id, String name, UUID owner, String ownerName, String salt, String hash, int iterations, List<UUID> members, Optional<Camera> camera)
        {
            this.id = id;
            this.name = name;
            this.owner = owner;
            this.ownerName = ownerName;
            this.salt = salt;
            this.hash = hash;
            this.iterations = iterations;
            this.members.addAll(members);
            this.camera = camera.orElse(null);
        }

        public boolean isOwner(UUID player)
        {
            return owner.equals(player);
        }

        public boolean hasAccess(UUID player)
        {
            return isOwner(player) || members.contains(player);
        }
    }

    public static final Codec<GlassNetworkData> CODEC = Channel.CODEC.listOf().optionalFieldOf("channels", List.of()).codec()
            .xmap(GlassNetworkData::new, data -> new ArrayList<>(data.channels.values()));
    public static final SavedDataType<GlassNetworkData> TYPE = new SavedDataType<>("glass_channels", GlassNetworkData::new, CODEC, null);

    public final Map<String, Channel> channels = new LinkedHashMap<>();

    public GlassNetworkData() {}

    private GlassNetworkData(List<Channel> list)
    {
        for(Channel channel : list)
        {
            channels.put(channel.id, channel);
        }
    }

    @Nullable
    public Channel channelByName(String name)
    {
        for(Channel channel : channels.values())
        {
            if(channel.name.equalsIgnoreCase(name))
            {
                return channel;
            }
        }
        return null;
    }
}
