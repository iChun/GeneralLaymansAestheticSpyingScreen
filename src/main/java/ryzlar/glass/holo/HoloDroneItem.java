package ryzlar.glass.holo;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import ryzlar.glass.GlassRegistry;
import ryzlar.glass.network.GlassNetworkManager;
import ryzlar.glass.network.GlassNetworking;

/**
 * The holo cube. Right-click a block to set it down there; then right-click the cube to scan the room around it, and
 * once it has a scan, to project it (see {@link HoloDroneEntity}). Sneak + right-click with a scanned cube wipes its scan.
 */
public class HoloDroneItem extends Item
{
    public HoloDroneItem(Properties properties)
    {
        super(properties);
    }

    /** Set the cube down where you click. */
    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        ItemStack stack = context.getItemInHand();
        DroneScan scan = stack.get(GlassRegistry.DRONE_SCAN);
        Player player = context.getPlayer();
        if(player == null || (scan != null && player.isSecondaryUseActive()))
        {
            return InteractionResult.PASS; //wiping goes through use()
        }
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        BlockPos pos = level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(context.getClickedFace());
        if(!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty())
        {
            return InteractionResult.FAIL;
        }
        if(player instanceof ServerPlayer serverPlayer)
        {
            HoloDroneEntity.place(serverPlayer, pos, stack.copyWithCount(1), scan);
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand)
    {
        if(level.isClientSide || !(player instanceof ServerPlayer serverPlayer))
        {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if(stack.get(GlassRegistry.DRONE_SCAN) != null && player.isSecondaryUseActive())
        {
            stack.remove(GlassRegistry.DRONE_SCAN);
            GlassNetworking.reply(serverPlayer, GlassNetworkManager.Result.ok("glass.msg.scanWiped", ""));
            return InteractionResult.SUCCESS;
        }
        GlassNetworking.reply(serverPlayer, GlassNetworkManager.Result.ok(stack.get(GlassRegistry.DRONE_SCAN) != null ? "glass.msg.dronePlaceHint" : "glass.msg.dronePlaceEmpty", ""));
        return InteractionResult.SUCCESS;
    }
}
