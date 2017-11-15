package me.ichun.mods.glass.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.fml.client.GuiScrollingList;

import java.util.ArrayList;

public class GuiChannelList extends GuiScrollingList
{
    private GuiChannelSetterProjector parent;
    public ArrayList<String> channels;

    public GuiChannelList(GuiChannelSetterProjector parent, int width, int height, int top, int bottom, int left, int entryHeight, ArrayList<String> track)
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
            String channel = channels.get(idx);
            GlStateManager.pushMatrix();
            GlStateManager.scale(0.5F, 0.5F, 1F);
            String name = font.trimStringToWidth(channel.substring(channel.indexOf(":") + 1), (listWidth - 10) * 2);
            if(channel.startsWith("public:"))
            {
                name = I18n.translateToLocal("glass.gui.public") + " " + name;
            }
            font.drawString(name, (this.left + 2) * 2, top * 2, idx % 2 == 0 ? 0xFFFFFF : 0xAAAAAA);
            GlStateManager.popMatrix();
        }
    }
}
