package ryzlar.glass.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.GlassConfig;
import ryzlar.glass.blockentity.GlassTerminalBlockEntity;

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
 * Server side of the GLASS channels. All methods run on the server thread.
 * <p>
 * A channel is created at a terminal (which becomes its camera), owned by that player (by UUID), and protected by a
 * password. Other players unlock it once with the password and can then show it on their projectors.
 */
public final class GlassNetworkManager
{
    public static final Pattern CHANNEL_NAME = Pattern.compile("[A-Za-z0-9_\\- ]{1,24}");
    public static final int MIN_PASSWORD = 4;
    public static final int MAX_PASSWORD = 64;

    private static final SecureRandom RANDOM = new SecureRandom();
    /** When each player last got a password wrong, and last made the server hash a new one (both kept for a minute). */
    private static final Map<UUID, Deque<Long>> FAILURES = new HashMap<>();
    private static final Map<UUID, Deque<Long>> NEW_PASSWORDS = new HashMap<>();
    private static final long WINDOW_MILLIS = 60_000L;

    private GlassNetworkManager() {}

    /** A reply for the screen: ok or not, a translation key and one argument. */
    public record Result(boolean ok, String message, String arg)
    {
        public static Result ok(String message, String arg)
        {
            return new Result(true, message, arg);
        }

        public static Result fail(String message)
        {
            return new Result(false, message, "");
        }

        public static Result fail(String message, String arg)
        {
            return new Result(false, message, arg);
        }
    }

    public static GlassNetworkData data(MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(GlassNetworkData.TYPE);
    }

    // ---- Players ----

    public static void onJoin(ServerPlayer player)
    {
        //Keep owner names up to date (Minecraft names can change; the UUID cannot).
        GlassNetworkData data = data(player.level().getServer());
        String name = player.getGameProfile().getName();
        boolean renamed = false;
        for(GlassNetworkData.Channel channel : data.channels.values())
        {
            if(channel.isOwner(player.getUUID()) && !channel.ownerName.equals(name))
            {
                channel.ownerName = name;
                renamed = true;
            }
        }
        if(renamed)
        {
            data.setDirty();
            syncAll(player.level().getServer());
        }
        else
        {
            sendState(player);
        }
    }

    /**
     * The wrong passwords are kept when a player leaves (or logging out and in again would reset the limit); only the
     * ones older than a minute are let go.
     */
    public static void onLeave(ServerPlayer player)
    {
        long cutoff = System.currentTimeMillis() - WINDOW_MILLIS;
        FAILURES.values().removeIf(times -> expire(times, cutoff));
        NEW_PASSWORDS.values().removeIf(times -> expire(times, cutoff));
    }

    public static void onServerStopped()
    {
        FAILURES.clear();
        NEW_PASSWORDS.clear();
    }

    /** Drops the times before the cutoff; true if none are left. */
    private static boolean expire(Deque<Long> times, long cutoff)
    {
        while(!times.isEmpty() && times.peekFirst() < cutoff)
        {
            times.pollFirst();
        }
        return times.isEmpty();
    }

    /** True if the player did this as often as allowed in the last minute. */
    private static boolean atLimit(Map<UUID, Deque<Long>> log, ServerPlayer player, int perMinute)
    {
        Deque<Long> times = log.get(player.getUUID());
        if(times == null)
        {
            return false;
        }
        if(expire(times, System.currentTimeMillis() - WINDOW_MILLIS))
        {
            log.remove(player.getUUID());
            return false;
        }
        return times.size() >= perMinute;
    }

