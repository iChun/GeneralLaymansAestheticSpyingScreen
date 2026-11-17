package ryzlar.glass.client.gui.holo;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** A text field in the GLASS style: the vanilla edit box without its border, inside a holographic frame. */
public class HoloField extends EditBox
{
    private static final int PAD = 4;

    public HoloField(Font font, int x, int y, int width, int height, Component label)
    {
        super(font, x + PAD, y + (height - 8) / 2, width - PAD * 2, 9, label);
        setBordered(false);
        setTextColor(Holo.TEXT);
        setTextShadow(false);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick)
    {
        int x = getX() - PAD;
        int y = getY() - 4;
        int w = getWidth() + PAD * 2;
        int h = 16;
        g.fill(x, y, x + w, y + h, Holo.FIELD);
        Holo.frame(g, x, y, w, h, isFocused() ? Holo.ACCENT : Holo.ACCENT_DIM);
        super.renderWidget(g, mouseX, mouseY, partialTick);
    }

    /** Where the frame starts (the edit box itself is inset by the padding). */
    public int frameY()
    {
        return getY() - 4;
    }
}
