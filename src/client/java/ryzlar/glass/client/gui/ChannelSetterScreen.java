package ryzlar.glass.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import ryzlar.glass.Glass;
import ryzlar.glass.blockentity.GlassTerminalBlockEntity;
import ryzlar.glass.network.GlassNetworking;

/**
 * Names a terminal's channel. Port of GuiChannelSetter.
 */
public class ChannelSetterScreen extends Screen
{
    public static final ResourceLocation TEX_BACKGROUND = Glass.id("textures/gui/channel_set.png");

    private final int xSize = 93;
    private final int ySize = 75;
    private int guiLeft;
    private int guiTop;

    private final GlassTerminalBlockEntity terminal;
    private EditBox channelName;
    private Button publicButton;
    private boolean isPublicChannel;

    public ChannelSetterScreen(GlassTerminalBlockEntity terminal)
    {
        super(Component.translatable("block.generallaymansaestheticspyingscreen.glass_terminal"));
        this.terminal = terminal;
        this.isPublicChannel = !terminal.channelName.isEmpty() && terminal.channelName.startsWith("public:");
    }

    @Override
    protected void init()
    {
        guiLeft = (width - xSize) / 2;
        guiTop = (height - ySize) / 2;

        String current = channelName != null ? channelName.getValue() : terminal.channelName.isEmpty() ? "" : terminal.channelName.substring(terminal.channelName.indexOf(':') + 1);
        channelName = new EditBox(font, guiLeft + 7, guiTop + 17, 80, font.lineHeight + 4, Component.translatable("glass.gui.channelName"));
        channelName.setMaxLength(GlassNetworking.MAX_CHANNEL_NAME);
        channelName.setTextColor(0xFFFFFFFF);
        channelName.setValue(current);
        addRenderableWidget(channelName);

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> confirm()).bounds(guiLeft + (xSize - 50) / 2, guiTop + ySize + 2, 50, 20).build());
        publicButton = addRenderableWidget(Button.builder(yesNo(), b -> togglePublic()).bounds(guiLeft + 6, guiTop + 47, 82, 20).build());

        setInitialFocus(channelName);
    }

    private Component yesNo()
    {
        return isPublicChannel ? CommonComponents.GUI_YES : CommonComponents.GUI_NO;
    }

    private void togglePublic()
    {
        isPublicChannel = !isPublicChannel;
        publicButton.setMessage(yesNo());
    }

    private void confirm()
    {
        if(minecraft != null && minecraft.player != null)
        {
            String prefix = GlassNetworking.channelPrefix(isPublicChannel, minecraft.player.getGameProfile().getName());
            ClientPlayNetworking.send(new GlassNetworking.SetChannel(terminal.getBlockPos(), prefix + channelName.getValue()));
        }
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        if(channelName.isFocused())
        {
            if(keyCode == GLFW.GLFW_KEY_TAB)
            {
                togglePublic();
                return true;
            }
            if(keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
            {
                confirm();
                return true;
            }
            if(keyCode == GLFW.GLFW_KEY_ESCAPE)
            {
                channelName.setValue("");
                channelName.setFocused(false);
                setFocused(null);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEX_BACKGROUND, guiLeft, guiTop, 0F, 0F, xSize, ySize, 256, 256);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, Component.translatable("glass.gui.channelName"), guiLeft + 8, guiTop + 6, 0xFFFFFFFF, true);
        graphics.drawString(font, Component.translatable("glass.gui.publicChannel"), guiLeft + 8, guiTop + 37, 0xFFFFFFFF, true);
    }

    @Override
    public boolean isPauseScreen()
    {
        return true;
    }
}
