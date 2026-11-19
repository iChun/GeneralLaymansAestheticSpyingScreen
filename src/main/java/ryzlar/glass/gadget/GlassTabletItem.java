package ryzlar.glass.gadget;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.GlassClientBridge;
import ryzlar.glass.network.GlassNetworkData;
import ryzlar.glass.network.GlassNetworkManager;

/**
 * A GLASS tablet: a screen in your hand. Right-click it to watch the channel it is tuned to, live, or to tune it to one
 * of your channels. It shows what the channel's terminal sees, as far as your game has that part of the world loaded.
 */
public class GlassTabletItem extends Item
{
    public GlassTabletItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand)
    {
        if(level.isClientSide)
        {
            GlassClientBridge.get().openTabletScreen(hand);
        }
        return InteractionResult.SUCCESS;
    }

    /** Keeps the channel's name up to date, and forgets a channel that is gone or no longer open to the holder. */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot)
    {
        TabletLink link = stack.get(Gadgets.TABLET_LINK);
        if(link == null || !(entity instanceof ServerPlayer player) || level.getGameTime() % 40L != entity.getId() % 40L)
        {
            return;
        }
        GlassNetworkData.Channel channel = GlassNetworkManager.data(level.getServer()).channels.get(link.channelId());
        if(channel == null || !channel.hasAccess(player.getUUID()))
        {
            stack.remove(Gadgets.TABLET_LINK);
        }
        else if(!channel.name.equals(link.channelName()))
        {
            stack.set(Gadgets.TABLET_LINK, new TabletLink(link.channelId(), channel.name));
        }
    }
}
