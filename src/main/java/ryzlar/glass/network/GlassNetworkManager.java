package ryzlar.glass.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.GlassConfig;

import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Server side of the GLASS network: who is logged in to which GLASS account, and every change to accounts, channels
 * and cameras. All methods run on the server thread.
 */
public final class GlassNetworkManager
{
    public static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_-]{3,16}");
    public static final Pattern CHANNEL_NAME = Pattern.compile("[A-Za-z0-9_\\- ]{1,24}");
    public static final int MIN_PASSWORD = 4;
    public static final int MAX_PASSWORD = 64;
    public static final int MAX_CAMERA_NAME = 24;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Map<UUID, String> SESSIONS = new HashMap<>();
    private static final Map<UUID, Deque<Long>> FAILURES = new HashMap<>();

    private GlassNetworkManager() {}

    /** A reply for the screen: ok or not, a translation key and one argument. */
    public record Result(boolean ok, String message, String arg)
    {
        static Result ok(String message)
        {
            return new Result(true, message, "");
        }

        static Result ok(String message, String arg)
        {
            return new Result(true, message, arg);
        }

        static Result fail(String message)
        {
            return new Result(false, message, "");
        }

        static Result fail(String message, String arg)
        {
            return new Result(false, message, arg);
        }
    }

    public static GlassNetworkData data(MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(GlassNetworkData.TYPE);
    }

    @Nullable
    public static String account(ServerPlayer player)
    {
        return SESSIONS.get(player.getUUID());
    }

    // ---- Sessions ----

    public static void onJoin(ServerPlayer player)
    {
        GlassNetworkData data = data(player.level().getServer());
        String remembered = data.remembered.get(player.getUUID().toString());
        if(remembered != null && data.accounts.containsKey(remembered))
        {
            SESSIONS.put(player.getUUID(), remembered);
        }
        sendState(player);
    }

    public static void onLeave(ServerPlayer player)
    {
        SESSIONS.remove(player.getUUID());
        FAILURES.remove(player.getUUID());
    }

    public static void onServerStopped()
    {
        SESSIONS.clear();
        FAILURES.clear();
    }

    private static boolean tooManyFailures(ServerPlayer player)
    {
        Deque<Long> failures = FAILURES.get(player.getUUID());
        if(failures == null)
        {
            return false;
        }
        long cutoff = System.currentTimeMillis() - 60_000L;
        while(!failures.isEmpty() && failures.peekFirst() < cutoff)
        {
            failures.pollFirst();
        }
        return failures.size() >= GlassConfig.get().failedLoginsPerMinute;
    }

    private static void recordFailure(ServerPlayer player)
    {
        FAILURES.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>()).addLast(System.currentTimeMillis());
    }

    private static boolean validPassword(String password)
    {
        return password.length() >= MIN_PASSWORD && password.length() <= MAX_PASSWORD;
    }

    public static Result register(ServerPlayer player, String username, String password, boolean remember)
    {
        GlassNetworkData data = data(player.level().getServer());
        if(!USERNAME.matcher(username).matches())
        {
            return Result.fail("glass.msg.badUsername");
        }
        if(!validPassword(password))
        {
            return Result.fail("glass.msg.badPassword");
        }
        String key = GlassNetworkData.key(username);
        if(data.accounts.containsKey(key))
        {
            return Result.fail("glass.msg.usernameTaken");
        }
        long owned = data.accounts.values().stream().filter(a -> a.creator().equals(player.getUUID())).count();
        if(owned >= GlassConfig.get().maxAccountsPerPlayer)
        {
            return Result.fail("glass.msg.tooManyAccounts", Integer.toString(GlassConfig.get().maxAccountsPerPlayer));
        }
        PasswordHasher.Hash hash = PasswordHasher.hash(password);
        data.accounts.put(key, new GlassNetworkData.Account(username, hash.salt(), hash.hash(), hash.iterations(), player.getUUID()));
        data.setDirty();
        startSession(player, data, key, remember);
        return Result.ok("glass.msg.registered", username);
    }

    public static Result login(ServerPlayer player, String username, String password, boolean remember)
    {
        if(tooManyFailures(player))
        {
            return Result.fail("glass.msg.tooManyAttempts");
        }
        GlassNetworkData data = data(player.level().getServer());
        String key = GlassNetworkData.key(username);
        GlassNetworkData.Account account = data.accounts.get(key);
        if(account == null || !PasswordHasher.verify(password, account.salt(), account.hash(), account.iterations()))
        {
            recordFailure(player);
            return Result.fail("glass.msg.loginFailed");
        }
        startSession(player, data, key, remember);
        return Result.ok("glass.msg.loggedIn", account.username());
    }

    private static void startSession(ServerPlayer player, GlassNetworkData data, String key, boolean remember)
    {
        SESSIONS.put(player.getUUID(), key);
        if(remember)
        {
            data.remembered.put(player.getUUID().toString(), key);
        }
        else
        {
            data.remembered.remove(player.getUUID().toString());
        }
        data.setDirty();
    }

    public static Result logout(ServerPlayer player)
    {
        GlassNetworkData data = data(player.level().getServer());
        SESSIONS.remove(player.getUUID());
        if(data.remembered.remove(player.getUUID().toString()) != null)
        {
            data.setDirty();
        }
        return Result.ok("glass.msg.loggedOut");
    }

    // ---- Channels ----

    private static String newId(Map<String, ?> taken)
    {
        String id;
        do
        {
            byte[] bytes = new byte[4];
            RANDOM.nextBytes(bytes);
            id = HexFormat.of().formatHex(bytes);
        }
        while(taken.containsKey(id));
        return id;
    }

    public static Result createChannel(ServerPlayer player, String name, String password)
    {
        String account = account(player);
        if(account == null)
        {
            return Result.fail("glass.msg.notLoggedIn");
        }
        name = name.trim();
        if(!CHANNEL_NAME.matcher(name).matches())
        {
            return Result.fail("glass.msg.badChannelName");
        }
        if(!validPassword(password))
        {
            return Result.fail("glass.msg.badPassword");
        }
        GlassNetworkData data = data(player.level().getServer());
        if(data.channelByName(name) != null)
        {
            return Result.fail("glass.msg.channelTaken");
        }
        long owned = data.channels.values().stream().filter(c -> c.owner.equals(account)).count();
        if(owned >= GlassConfig.get().maxChannelsPerAccount)
        {
            return Result.fail("glass.msg.tooManyChannels", Integer.toString(GlassConfig.get().maxChannelsPerAccount));
        }
        PasswordHasher.Hash hash = PasswordHasher.hash(password);
        GlassNetworkData.Channel channel = new GlassNetworkData.Channel(newId(data.channels), name, account, hash.salt(), hash.hash(), hash.iterations(), List.of(account), List.of());
        data.channels.put(channel.id, channel);
        data.setDirty();
        return Result.ok("glass.msg.channelCreated", name);
    }

    public static Result joinChannel(ServerPlayer player, String name, String password)
    {
        String account = account(player);
        if(account == null)
        {
            return Result.fail("glass.msg.notLoggedIn");
        }
        if(tooManyFailures(player))
        {
            return Result.fail("glass.msg.tooManyAttempts");
        }
        GlassNetworkData data = data(player.level().getServer());
        GlassNetworkData.Channel channel = data.channelByName(name.trim());
        if(channel == null || !PasswordHasher.verify(password, channel.salt, channel.hash, channel.iterations))
        {
            recordFailure(player);
            return Result.fail("glass.msg.joinFailed");
        }
        channel.members.add(account);
        data.setDirty();
        return Result.ok("glass.msg.joined", channel.name);
    }

    @Nullable
    private static GlassNetworkData.Channel owned(GlassNetworkData data, String account, String channelId)
    {
        GlassNetworkData.Channel channel = data.channels.get(channelId);
        return channel != null && channel.owner.equals(account) ? channel : null;
    }

    public static Result leaveChannel(ServerPlayer player, String channelId)
    {
        String account = account(player);
        GlassNetworkData data = data(player.level().getServer());
        GlassNetworkData.Channel channel = data.channels.get(channelId);
        if(account == null || channel == null || !channel.isMember(account))
        {
            return Result.fail("glass.msg.noAccess");
        }
        if(channel.owner.equals(account))
        {
            return Result.fail("glass.msg.ownerCannotLeave");
        }
        channel.members.remove(account);
        data.setDirty();
        return Result.ok("glass.msg.left", channel.name);
    }

    public static Result deleteChannel(ServerPlayer player, String channelId)
    {
        GlassNetworkData data = data(player.level().getServer());
        GlassNetworkData.Channel channel = owned(data, account(player), channelId);
        if(channel == null)
        {
            return Result.fail("glass.msg.ownerOnly");
        }
        data.channels.remove(channelId);
        data.setDirty();
        return Result.ok("glass.msg.channelDeleted", channel.name);
    }

    /** Also signs every member but the owner out of the channel: they need the new password. */
    public static Result changeChannelPassword(ServerPlayer player, String channelId, String password)
    {
        GlassNetworkData data = data(player.level().getServer());
        GlassNetworkData.Channel channel = owned(data, account(player), channelId);
        if(channel == null)
        {
            return Result.fail("glass.msg.ownerOnly");
        }
        if(!validPassword(password))
        {
            return Result.fail("glass.msg.badPassword");
        }
        PasswordHasher.Hash hash = PasswordHasher.hash(password);
        channel.salt = hash.salt();
        channel.hash = hash.hash();
        channel.iterations = hash.iterations();
        channel.members.removeIf(member -> !member.equals(channel.owner));
        data.setDirty();
        return Result.ok("glass.msg.passwordChanged", channel.name);
    }

    public static Result kickMember(ServerPlayer player, String channelId, String member)
    {
        GlassNetworkData data = data(player.level().getServer());
        GlassNetworkData.Channel channel = owned(data, account(player), channelId);
        if(channel == null)
        {
            return Result.fail("glass.msg.ownerOnly");
        }
        String key = GlassNetworkData.key(member);
        if(key.equals(channel.owner) || !channel.members.remove(key))
        {
            return Result.fail("glass.msg.noSuchMember");
        }
        data.setDirty();
        return Result.ok("glass.msg.kicked", member);
    }

    public static Result removeCamera(ServerPlayer player, String channelId, String cameraId)
    {
        GlassNetworkData data = data(player.level().getServer());
        GlassNetworkData.Channel channel = owned(data, account(player), channelId);
        if(channel == null)
        {
            return Result.fail("glass.msg.ownerOnly");
        }
        GlassNetworkData.Camera camera = channel.cameras.remove(cameraId);
        if(camera == null)
        {
            return Result.fail("glass.msg.noSuchCamera");
        }
        data.setDirty();
        return Result.ok("glass.msg.cameraRemoved", camera.name());
    }

    // ---- Cameras (called by terminals) ----

    /** Puts a camera on a channel, or moves/renames it. Returns the camera id, or a failure. */
    public static Result linkCamera(ServerPlayer player, String channelId, @Nullable String existingCameraId, @Nullable String existingChannelId,
                                    String name, ResourceKey<Level> dimension, BlockPos pos, Direction facing)
    {
        String account = account(player);
        if(account == null)
        {
            return Result.fail("glass.msg.notLoggedIn");
        }
        name = name.trim();
        if(name.isEmpty() || name.length() > MAX_CAMERA_NAME)
        {
            return Result.fail("glass.msg.badCameraName");
        }
        GlassNetworkData data = data(player.level().getServer());
        GlassNetworkData.Channel channel = data.channels.get(channelId);
        if(channel == null || !channel.isMember(account))
        {
            return Result.fail("glass.msg.noAccess");
        }
        boolean sameChannel = channelId.equals(existingChannelId) && existingCameraId != null && channel.cameras.containsKey(existingCameraId);
        if(!sameChannel && channel.cameras.size() >= GlassConfig.get().maxCamerasPerChannel)
        {
            return Result.fail("glass.msg.tooManyCameras", Integer.toString(GlassConfig.get().maxCamerasPerChannel));
        }
        if(existingChannelId != null && existingCameraId != null && !sameChannel)
        {
            GlassNetworkData.Channel old = data.channels.get(existingChannelId);
            if(old != null)
            {
                old.cameras.remove(existingCameraId);
            }
        }
        String id = sameChannel ? existingCameraId : newId(channel.cameras);
        channel.cameras.put(id, new GlassNetworkData.Camera(id, name, dimension, pos.immutable(), facing));
        data.setDirty();
        return Result.ok("glass.msg.cameraLinked", id);
    }

    /** A terminal was broken or unlinked: take its camera off the network. */
    public static void forgetCamera(MinecraftServer server, String channelId, String cameraId)
    {
        GlassNetworkData data = data(server);
        GlassNetworkData.Channel channel = data.channels.get(channelId);
        if(channel != null && channel.cameras.remove(cameraId) != null)
        {
            data.setDirty();
            syncChannelMembers(server, channel);
        }
    }

    /**
     * Removes cameras whose terminal is gone. Breaking a terminal removes its camera straight away, but some removals
     * skip that (/setblock, other mods), so projectors check the cameras they use: a camera in a loaded chunk with no
     * matching terminal is dropped. Cameras in unloaded chunks are left alone.
     */
    public static void dropMissingCameras(MinecraftServer server, GlassNetworkData.Channel channel)
    {
        boolean removed = channel.cameras.values().removeIf(camera -> {
            var level = server.getLevel(camera.dimension());
            if(level == null || !level.isLoaded(camera.pos()))
            {
                return false;
            }
            return !(level.getBlockEntity(camera.pos()) instanceof ryzlar.glass.blockentity.GlassTerminalBlockEntity terminal) || !terminal.cameraId.equals(camera.id());
        });
        if(removed)
        {
            data(server).setDirty();
            syncChannelMembers(server, channel);
        }
    }

    // ---- Syncing ----

    public static void sendState(ServerPlayer player)
    {
        if(ServerPlayNetworking.canSend(player, GlassNetworking.NetworkState.TYPE))
        {
            ServerPlayNetworking.send(player, buildState(player));
        }
    }

    public static GlassNetworking.NetworkState buildState(ServerPlayer player)
    {
        GlassNetworkData data = data(player.level().getServer());
        String key = account(player);
        GlassNetworkData.Account account = key == null ? null : data.accounts.get(key);
        if(account == null)
        {
            return new GlassNetworking.NetworkState("", List.of());
        }
        List<GlassNetworking.ChannelInfo> channels = new ArrayList<>();
        for(GlassNetworkData.Channel channel : data.channels.values())
        {
            if(!channel.isMember(key))
            {
                continue;
            }
            boolean owner = channel.owner.equals(key);
            List<String> members = new ArrayList<>();
            if(owner)
            {
                for(String member : channel.members)
                {
                    GlassNetworkData.Account a = data.accounts.get(member);
                    members.add(a != null ? a.username() : member);
                }
            }
            GlassNetworkData.Account ownerAccount = data.accounts.get(channel.owner);
            List<GlassNetworking.CameraInfo> cameras = new ArrayList<>();
            for(GlassNetworkData.Camera camera : channel.cameras.values())
            {
                cameras.add(new GlassNetworking.CameraInfo(camera.id(), camera.name(), camera.dimension().location().toString(), camera.pos(), camera.facing()));
            }
            channels.add(new GlassNetworking.ChannelInfo(channel.id, channel.name, ownerAccount != null ? ownerAccount.username() : channel.owner, owner, members, cameras));
        }
        return new GlassNetworking.NetworkState(account.username(), channels);
    }

    /** Re-sends the network state to every online player logged in to a member account of this channel. */
    public static void syncChannelMembers(MinecraftServer server, GlassNetworkData.Channel channel)
    {
        for(ServerPlayer player : server.getPlayerList().getPlayers())
        {
            String account = account(player);
            if(account != null && channel.isMember(account))
            {
                sendState(player);
            }
        }
    }

    /** After anything changed: every logged-in player gets a fresh state (cheap: a few channels each). */
    public static void syncAll(MinecraftServer server)
    {
        for(ServerPlayer player : server.getPlayerList().getPlayers())
        {
            sendState(player);
        }
    }
}
