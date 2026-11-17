package ryzlar.glass.client.gui;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * The small scrolling list with half-size text used by the GLASS screens (1.12 used Forge's GuiScrollingList).
 */
public class MiniList<T>
{
    private static final int ROW_HEIGHT = 8;

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final List<T> items;
    private final Function<T, String> label;
    private final ToIntFunction<Integer> colour;

    public int selected = -1;
    private double scroll;
    private int lastClicked = -1;
    private long lastClickTime;

    public MiniList(int x, int y, int width, int height, List<T> items, Function<T, String> label, ToIntFunction<Integer> colour)
    {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.items = items;
        this.label = label;
        this.colour = colour;
    }

    public List<T> items()
    {
        return items;
    }

    private int maxScroll()
    {
        return Math.max(0, items.size() * ROW_HEIGHT - height);
    }

    public boolean isMouseOver(double mouseX, double mouseY)
    {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public void render(GuiGraphics graphics, Font font)
    {
        scroll = Mth.clamp(scroll, 0, maxScroll());
        boolean scrollBar = maxScroll() > 0;
        int textWidth = width - (scrollBar ? 6 : 0);

        graphics.enableScissor(x, y, x + width, y + height);
        for(int i = 0; i < items.size(); i++)
        {
            int rowY = y + i * ROW_HEIGHT - (int)scroll;
            if(rowY + ROW_HEIGHT < y || rowY > y + height)
            {
                continue;
            }
            if(i == selected)
            {
                graphics.fill(x, rowY, x + textWidth, rowY + ROW_HEIGHT, 0xFF808080);
                graphics.fill(x + 1, rowY + 1, x + textWidth - 1, rowY + ROW_HEIGHT - 1, 0xFF000000);
            }
            String text = font.plainSubstrByWidth(label.apply(items.get(i)), (textWidth - 4) * 2);
            graphics.pose().pushMatrix();
            graphics.pose().translate(x + 2, rowY + 2);
            graphics.pose().scale(0.5F, 0.5F);
            graphics.drawString(font, text, 0, 0, colour.applyAsInt(i), false);
            graphics.pose().popMatrix();
        }
        if(scrollBar)
        {
            int barHeight = Math.max(8, height * height / (items.size() * ROW_HEIGHT));
            int barY = y + (int)((height - barHeight) * (scroll / maxScroll()));
            graphics.fill(x + width - 4, y, x + width, y + height, 0xFF000000);
            graphics.fill(x + width - 4, barY, x + width, barY + barHeight, 0xFF808080);
        }
        graphics.disableScissor();
    }

    /** Returns true if the click was on a row. {@code doubleClick} is set by {@link #wasDoubleClick()}. */
    public boolean mouseClicked(double mouseX, double mouseY)
    {
        if(!isMouseOver(mouseX, mouseY))
        {
            return false;
        }
        int index = (int)((mouseY - y + scroll) / ROW_HEIGHT);
        if(index < 0 || index >= items.size())
        {
            return false;
        }
        long now = Util.getMillis();
        doubleClick = index == lastClicked && now - lastClickTime < 250L;
        lastClicked = index;
        lastClickTime = now;
        selected = index;
        return true;
    }

    private boolean doubleClick;

    public boolean wasDoubleClick()
    {
        return doubleClick;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount)
    {
        if(!isMouseOver(mouseX, mouseY))
        {
            return false;
        }
        scroll = Mth.clamp(scroll - amount * ROW_HEIGHT * 2, 0, maxScroll());
        return true;
    }

    public static int alternatingColour(int index)
    {
        return index % 2 == 0 ? 0xFFFFFFFF : 0xFFAAAAAA;
    }
}
