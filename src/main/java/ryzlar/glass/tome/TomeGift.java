package ryzlar.glass.tome;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import ryzlar.glass.Glass;
import ryzlar.glass.GlassRegistry;

/**
 * Every player gets a GLASS Tome once, the first time they join a world or server. That they had it is kept with the
 * player (and through death), so leaving, dying or throwing it away does not bring another one: craft one for that.
 */
public final class TomeGift
{
    public static final AttachmentType<Boolean> GOT_TOME = AttachmentRegistry.create(Glass.id("got_tome"),
            builder -> builder.persistent(Codec.BOOL).copyOnDeath());

    private TomeGift() {}

    public static void init()
    {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> give(handler.getPlayer()));
    }

    /** Gives the player their tome if they never had one. Returns true if it was given now. */
    public static boolean give(ServerPlayer player)
    {
        if(player.getAttachedOrElse(GOT_TOME, false))
        {
            return false;
        }
        player.setAttached(GOT_TOME, true);
        ItemStack tome = new ItemStack(GlassRegistry.GLASS_TOME);
        if(!player.getInventory().add(tome))
        {
            player.drop(tome, false);
        }
        return true;
    }
}
