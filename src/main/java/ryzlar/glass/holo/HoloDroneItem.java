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
 * The holo drone. Empty: right-click starts scanning the room you stand in (look around until it is done; sneak +
 * right-click stops early). With a scan: right-click a block to set the drone down on it, then right-click the drone to
 * project the room; sneak + right-click in the air wipes the scan.
 */
public class HoloDroneItem extends Item
{
    public HoloDroneItem(Properties properties)
    {
        super(properties);
    }

    /** With a scan: set the drone down where you click. */
    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        ItemStack stack = context.getItemInHand();
        DroneScan scan = stack.get(GlassRegistry.DRONE_SCAN);
        Player player = context.getPlayer();
        if(scan == null || player == null || player.isSecondaryUseActive())
        {
            return InteractionResult.PASS; //scanning (or wiping) goes through use()
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
        if(stack.get(GlassRegistry.DRONE_SCAN) != null)
        {
            if(player.isSecondaryUseActive())
            {
                stack.remove(GlassRegistry.DRONE_SCAN);
                GlassNetworking.reply(serverPlayer, GlassNetworkManager.Result.ok("glass.msg.scanWiped", ""));
            }
            else
            {
                GlassNetworking.reply(serverPlayer, GlassNetworkManager.Result.ok("glass.msg.dronePlaceHint", ""));
            }
            return InteractionResult.SUCCESS;
        }
        if(HoloScanSessions.isScanning(serverPlayer))
        {
            if(player.isSecondaryUseActive())
            {
                HoloScanSessions.finishNow(serverPlayer);
            }
            return InteractionResult.SUCCESS;
        }
        HoloScanSessions.start(serverPlayer);
        return InteractionResult.SUCCESS;
    }
}
