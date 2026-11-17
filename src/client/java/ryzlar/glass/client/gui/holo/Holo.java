package ryzlar.glass.client.gui.holo;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * The look of the GLASS screens: dark glass panels with cyan "holographic" lines, corner brackets and scanlines.
 * Everything is drawn in code, no textures.
 */
public final class Holo
{
    public static final int ACCENT = 0xFF3CE6FF;
    public static final int ACCENT_DIM = 0xFF1A7C8C;
    public static final int ACCENT_FAINT = 0x333CE6FF;
    public static final int PANEL = 0xEE06111A;
    public static final int PANEL_LIGHT = 0xF00B1E2A;
    public static final int FIELD = 0xFF030B11;
    public static final int TEXT = 0xFFD9FBFF;
    public static final int MUTED = 0xFF79A9B6;
    public static final int ERROR = 0xFFFF5C77;
    public static final int OK = 0xFF5CFFB0;
    public static final int WARN = 0xFFFFC857;

    private Holo() {}

    public static float seconds()
    {
        return (Util.getMillis() % 1_000_000L) / 1000F;
    }

    /** 0..1 pulse, for blinking indicators. */
    public static float pulse(float speed)
    {
        return 0.5F + 0.5F * Mth.sin(seconds() * speed);
    }

    public static int withAlpha(int colour, float alpha)
    {
        return ARGB.color(Mth.clamp((int)(ARGB.alpha(colour) * alpha), 0, 255), colour);
    }

    public static void frame(GuiGraphics g, int x, int y, int w, int h, int colour)
    {
        g.fill(x, y, x + w, y + 1, colour);
        g.fill(x, y + h - 1, x + w, y + h, colour);
        g.fill(x, y + 1, x + 1, y + h - 1, colour);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
    }

    public static void brackets(GuiGraphics g, int x, int y, int w, int h, int len, int colour)
    {
        g.fill(x - 1, y - 1, x + len, y + 1, colour);
        g.fill(x - 1, y - 1, x + 1, y + len, colour);
        g.fill(x + w - len, y - 1, x + w + 1, y + 1, colour);
        g.fill(x + w - 1, y - 1, x + w + 1, y + len, colour);
        g.fill(x - 1, y + h - 1, x + len, y + h + 1, colour);
        g.fill(x - 1, y + h - len, x + 1, y + h + 1, colour);
        g.fill(x + w - len, y + h - 1, x + w + 1, y + h + 1, colour);
        g.fill(x + w - 1, y + h - len, x + w + 1, y + h + 1, colour);
    }

    /** Faint horizontal lines and a slow sweep, like an old CRT. */
    public static void scanlines(GuiGraphics g, int x, int y, int w, int h)
    {
        for(int ly = y + 1; ly < y + h - 1; ly += 3)
        {
            g.fill(x + 1, ly, x + w - 1, ly + 1, 0x0C3CE6FF);
        }
        float t = (seconds() * 0.35F) % 1F;
        int sweepY = y + (int)(t * h);
        g.fillGradient(x + 1, Math.max(y + 1, sweepY - 12), x + w - 1, sweepY, 0x003CE6FF, 0x183CE6FF);
    }

    /** A main panel: dark glass, thin frame, bright corner brackets, scanlines. {@code reveal} 0..1 animates it opening. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, float reveal)
    {
        int shownH = Math.max(2, (int)(h * Mth.clamp(reveal, 0F, 1F)));
        int top = y + (h - shownH) / 2;
        g.fill(x, top, x + w, top + shownH, PANEL);
        frame(g, x, top, w, shownH, ACCENT_DIM);
        brackets(g, x, top, w, shownH, 8, ACCENT);
        if(reveal >= 1F)
        {
            scanlines(g, x, y, w, h);
        }
    }

    /** A smaller inset box (lists, preview areas). */
    public static void inset(GuiGraphics g, int x, int y, int w, int h)
    {
        g.fill(x, y, x + w, y + h, 0xCC02080D);
        frame(g, x, y, w, h, 0xFF123F48);
    }

    /** Title bar: "GLASS//NET :: TITLE", a subtitle and a blinking status light. */
    public static void header(GuiGraphics g, Font font, int x, int y, int w, Component title, Component subtitle)
    {
        g.drawString(font, Component.literal("GLASS//NET ").withColor(MUTED).append(Component.literal(":: ").withColor(ACCENT_DIM)).append(title.copy().withColor(TEXT)), x + 10, y + 8, TEXT, false);
        g.drawString(font, subtitle, x + 10, y + 19, MUTED, false);
        g.fill(x + 8, y + 31, x + w - 8, y + 32, ACCENT_DIM);
        g.fill(x + 8, y + 31, x + 8 + Math.min(w - 16, 40), y + 32, ACCENT);
        int light = withAlpha(OK, 0.4F + 0.6F * pulse(4F));
        g.fill(x + w - 14, y + 9, x + w - 9, y + 14, light);
    }

    public static void label(GuiGraphics g, Font font, Component text, int x, int y)
    {
        g.drawString(font, text.copy().withColor(ACCENT), x, y, ACCENT, false);
    }

    /** Text that types itself out over {@code duration} seconds since {@code start}. */
    public static String typed(String text, float start, float duration)
    {
        float t = Mth.clamp((seconds() - start) / duration, 0F, 1F);
        return text.substring(0, Math.round(text.length() * t));
    }
}
