package ryzlar.glass;

import net.minecraft.core.BlockPos;
import ryzlar.glass.blockentity.GlassBaseBlockEntity;
import ryzlar.glass.blockentity.GlassProjectorBlockEntity;
import ryzlar.glass.blockentity.GlassSceneSelectorBlockEntity;
import ryzlar.glass.blockentity.GlassTerminalBlockEntity;
import ryzlar.glass.blockentity.GlassWirelessBlockEntity;

/**
 * Lets common code (blocks, block entities) reach client-only code without loading client classes on a server.
 * The client mod initializer installs the real implementation with {@link #set}.
 */
public interface GlassClientBridge
{
    static GlassClientBridge get()
    {
        return Holder.instance;
    }

    static void set(GlassClientBridge bridge)
    {
        Holder.instance = bridge;
    }

    default void openTerminalScreen(GlassTerminalBlockEntity terminal) {}

    default void openProjectorScreen(GlassProjectorBlockEntity projector) {}

    default void openWirelessScreen(GlassWirelessBlockEntity wireless) {}

    default void openSceneSelectorScreen(GlassSceneSelectorBlockEntity selector) {}

    default void openHoloScannerScreen(ryzlar.glass.holo.HoloScannerBlockEntity scanner) {}

    default void openHoloProjectorScreen(ryzlar.glass.holo.HoloProjectorBlockEntity projector) {}

    default void onGlassClicked(BlockPos pos) {}

    /** The GLASS Tome was used: open it (at a page, or where it was left when null). */
    default void openTome(@org.jetbrains.annotations.Nullable String page) {}

    /** A GLASS tablet was used: open its screen. */
    default void openTabletScreen(net.minecraft.world.InteractionHand hand) {}

    /** Called every client tick by each glass block entity so the client knows which glass belongs to which channel. */
    default void syncGlass(GlassBaseBlockEntity glass) {}

    /** Called when a glass block entity is removed or unloaded on the client. */
    default void removeGlass(GlassBaseBlockEntity glass) {}

    final class Holder
    {
        private static GlassClientBridge instance = new GlassClientBridge() {};

        private Holder() {}
    }
}
