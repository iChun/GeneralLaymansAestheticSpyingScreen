package me.ichun.mods.glass.client.gui;

import me.ichun.mods.glass.common.GeneralLaymansAestheticSpyingScreen;
import me.ichun.mods.glass.common.packet.PacketSetChannel;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassMaster;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.translation.I18n;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

public class GuiChannelSetterProjector extends GuiScreen
{
    public static final ResourceLocation texBackground = new ResourceLocation("generallaymansaestheticspyingscreen", "textures/gui/channel_set.png");

    public static final int ID_CONFIRM = 0;
    public static final int ID_WIRELESS_ORDER = 1;

    public int xSize = 93;
    public int ySize = 75;

    protected int guiLeft;
    protected int guiTop;

    public TileEntityGlassMaster master;

    public int index = -1;

    public ArrayList<String> channels;
    public GuiChannelList trackList;

    public GuiChannelSetterProjector(TileEntityGlassMaster master)
    {
        this.master = master;
    }

    @Override
    public void initGui()
    {
        Keyboard.enableRepeatEvents(true);

        this.guiLeft = (this.width - this.xSize) / 2;
        this.guiTop = (this.height - this.ySize) / 2;

        buttonList.clear();
        buttonList.add(new GuiButton(ID_CONFIRM, guiLeft + (xSize - 50) / 2, guiTop + ySize + 2, 50, 20, I18n.translateToLocal("gui.done")));
        buttonList.add(new GuiButton(ID_WIRELESS_ORDER, guiLeft + xSize + 1, guiTop , 20, 20, ""));


        channels = new ArrayList<>();
        for(String s : GeneralLaymansAestheticSpyingScreen.eventHandlerClient.terminalLocations.keySet())
        {
            if(s.startsWith("public:") || s.startsWith(Minecraft.getMinecraft().player.getName() + ":"))
            {
                channels.add(s);
            }
        }
        Collections.sort(channels);
        for(int i = 0; i < channels.size(); i++)
        {
            String channel = channels.get(i);
            if(channel.equalsIgnoreCase(master.setChannel))
            {
                index = i;
                break;
            }
        }
        trackList = new GuiChannelList(this, xSize - 10, ySize - 22, guiTop + 11, guiTop + ySize - 5, guiLeft + 5, 8, channels);
    }

    public FontRenderer getFontRenderer()
    {
        return fontRenderer;
    }

    public void setIndex(int i)
    {
        index = i;
    }

    public boolean isSelectedIndex(int i)
    {
        return index == i;
    }

    public void confirmSelection(boolean doubleClick)
    {
        if(!(index >= 0 && index < channels.size()))
        {
            return;
        }
        GeneralLaymansAestheticSpyingScreen.channel.sendToServer(new PacketSetChannel(master.getPos(), channels.get(index)));
        mc.displayGuiScreen(null);
    }

    @Override
    public void handleMouseInput() throws IOException
    {
        int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;

        super.handleMouseInput();
        this.trackList.handleMouseInput(mouseX, mouseY);
    }

    @Override
    public void onGuiClosed()
    {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen()
    {
    }

    @Override
    protected void keyTyped(char c, int i)
    {
        if (i == 1)
        {
            mc.displayGuiScreen(null);
            mc.setIngameFocus();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks)
    {
        if(mc == null)
        {
            mc = Minecraft.getMinecraft();
            fontRenderer = mc.fontRenderer;
        }
        drawDefaultBackground();

        GlStateManager.color(1F, 1F, 1F, 1F);
        this.mc.getTextureManager().bindTexture(texBackground);
        this.drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);

        trackList.drawScreen(mouseX, mouseY, partialTicks);

        super.drawScreen(mouseX, mouseY, partialTicks);

        GlStateManager.pushMatrix();
        GlStateManager.enableDepth();
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.translate(guiLeft + xSize + 3, guiTop + 2, 0);
        mc.getRenderItem().renderItemAndEffectIntoGUI(new ItemStack(GeneralLaymansAestheticSpyingScreen.blockGlass, 1, 2), 0, 0);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();
        GlStateManager.popMatrix();

        GlStateManager.pushMatrix();
        GlStateManager.scale(0.5F, 0.5F, 0.5F);
        fontRenderer.drawString(I18n.translateToLocal("glass.gui.availableChannels"), (guiLeft + 5) / 0.5F, (guiTop + 5) / 0.5F, 16777215, true);
        GlStateManager.popMatrix();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int btn) throws IOException
    {
        super.mouseClicked(mouseX, mouseY, btn);
        trackList.handleMouseInput(mouseX, mouseY);
    }

    @Override
    public boolean doesGuiPauseGame()
    {
        return true;
    }

    @Override
    protected void actionPerformed(GuiButton btn)
    {
        if(btn.id == ID_CONFIRM)
        {
            confirmSelection(false);
        }
        else if(btn.id == ID_WIRELESS_ORDER)
        {
            mc.displayGuiScreen(new GuiWirelessOrder(master));
        }
    }
}
