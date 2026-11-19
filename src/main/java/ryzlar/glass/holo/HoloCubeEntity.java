package ryzlar.glass.holo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.GlassRegistry;
import ryzlar.glass.network.GlassNetworkManager;
import ryzlar.glass.network.GlassNetworking;

import java.util.Optional;
import java.util.UUID;

/**
 * A holo cube set down on the ground. Without a scan, right-click it and it scans the room around it: it rises, and a
 * wave of light runs out over the room's walls, floor and ceiling from it (see the client's ScanEffect); once the wave
 * has been everywhere, the room is stored in the cube. With a scan, right-click it and it projects the room 1:1 around
 * itself, as a solid simulation you can walk around in: the block it scanned from is the cube's own block, turned the
 * way the player who set it down was facing. Right-click again to switch it off; sneak + right-click (or hit it) to pick
 * it up, scan and all.
 */
public class HoloCubeEntity extends Entity
{
    /** How fast the nano wave spreads over the room, in blocks per tick: slower than screens, to watch the room build. */
    public static final float WAVE_SPEED = 0.12F;
    /** How fast the scan's light runs out over a big room, in blocks per tick (a small room is scanned more slowly). */
    public static final float SCAN_SPEED = 0.2F;
    /** A scan takes at least, and at most, this long (ticks). */
    private static final int MIN_SCAN_TICKS = 50;
    private static final int MAX_SCAN_TICKS = 180;
    /** How often the scan pings, in ticks. */
    private static final int SWEEP_TICKS = 22;
    /** How long picking it up takes while it projects (the room shrinks back into it first), in ticks. */
    public static final int PACK_TICKS = 70;

