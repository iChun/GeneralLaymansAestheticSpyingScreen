package ryzlar.glass.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import ryzlar.glass.GlassRegistry;

/**
 * A wireless GLASS extension. Projectors linked to wireless extensions draw a floating screen between them.
 * Port of TileEntityGlassWireless.
 */
public class GlassWirelessBlockEntity extends GlassBaseBlockEntity
{
    public int ticks;
    /** Client only: how many projectors currently use this extension (makes the crystal spin faster). */
    public int users;

    public GlassWirelessBlockEntity(BlockPos pos, BlockState state)
    {
        super(GlassRegistry.GLASS_WIRELESS_BE, pos, state);
    }

    @Override
    public void update()
    {
        super.update();
        ticks++;
    }
}
