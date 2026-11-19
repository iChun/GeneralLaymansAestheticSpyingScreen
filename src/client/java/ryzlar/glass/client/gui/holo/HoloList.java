package ryzlar.glass.client.gui.holo;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/** A scrolling list of rows: a label on the left, an optional tag on the right, one row selectable. */
public class HoloList<T> extends AbstractWidget
{
    public static final int ROW = 13;

    private final Function<T, Component> label;
    @Nullable
    private final Function<T, Component> tag;
    private final Component empty;
    private List<T> items = new ArrayList<>();
    private int selected = -1;
    private double scroll;
    @Nullable
    private Consumer<T> onSelect;
    @Nullable
    private Consumer<T> onActivate;
    private int lastClicked = -1;
    private long lastClickTime;

    public HoloList(int x, int y, int width, int height, Function<T, Component> label, @Nullable Function<T, Component> tag, Component empty)
    {
        super(x, y, width, height, Component.empty());
        this.label = label;
        this.tag = tag;
        this.empty = empty;
    }

    public HoloList<T> onSelect(Consumer<T> onSelect)
    {
        this.onSelect = onSelect;
        return this;
    }

    /** Double click. */
    public HoloList<T> onActivate(Consumer<T> onActivate)
    {
        this.onActivate = onActivate;
        return this;
    }

    /** Rows with the same key are the same row (so the selection survives updated data). Default: equals. */
    public HoloList<T> keyedBy(Function<T, Object> key)
    {
        this.key = key;
        return this;
    }

    @Nullable
    private Function<T, Object> key;

    /** Replaces the rows, keeping the selection on the same row if it is still there. */
    public void setItems(List<T> newItems)
    {
        T previous = getSelected();
        items = new ArrayList<>(newItems);
        selected = -1;
        if(previous != null)
        {
            Object previousKey = key != null ? key.apply(previous) : previous;
            for(int i = 0; i < items.size(); i++)
            {
                if(Objects.equals(previousKey, key != null ? key.apply(items.get(i)) : items.get(i)))
                {
                    selected = i;
                    break;
                }
            }
        }
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    public List<T> items()
    {
        return items;
    }

    @Nullable
    public T getSelected()
    {
        return selected >= 0 && selected < items.size() ? items.get(selected) : null;
    }

    public int getSelectedIndex()
    {
        return selected;
    }

    public void select(@Nullable T item)
    {
        selected = item == null ? -1 : items.indexOf(item);
    }

    public void selectIndex(int index)
    {
        selected = index >= 0 && index < items.size() ? index : -1;
    }

    public void selectWhere(java.util.function.Predicate<T> test)
    {
        for(int i = 0; i < items.size(); i++)
        {
            if(test.test(items.get(i)))
            {
                selected = i;
                return;
            }
        }
    }

    /** Scrolls just enough to show the row. */
    public void ensureVisible(int index)
    {
        if(index < 0 || index >= items.size())
        {
            return;
        }
        int rowTop = index * ROW;
        int view = getHeight() - 4;
        if(rowTop < scroll)
        {
            scroll = rowTop;
        }
        else if(rowTop + ROW > scroll + view)
        {
            scroll = rowTop + ROW - view;
        }
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    private int maxScroll()
    {
        return Math.max(0, items.size() * ROW - (getHeight() - 4));
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick)
    {
        Font font = Minecraft.getInstance().font;
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        Holo.inset(g, x, y, w, h);
        if(items.isEmpty())
        {
            g.drawCenteredString(font, empty, x + w / 2, y + h / 2 - 4, 0xFF3E6670);
            return;
        }
        boolean bar = maxScroll() > 0;
        int rowW = w - 4 - (bar ? 4 : 0);
        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        for(int i = 0; i < items.size(); i++)
        {
            int rowY = y + 2 + i * ROW - (int)scroll;
            if(rowY + ROW < y || rowY > y + h)
            {
                continue;
            }
            boolean hover = mouseX >= x + 2 && mouseX < x + 2 + rowW && mouseY >= rowY && mouseY < rowY + ROW && isMouseOver(mouseX, mouseY);
            if(i == selected)
            {
                g.fill(x + 2, rowY, x + 2 + rowW, rowY + ROW, 0x553CE6FF);
                g.fill(x + 2, rowY, x + 4, rowY + ROW, Holo.ACCENT);
            }
            else if(hover)
            {
                g.fill(x + 2, rowY, x + 2 + rowW, rowY + ROW, 0x223CE6FF);
            }
            T item = items.get(i);
            int textColour = i == selected ? 0xFFFFFFFF : Holo.TEXT;
            Component tagText = tag != null ? tag.apply(item) : null;
            int tagWidth = tagText == null ? 0 : font.width(tagText) + 6;
            String text = font.plainSubstrByWidth(label.apply(item).getString(), rowW - 10 - tagWidth);
            g.drawString(font, text, x + 7, rowY + 3, textColour, false);
            if(tagText != null)
            {
                g.drawString(font, tagText, x + 2 + rowW - tagWidth + 2, rowY + 3, Holo.MUTED, false);
            }
        }
        g.disableScissor();
        if(bar)
        {
            int barH = Math.max(10, (h - 4) * (h - 4) / (items.size() * ROW));
            int barY = y + 2 + (int)((h - 4 - barH) * (scroll / maxScroll()));
            g.fill(x + w - 5, y + 2, x + w - 2, y + h - 2, 0xFF07161E);
            g.fill(x + w - 5, barY, x + w - 2, barY + barH, Holo.ACCENT_DIM);
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY)
    {
        int index = (int)((mouseY - getY() - 2 + scroll) / ROW);
        if(index < 0 || index >= items.size())
        {
            return;
        }
        long now = Util.getMillis();
        boolean doubleClick = index == lastClicked && now - lastClickTime < 300L;
        lastClicked = index;
        lastClickTime = now;
        selected = index;
        T item = items.get(index);
        if(onSelect != null)
        {
            onSelect.accept(item);
        }
        if(doubleClick && onActivate != null)
        {
            onActivate.accept(item);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical)
    {
        if(!isMouseOver(mouseX, mouseY))
        {
            return false;
        }
        scroll = Mth.clamp(scroll - vertical * ROW * 2, 0, maxScroll());
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output)
    {
        T item = getSelected();
        if(item != null)
        {
            output.add(NarratedElementType.TITLE, Objects.requireNonNull(label.apply(item)));
        }
    }
}
