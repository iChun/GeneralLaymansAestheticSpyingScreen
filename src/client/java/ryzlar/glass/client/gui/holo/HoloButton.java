package ryzlar.glass.client.gui.holo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** A flat holographic button. "Danger" buttons are red (delete, leave, kick). */
public class HoloButton extends Button
{
    private final boolean danger;
    private boolean selected;

    public HoloButton(int x, int y, int width, int height, Component message, OnPress onPress)
    {
        this(x, y, width, height, message, onPress, false);
    }

    public HoloButton(int x, int y, int width, int height, Component message, OnPress onPress, boolean danger)
    {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.danger = danger;
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager sounds)
    {
        sounds.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(ryzlar.glass.GlassSounds.UI_CLICK, danger ? 0.8F : 1F));
    }

    /** Draws the button lit, for tabs and toggles. */
    public HoloButton selected(boolean selected)
    {
        this.selected = selected;
        return this;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick)
    {
        int accent = danger ? Holo.ERROR : Holo.ACCENT;
        boolean hot = active && (isHoveredOrFocused() || selected);
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        g.fill(x, y, x + w, y + h, !active ? 0x88050C10 : hot ? Holo.withAlpha(accent, 0.22F) : 0xCC07141C);
        Holo.frame(g, x, y, w, h, !active ? 0xFF1C2D33 : hot ? accent : Holo.withAlpha(accent, 0.55F));
        if(hot)
        {
            g.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, accent);
        }
        int textColour = !active ? 0xFF3E5A62 : hot ? 0xFFFFFFFF : (danger ? Holo.ERROR : Holo.TEXT);
        var font = Minecraft.getInstance().font;
        g.drawCenteredString(font, getMessage(), x + w / 2, y + (h - 8) / 2, textColour);
    }
}
