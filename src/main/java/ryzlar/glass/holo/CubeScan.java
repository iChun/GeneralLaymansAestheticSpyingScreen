package ryzlar.glass.holo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A room scan held by a holo cube: the projection, and where in it the scan was made (the block the scanner stood in,
 * from the projection's corner) and which way the scanner faced (quarter turns), so the cube can lay the room out
 * around where it is thrown, facing the way the thrower faces.
 */
public record CubeScan(String id, String name, int sizeX, int sizeY, int sizeZ, int originX, int originY, int originZ, int facing)
{
    public static final Codec<CubeScan> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(CubeScan::id),
            Codec.STRING.fieldOf("name").forGetter(CubeScan::name),
            Codec.INT.fieldOf("sizeX").forGetter(CubeScan::sizeX),
            Codec.INT.fieldOf("sizeY").forGetter(CubeScan::sizeY),
            Codec.INT.fieldOf("sizeZ").forGetter(CubeScan::sizeZ),
            Codec.INT.fieldOf("originX").forGetter(CubeScan::originX),
            Codec.INT.fieldOf("originY").forGetter(CubeScan::originY),
            Codec.INT.fieldOf("originZ").forGetter(CubeScan::originZ),
            Codec.INT.fieldOf("facing").forGetter(CubeScan::facing)
    ).apply(i, CubeScan::new));

    public static final StreamCodec<ByteBuf, CubeScan> STREAM_CODEC = new StreamCodec<>()
    {
        @Override
        public CubeScan decode(ByteBuf buf)
        {
            return new CubeScan(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf));
        }

        @Override
        public void encode(ByteBuf buf, CubeScan scan)
        {
            ByteBufCodecs.STRING_UTF8.encode(buf, scan.id());
            ByteBufCodecs.STRING_UTF8.encode(buf, scan.name());
            ByteBufCodecs.VAR_INT.encode(buf, scan.sizeX());
            ByteBufCodecs.VAR_INT.encode(buf, scan.sizeY());
            ByteBufCodecs.VAR_INT.encode(buf, scan.sizeZ());
            ByteBufCodecs.VAR_INT.encode(buf, scan.originX());
            ByteBufCodecs.VAR_INT.encode(buf, scan.originY());
            ByteBufCodecs.VAR_INT.encode(buf, scan.originZ());
            ByteBufCodecs.VAR_INT.encode(buf, scan.facing());
        }
    };
}
