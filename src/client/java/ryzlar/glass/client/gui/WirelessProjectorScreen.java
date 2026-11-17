package ryzlar.glass.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import ryzlar.glass.blockentity.GlassProjectorBlockEntity;
import ryzlar.glass.blockentity.GlassWirelessBlockEntity;
import ryzlar.glass.client.GlassClientState;
import ryzlar.glass.network.GlassNetworking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Links a wireless extension to a projector. Port of GuiProjectorSetter.
 */
public class WirelessProjectorScreen extends Screen
{
    private final int xSize = 93;
    private final int ySize = 75;
    private int guiLeft;
    private int guiTop;

    private final GlassWirelessBlockEntity wireless;
    private MiniList<GlassProjectorBlockEntity> list;

    public WirelessProjectorScreen(GlassWirelessBlockEntity wireless)
    {
        super(Component.translatable("block.generallaymansaestheticspyingscreen.glass_wireless_extension"));
        this.wireless = wireless;
    }

    @Override
    protected void init()
    {
        guiLeft = (width - xSize) / 2;
        guiTop = (height - ySize) / 2;

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> confirmSelection()).bounds(guiLeft + (xSize - 50) / 2, guiTop + ySize + 2, 50, 20).build());

        String playerName = minecraft.player.getGameProfile().getName();
        List<GlassProjectorBlockEntity> projectors = new ArrayList<>();
        for(GlassProjectorBlockEntity projector : GlassClientState.INSTANCE.loadedProjectors)
        {
            if(!projector.isRemoved() && GlassClientState.INSTANCE.isOwnChannel(projector.setChannel, playerName))
            {
                projectors.add(projector);
            }
        }
        BlockPos here = wireless.getBlockPos();
        projectors.sort(Comparator.comparingDouble(p -> p.getBlockPos().distSqr(here)));
        list = new MiniList<>(guiLeft + 5, guiTop + 11, xSize - 10, ySize - 16, projectors, WirelessProjectorScreen::projectorLabel, MiniList::alternatingColour);
    }

    private static String projectorLabel(GlassProjectorBlockEntity projector)
    {
        String setChannel = projector.setChannel;
        String info = setChannel.isEmpty() ? Component.translatable("glass.gui.notSet").getString()
                : (setChannel.startsWith("public") ? Component.translatable("glass.gui.public").getString() + " " : "") + setChannel.substring(setChannel.indexOf(':') + 1);
        BlockPos pos = projector.getBlockPos();
        return info + " (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    private void confirmSelection()
    {
        if(list.selected >= 0 && list.selected < list.items().size())
        {
            ClientPlayNetworking.send(new GlassNetworking.SetProjector(wireless.getBlockPos(), list.items().get(list.selected).getBlockPos()));
            onClose();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if(list.mouseClicked(mouseX, mouseY))
        {
            if(list.wasDoubleClick())
            {
                confirmSelection();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical)
    {
        return list.mouseScrolled(mouseX, mouseY, vertical) || super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, ChannelSetterScreen.TEX_BACKGROUND, guiLeft, guiTop, 0F, 0F, xSize, ySize, 256, 256);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        super.render(graphics, mouseX, mouseY, partialTick);
        list.render(graphics, font);

        graphics.pose().pushMatrix();
        graphics.pose().translate(guiLeft + 5, guiTop + 5);
        graphics.pose().scale(0.5F, 0.5F);
        graphics.drawString(font, Component.translatable("glass.gui.availableProjectors"), 0, 0, 0xFFFFFFFF, true);
        graphics.pose().popMatrix();
    }

    @Override
    public boolean isPauseScreen()
    {
        return true;
    }
}
