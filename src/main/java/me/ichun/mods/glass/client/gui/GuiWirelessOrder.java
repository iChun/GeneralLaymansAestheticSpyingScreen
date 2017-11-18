package me.ichun.mods.glass.client.gui;

import me.ichun.mods.glass.common.GeneralLaymansAestheticSpyingScreen;
import me.ichun.mods.glass.common.packet.PacketSetChannel;
import me.ichun.mods.glass.common.packet.PacketWirelessOrder;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassMaster;
import me.ichun.mods.ichunutil.client.render.RendererHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.translation.I18n;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

public class GuiWirelessOrder extends GuiScreen
{
    public static final ResourceLocation texBackground = new ResourceLocation("generallaymansaestheticspyingscreen", "textures/gui/wireless_order.png");

    public static final int ID_CONFIRM = 0;
    public static final int ID_WIRELESS_ORDER = 1;
    public static final int ID_UP = 2;
    public static final int ID_DOWN = 3;

    public int xSize = 170;
    public int ySize = 75;

    protected int guiLeft;
    protected int guiTop;

    public TileEntityGlassMaster master;

    public int index = -1;

    public ArrayList<BlockPos> channels;
    public GuiWirelessList trackList;

    public float rotateX = 0F;
    public float rotateY = 0F;
    public float scale = 1F;

    public boolean hasClicked = false;
    public boolean releasedMouse = false;

    public int prevMouseX;
    public int prevMouseY;

    public GuiWirelessOrder(TileEntityGlassMaster master)
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
        buttonList.add(new GuiButton(ID_UP, guiLeft + xSize + 1, guiTop + 21 , 20, 20, "^"));
        buttonList.add(new GuiButton(ID_DOWN, guiLeft + xSize + 1, guiTop + 41, 20, 20, "v"));

        channels = new ArrayList<>(master.wirelessPos);
        trackList = new GuiWirelessList(this, 93 - 10, ySize - 22, guiTop + 11, guiTop + ySize - 5, guiLeft + 5, 8, channels);

        rotateX = 0F;
        rotateY = 0F;

