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
 * A holo cube set down on the ground. Without a scan, right-click it and it scans the ball of the world round it (see
 * {@link HoloSphere}): it rises, a holo grid grows out of it, and everything inside falls apart into glowing pixels and
 * comes back together (see the client's ScanEffect); then the ball is stored in the cube: every block, what is in the
 * chests and on the signs, the animals and mobs, and the sky. With a scan, right-click it and a glowing bubble grows out
 * of it with the scan inside, shrunk to two thirds: the block it scanned from is on the cube's own block, turned the way
 * the player who set it down was facing. The scan is a picture over the real world, which stays as it is; its ground can
 * be stood on where the real world is empty, the rest walked through (see {@link HoloBubbles}). Right-click again to
 * switch it off; sneak + right-click (or hit it) to pick it up, scan and all.
 */
public class HoloCubeEntity extends Entity
{
    /** How fast the nano wave (and the bubble) grows, in blocks per tick: slow, to watch the place build. */
    public static final float WAVE_SPEED = 0.12F;
    /** How long a scan takes (ticks). */
    public static final int SCAN_TICKS = 100;
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
    /** Server: how long the scan has run, and who started it. */
    private int scanAge;
    @Nullable
    private UUID scanner;

    /** Client: when the projection went on (the wave grows from then), and when and from how far it went off. */
    public long clientOnStart = Long.MIN_VALUE / 2;
    public long clientOffStart = Long.MIN_VALUE / 2;
    public float clientOffRadius;
    /** Client: when the scan started and ended, and the faces of the ball it runs over (from the client's world). */
    public long clientScanStart = Long.MIN_VALUE / 2;
    public long clientScanEnd = Long.MIN_VALUE / 2;
    @Nullable
    public it.unimi.dsi.fastutil.longs.LongList clientSurfaces;

    /** The scanned blocks this cube's simulation stands on (looked up once per scan), and the scan they are of. */
    @Nullable
    private HoloBubbles.ScannedBlocks bubbleBlocks;
    @Nullable
    private String bubbleScan;
    private boolean inBubbles;
    /** Client: the scanned blocks of a scan, once they are here (see HoloClient); null while they are on their way. */
    public static java.util.function.Function<String, HoloBubbles.ScannedBlocks> clientBlocks = id -> null;

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

    /** The scan it holds; an old room scan does not count (it has to scan again). */
    @Nullable
    public CubeScan scan()
    {
        CubeScan scan = entityData.get(ITEM).get(GlassRegistry.CUBE_SCAN);
        return scan != null && scan.isCurrent() ? scan : null;
    }

    /** How far the bubble reaches when it has grown all the way: its wall, and a little past it. */
    public static final float FULL_RADIUS = HoloSphere.BUBBLE_RADIUS + 0.5F;

    /** Client: how far the wave has spread now (growing while on, shrinking back after it went off; 0 when done). */
    public float waveRadius(float partialTick)
    {
        long now = level().getGameTime();
        float full = FULL_RADIUS;
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

    /** Client: how far into the scan it is (0..1), going on to 1 once it is done. */
    public float scanProgress(float partialTick)
    {
        long end = scanning() ? level().getGameTime() : clientScanEnd;
        return Math.clamp((end - clientScanStart + (scanning() ? partialTick : 0F)) / SCAN_TICKS, 0F, 1F);
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
                clientOffRadius = Math.min(FULL_RADIUS, (now - clientOnStart) * WAVE_SPEED);
                clientOffStart = now;
            }
        }
        else if(accessor == SCANNING)
        {
            if(scanning())
            {
                clientScanStart = now;
                clientSurfaces = HoloSphere.surfaces(level(), blockPosition());
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
        updateBubble();
        if(level().isClientSide)
        {
            return;
        }
        if(entityData.get(PACKING) && ++packTicks >= PACK_TICKS)
        {
            pickUp(null);
            return;
        }
        if(scanning() && ++scanAge >= SCAN_TICKS)
        {
            finishScan();
        }
        else if(scanning() && scanAge % SWEEP_TICKS == SWEEP_TICKS / 2)
        {
            //A sonar ping now and then, rising as the scan goes on.
            level().playSound(null, getX(), getY(), getZ(), ryzlar.glass.GlassSounds.SCAN_SWEEP, SoundSource.NEUTRAL, 0.6F, 0.9F + 0.35F * scanAge / SCAN_TICKS);
        }
    }

    /**
     * Keeps this cube's simulation in {@link HoloBubbles}: there while it is switched on (and on the client, while its
     * bubble still shrinks away), its ground to stand on only while switched on.
     */
    private void updateBubble()
    {
        CubeScan scan = scan();
        boolean client = level().isClientSide;
        boolean on = scan != null && !entityData.get(PACKING) && projecting();
        if(scan == null || !(on || client && waveRadius(0F) > 0F))
        {
            dropBubble();
            return;
        }
        if(bubbleBlocks == null || !scan.id().equals(bubbleScan))
        {
            bubbleBlocks = client ? clientBlocks.apply(scan.id()) : serverBlocks(scan.id());
            bubbleScan = bubbleBlocks == null ? null : scan.id();
            if(bubbleBlocks == null)
            {
                return; //its blocks are on their way (client) or gone
            }
        }
        HoloBubbles.Bubble old = HoloBubbles.byId(level(), getId());
        HoloBubbles.Bubble bubble = new HoloBubbles.Bubble(getId(), anchor(), turn(), scan.originX(), scan.originY(), scan.originZ(), bubbleBlocks, on);
        if(old != null && old.solid == on && old.samePlace(bubble))
        {
            return;
        }
        HoloBubbles.put(level(), bubble);
        inBubbles = true;
    }

    private void dropBubble()
    {
        if(inBubbles)
        {
            inBubbles = false;
            HoloBubbles.remove(level(), getId());
        }
    }

    @Nullable
    private HoloBubbles.ScannedBlocks serverBlocks(String id)
    {
        if(!(level() instanceof ServerLevel serverLevel))
        {
            return null;
        }
        HoloProjectionData.Projection projection = HoloProjectionData.find(serverLevel.getServer(), id);
        return projection == null ? null : projection::get;
    }

    /** Gone (picked up, unloaded with its chunk, or gone from the client): its simulation goes with it. */
    @Override
    public void onRemoval(RemovalReason reason)
    {
        super.onRemoval(reason);
        dropBubble();
    }

    /** Starts scanning the ball round the cube. */
    private void startScan(Player player)
    {
        if(scanning() || projecting() || scan() != null || entityData.get(PACKING))
        {
            return;
        }
        scanAge = 0;
        scanner = player.getUUID();
        entityData.set(SCANNING, true);
        level().playSound(null, getX(), getY(), getZ(), ryzlar.glass.GlassSounds.SCAN_START, SoundSource.NEUTRAL, 0.9F, 1F);
    }

    /** The ball has been taken apart and put back together: store it in the cube. */
    private void finishScan()
    {
        entityData.set(SCANNING, false);
        if(!(level() instanceof ServerLevel serverLevel))
        {
            return;
        }
        String name = Component.translatable("glass.ui.cube.scanName").getString();
        HoloProjectionData.Projection projection = HoloProjectionData.storeBall(serverLevel, blockPosition(), name);
        int r = HoloSphere.SCAN_RADIUS;
        ItemStack item = entityData.get(ITEM).copy();
        //The sky over it too (in a dimension that has one): inside the simulation the whole sky is as it was.
        boolean sky = serverLevel.dimensionType().hasSkyLight() && !serverLevel.dimensionType().hasFixedTime();
        item.set(GlassRegistry.CUBE_SCAN, new CubeScan(projection.id(), projection.name(), projection.sizeX(), projection.sizeY(), projection.sizeZ(),
                r, r, r, facingOf(getYRot()),
                sky ? serverLevel.getDayTime() : CubeScan.NO_SKY, sky ? serverLevel.getRainLevel(1F) : 0F, sky ? serverLevel.getThunderLevel(1F) : 0F,
                CubeScan.VERSION));
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
