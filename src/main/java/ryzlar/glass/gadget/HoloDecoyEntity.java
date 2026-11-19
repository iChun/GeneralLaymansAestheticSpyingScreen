package ryzlar.glass.gadget;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.GlassSounds;

/**
 * A holo decoy: a hologram of the player who threw it, standing where it landed. Monsters around it go for it instead
 * of its owner, and motion sensors see it, until it is hit hard enough or its time runs out; then it falls apart.
 * Its owner can right-click it to switch it off.
 */
public class HoloDecoyEntity extends LivingEntity
{
    /** How long a decoy lasts, how far it draws monsters from, and how often it calls them (ticks, blocks). */
    public static final int LIFETIME = 30 * 20;
    private static final double LURE_RANGE = 20D;
    private static final int LURE_TICKS = 10;

    private static final EntityDataAccessor<String> OWNER = SynchedEntityData.defineId(HoloDecoyEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> OWNER_NAME = SynchedEntityData.defineId(HoloDecoyEntity.class, EntityDataSerializers.STRING);

    private int age;

    public HoloDecoyEntity(EntityType<? extends HoloDecoyEntity> type, Level level)
    {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes()
    {
        return LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, 10D);
    }

    /** A decoy of this player, here, facing the way they face. */
    public static HoloDecoyEntity of(ServerPlayer player, double x, double y, double z)
    {
        HoloDecoyEntity decoy = new HoloDecoyEntity(Gadgets.HOLO_DECOY_ENTITY, player.level());
        decoy.setPos(x, y, z);
        decoy.setYRot(player.getYRot());
        decoy.setYBodyRot(player.getYRot());
        decoy.setYHeadRot(player.getYRot());
        decoy.entityData.set(OWNER, player.getUUID().toString());
        decoy.entityData.set(OWNER_NAME, player.getGameProfile().getName());
        return decoy;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(OWNER, "");
        builder.define(OWNER_NAME, "");
    }

    /** Whose hologram it is (for the skin): their id, or empty. */
    public String ownerId()
    {
        return entityData.get(OWNER);
    }

    public String ownerName()
    {
        return entityData.get(OWNER_NAME);
    }

    public int age()
    {
        return age;
    }

    @Override
    public void tick()
    {
        super.tick();
        age++;
        if(level().isClientSide)
        {
            return;
        }
        if(age >= LIFETIME)
        {
            dissolve();
            return;
        }
        if(age % LURE_TICKS == 0)
        {
            //Monsters nearby go for the decoy.
            for(Mob mob : level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(LURE_RANGE), m -> m instanceof Enemy && m.isAlive()))
            {
                if(mob.getTarget() != this)
                {
                    mob.setTarget(this);
                }
            }
        }
    }

    /** Falls apart in blue sparks. */
    public void dissolve()
    {
        if(level() instanceof ServerLevel server)
        {
            server.sendParticles(new DustParticleOptions(0x3CE6FF, 1.0F), getX(), getY() + 1D, getZ(), 40, 0.3D, 0.8D, 0.3D, 0.02D);
            level().playSound(null, getX(), getY(), getZ(), GlassSounds.NANO_DISSOLVE, SoundSource.PLAYERS, 0.9F, 1.4F);
        }
        discard();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount)
    {
        boolean hurt = super.hurtServer(level, source, amount);
        if(hurt)
        {
            level.sendParticles(new DustParticleOptions(0x6CF0FF, 0.8F), getX(), getY() + 1D, getZ(), 8, 0.3D, 0.6D, 0.3D, 0.01D);
        }
        return hurt;
    }

    /** No body falling over: it just falls apart. */
    @Override
    protected void tickDeath()
    {
        dissolve();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand)
    {
        if(!level().isClientSide && player.getUUID().toString().equals(ownerId()))
        {
            dissolve();
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return GlassSounds.RIPPLE;
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound()
    {
        return null;
    }

    @Override
    public HumanoidArm getMainArm()
    {
        return HumanoidArm.RIGHT;
    }

    @Override
    public boolean isPushable()
    {
        return false;
    }

    @Override
    public boolean shouldShowName()
    {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output)
    {
        super.addAdditionalSaveData(output);
        output.putString("owner", ownerId());
        output.putString("ownerName", ownerName());
        output.putInt("age", age);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input)
    {
        super.readAdditionalSaveData(input);
        entityData.set(OWNER, input.getStringOr("owner", ""));
        entityData.set(OWNER_NAME, input.getStringOr("ownerName", ""));
        age = input.getIntOr("age", 0);
    }
}
