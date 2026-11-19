package ryzlar.glass.client;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * A GLASS tablet's view: the channel's terminal looking out, the way it faces, drawn by the spy view renderer like a
 * screen's view. It stays wanted while the tablet's screen keeps asking for it.
 */
public class TabletPlacement extends TerminalPlacement
{
    private static final long KEEP_MILLIS = 1000L;
    private final BlockPos terminal;
    private final Direction facing;
    private long wantedAt = Util.getMillis();

    public TabletPlacement(String channelId, BlockPos terminal, Direction facing)
    {
        super("tablet@" + channelId, terminal, facing);
        this.terminal = terminal.immutable();
        this.facing = facing;
    }

    public boolean shows(BlockPos pos, Direction direction)
    {
        return terminal.equals(pos) && facing == direction;
    }

    /** The tablet's screen is open and asking for this view. */
    public void keep()
    {
        wantedAt = Util.getMillis();
    }

    @Override
    public boolean isValid()
    {
        return Util.getMillis() - wantedAt < KEEP_MILLIS;
    }

    @Override
    public boolean isSameScreen()
    {
        return isValid();
    }

    @Override
    public Vec3 mapPosition(Vec3 pos)
    {
        return fixedEye();
    }

    @Override
    public Quaternionf mapRotation(Quaternionf cameraRotation)
    {
        return fixedRotation();
    }
}