        releasedMouse = false;
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
        GeneralLaymansAestheticSpyingScreen.channel.sendToServer(new PacketWirelessOrder(master.getPos(), channels));
        mc.displayGuiScreen(null);
    }

    @Override
    public void handleMouseInput() throws IOException
    {
        int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;

        super.handleMouseInput();

        if(mouseX > guiLeft + 100 && mouseX < guiLeft + 165 && mouseY > guiTop + 5 && mouseY < guiTop + 70)
        {
            int scroll = Mouse.getEventDWheel();
            if(scroll != 0)
            {
                scale += 0.1F * (1F * scroll / 120.0F);
                if(scale < 0.1F)
                {
                    scale = 0.1F;
                }
            }
        }

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
        if(!hasClicked)
        {
            rotateX += 2.5F;
            releasedMouse = releasedMouse || !Mouse.isButtonDown(0);
        }
    }

    @Override
    protected void keyTyped(char c, int i)
    {
        if (i == 1)
        {
            mc.displayGuiScreen(null);
            mc.setIngameFocus();
        }
        if(i == Keyboard.KEY_DELETE && index >= 0 && index < channels.size())
        {
            channels.remove(index);
            index--;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks)
    {
        if(mc == null)
        {
            mc = Minecraft.getMinecraft();
            fontRenderer = mc.fontRenderer;
            prevMouseX = mouseX;
            prevMouseY = mouseY;
        }
        drawDefaultBackground();

        GlStateManager.color(1F, 1F, 1F, 1F);
        this.mc.getTextureManager().bindTexture(texBackground);
        this.drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);

        trackList.drawScreen(mouseX, mouseY, partialTicks);

        super.drawScreen(mouseX, mouseY, partialTicks);

        RendererHelper.drawColourOnScreen(0x444444, 255, guiLeft + 100, guiTop + 5, 65, 65, 0);

        GlStateManager.pushMatrix();

        GlStateManager.enableCull();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);

        RendererHelper.startGlScissor(guiLeft + 100, guiTop + 5, 65, 65);
        if(!channels.isEmpty())
        {
            if(channels.size() != 1)
            {
                if(prevMouseX != 0 && prevMouseY != 0 && mouseX > guiLeft + 100 && mouseX < guiLeft + 165 && mouseY > guiTop + 5 && mouseY < guiTop + 70 && releasedMouse && Mouse.isButtonDown(0)) //dragging
                {
                    hasClicked = true;
                    float mag = 1F;
                    rotateX += (mouseX - prevMouseX) * mag;
                    rotateY += (mouseY - prevMouseY) * mag;
                }

                EnumFacing face = master.placingFace;

                GlStateManager.translate(guiLeft + 135, guiTop + 40, 50F);

                GlStateManager.scale(-5F * scale, -5F * scale, 5F * scale);

                int horiOrient = (face.getAxis() == EnumFacing.Axis.Y ? EnumFacing.UP : face).getOpposite().getHorizontalIndex();
                GlStateManager.rotate((face.getIndex() > 0 ? 180F : 0F) + -horiOrient * 90F, 0F, 1F, 0F);
                if(face.getAxis() == EnumFacing.Axis.Y)
                {
                    GlStateManager.rotate(face == EnumFacing.UP ? -90F : 90F, 1F, 0F, 0F);
                }

                GlStateManager.rotate(rotateX, 0F, 1F, 0F);
                GlStateManager.rotate(rotateY, 0F, 0F, 1F);

                float pushback = 0.501F;

                GlStateManager.glBegin(GL11.GL_TRIANGLE_STRIP);
                GlStateManager.color(1F, 1F, 1F, 1F);
                GlStateManager.glVertex3f((float)(face.getFrontOffsetX() * pushback), (float)(face.getFrontOffsetY() * pushback), (float)(face.getFrontOffsetZ() * pushback));
                for(BlockPos pos : channels)
                {
                    float pX = (float)(face.getFrontOffsetX() * pushback * (face.getFrontOffsetX() > 0 && pos.getX() > master.getPos().getX() ? -1D : 1D));
                    float pY = (float)(face.getFrontOffsetY() * pushback * (face.getFrontOffsetY() > 0 && pos.getY() > master.getPos().getY() ? -1D : 1D));
                    float pZ = (float)(face.getFrontOffsetZ() * pushback * (face.getFrontOffsetZ() > 0 && pos.getZ() > master.getPos().getZ() ? -1D : 1D));
                    String name = "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
                    int clr = RendererHelper.getRandomColourFromString(name);
                    RendererHelper.setColorFromInt(clr);
                    GlStateManager.glVertex3f((pos.getX() - master.getPos().getX() + pX), (pos.getY() - master.getPos().getY() + pY), (pos.getZ() - master.getPos().getZ() + pZ));
                }
                GlStateManager.glEnd();

                GlStateManager.color(1F, 1F, 1F, 1F);
            }
        }
        RendererHelper.endGlScissor();

        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();

        GlStateManager.popMatrix();

        GlStateManager.pushMatrix();
        GlStateManager.enableDepth();
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.translate(guiLeft + xSize + 3, guiTop + 2, 0);
        mc.getRenderItem().renderItemAndEffectIntoGUI(new ItemStack(GeneralLaymansAestheticSpyingScreen.blockGlass, 1, 1), 0, 0);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();
        GlStateManager.popMatrix();

        GlStateManager.pushMatrix();
        GlStateManager.scale(0.5F, 0.5F, 0.5F);
        fontRenderer.drawString(I18n.translateToLocal("glass.gui.wirelessExtensionOrder"), (guiLeft + 5) / 0.5F, (guiTop + 5) / 0.5F, 16777215, true);
        GlStateManager.popMatrix();

        prevMouseX = mouseX;
        prevMouseY = mouseY;
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
            mc.displayGuiScreen(new GuiChannelSetterProjector(master));
        }
        else if(btn.id == ID_UP)
        {
            if(index > 0 && index < channels.size())
            {
                BlockPos pos = channels.get(index);
                channels.remove(index);
                channels.add(index - 1, pos);
                index--;
            }
        }
        else if(btn.id == ID_DOWN)
        {
            if(index >= 0 && index < channels.size() - 1)
            {
                BlockPos pos = channels.get(index);
                channels.remove(index);
                channels.add(index + 1, pos);
                index++;
            }
        }
    }
}
