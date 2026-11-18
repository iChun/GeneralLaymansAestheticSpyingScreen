package ryzlar.glass.holo;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.GlassRegistry;
import ryzlar.glass.network.GlassNetworkManager;
import ryzlar.glass.network.GlassNetworking;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Room scans with a holo drone, done on the server so what ends up in the scan is what the player really looked at.
 * <p>
 * Starting a scan finds the room: the open space around the player (flooded out to the walls, within a box), and the
 * surfaces around that space. While the player holds the drone, it casts a fan of rays where they look every tick; every
 * block a ray hits is scanned. Once nearly every surface of the room has been hit, the scan is done (or earlier, when the
 * player says so) and is stored in the drone.
 */
public final class HoloScanSessions
{
    /** The box a room is looked for in, around the block the player stands in. */
    private static final int HALF_WIDTH = 16;
    private static final int BELOW = 4;
    private static final int ABOVE = 12;
    private static final int RAYS_PER_TICK = 40;
    private static final double RAY_LENGTH = 24D;
    /** How far rays fan out around where the player looks (radians). */
    private static final double SPREAD = 0.45D;
    /** The scan is done once this much of the room's surfaces were hit. */
    public static final float DONE_AT = 0.95F;
    private static final int STATUS_INTERVAL = 5;

    private static final class Session
    {
        final ServerLevel level;
        final BlockPos start;
        final int facing;
        final BlockPos min;
        final BlockPos max;
        final LongSet surfaces;
        final LongSet scanned = new LongOpenHashSet();
        int surfacesHit;
        int timer;
        float lastSent = -1F;

        Session(ServerLevel level, BlockPos start, int facing, BlockPos min, BlockPos max, LongSet surfaces)
        {
            this.level = level;
            this.start = start;
            this.facing = facing;
            this.min = min;
            this.max = max;
            this.surfaces = surfaces;
        }

        float progress()
        {
            return surfaces.isEmpty() ? 0F : Math.min(1F, (float)surfacesHit / surfaces.size());
        }

