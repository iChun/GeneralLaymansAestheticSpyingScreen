package ryzlar.glass.gadget;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The channel a GLASS tablet shows. */
public record TabletLink(String channelId, String channelName)
{
    public static final Codec<TabletLink> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("channel").forGetter(TabletLink::channelId),
            Codec.STRING.fieldOf("name").forGetter(TabletLink::channelName)
    ).apply(i, TabletLink::new));

    public static final StreamCodec<ByteBuf, TabletLink> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, TabletLink::channelId,
            ByteBufCodecs.STRING_UTF8, TabletLink::channelName,
            TabletLink::new);
}
