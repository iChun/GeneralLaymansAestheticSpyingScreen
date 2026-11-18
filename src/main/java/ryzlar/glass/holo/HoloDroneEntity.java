package ryzlar.glass.holo;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

import java.util.Optional;

/**
 * A holo drone set down on the ground. Right-click it and it projects its room scan 1:1 around itself, as a solid
 * simulation you can walk around in: the block the scanner stood in is the drone's own block, turned the way the player
 * who set it down was facing. Right-click again to switch it off; sneak + right-click (or hit it) to pick it up.
 */
public class HoloDroneEntity extends Entity
{
    /** How fast the nano wave spreads over the room, in blocks per tick: slower than screens, to watch the room build. */
    public static final float WAVE_SPEED = 0.12F;
    /** How long picking it up takes while it projects (the room shrinks back into it first), in ticks. */
    public static final int PACK_TICKS = 70;

    private static final EntityDataAccessor<Boolean> PROJECTING = SynchedEntityData.defineId(HoloDroneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> PACKING = SynchedEntityData.defineId(HoloDroneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> TURN = SynchedEntityData.defineId(HoloDroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(HoloDroneEntity.class, EntityDataSerializers.ITEM_STACK);

    private int packTicks;

    /** Client: when the projection went on (the wave grows from then), and when and from how far it went off. */
    public long clientOnStart = Long.MIN_VALUE / 2;
    public long clientOffStart = Long.MIN_VALUE / 2;
    public float clientOffRadius;
    /** Client: how far the room reaches from the drone, set by the renderer. */
    public float clientReach = 16F;

    public HoloDroneEntity(EntityType<? extends HoloDroneEntity> type, Level level)
    {
        super(type, level);
    }

    /** Sets a drone holding this scan down in a block (on its floor), turned the way the player faces. */
    public static void place(ServerPlayer player, BlockPos pos, ItemStack item, DroneScan scan)
    {
        HoloDroneEntity drone = new HoloDroneEntity(GlassRegistry.HOLO_DRONE_ENTITY, player.level());
        drone.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        drone.setYRot(player.getYRot());
        drone.entityData.set(ITEM, item);
        //Turn the room so the way the scanner faced is the way the player faces now.
        int facing = Math.floorMod(Math.round(player.getYRot() / 90F), 4);
        drone.entityData.set(TURN, Math.floorMod(facing - scan.facing(), 4));
        player.level().addFreshEntity(drone);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        builder.define(PROJECTING, false);
        builder.define(PACKING, false);
        builder.define(TURN, 0);
        builder.define(ITEM, ItemStack.EMPTY);
    }

    public boolean projecting()
    {
        return entityData.get(PROJECTING);
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
    public DroneScan scan()
    {
        return entityData.get(ITEM).get(GlassRegistry.DRONE_SCAN);
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

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor)
    {
        super.onSyncedDataUpdated(accessor);
        if(accessor == PROJECTING && level().isClientSide)
        {
            long now = level().getGameTime();
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
        if(!level().isClientSide && entityData.get(PACKING) && ++packTicks >= PACK_TICKS)
        {
            pickUp(null);
        }
    }

    public void toggle()
    {
        if(!entityData.get(PACKING))
        {
            entityData.set(PROJECTING, !projecting());
        }
    }

    /** Picks the drone up: at once if it is off, after the room has shrunk back into it if it is on. */
    public void startPickUp(@Nullable Player player)
    {
        if(entityData.get(PACKING))
        {
            return;
        }
        if(!projecting())
        {
            pickUp(player);
            return;
        }
        entityData.set(PACKING, true);
        entityData.set(PROJECTING, false);
        packTicks = 0;
    }

    /** Gives the drone (with its scan) to the player, or the nearest one, or drops it. */
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
            //Sneak + right-click: pick it up. Right-click: projection on or off.
            if(player.isSecondaryUseActive())
            {
                startPickUp(player);
            }
            else
            {
                toggle();
            }
        }
        return InteractionResult.SUCCESS;
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