    private static void record(Map<UUID, Deque<Long>> log, ServerPlayer player)
    {
        log.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>()).addLast(System.currentTimeMillis());
    }

    private static boolean tooManyFailures(ServerPlayer player)
    {
        return atLimit(FAILURES, player, GlassConfig.get().failedPasswordsPerMinute);
    }

    private static void recordFailure(ServerPlayer player)
    {
        record(FAILURES, player);
    }

    /**
     * Hashing a password is slow on purpose (that is what makes guessing it slow), so setting one (a new channel, a
     * changed password) is limited too, or a player could stall the server by asking for it over and over.
     */
    private static boolean tooManyNewPasswords(ServerPlayer player)
    {
        return atLimit(NEW_PASSWORDS, player, GlassConfig.get().newPasswordsPerMinute);
    }

    private static boolean validPassword(String password)
    {
        return password.length() >= MIN_PASSWORD && password.length() <= MAX_PASSWORD;
    }

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

    // ---- Channels ----

    /** Makes a new channel with this terminal as its camera. */
    public static Result createChannel(ServerPlayer player, GlassTerminalBlockEntity terminal, String name, String password)
    {
        name = name.trim();
        if(!CHANNEL_NAME.matcher(name).matches())
        {
            return Result.fail("glass.msg.badChannelName");
        }
        if(!validPassword(password))
        {
            return Result.fail("glass.msg.badPassword");
        }
        MinecraftServer server = player.level().getServer();
        GlassNetworkData data = data(server);
        if(data.channelByName(name) != null)
        {
            return Result.fail("glass.msg.channelTaken");
        }
        long owned = data.channels.values().stream().filter(c -> c.isOwner(player.getUUID())).count();
        if(owned >= GlassConfig.get().maxChannelsPerPlayer)
        {
            return Result.fail("glass.msg.tooManyChannels", Integer.toString(GlassConfig.get().maxChannelsPerPlayer));
        }
        GlassNetworkData.Channel current = data.channels.get(terminal.channelId);
        if(current != null && !current.isOwner(player.getUUID()))
        {
            return Result.fail("glass.msg.notYourCamera");
        }
        if(tooManyNewPasswords(player))
        {
            return Result.fail("glass.msg.tooManyPasswords");
        }
        record(NEW_PASSWORDS, player);
        PasswordHasher.Hash hash = PasswordHasher.hash(password);
        GlassNetworkData.Channel channel = new GlassNetworkData.Channel(newId(data.channels), name, player.getUUID(), player.getGameProfile().getName(),
                hash.salt(), hash.hash(), hash.iterations(), List.of(), java.util.Optional.empty());
        data.channels.put(channel.id, channel);
        attach(server, data, channel, terminal);
        return Result.ok("glass.msg.channelCreated", name);
    }

    /** Owner only: makes this terminal the channel's camera (the old camera terminal, if any, is unlinked). */
    public static Result moveCamera(ServerPlayer player, GlassTerminalBlockEntity terminal, String channelId)
    {
        MinecraftServer server = player.level().getServer();
        GlassNetworkData data = data(server);
        GlassNetworkData.Channel channel = data.channels.get(channelId);
        if(channel == null || !channel.isOwner(player.getUUID()))
        {
            return Result.fail("glass.msg.ownerOnly");
        }
        GlassNetworkData.Channel current = data.channels.get(terminal.channelId);
        if(current != null && !current.isOwner(player.getUUID()))
        {
            return Result.fail("glass.msg.notYourCamera");
        }
        attach(server, data, channel, terminal);
        return Result.ok("glass.msg.cameraMoved", channel.name);
    }

    private static void attach(MinecraftServer server, GlassNetworkData data, GlassNetworkData.Channel channel, GlassTerminalBlockEntity terminal)
    {
        //This terminal stops being the camera of whatever channel it was on.
        GlassNetworkData.Channel previous = data.channels.get(terminal.channelId);
        if(previous != null && previous != channel)
        {
            previous.camera = null;
        }
        //The channel's old terminal stops being its camera.
        unlinkTerminal(server, channel.camera);
        channel.camera = new GlassNetworkData.Camera(terminal.getLevel().dimension(), terminal.getBlockPos().immutable(), terminal.facing);
        terminal.channelId = channel.id;
        terminal.sync();
        data.setDirty();
    }

    private static void unlinkTerminal(MinecraftServer server, @Nullable GlassNetworkData.Camera camera)
    {
        if(camera == null)
        {
            return;
        }
        ServerLevel level = server.getLevel(camera.dimension());
        if(level != null && level.isLoaded(camera.pos()) && level.getBlockEntity(camera.pos()) instanceof GlassTerminalBlockEntity old)
        {
            old.channelId = "";
            old.sync();
        }
    }

    /** Owner only: this terminal is no longer the channel's camera (the channel stays). */
    public static Result detachCamera(ServerPlayer player, GlassTerminalBlockEntity terminal)
    {
        GlassNetworkData data = data(player.level().getServer());
        GlassNetworkData.Channel channel = data.channels.get(terminal.channelId);
        if(channel != null && !channel.isOwner(player.getUUID()))
        {
            return Result.fail("glass.msg.ownerOnly");
        }
        if(channel != null)
        {
            channel.camera = null;
            data.setDirty();
        }
        terminal.channelId = "";
        terminal.sync();
        return Result.ok("glass.msg.cameraDetached", channel != null ? channel.name : "");
    }

    public static Result deleteChannel(ServerPlayer player, String channelId)
    {
        MinecraftServer server = player.level().getServer();
        GlassNetworkData data = data(server);
        GlassNetworkData.Channel channel = data.channels.get(channelId);
        if(channel == null || !channel.isOwner(player.getUUID()))
        {
            return Result.fail("glass.msg.ownerOnly");
        }
        unlinkTerminal(server, channel.camera);
        data.channels.remove(channelId);
        data.setDirty();
        return Result.ok("glass.msg.channelDeleted", channel.name);
    }

    /** Owner only. Everyone else has to enter the new password. */
    public static Result changePassword(ServerPlayer player, String channelId, String password)
    {
        GlassNetworkData data = data(player.level().getServer());
        GlassNetworkData.Channel channel = data.channels.get(channelId);
        if(channel == null || !channel.isOwner(player.getUUID()))
        {
            return Result.fail("glass.msg.ownerOnly");
        }
        if(!validPassword(password))
        {
            return Result.fail("glass.msg.badPassword");
        }
        if(tooManyNewPasswords(player))
        {
            return Result.fail("glass.msg.tooManyPasswords");
        }
        record(NEW_PASSWORDS, player);
        PasswordHasher.Hash hash = PasswordHasher.hash(password);
        channel.salt = hash.salt();
        channel.hash = hash.hash();
        channel.iterations = hash.iterations();
        channel.members.clear();
        data.setDirty();
        return Result.ok("glass.msg.passwordChanged", channel.name);
    }

    /** Gives a player access to a channel if the password is right. */
    public static Result unlock(ServerPlayer player, String channelId, String password)
    {
        GlassNetworkData data = data(player.level().getServer());
        GlassNetworkData.Channel channel = data.channels.get(channelId);
        if(channel == null)
        {
            return Result.fail("glass.msg.noSuchChannel");
        }
        if(channel.hasAccess(player.getUUID()))
        {
            return Result.ok("glass.msg.unlocked", channel.name);
        }
        if(tooManyFailures(player))
        {
            return Result.fail("glass.msg.tooManyAttempts");
        }
        if(!PasswordHasher.verify(password, channel.salt, channel.hash, channel.iterations))
        {
            recordFailure(player);
            return Result.fail("glass.msg.wrongPassword");
        }
        channel.members.add(player.getUUID());
        data.setDirty();
        return Result.ok("glass.msg.unlocked", channel.name);
    }

    public static boolean hasAccess(ServerPlayer player, String channelId)
    {
        GlassNetworkData.Channel channel = data(player.level().getServer()).channels.get(channelId);
        return channel != null && channel.hasAccess(player.getUUID());
    }

    // ---- Keeping cameras honest ----

    /** A terminal was broken: its channel has no camera until the owner links another. */
    public static void forgetCamera(MinecraftServer server, String channelId, ServerLevel level, net.minecraft.core.BlockPos pos)
    {
        GlassNetworkData data = data(server);
        GlassNetworkData.Channel channel = data.channels.get(channelId);
        if(channel != null && channel.camera != null && channel.camera.pos().equals(pos) && channel.camera.dimension().equals(level.dimension()))
        {
            channel.camera = null;
            data.setDirty();
            syncAll(server);
        }
    }

    /**
     * Breaking a terminal removes its camera straight away, but some removals skip that (/setblock, other mods), so
     * projectors check the camera they show: one in a loaded chunk with no matching terminal is dropped.
     */
    public static void dropMissingCamera(MinecraftServer server, GlassNetworkData.Channel channel)
    {
        GlassNetworkData.Camera camera = channel.camera;
        if(camera == null)
        {
            return;
        }
        ServerLevel level = server.getLevel(camera.dimension());
        if(level == null || !level.isLoaded(camera.pos()))
        {
            return;
        }
        if(!(level.getBlockEntity(camera.pos()) instanceof GlassTerminalBlockEntity terminal) || !terminal.channelId.equals(channel.id))
        {
            channel.camera = null;
            data(server).setDirty();
            syncAll(server);
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

    /** Every channel's name, owner and status (names are public, so they can be searched); never passwords. */
    public static GlassNetworking.NetworkState buildState(ServerPlayer player)
    {
        GlassNetworkData data = data(player.level().getServer());
        List<GlassNetworking.ChannelInfo> channels = new ArrayList<>();
        for(GlassNetworkData.Channel channel : data.channels.values())
        {
            channels.add(new GlassNetworking.ChannelInfo(channel.id, channel.name, channel.ownerName,
                    channel.isOwner(player.getUUID()), channel.hasAccess(player.getUUID()), channel.camera != null));
        }
        return new GlassNetworking.NetworkState(channels);
    }

    public static void syncAll(MinecraftServer server)
    {
        for(ServerPlayer player : server.getPlayerList().getPlayers())
        {
            sendState(player);
        }
    }
}
