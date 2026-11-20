package ryzlar.glass.holo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A room scan held by a holo cube: the projection, and where in it the scan was made (the block the scanner stood in,
 * from the projection's corner) and which way the scanner faced (quarter turns), so the cube can lay the room out
 * around where it is thrown, facing the way the thrower faces. It also keeps the sky over the room as it was scanned
 * (the time of day, so sun, moon and stars, and the weather), shown overhead inside the simulation; {@link #NO_SKY}
 * for a scan made where there is no sky, or before skies were kept.
 * <p>
 * {@code version}: what kind of scan it is. Only {@link #VERSION} (a ball round the cube, see {@link HoloSphere}) can be
 * projected; a cube holding an older room scan counts as empty and has to scan again.
 */
public record CubeScan(String id, String name, int sizeX, int sizeY, int sizeZ, int originX, int originY, int originZ, int facing,
                       long skyTime, float rain, float thunder, int version)
{
    public static final long NO_SKY = -1L;
    /** A ball scan with everything in it. 1: the old room scans (and before versions were kept). */
    public static final int VERSION = 2;

    public boolean isCurrent()
    {
        return version == VERSION;
    }

    public static final Codec<CubeScan> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(CubeScan::id),
            Codec.STRING.fieldOf("name").forGetter(CubeScan::name),
            Codec.INT.fieldOf("sizeX").forGetter(CubeScan::sizeX),
            Codec.INT.fieldOf("sizeY").forGetter(CubeScan::sizeY),
            Codec.INT.fieldOf("sizeZ").forGetter(CubeScan::sizeZ),
            Codec.INT.fieldOf("originX").forGetter(CubeScan::originX),
            Codec.INT.fieldOf("originY").forGetter(CubeScan::originY),
            Codec.INT.fieldOf("originZ").forGetter(CubeScan::originZ),
            Codec.INT.fieldOf("facing").forGetter(CubeScan::facing),
            Codec.LONG.optionalFieldOf("skyTime", NO_SKY).forGetter(CubeScan::skyTime),
            Codec.FLOAT.optionalFieldOf("rain", 0F).forGetter(CubeScan::rain),
            Codec.FLOAT.optionalFieldOf("thunder", 0F).forGetter(CubeScan::thunder),
            Codec.INT.optionalFieldOf("version", 1).forGetter(CubeScan::version)
    ).apply(i, CubeScan::new));

    public static final StreamCodec<ByteBuf, CubeScan> STREAM_CODEC = new StreamCodec<>()
    {
        @Override
        public CubeScan decode(ByteBuf buf)
        {
            return new CubeScan(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.FLOAT.decode(buf),
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
            ByteBufCodecs.VAR_LONG.encode(buf, scan.skyTime());
            ByteBufCodecs.FLOAT.encode(buf, scan.rain());
            ByteBufCodecs.FLOAT.encode(buf, scan.thunder());
            ByteBufCodecs.VAR_INT.encode(buf, scan.version());
        }
    };

    public boolean hasSky()
    {
        return skyTime != NO_SKY;
    }
}