        boolean contains(BlockPos pos)
        {
            return pos.getX() >= min.getX() && pos.getY() >= min.getY() && pos.getZ() >= min.getZ()
                    && pos.getX() <= max.getX() && pos.getY() <= max.getY() && pos.getZ() <= max.getZ();
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private HoloScanSessions() {}

    public static boolean isScanning(ServerPlayer player)
    {
        return SESSIONS.containsKey(player.getUUID());
    }

    /** Starts a scan of the room the player stands in. */
    public static void start(ServerPlayer player)
    {
        ServerLevel level = player.level();
        BlockPos start = player.blockPosition();
        BlockPos min = start.offset(-HALF_WIDTH, -BELOW, -HALF_WIDTH);
        BlockPos max = start.offset(HALF_WIDTH - 1, ABOVE, HALF_WIDTH - 1);
        LongSet surfaces = findSurfaces(level, BlockPos.containing(player.getEyePosition()), min, max);
        if(surfaces.isEmpty())
        {
            GlassNetworking.reply(player, GlassNetworkManager.Result.fail("glass.msg.scanNoRoom"));
            return;
        }
        int facing = Math.floorMod(Math.round(player.getYRot() / 90F), 4);
        SESSIONS.put(player.getUUID(), new Session(level, start, facing, min, max, surfaces));
        sendStatus(player, SESSIONS.get(player.getUUID()), true);
        GlassNetworking.reply(player, GlassNetworkManager.Result.ok("glass.msg.scanStarted", ""));
    }

    public static void stop(ServerPlayer player)
    {
        if(SESSIONS.remove(player.getUUID()) != null)
        {
            ServerPlayNetworking.send(player, new HoloNetworking.ScanStatus(false, 0F, BlockPos.ZERO, BlockPos.ZERO));
        }
    }

    /**
     * The room: flood the open space (anything without collision) from the player's eye, inside the box; its surfaces are
     * the solid blocks touching it.
     */
    private static LongSet findSurfaces(Level level, BlockPos from, BlockPos min, BlockPos max)
    {
        LongSet open = new LongOpenHashSet();
        LongSet surfaces = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        if(!isOpen(level, from))
        {
            return surfaces;
        }
        open.add(from.asLong());
        queue.enqueue(from.asLong());
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
        while(!queue.isEmpty())
        {
            BlockPos pos = BlockPos.of(queue.dequeueLong());
            for(Direction direction : Direction.values())
            {
                next.setWithOffset(pos, direction);
                if(next.getX() < min.getX() || next.getY() < min.getY() || next.getZ() < min.getZ()
                        || next.getX() > max.getX() || next.getY() > max.getY() || next.getZ() > max.getZ())
                {
                    continue;
                }
                long key = next.asLong();
                if(open.contains(key) || surfaces.contains(key))
                {
                    continue;
                }
                if(isOpen(level, next))
                {
                    open.add(key);
                    queue.enqueue(key);
                }
                else
                {
                    surfaces.add(key);
                }
            }
        }
        return surfaces;
    }

    private static boolean isOpen(Level level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty();
    }

    public static void tick(MinecraftServer server)
    {
        var ite = SESSIONS.entrySet().iterator();
        while(ite.hasNext())
        {
            var entry = ite.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Session session = entry.getValue();
            if(player == null || player.level() != session.level)
            {
                ite.remove();
                if(player != null)
                {
                    ServerPlayNetworking.send(player, new HoloNetworking.ScanStatus(false, 0F, BlockPos.ZERO, BlockPos.ZERO));
                }
                continue;
            }
            InteractionHand hand = droneHand(player);
            if(hand == null)
            {
                continue; //paused: not holding an empty drone
            }
            castRays(player, session);
            if(session.progress() >= DONE_AT)
            {
                ite.remove();
                finish(player, session, hand);
                continue;
            }
            if(++session.timer >= STATUS_INTERVAL && session.progress() != session.lastSent)
            {
                session.timer = 0;
                sendStatus(player, session, true);
            }
        }
    }

    /** The hand holding a drone with no scan in it yet, or null. */
    @Nullable
    private static InteractionHand droneHand(ServerPlayer player)
    {
        for(InteractionHand hand : InteractionHand.values())
        {
            ItemStack stack = player.getItemInHand(hand);
            if(stack.is(GlassRegistry.HOLO_DRONE) && stack.get(GlassRegistry.DRONE_SCAN) == null)
            {
                return hand;
            }
        }
        return null;
    }

    private static void castRays(ServerPlayer player, Session session)
    {
        RandomSource random = player.getRandom();
        Vec3 eye = player.getEyePosition();
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        float pitch = player.getXRot() * Mth.DEG_TO_RAD;
        for(int i = 0; i < RAYS_PER_TICK; i++)
        {
            double a = Math.sqrt(random.nextDouble()) * SPREAD;
            double b = random.nextDouble() * Math.PI * 2D;
            Vec3 direction = direction(yaw + (float)(Math.cos(b) * a), pitch + (float)(Math.sin(b) * a));
            BlockHitResult hit = session.level.clip(new ClipContext(eye, eye.add(direction.scale(RAY_LENGTH)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if(hit.getType() != HitResult.Type.BLOCK || !session.contains(hit.getBlockPos()))
            {
                continue;
            }
            long key = hit.getBlockPos().asLong();
            if(session.scanned.add(key) && session.surfaces.contains(key))
            {
                session.surfacesHit++;
            }
        }
    }

    /** The view direction for a yaw and pitch (radians), as the game does it. */
    public static Vec3 direction(float yaw, float pitch)
    {
        float cosPitch = Mth.cos(pitch);
        return new Vec3(-Mth.sin(yaw) * cosPitch, -Mth.sin(pitch), Mth.cos(yaw) * cosPitch);
    }

    /** The player stops early: store what was scanned so far. */
    public static void finishNow(ServerPlayer player)
    {
        Session session = SESSIONS.remove(player.getUUID());
        InteractionHand hand = droneHand(player);
        if(session != null && hand != null)
        {
            finish(player, session, hand);
        }
    }

    private static void finish(ServerPlayer player, Session session, InteractionHand hand)
    {
        ServerPlayNetworking.send(player, new HoloNetworking.ScanStatus(false, session.progress(), BlockPos.ZERO, BlockPos.ZERO));
        if(session.scanned.isEmpty())
        {
            GlassNetworking.reply(player, GlassNetworkManager.Result.fail("glass.msg.scanNothing"));
            return;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for(long key : session.scanned)
        {
            int x = BlockPos.getX(key), y = BlockPos.getY(key), z = BlockPos.getZ(key);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
        BlockPos corner = new BlockPos(minX, minY, minZ);
        int percent = Math.round(session.progress() * 100F);
        String name = Component.translatable("glass.ui.drone.scanName", percent).getString();
        HoloProjectionData.Projection projection = HoloProjectionData.store(session.level, corner, maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, session.scanned, name);
        BlockPos origin = session.start.subtract(corner);
        player.getItemInHand(hand).set(GlassRegistry.DRONE_SCAN, new DroneScan(projection.id(), projection.name(), projection.sizeX(), projection.sizeY(), projection.sizeZ(),
                origin.getX(), origin.getY(), origin.getZ(), session.facing));
        GlassNetworking.reply(player, GlassNetworkManager.Result.ok("glass.msg.roomScanned", Integer.toString(percent)));
    }

    private static void sendStatus(ServerPlayer player, Session session, boolean active)
    {
        session.lastSent = session.progress();
        ServerPlayNetworking.send(player, new HoloNetworking.ScanStatus(active, session.progress(), session.min, session.max));
    }

    public static void clear()
    {
        SESSIONS.clear();
    }
}
