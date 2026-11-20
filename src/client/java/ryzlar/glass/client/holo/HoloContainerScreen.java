package ryzlar.glass.client.holo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import ryzlar.glass.client.gui.holo.Holo;
import ryzlar.glass.client.gui.holo.HoloScreen;

import java.util.List;

/**
 * What was in a chest (or barrel, shulker box, furnace...) when a holo cube scanned it, seen inside its simulation: the
 * items in their slots, with their tooltips. Only a record: nothing can be taken out or put in.
 */
public class HoloContainerScreen extends HoloScreen
{
    private static final int COLUMNS = 9;
    private static final int SLOT = 20;
    private static final int PAD = 12;
    private static final int HEADER = 34;
    private static final int FOOTER = 24;

    private final List<ItemStack> items;
    private final int rows;
    private int gridLeft;
    private int gridTop;

    public HoloContainerScreen(Component name, List<ItemStack> items)
    {
        super(name, PAD * 2 + COLUMNS * SLOT, HEADER + rows(items.size()) * SLOT + FOOTER);
        this.items = items;
        this.rows = rows(items.size());
    }

    private static int rows(int slots)
    {
        return Math.max(1, (slots + COLUMNS - 1) / COLUMNS);
    }

    @Override
    protected void build()
    {
        gridLeft = left + PAD;
        gridTop = top + HEADER;
    }

    @Override
    protected Component subtitle()
    {
        return Component.translatable("glass.ui.cube.container");
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        if(age() < 0.18F)
        {
            return;
        }
        ItemStack hovered = ItemStack.EMPTY;
        for(int i = 0; i < rows * COLUMNS; i++)
        {
            int x = gridLeft + (i % COLUMNS) * SLOT;
            int y = gridTop + (i / COLUMNS) * SLOT;
            boolean exists = i < items.size();
            g.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, exists ? Holo.FIELD : Holo.withAlpha(Holo.FIELD, 0.4F));
            Holo.frame(g, x + 1, y + 1, SLOT - 2, SLOT - 2, exists ? Holo.ACCENT_FAINT : Holo.withAlpha(Holo.ACCENT_FAINT, 0.4F));
            if(!exists)
            {
                continue;
            }
            ItemStack stack = items.get(i);
            if(stack.isEmpty())
            {
                continue;
            }
            g.renderItem(stack, x + 2, y + 2);
            g.renderItemDecorations(font, stack, x + 2, y + 2);
            if(mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT)
            {
                g.fill(x + 2, y + 2, x + SLOT - 2, y + SLOT - 2, 0x403CE6FF);
                hovered = stack;
            }
        }
        g.drawString(font, Component.translatable("glass.ui.cube.containerReadOnly"), left + PAD, top + panelH - 15, Holo.MUTED, false);
        if(!hovered.isEmpty())
        {
            g.setTooltipForNextFrame(font, hovered, mouseX, mouseY);
        }
    }
}
