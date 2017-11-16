package me.ichun.mods.glass.client.gui;

import me.ichun.mods.glass.common.tileentity.TileEntityGlassMaster;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.fml.client.GuiScrollingList;

import java.util.ArrayList;

public class GuiProjectorList extends GuiScrollingList
{
    private GuiProjectorSetter parent;
    public ArrayList<TileEntityGlassMaster> channels;

    public GuiProjectorList(GuiProjectorSetter parent, int width, int height, int top, int bottom, int left, int entryHeight, ArrayList<TileEntityGlassMaster> track)
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
        if(doubleClick)
        {
            parent.confirmSelection(true);
        }
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
            TileEntityGlassMaster channel = channels.get(idx);
            GlStateManager.pushMatrix();
            GlStateManager.scale(0.5F, 0.5F, 1F);
            String info = (channel.setChannel.isEmpty() ? I18n.translateToLocal("glass.gui.notSet")  : channel.setChannel.startsWith("public") ? I18n.translateToLocal("glass.gui.public") : "") + (!channel.setChannel.isEmpty() ? " " + channel.setChannel.substring(channel.setChannel.indexOf(":") + 1) : "");
            info = info + " (" + channel.getPos().getX() + ", " + channel.getPos().getY() + ", " + channel.getPos().getZ() + ")";
            String name = font.trimStringToWidth(info, (listWidth - 10) * 2);
            font.drawString(name, (this.left + 2) * 2, top * 2, idx % 2 == 0 ? 0xFFFFFF : 0xAAAAAA);
            GlStateManager.popMatrix();
        }
    }
}
