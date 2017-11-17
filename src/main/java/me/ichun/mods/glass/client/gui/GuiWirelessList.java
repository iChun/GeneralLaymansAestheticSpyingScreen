package me.ichun.mods.glass.client.gui;

import me.ichun.mods.ichunutil.client.render.RendererHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.fml.client.GuiScrollingList;

import java.util.ArrayList;

public class GuiWirelessList extends GuiScrollingList
{
    private GuiWirelessOrder parent;
    public ArrayList<BlockPos> channels;

    public GuiWirelessList(GuiWirelessOrder parent, int width, int height, int top, int bottom, int left, int entryHeight, ArrayList<BlockPos> track)
    {
        super(Minecraft.getMinecraft(), width, height, top, bottom, left, entryHeight, parent.width, parent.height);
        this.parent = parent;
        this.channels = track;
    }

    @Override
    protected int getSize()
    {
        return channels.size();
    }

    @Override
    protected void elementClicked(int index, boolean doubleClick)
    {
        parent.setIndex(index);
    }

    @Override
    protected boolean isSelected(int index)
    {
        return parent.isSelectedIndex(index);
    }

    @Override
    protected void drawBackground()
    {
    }

    @Override
    protected void drawSlot(int idx, int right, int top, int height, Tessellator tess)
    {
        if(idx >= 0 && idx < channels.size())
        {
            FontRenderer font = this.parent.getFontRenderer();
            BlockPos channel = channels.get(idx);
            GlStateManager.pushMatrix();
            GlStateManager.scale(0.5F, 0.5F, 1F);
            String name = "(" + channel.getX() + ", " + channel.getY() + ", " + channel.getZ() + ")";
            font.drawString(name, (this.left + 2) * 2, top * 2, RendererHelper.getRandomColourFromString(name));
            GlStateManager.popMatrix();
        }
    }
}
