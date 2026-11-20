package ryzlar.glass.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import ryzlar.glass.blockentity.GlassProjectorBlockEntity;
import ryzlar.glass.blockentity.GlassWirelessBlockEntity;
import ryzlar.glass.client.GlassClientState;
import ryzlar.glass.client.gui.holo.Holo;
import ryzlar.glass.client.gui.holo.HoloButton;
import ryzlar.glass.client.gui.holo.HoloList;
import ryzlar.glass.client.gui.holo.HoloScreen;
import ryzlar.glass.client.render.LinkHighlight;
import ryzlar.glass.network.GlassNetworking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A wireless extension: which projectors it is connected to (their name, whether they are on, and where this extension
 * comes in their screen's outline), to disconnect it or show where they are; and the projectors nearby to connect it to.
 * Picking a projector in either list shows it in the world (blue: connected, amber: could connect). Port of
 * GuiProjectorSetter, grown.
 */
public class WirelessProjectorScreen extends HoloScreen
{
    private static final float HIGHLIGHT_SECONDS = 6F;

    private final GlassWirelessBlockEntity wireless;
    private HoloList<GlassProjectorBlockEntity> connected;
    private HoloList<GlassProjectorBlockEntity> nearby;
    private HoloButton disconnect;
    private HoloButton link;

    public WirelessProjectorScreen(GlassWirelessBlockEntity wireless)
    {
        super(Component.translatable("glass.ui.wirelessLink"), 300, 244);
        this.wireless = wireless;
    }

    @Override
    protected Component subtitle()
    {
        BlockPos pos = wireless.getBlockPos();
        return Component.translatable("glass.ui.wirelessLink.sub", pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    protected void build()
    {
        BlockPos here = wireless.getBlockPos();
        connected = addRenderableWidget(new HoloList<GlassProjectorBlockEntity>(left + 10, top + 52, panelW - 20, 66,
                WirelessProjectorScreen::name,
                p -> {
                    int index = p.wirelessPos.indexOf(here) + 1;
                    Component state = Component.translatable(p.active ? "glass.ui.linkOn" : "glass.ui.linkOff")
                            .withColor(p.active ? Holo.OK : Holo.MUTED);
                    if(p.cloakFieldBox() != null)
                    {
                        state = Component.translatable("glass.ui.linkField").withColor(Holo.ACCENT);
                    }
                    return Component.translatable("glass.ui.linkCorner", state, index, p.wirelessPos.size());
                },
                Component.translatable("glass.ui.notConnected"))
                .keyedBy(GlassProjectorBlockEntity::getBlockPos)
                .onSelect(p -> {
                    nearby.select(null);
                    LinkHighlight.show(here, p.getBlockPos(), true, HIGHLIGHT_SECONDS);
                    updateButtons();
                })
                .onActivate(p -> locate(p, true)));
        int y = top + 122;
        addRenderableWidget(new HoloButton(left + 10, y, 90, 14, Component.translatable("glass.ui.locate"), b -> {
            GlassProjectorBlockEntity p = connected.getSelected();
            if(p == null)
            {
                warn(Component.translatable("glass.msg.pickLinked"));
                return;
            }
            locate(p, true);
        }));
        disconnect = addRenderableWidget(new HoloButton(left + panelW - 110, y, 100, 14, Component.translatable("glass.ui.disconnect"), b -> disconnect(), true));

        nearby = addRenderableWidget(new HoloList<GlassProjectorBlockEntity>(left + 10, top + 158, panelW - 20, 53,
                WirelessProjectorScreen::name,
                p -> Component.translatable("glass.ui.linkAway", Math.round(Math.sqrt(p.getBlockPos().distSqr(here)))).withColor(Holo.MUTED),
                Component.translatable("glass.ui.noProjectors"))
                .keyedBy(GlassProjectorBlockEntity::getBlockPos)
                .onSelect(p -> {
                    connected.select(null);
                    LinkHighlight.show(here, p.getBlockPos(), false, HIGHLIGHT_SECONDS);
                    updateButtons();
                })
                .onActivate(p -> confirm()));
        link = addRenderableWidget(new HoloButton(left + panelW - 110, top + panelH - 26, 100, 16, Component.translatable("glass.ui.link"), b -> confirm()));
        updateButtons();
    }

    private static Component name(GlassProjectorBlockEntity p)
    {
        BlockPos pos = p.getBlockPos();
        String name = !p.channelName.isEmpty() ? p.channelName : Component.translatable("glass.ui.unnamedProjector").getString();
        return Component.literal(name).append(Component.literal("  " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).withColor(Holo.MUTED));
    }

    @Override
    protected void refresh()
    {
        BlockPos here = wireless.getBlockPos();
        List<GlassProjectorBlockEntity> linked = new ArrayList<>();
        List<GlassProjectorBlockEntity> free = new ArrayList<>();
        for(GlassProjectorBlockEntity projector : GlassClientState.INSTANCE.loadedProjectors)
        {
            if(projector.isRemoved())
            {
                continue;
            }
            if(projector.wirelessPos.contains(here))
            {
                linked.add(projector);
            }
            else if(!projector.active && projector.wirelessPos.size() < GlassProjectorBlockEntity.MAX_WIRELESS
                    && GlassProjectorBlockEntity.inWirelessRange(projector.getBlockPos(), here))
            {
                free.add(projector);
            }
        }
        Comparator<GlassProjectorBlockEntity> byDistance = Comparator.comparingDouble(p -> p.getBlockPos().distSqr(here));
        linked.sort(byDistance);
        free.sort(byDistance);
        if(!sameItems(connected.items(), linked))
        {
            connected.setItems(linked);
        }
        if(!sameItems(nearby.items(), free))
        {
            nearby.setItems(free);
        }
        updateButtons();
    }

    private static boolean sameItems(List<GlassProjectorBlockEntity> a, List<GlassProjectorBlockEntity> b)
    {
        if(a.size() != b.size())
        {
            return false;
        }
        for(int i = 0; i < a.size(); i++)
        {
            if(!Objects.equals(a.get(i), b.get(i)))
            {
                return false;
            }
        }
        return true;
    }

    /** Connections change without a network update (a projector syncs its own block): look again every tick. */
    @Override
    public void tick()
    {
        super.tick();
        refresh();
    }

    private void updateButtons()
    {
        if(disconnect != null)
        {
            GlassProjectorBlockEntity p = connected.getSelected();
            disconnect.active = p != null;
        }
        if(link != null)
        {
            link.active = nearby.getSelected() != null;
        }
    }

    private void locate(GlassProjectorBlockEntity p, boolean isConnected)
    {
        LinkHighlight.show(wireless.getBlockPos(), p.getBlockPos(), isConnected, HIGHLIGHT_SECONDS);
        onClose();
        if(minecraft != null && minecraft.player != null)
        {
            minecraft.player.displayClientMessage(Component.translatable("glass.msg.located"), true);
        }
    }

    private void disconnect()
    {
        GlassProjectorBlockEntity p = connected.getSelected();
        if(p == null)
        {
            warn(Component.translatable("glass.msg.pickLinked"));
            return;
        }
        if(p.active)
        {
            warn(Component.translatable("glass.msg.switchOffFirst"));
            return;
        }
        clearWarning();
        ClientPlayNetworking.send(new GlassNetworking.UnlinkWireless(wireless.getBlockPos(), p.getBlockPos()));
    }

    private void confirm()
    {
        GlassProjectorBlockEntity projector = nearby.getSelected();
        if(projector == null)
        {
            warn(Component.translatable("glass.msg.pickProjector"));
            return;
        }
        clearWarning();
        ClientPlayNetworking.send(new GlassNetworking.SetProjector(wireless.getBlockPos(), projector.getBlockPos()));
        LinkHighlight.show(wireless.getBlockPos(), projector.getBlockPos(), true, HIGHLIGHT_SECONDS);
    }

    @Override
    protected void onEnter()
    {
        confirm();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        if(age() >= 0.18F)
        {
            Holo.label(g, font, Component.translatable("glass.ui.connectedTo", connected.items().size()), left + 10, top + 41);
            Holo.label(g, font, Component.translatable("glass.ui.availableProjectors"), left + 10, top + 147);
        }
    }
}
