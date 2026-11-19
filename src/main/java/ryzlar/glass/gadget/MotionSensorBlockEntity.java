package ryzlar.glass.gadget;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.blockentity.GlassTerminalBlockEntity;

/** Watches through the terminal next to it, a few times a second. */
public class MotionSensorBlockEntity extends BlockEntity
{
    /** How far the terminal's view reaches for the sensor, and how wide it is (half the angle, in degrees). */
    public static final double RANGE = 16D;
    private static final double HALF_ANGLE = 55D;
    private static final int CHECK_TICKS = 5;
    /** How often a running alert is sent again (ticks), and how long each keeps the screens flashing. */
    private static final int ALERT_REPEAT = 20;
    public static final int ALERT_TICKS = 30;

    private int timer;
    private int sinceAlert = ALERT_REPEAT;

    public MotionSensorBlockEntity(BlockPos pos, BlockState state)
    {
        super(Gadgets.MOTION_SENSOR_BE, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MotionSensorBlockEntity sensor)
    {
        sensor.sinceAlert++;
        if(++sensor.timer < CHECK_TICKS)
        {
            return;
        }
        sensor.timer = 0;
        GlassTerminalBlockEntity terminal = sensor.terminal(level);
        boolean seen = terminal != null && sensor.seesSomeone(level, terminal);
        if(seen != state.getValue(MotionSensorBlock.POWERED))
        {
            level.setBlock(pos, state.setValue(MotionSensorBlock.POWERED, seen), Block.UPDATE_ALL);
            if(seen)
            {
                sensor.sinceAlert = ALERT_REPEAT; //alert at once
            }
        }
        if(seen && sensor.sinceAlert >= ALERT_REPEAT && !terminal.channelId.isEmpty() && level instanceof ServerLevel server)
        {
            sensor.sinceAlert = 0;
            GadgetNetworking.sendAlert(server, terminal.channelId);
        }
    }

    /** The terminal right next to it, if any. */
    @Nullable
    public GlassTerminalBlockEntity terminal(Level level)
    {
        for(Direction direction : Direction.values())
        {
            if(level.getBlockEntity(worldPosition.relative(direction)) instanceof GlassTerminalBlockEntity terminal)
            {
                return terminal;
            }
        }
        return null;
    }

    /** Someone the terminal can see: in front of it, within its view and range, with nothing in between. */
    public boolean seesSomeone(Level level, GlassTerminalBlockEntity terminal)
    {
        Direction facing = terminal.facing;
        Vec3 look = Vec3.atLowerCornerOf(facing.getUnitVec3i());
        Vec3 eye = Vec3.atCenterOf(terminal.getBlockPos()).add(look.scale(0.55D));
        double cos = Math.cos(Math.toRadians(HALF_ANGLE));
        AABB around = new AABB(terminal.getBlockPos()).inflate(RANGE);
        for(LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, around, MotionSensorBlockEntity::isWatched))
        {
            Vec3 target = entity.getBoundingBox().getCenter();
            Vec3 to = target.subtract(eye);
            double distance = to.length();
            if(distance > RANGE || distance < 1.0E-3D || to.scale(1D / distance).dot(look) < cos)
            {
                continue;
            }
            HitResult hit = level.clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
            if(hit.getType() == HitResult.Type.MISS)
            {
                return true;
            }
        }
        return false;
    }

    /** Players (not spectators), monsters, and decoys (made to be seen). */
    private static boolean isWatched(LivingEntity entity)
    {
        if(!entity.isAlive())
        {
            return false;
        }
        if(entity instanceof HoloDecoyEntity)
        {
            return true;
        }
        return entity instanceof Player player ? !player.isSpectator() : entity instanceof Enemy;
    }
}
