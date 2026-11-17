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
import ryzlar.glass.network.GlassNetworking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Links a wireless extension to a (nearby, loaded) projector. Port of GuiProjectorSetter.
 */
public class WirelessProjectorScreen extends HoloScreen
{
    private final GlassWirelessBlockEntity wireless;
    private HoloList<GlassProjectorBlockEntity> list;

    public WirelessProjectorScreen(GlassWirelessBlockEntity wireless)
    {
        super(Component.translatable("glass.ui.wirelessLink"), 280, 200);
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
        list = addRenderableWidget(new HoloList<GlassProjectorBlockEntity>(left + 10, top + 52, panelW - 20, 104,
                p -> Component.literal(p.channelName.isEmpty() ? Component.translatable("glass.ui.unassigned").getString() : p.channelName),
                p -> Component.literal(p.getBlockPos().getX() + ", " + p.getBlockPos().getY() + ", " + p.getBlockPos().getZ()),
                Component.translatable("glass.ui.noProjectors"))
                .onActivate(p -> confirm()));
        addRenderableWidget(new HoloButton(left + panelW - 110, top + panelH - 34, 100, 16, Component.translatable("glass.ui.link"), b -> confirm()));
    }

    @Override
    protected void refresh()
    {
        List<GlassProjectorBlockEntity> projectors = new ArrayList<>();
        for(GlassProjectorBlockEntity projector : GlassClientState.INSTANCE.loadedProjectors)
        {
            if(!projector.isRemoved() && !projector.active)
            {
                projectors.add(projector);
            }
        }
        BlockPos here = wireless.getBlockPos();
        projectors.sort(Comparator.comparingDouble(p -> p.getBlockPos().distSqr(here)));
        list.setItems(projectors);
    }

    private void confirm()
    {
        GlassProjectorBlockEntity projector = list.getSelected();
        if(projector == null)
        {
            warn(Component.translatable("glass.msg.pickProjector"));
            return;
        }
        ClientPlayNetworking.send(new GlassNetworking.SetProjector(wireless.getBlockPos(), projector.getBlockPos()));
        onClose();
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
            Holo.label(g, font, Component.translatable("glass.ui.availableProjectors"), left + 10, top + 41);
        }
    }
}
