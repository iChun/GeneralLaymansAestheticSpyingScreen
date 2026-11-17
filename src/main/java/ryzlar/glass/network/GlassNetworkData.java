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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The GLASS network of one world: accounts, channels and the cameras on them. Saved with the overworld
 * (data/glass_network.dat), so it is shared by every dimension.
 */
public class GlassNetworkData extends SavedData
{
    public record Account(String username, String salt, String hash, int iterations, UUID creator)
    {
        public static final Codec<Account> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("username").forGetter(Account::username),
                Codec.STRING.fieldOf("salt").forGetter(Account::salt),
                Codec.STRING.fieldOf("hash").forGetter(Account::hash),
                Codec.INT.fieldOf("iterations").forGetter(Account::iterations),
                UUIDUtil.CODEC.fieldOf("creator").forGetter(Account::creator)
        ).apply(i, Account::new));
    }

    public record Camera(String id, String name, ResourceKey<Level> dimension, BlockPos pos, Direction facing)
    {
        public static final Codec<Camera> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Camera::id),
                Codec.STRING.fieldOf("name").forGetter(Camera::name),
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
                Codec.STRING.fieldOf("owner").forGetter(c -> c.owner),
                Codec.STRING.fieldOf("salt").forGetter(c -> c.salt),
                Codec.STRING.fieldOf("hash").forGetter(c -> c.hash),
                Codec.INT.fieldOf("iterations").forGetter(c -> c.iterations),
                Codec.STRING.listOf().fieldOf("members").forGetter(c -> List.copyOf(c.members)),
                Camera.CODEC.listOf().fieldOf("cameras").forGetter(c -> List.copyOf(c.cameras.values()))
        ).apply(i, Channel::new));

        public final String id;
        public String name;
        /** Owner's account key (lower case username). */
        public String owner;
        public String salt;
        public String hash;
        public int iterations;
        /** Account keys that know the password (the owner is always one). */
        public final Set<String> members = new LinkedHashSet<>();
        public final Map<String, Camera> cameras = new LinkedHashMap<>();

        public Channel(String id, String name, String owner, String salt, String hash, int iterations, List<String> members, List<Camera> cameras)
        {
            this.id = id;
            this.name = name;
            this.owner = owner;
            this.salt = salt;
            this.hash = hash;
            this.iterations = iterations;
            this.members.addAll(members);
            for(Camera camera : cameras)
            {
                this.cameras.put(camera.id(), camera);
            }
        }

        public boolean isMember(@Nullable String account)
        {
            return account != null && members.contains(account);
        }
    }

    private record Stored(List<Account> accounts, List<Channel> channels, Map<String, String> remembered)
    {
        static final Codec<Stored> CODEC = RecordCodecBuilder.create(i -> i.group(
                Account.CODEC.listOf().optionalFieldOf("accounts", List.of()).forGetter(Stored::accounts),
                Channel.CODEC.listOf().optionalFieldOf("channels", List.of()).forGetter(Stored::channels),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("remembered", Map.of()).forGetter(Stored::remembered)
        ).apply(i, Stored::new));
    }

    public static final Codec<GlassNetworkData> CODEC = Stored.CODEC.xmap(GlassNetworkData::new, GlassNetworkData::toStored);
    public static final SavedDataType<GlassNetworkData> TYPE = new SavedDataType<>("glass_network", GlassNetworkData::new, CODEC, null);

    /** Lower case username to account. */
    public final Map<String, Account> accounts = new LinkedHashMap<>();
    public final Map<String, Channel> channels = new LinkedHashMap<>();
    /** Minecraft player UUID (as text) to the account they stay logged in to. */
    public final Map<String, String> remembered = new HashMap<>();

    public GlassNetworkData() {}

    private GlassNetworkData(Stored stored)
    {
        for(Account account : stored.accounts())
        {
            accounts.put(key(account.username()), account);
        }
        for(Channel channel : stored.channels())
        {
            channels.put(channel.id, channel);
        }
        remembered.putAll(stored.remembered());
    }

    private Stored toStored()
    {
        return new Stored(new ArrayList<>(accounts.values()), new ArrayList<>(channels.values()), new HashMap<>(remembered));
    }

    public static String key(String name)
    {
        return name.toLowerCase(Locale.ROOT);
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

    @Nullable
    public Camera camera(String channelId, String cameraId)
    {
        Channel channel = channels.get(channelId);
        return channel == null ? null : channel.cameras.get(cameraId);
    }
}
