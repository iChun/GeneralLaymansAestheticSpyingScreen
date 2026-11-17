package ryzlar.glass.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import ryzlar.glass.GlassRegistry;
import ryzlar.glass.blockentity.GlassProjectorBlockEntity;
import ryzlar.glass.client.GlassClientState;
import ryzlar.glass.network.GlassNetworking;

import java.util.List;

/**
 * Picks which terminal channel a projector shows. Port of GuiChannelSetterProjector.
 */
public class ProjectorChannelScreen extends Screen
{
    private final int xSize = 93;
    private final int ySize = 75;
    private int guiLeft;
    private int guiTop;

    private final GlassProjectorBlockEntity master;
    private MiniList<String> list;

    public ProjectorChannelScreen(GlassProjectorBlockEntity master)
    {
        super(Component.translatable("block.generallaymansaestheticspyingscreen.glass_projector"));
        this.master = master;
    }

    @Override
    protected void init()
    {
        guiLeft = (width - xSize) / 2;
        guiTop = (height - ySize) / 2;

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> confirmSelection()).bounds(guiLeft + (xSize - 50) / 2, guiTop + ySize + 2, 50, 20).build());
        addRenderableWidget(Button.builder(Component.empty(), b -> minecraft.setScreen(new WirelessOrderScreen(master)))
                .bounds(guiLeft + xSize + 1, guiTop, 20, 20)
                .tooltip(Tooltip.create(Component.translatable("glass.gui.wirelessOrderButton")))
                .build());

        String playerName = minecraft.player.getGameProfile().getName();
        List<String> channels = GlassClientState.INSTANCE.availableChannels(playerName);
        list = new MiniList<>(guiLeft + 5, guiTop + 11, xSize - 10, ySize - 16, channels, ProjectorChannelScreen::channelLabel, MiniList::alternatingColour);
        for(int i = 0; i < channels.size(); i++)
        {
            if(channels.get(i).equalsIgnoreCase(master.setChannel))
            {
                list.selected = i;
                break;
            }
        }
    }

    private static String channelLabel(String channel)
    {
        String name = channel.substring(channel.indexOf(':') + 1);
        return channel.startsWith("public:") ? Component.translatable("glass.gui.public").getString() + " " + name : name;
    }

    private void confirmSelection()
    {
        if(list.selected >= 0 && list.selected < list.items().size())
        {
            ClientPlayNetworking.send(new GlassNetworking.SetChannel(master.getBlockPos(), list.items().get(list.selected)));
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
        graphics.renderItem(new ItemStack(GlassRegistry.GLASS_WIRELESS_EXTENSION), guiLeft + xSize + 3, guiTop + 2);

        graphics.pose().pushMatrix();
        graphics.pose().translate(guiLeft + 5, guiTop + 5);
        graphics.pose().scale(0.5F, 0.5F);
        graphics.drawString(font, Component.translatable("glass.gui.availableChannels"), 0, 0, 0xFFFFFFFF, true);
        graphics.pose().popMatrix();
    }

    @Override
    public boolean isPauseScreen()
    {
        return true;
    }
}