    private static final EntityDataAccessor<Boolean> PROJECTING = SynchedEntityData.defineId(HoloCubeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> PACKING = SynchedEntityData.defineId(HoloCubeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SCANNING = SynchedEntityData.defineId(HoloCubeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> TURN = SynchedEntityData.defineId(HoloCubeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(HoloCubeEntity.class, EntityDataSerializers.ITEM_STACK);

    private int packTicks;
    /** Server: the room being scanned, how long the scan has run and will run, and who started it. */
    @Nullable
    private RoomScan.Room scanRoom;
    private int scanAge;
    private int scanTicks;
    @Nullable
    private UUID scanner;

    /** Client: when the projection went on (the wave grows from then), and when and from how far it went off. */
    public long clientOnStart = Long.MIN_VALUE / 2;
    public long clientOffStart = Long.MIN_VALUE / 2;
    public float clientOffRadius;
    /** Client: how far the room reaches from the cube, set by the renderer. */
    public float clientReach = 16F;
    /** Client: when the scan started and ended, and the room it runs over (worked out here from the client's world). */
    public long clientScanStart = Long.MIN_VALUE / 2;
    public long clientScanEnd = Long.MIN_VALUE / 2;
    @Nullable
    public RoomScan.Room clientRoom;
    private float clientScanSpeed = SCAN_SPEED;
    /** Client: the scan this cube held when last seen (to notice a scan it has just finished). */
    @Nullable
    private String clientSeenScan;
    /** Client: told when a cube this client watched scanning has its scan (to take its panorama, see the client's Panoramas). */
    public static java.util.function.Consumer<HoloCubeEntity> onClientScanDone = cube -> {};

    public HoloCubeEntity(EntityType<? extends HoloCubeEntity> type, Level level)
    {
        super(type, level);
    }

    /**
     * Sets a cube down in a block (on its floor), facing the way the player faces. With a scan, the room is turned so the
     * way the cube faced while scanning is the way the player faces now.
     */
    public static void place(ServerPlayer player, BlockPos pos, ItemStack item, @Nullable CubeScan scan)
    {
        HoloCubeEntity cube = new HoloCubeEntity(GlassRegistry.HOLO_CUBE_ENTITY, player.level());
        cube.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        cube.setYRot(player.getYRot());
        cube.entityData.set(ITEM, item);
        int facing = facingOf(player.getYRot());
        cube.entityData.set(TURN, scan == null ? 0 : Math.floorMod(facing - scan.facing(), 4));
        player.level().addFreshEntity(cube);
    }

    private static int facingOf(float yaw)
    {
        return Math.floorMod(Math.round(yaw / 90F), 4);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        builder.define(PROJECTING, false);
        builder.define(PACKING, false);
        builder.define(SCANNING, false);
        builder.define(TURN, 0);
        builder.define(ITEM, ItemStack.EMPTY);
    }

    public boolean projecting()
    {
        return entityData.get(PROJECTING);
    }

    public boolean scanning()
    {
        return entityData.get(SCANNING);
    }

    /** The block the room is laid around (the scanner's spot goes here). */
    public BlockPos anchor()
    {
        return blockPosition();
    }

    /** Quarter turns the room is turned by. */
    public int turn()
    {
        return entityData.get(TURN);
    }

    @Nullable
    public CubeScan scan()
    {
        return entityData.get(ITEM).get(GlassRegistry.CUBE_SCAN);
    }

    /** Client: how far the wave has spread now (growing while on, shrinking back after it went off; 0 when done). */
    public float waveRadius(float partialTick)
    {
        long now = level().getGameTime();
        float full = clientReach + 2F;
        if(projecting())
        {
            return Math.min(full, (now - clientOnStart + partialTick) * WAVE_SPEED);
        }
        float duration = Math.max(20F, clientOffRadius / WAVE_SPEED);
        if(entityData.get(PACKING))
        {
            duration = Math.min(duration, PACK_TICKS - 4F);
        }
        float x = Math.clamp((now - clientOffStart + partialTick) / duration, 0F, 1F);
        float eased = x * x * x * (x * (x * 6F - 15F) + 10F);
        return clientOffRadius * (1F - eased);
    }

    /** Client: how far the scan's light has run out from the cube. */
    public float scanRadius(float partialTick)
    {
        long end = scanning() ? level().getGameTime() : clientScanEnd;
        return Math.max(0F, (end - clientScanStart + (scanning() ? partialTick : 0F)) * clientScanSpeed);
    }

    /** How long a scan of a room this big takes (ticks): its light reaches the far end a little before it is done. */
    private static int scanTicksFor(float reach)
    {
        return Mth.clamp((int)Math.ceil(reach / SCAN_SPEED) + 10, MIN_SCAN_TICKS, MAX_SCAN_TICKS);
    }

    /** How fast the light runs in a room this big, so it takes the whole scan to get to the far end. */
    private static float scanSpeedFor(float reach)
    {
        return (reach + 0.5F) / Math.max(1, scanTicksFor(reach) - 10);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor)
    {
        super.onSyncedDataUpdated(accessor);
        if(!level().isClientSide)
        {
            return;
        }
        long now = level().getGameTime();
        if(accessor == PROJECTING)
        {
            if(projecting())
            {
                clientOnStart = now;
            }
            else
            {
                clientOffRadius = Math.min(clientReach + 2F, (now - clientOnStart) * WAVE_SPEED);
                clientOffStart = now;
            }
        }
        else if(accessor == ITEM)
        {
            CubeScan scan = scan();
            String id = scan == null ? null : scan.id();
            if(id != null && !id.equals(clientSeenScan) && clientScanStart > Long.MIN_VALUE / 4 && now - clientScanStart < 20L * 30L)
            {
                clientSeenScan = id;
                onClientScanDone.accept(this);
            }
            clientSeenScan = id;
        }
        else if(accessor == SCANNING)
        {
            if(scanning())
            {
                clientScanStart = now;
                clientRoom = RoomScan.find(level(), blockPosition());
                clientScanSpeed = clientRoom == null ? SCAN_SPEED : scanSpeedFor(clientRoom.reach());
            }
            else
            {
                clientScanEnd = now;
            }
        }
    }

    @Override
    public void tick()
    {
        super.tick();
        //Lie on the ground: fall if there is nothing under it.
        if(!onGround())
        {
            Vec3 motion = getDeltaMovement().add(0D, -0.04D, 0D).scale(0.98D);
            setDeltaMovement(motion.x * 0.5D, motion.y, motion.z * 0.5D);
            move(MoverType.SELF, getDeltaMovement());
        }
        else
        {
            setDeltaMovement(Vec3.ZERO);
        }
        if(level().isClientSide)
        {
            if(scanning())
            {
                scanSparks();
            }
            return;
        }
        if(entityData.get(PACKING) && ++packTicks >= PACK_TICKS)
        {
            pickUp(null);
            return;
        }
        if(scanning() && ++scanAge >= scanTicks)
        {
            finishScan();
        }
        else if(scanning() && scanAge % SWEEP_TICKS == SWEEP_TICKS / 2)
        {
            //A sonar ping now and then, rising as the scan goes on.
            level().playSound(null, getX(), getY(), getZ(), ryzlar.glass.GlassSounds.SCAN_SWEEP, SoundSource.NEUTRAL, 0.6F, 0.9F + 0.35F * scanAge / Math.max(1, scanTicks));
        }
    }

    /** Client: sparks where the scan's light is running over the room. */
    private void scanSparks()
    {
        RoomScan.Room room = clientRoom;
        if(room == null || room.surfaces().isEmpty())
        {
            return;
        }
        float radius = scanRadius(0F);
        Vec3 centre = Vec3.atCenterOf(blockPosition());
        DustParticleOptions dust = new DustParticleOptions(0x3CE6FF, 0.8F);
        long[] keys = room.surfaces().toLongArray();
        for(int i = 0, found = 0; i < 40 && found < 5; i++)
        {
            long key = keys[random.nextInt(keys.length)];
            Vec3 at = Vec3.atCenterOf(BlockPos.of(key));
            if(Math.abs(at.distanceTo(centre) - radius) < 0.8D)
            {
                //On the face towards the open space, roughly: pulled towards the cube.
                Vec3 out = centre.subtract(at).normalize().scale(0.6D);
                level().addParticle(dust, at.x + out.x, at.y + out.y, at.z + out.z, 0D, 0.01D, 0D);
                found++;
            }
        }
    }

    /** Starts scanning the room around the cube. */
    private void startScan(Player player)
    {
        if(scanning() || projecting() || scan() != null || entityData.get(PACKING))
        {
            return;
        }
        RoomScan.Room room = RoomScan.find(level(), blockPosition());
        if(room == null)
        {
            if(player instanceof ServerPlayer serverPlayer)
            {
                GlassNetworking.reply(serverPlayer, GlassNetworkManager.Result.fail("glass.msg.scanNoRoom"));
            }
            return;
        }
        scanRoom = room;
        scanAge = 0;
        scanTicks = scanTicksFor(room.reach());
        scanner = player.getUUID();
        entityData.set(SCANNING, true);
        level().playSound(null, getX(), getY(), getZ(), ryzlar.glass.GlassSounds.SCAN_START, SoundSource.NEUTRAL, 0.9F, 1F);
    }

    /** The light has been everywhere: store the room in the cube. */
    private void finishScan()
    {
        RoomScan.Room room = scanRoom;
        scanRoom = null;
        entityData.set(SCANNING, false);
        if(room == null || !(level() instanceof ServerLevel serverLevel))
        {
            return;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for(long key : room.blocks())
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
        String name = Component.translatable("glass.ui.cube.scanName").getString();
        HoloProjectionData.Projection projection = HoloProjectionData.store(serverLevel, corner, maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, room.blocks(), name);
        BlockPos origin = blockPosition().subtract(corner);
        ItemStack item = entityData.get(ITEM).copy();
        //The sky over the room too (in a dimension that has one): the simulation shows it overhead.
        boolean sky = serverLevel.dimensionType().hasSkyLight() && !serverLevel.dimensionType().hasFixedTime();
        item.set(GlassRegistry.CUBE_SCAN, new CubeScan(projection.id(), projection.name(), projection.sizeX(), projection.sizeY(), projection.sizeZ(),
                origin.getX(), origin.getY(), origin.getZ(), facingOf(getYRot()),
                sky ? serverLevel.getDayTime() : CubeScan.NO_SKY, sky ? serverLevel.getRainLevel(1F) : 0F, sky ? serverLevel.getThunderLevel(1F) : 0F));
        entityData.set(ITEM, item);
        //Projected where it was scanned, the room lies right over itself.
        entityData.set(TURN, 0);
        level().playSound(null, getX(), getY(), getZ(), ryzlar.glass.GlassSounds.SCAN_COMPLETE, SoundSource.NEUTRAL, 0.9F, 1F);
        if(scanner != null && serverLevel.getPlayerByUUID(scanner) instanceof ServerPlayer player)
        {
            GlassNetworking.reply(player, GlassNetworkManager.Result.ok("glass.msg.roomScanned", ""));
        }
    }

    public void toggle()
    {
        if(!entityData.get(PACKING) && !scanning() && scan() != null)
        {
            entityData.set(PROJECTING, !projecting());
            level().playSound(null, getX(), getY(), getZ(), projecting() ? ryzlar.glass.GlassSounds.SIM_OPEN : ryzlar.glass.GlassSounds.SIM_CLOSE,
                    SoundSource.NEUTRAL, 1F, 1F);
        }
    }

    /** Picks the cube up: at once if it is off (a scan in progress is dropped), after the room has shrunk back if it is on. */
    public void startPickUp(@Nullable Player player)
    {
        if(entityData.get(PACKING))
        {
            return;
        }
        if(scanning())
        {
            scanRoom = null;
            entityData.set(SCANNING, false);
        }
        if(!projecting())
        {
            pickUp(player);
            return;
        }
        entityData.set(PACKING, true);
        entityData.set(PROJECTING, false);
        level().playSound(null, getX(), getY(), getZ(), ryzlar.glass.GlassSounds.SIM_CLOSE, SoundSource.NEUTRAL, 1F, 1.1F);
        packTicks = 0;
    }

    /** Gives the cube (with its scan) to the player, or the nearest one, or drops it. */
    private void pickUp(@Nullable Player player)
    {
        ItemStack item = entityData.get(ITEM).copy();
        Player to = player != null ? player : level().getNearestPlayer(this, 16D);
        if(to == null || !to.addItem(item))
        {
            level().addFreshEntity(new ItemEntity(level(), getX(), getY() + 0.25D, getZ(), item));
        }
        discard();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand)
    {
        if(!level().isClientSide)
        {
            //Sneak + right-click: pick it up. Right-click: scan the room (no scan yet), or the projection on or off.
            if(player.isSecondaryUseActive())
            {
                startPickUp(player);
            }
            else if(scan() == null)
            {
                startScan(player);
            }
            else
            {
                toggle();
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Tests: what the cube holds (its item, with or without a scan). */
    public void setItem(ItemStack item)
    {
        entityData.set(ITEM, item);
    }

    /** Tests: starts a scan as if this player right-clicked the cube. */
    public void scanFor(Player player)
    {
        startScan(player);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount)
    {
        startPickUp(source.getEntity() instanceof Player player ? player : null);
        return true;
    }

    @Override
    public boolean isPickable()
    {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance)
    {
        return distance < 128D * 128D;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output)
    {
        output.putBoolean("projecting", projecting() && !entityData.get(PACKING));
        output.putInt("turn", turn());
        ItemStack item = entityData.get(ITEM);
        if(!item.isEmpty())
        {
            output.store("item", ItemStack.CODEC, item);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input)
    {
        entityData.set(PROJECTING, input.getBooleanOr("projecting", false));
        entityData.set(TURN, Mth.clamp(input.getIntOr("turn", 0), 0, 3));
        Optional<ItemStack> item = input.read("item", ItemStack.CODEC);
        entityData.set(ITEM, item.orElse(ItemStack.EMPTY));
    }
}
