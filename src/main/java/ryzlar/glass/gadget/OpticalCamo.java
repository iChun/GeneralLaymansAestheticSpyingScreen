package ryzlar.glass.gadget;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import ryzlar.glass.GlassSounds;

/**
 * Optical camo: a personal cloak. Right-click the module and for ten seconds you are invisible; standing still you are
 * gone, moving you shimmer faintly (see the client's camo rendering). Attacking or being hurt breaks it. It needs a
 * while to recharge.
 */
public final class OpticalCamo
{
    public static final int CAMO_TICKS = 10 * 20;
    public static final int RECHARGE_TICKS = 40 * 20;

    private OpticalCamo() {}

    /** The effect that marks someone as camouflaged (its icon shows how long is left). */
    public static class Effect extends MobEffect
    {
        public Effect()
        {
            super(MobEffectCategory.BENEFICIAL, 0x3CE6FF);
        }
    }

    public static class ModuleItem extends Item
    {
        public ModuleItem(Properties properties)
        {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand)
        {
            ItemStack stack = player.getItemInHand(hand);
            if(player instanceof ServerPlayer serverPlayer && !isCamouflaged(player))
            {
                engage(serverPlayer);
                player.getCooldowns().addCooldown(stack, RECHARGE_TICKS);
            }
            return InteractionResult.SUCCESS;
        }
    }

    public static boolean isCamouflaged(Player player)
    {
        return player.hasEffect(Gadgets.OPTICAL_CAMO);
    }

    public static void engage(ServerPlayer player)
    {
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, CAMO_TICKS, 0, false, false, false));
        player.addEffect(new MobEffectInstance(Gadgets.OPTICAL_CAMO, CAMO_TICKS, 0, false, false, true));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), GlassSounds.CLOAK_ENGAGE, SoundSource.PLAYERS, 0.8F, 1.4F);
        GadgetNetworking.sendCamo(player, CAMO_TICKS);
    }

    public static void breakCamo(ServerPlayer player)
    {
        if(!isCamouflaged(player))
        {
            return;
        }
        player.removeEffect(Gadgets.OPTICAL_CAMO);
        player.removeEffect(MobEffects.INVISIBILITY);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), GlassSounds.NANO_ASSEMBLE, SoundSource.PLAYERS, 0.7F, 1.5F);
        GadgetNetworking.sendCamo(player, 0);
    }

    public static void init()
    {
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if(player instanceof ServerPlayer serverPlayer)
            {
                breakCamo(serverPlayer);
            }
            return InteractionResult.PASS;
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if(entity instanceof ServerPlayer player && damageTaken > 0F)
            {
                breakCamo(player);
            }
        });
    }
}
