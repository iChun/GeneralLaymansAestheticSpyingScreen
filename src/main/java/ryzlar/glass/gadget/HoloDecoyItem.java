package ryzlar.glass.gadget;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ryzlar.glass.GlassSounds;

/**
 * A holo decoy emitter. Right-click: a hologram of you appears where you look (a few blocks ahead at most) and draws
 * the monsters around it for half a minute. It needs a while to recharge.
 */
public class HoloDecoyItem extends Item
{
    private static final double REACH = 6D;
    public static final int RECHARGE_TICKS = 45 * 20;

    public HoloDecoyItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        if(!(player instanceof ServerPlayer serverPlayer))
        {
            return InteractionResult.SUCCESS;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(REACH));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 at = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation().subtract(player.getLookAngle().scale(0.6D));
        //Stand it on the ground below that point.
        BlockHitResult ground = level.clip(new ClipContext(at, at.subtract(0D, 8D, 0D), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 feet = ground.getType() == HitResult.Type.MISS ? at : ground.getLocation();
        HoloDecoyEntity decoy = HoloDecoyEntity.of(serverPlayer, feet.x, feet.y, feet.z);
        level.addFreshEntity(decoy);
        level.playSound(null, feet.x, feet.y, feet.z, GlassSounds.SIM_OPEN, SoundSource.PLAYERS, 0.8F, 1.6F);
        player.getCooldowns().addCooldown(stack, RECHARGE_TICKS);
        return InteractionResult.SUCCESS;
    }
}
