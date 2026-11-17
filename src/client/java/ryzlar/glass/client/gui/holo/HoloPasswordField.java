package ryzlar.glass.client.gui.holo;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import org.lwjgl.glfw.GLFW;

/**
 * A password field: shows a dot per character (with a cursor that lines up, which the vanilla edit box cannot do with
 * masked text). Supports typing, backspace/delete, arrows, home/end and paste. Never narrated aloud.
 */
public class HoloPasswordField extends AbstractWidget
{
    private static final String DOT = "•";

    private final Font font;
    private String value = "";
    private int cursor;
    private final int maxLength;
    private final Component hint;

    public HoloPasswordField(Font font, int x, int y, int width, int height, Component label, int maxLength)
    {
        super(x, y, width, height, label);
        this.font = font;
        this.maxLength = maxLength;
        this.hint = label;
    }

    public String getValue()
    {
        return value;
    }

    public void setValue(String value)
    {
        this.value = value.length() > maxLength ? value.substring(0, maxLength) : value;
        this.cursor = this.value.length();
    }

    private void insert(String text)
    {
        String clean = StringUtil.filterText(text);
        int room = maxLength - value.length();
        if(room <= 0 || clean.isEmpty())
        {
            return;
        }
        if(clean.length() > room)
        {
            clean = clean.substring(0, room);
        }
        value = value.substring(0, cursor) + clean + value.substring(cursor);
        cursor += clean.length();
    }

    @Override
    public boolean charTyped(char c, int modifiers)
    {
        if(!isFocused() || !StringUtil.isAllowedChatCharacter(c))
        {
            return false;
        }
        insert(Character.toString(c));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        if(!isFocused())
        {
            return false;
        }
        if(Screen.isPaste(keyCode))
        {
            insert(Minecraft.getInstance().keyboardHandler.getClipboard());
            return true;
        }
        switch(keyCode)
        {
            case GLFW.GLFW_KEY_BACKSPACE ->
            {
                if(cursor > 0)
                {
                    value = value.substring(0, cursor - 1) + value.substring(cursor);
                    cursor--;
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE ->
            {
                if(cursor < value.length())
                {
                    value = value.substring(0, cursor) + value.substring(cursor + 1);
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT ->
            {
                cursor = Math.max(0, cursor - 1);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT ->
            {
                cursor = Math.min(value.length(), cursor + 1);
                return true;
            }
            case GLFW.GLFW_KEY_HOME ->
            {
                cursor = 0;
                return true;
            }
            case GLFW.GLFW_KEY_END ->
            {
                cursor = value.length();
                return true;
            }
            default ->
            {
                return false;
            }
        }
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick)
    {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        g.fill(x, y, x + w, y + h, Holo.FIELD);
        Holo.frame(g, x, y, w, h, isFocused() ? Holo.ACCENT : Holo.ACCENT_DIM);

        int textY = y + (h - 8) / 2;
        int dotWidth = font.width(DOT);
        int visible = Math.max(1, (w - 10) / dotWidth);
        int start = Math.max(0, cursor - visible);
        String shown = DOT.repeat(Math.min(visible, value.length() - start));
        if(value.isEmpty() && !isFocused())
        {
            g.drawString(font, hint, x + 4, textY, 0xFF3E6670, false);
        }
        else
        {
            g.drawString(font, shown, x + 4, textY, Holo.TEXT, false);
        }
        if(isFocused() && (Util.getMillis() / 500L) % 2L == 0L)
        {
            int cx = x + 4 + (cursor - start) * dotWidth;
            g.fill(cx, textY - 1, cx + 1, textY + 9, Holo.ACCENT);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output)
    {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
