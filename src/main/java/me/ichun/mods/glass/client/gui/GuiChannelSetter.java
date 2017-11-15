package me.ichun.mods.glass.client.gui;

import me.ichun.mods.glass.common.GeneralLaymansAestheticSpyingScreen;
import me.ichun.mods.glass.common.packet.PacketSetChannel;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassTerminal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.translation.I18n;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

public class GuiChannelSetter extends GuiScreen
{
    public static final ResourceLocation texBackground = new ResourceLocation("generallaymansaestheticspyingscreen", "textures/gui/channel_set.png");

    public static final int ID_CONFIRM = 0;
    public static final int ID_PUBLIC_CHANNEL = 1;

    public int xSize = 93;
    public int ySize = 75;

    protected int guiLeft;
    protected int guiTop;

    public TileEntityGlassTerminal terminal;

    public GuiTextField channelName;
    public boolean isPublicChannel;

    public GuiChannelSetter(TileEntityGlassTerminal terminal)
    {
        this.terminal = terminal;
    }

    @Override
    public void initGui()
    {
//        xSize = 93;
//        ySize = 75;

        Keyboard.enableRepeatEvents(true);

        this.guiLeft = (this.width - this.xSize) / 2;
        this.guiTop = (this.height - this.ySize) / 2;

        channelName = new GuiTextField(0, mc.fontRenderer, this.guiLeft + 7, this.guiTop + 17, 80, mc.fontRenderer.FONT_HEIGHT + 4);
        channelName.setMaxStringLength(15);
        channelName.setTextColor(16777215);
        channelName.setText(terminal.channelName.isEmpty() ? "" : terminal.channelName.substring(terminal.channelName.indexOf(":") + 1));

        isPublicChannel = !terminal.channelName.isEmpty() && terminal.channelName.contains("public");

        buttonList.clear();
        buttonList.add(new GuiButton(ID_CONFIRM, guiLeft + (xSize - 50) / 2, guiTop + ySize + 2, 50, 20, I18n.translateToLocal("gui.done")));
        buttonList.add(new GuiButton(ID_PUBLIC_CHANNEL, guiLeft + 6, guiTop + 47, 82, 20, I18n.translateToLocal(isPublicChannel ? "gui.yes" : "gui.no")));
    }

    @Override
    public void onGuiClosed()
    {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen()
    {
        channelName.updateCursorCounter();
    }

    @Override
    protected void keyTyped(char c, int i)
    {
        channelName.textboxKeyTyped(c, i);
        if(channelName.isFocused())
        {
            if(i == Keyboard.KEY_TAB)
            {
                isPublicChannel = !isPublicChannel;
                for(GuiButton btn : buttonList)
                {
                    if(btn.id == ID_PUBLIC_CHANNEL)
                    {
                        btn.displayString = I18n.translateToLocal(isPublicChannel ? "gui.yes" : "gui.no");
                        break;
                    }
                }
            }
            if (i == Keyboard.KEY_RETURN)
            {
                confirm();
            }
        }
        if (i == 1)
        {
            if(channelName.isFocused())
            {
                channelName.setText("");
                channelName.setFocused(false);
            }
            else
            {
                mc.displayGuiScreen(null);
                mc.setIngameFocus();
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks)
    {
//                initGui();
        if(mc == null)
        {
            mc = Minecraft.getMinecraft();
            fontRenderer = mc.fontRenderer;
        }
        drawDefaultBackground();

        GlStateManager.color(1F, 1F, 1F, 1F);
        this.mc.getTextureManager().bindTexture(texBackground);
        this.drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);

        if(channelName.getVisible())
        {
            channelName.drawTextBox();
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        fontRenderer.drawString(I18n.translateToLocal("glass.gui.channelName"), guiLeft + 8, guiTop + 6, 16777215, true);

        fontRenderer.drawString(I18n.translateToLocal("glass.gui.publicChannel"), guiLeft + 8, guiTop + 37, 16777215, true);

    }

    public void confirm()
    {
        GeneralLaymansAestheticSpyingScreen.channel.sendToServer(new PacketSetChannel(terminal.getPos(), (isPublicChannel ? "public:" : (Minecraft.getMinecraft().player.getName() + ":")) + channelName.getText()));
        mc.displayGuiScreen(null);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int btn) throws IOException
    {
        super.mouseClicked(mouseX, mouseY, btn);
        channelName.mouseClicked(mouseX, mouseY, btn);
    }

    @Override
    public boolean doesGuiPauseGame()
    {
        return true;
    }

    @Override
    protected void actionPerformed(GuiButton btn)
    {
        if(btn.id == ID_PUBLIC_CHANNEL)
        {
            isPublicChannel = !isPublicChannel;
            btn.displayString = I18n.translateToLocal(isPublicChannel ? "gui.yes" : "gui.no");
        }
        else if(btn.id == ID_CONFIRM)
        {
            confirm();
        }
    }
}
