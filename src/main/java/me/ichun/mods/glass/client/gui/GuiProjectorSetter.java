package me.ichun.mods.glass.client.gui;

import me.ichun.mods.glass.common.GeneralLaymansAestheticSpyingScreen;
import me.ichun.mods.glass.common.packet.PacketSetProjector;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassMaster;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassWireless;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.translation.I18n;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;

public class GuiProjectorSetter extends GuiScreen
{
    public static final ResourceLocation texBackground = new ResourceLocation("generallaymansaestheticspyingscreen", "textures/gui/channel_set.png");

    public static final int ID_CONFIRM = 0;

    public int xSize = 93;
    public int ySize = 75;

    protected int guiLeft;
    protected int guiTop;

    public TileEntityGlassWireless wireless;

    public int index = -1;

    public ArrayList<TileEntityGlassMaster> channels;
    public GuiProjectorList trackList;

    public GuiProjectorSetter(TileEntityGlassWireless wireless)
    {
        this.wireless = wireless;
    }

    @Override
    public void initGui()
    {
        Keyboard.enableRepeatEvents(true);

        this.guiLeft = (this.width - this.xSize) / 2;
        this.guiTop = (this.height - this.ySize) / 2;

        buttonList.clear();
        buttonList.add(new GuiButton(ID_CONFIRM, guiLeft + (xSize - 50) / 2, guiTop + ySize + 2, 50, 20, I18n.translateToLocal("gui.done")));

        channels = new ArrayList<>();
        for(TileEntity te : Minecraft.getMinecraft().world.loadedTileEntityList)
        {
            if(te instanceof TileEntityGlassMaster && (((TileEntityGlassMaster)te).setChannel.isEmpty() || ((TileEntityGlassMaster)te).setChannel.startsWith("public:") || ((TileEntityGlassMaster)te).setChannel.startsWith(Minecraft.getMinecraft().player.getName())))
            {
                channels.add((TileEntityGlassMaster)te);
            }
        }
        trackList = new GuiProjectorList(this, xSize - 10, ySize - 22, guiTop + 11, guiTop + ySize - 5, guiLeft + 5, 8, channels);
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
        GeneralLaymansAestheticSpyingScreen.channel.sendToServer(new PacketSetProjector(wireless.getPos(), channels.get(index).getPos()));
        mc.displayGuiScreen(null);
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
        GlStateManager.scale(0.5F, 0.5F, 0.5F);
        fontRenderer.drawString(I18n.translateToLocal("glass.gui.availableProjectors"), (guiLeft + 5) / 0.5F, (guiTop + 5) / 0.5F, 16777215, true);
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
    }
}
