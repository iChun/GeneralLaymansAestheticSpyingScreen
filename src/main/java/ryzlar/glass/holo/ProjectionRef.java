package ryzlar.glass.holo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** What a projection crystal holds: which projection, its name and size (for the tooltip and the projector's screen). */
public record ProjectionRef(String id, String name, int sizeX, int sizeY, int sizeZ)
{
    public static final Codec<ProjectionRef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(ProjectionRef::id),
            Codec.STRING.fieldOf("name").forGetter(ProjectionRef::name),
            Codec.INT.fieldOf("sizeX").forGetter(ProjectionRef::sizeX),
            Codec.INT.fieldOf("sizeY").forGetter(ProjectionRef::sizeY),
            Codec.INT.fieldOf("sizeZ").forGetter(ProjectionRef::sizeZ)
    ).apply(i, ProjectionRef::new));

    public static final StreamCodec<ByteBuf, ProjectionRef> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ProjectionRef::id,
            ByteBufCodecs.STRING_UTF8, ProjectionRef::name,
            ByteBufCodecs.VAR_INT, ProjectionRef::sizeX,
            ByteBufCodecs.VAR_INT, ProjectionRef::sizeY,
            ByteBufCodecs.VAR_INT, ProjectionRef::sizeZ,
            ProjectionRef::new);
}
