package ryzlar.glass.tome;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import ryzlar.glass.GlassClientBridge;

/** The GLASS Tome: the guide to everything in the mod. Right-click to read it (it opens on the client only). */
public class GlassTomeItem extends Item
{
    public GlassTomeItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand)
    {
        if(level.isClientSide)
        {
            GlassClientBridge.get().openTome(null);
        }
        return InteractionResult.SUCCESS;
    }
}
