package ryzlar.glass.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.network.GlassNetworking;

import java.util.List;

/**
 * What the client knows about the GLASS channels (every channel's name, owner and whether you may use it) and the
 * server's answer to the last action. Screens read this and redraw when {@link #version()} changes.
 */
public final class GlassNetClient
{
    private static GlassNetworking.NetworkState state = new GlassNetworking.NetworkState(List.of());
    private static int version;
    @Nullable
    private static GlassNetworking.ActionResult lastResult;
    private static long lastResultTime;

    private GlassNetClient() {}

    public static void init()
    {
        ClientPlayNetworking.registerGlobalReceiver(GlassNetworking.NetworkState.TYPE, (payload, context) -> {
            state = payload;
            version++;
        });
        ClientPlayNetworking.registerGlobalReceiver(GlassNetworking.ActionResult.TYPE, (payload, context) -> {
            lastResult = payload;
            lastResultTime = Util.getMillis();
            version++;
        });
    }

    public static void reset()
    {
        state = new GlassNetworking.NetworkState(List.of());
        lastResult = null;
        version++;
    }

    public static int version()
    {
        return version;
    }

    public static List<GlassNetworking.ChannelInfo> channels()
    {
        return state.channels();
    }

    @Nullable
    public static GlassNetworking.ChannelInfo channel(String id)
    {
        for(GlassNetworking.ChannelInfo channel : state.channels())
        {
            if(channel.id().equals(id))
            {
                return channel;
            }
        }
        return null;
    }

    /** The last server reply, if it is recent enough to still show. */
    @Nullable
    public static GlassNetworking.ActionResult recentResult(long maxAgeMillis)
    {
        return lastResult != null && Util.getMillis() - lastResultTime < maxAgeMillis ? lastResult : null;
    }

    public static void clearResult()
    {
        lastResult = null;
    }

    public static Component describe(GlassNetworking.ActionResult result)
    {
        return Component.translatable(result.message(), result.arg());
    }

    public static void send(GlassNetworking.Action action, String... args)
    {
        if(ClientPlayNetworking.canSend(GlassNetworking.NetAction.TYPE))
        {
            ClientPlayNetworking.send(GlassNetworking.NetAction.of(action, args));
        }
    }
}
