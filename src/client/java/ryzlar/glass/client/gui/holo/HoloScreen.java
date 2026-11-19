package ryzlar.glass.client.gui.holo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import ryzlar.glass.client.GlassNetClient;
import ryzlar.glass.network.GlassNetworking;

/**
 * Base for the GLASS screens: a centred holographic panel that "opens" when shown, a header, the server's last reply
 * at the bottom, and a refresh whenever the channel list changes.
 */
public abstract class HoloScreen extends Screen
{
    private static final long RESULT_SHOW_MILLIS = 6000L;

    protected final int panelW;
    protected final int panelH;
    protected int left;
    protected int top;
    private float openedAt = -1F;
    private int seenVersion;
    @org.jetbrains.annotations.Nullable
    private Component localMessage;

    protected HoloScreen(Component title, int panelW, int panelH)
    {
        super(title);
        this.panelW = panelW;
        this.panelH = panelH;
    }

    @Override
    protected final void init()
    {
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        if(openedAt < 0F)
        {
            openedAt = Holo.seconds();
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(ryzlar.glass.GlassSounds.UI_OPEN, 1F, 0.8F));
            GlassNetClient.clearResult();
        }
        seenVersion = GlassNetClient.version();
        GlassNetClient.send(GlassNetworking.Action.REQUEST_STATE);
        build();
        refresh();
    }

    /** Adds the widgets. Called on open and whenever the window is resized. */
    protected abstract void build();

    /** Updates lists and labels from the network state; called after {@link #build()} and when the state changes. */
    protected void refresh() {}

    protected abstract Component subtitle();

    /** Enter key. */
    protected void onEnter() {}

    /** Seconds since the screen opened, for the opening animation. */
    protected float age()
    {
        return Holo.seconds() - openedAt;
    }

    @Override
    public void tick()
    {
        super.tick();
        if(seenVersion != GlassNetClient.version())
        {
            seenVersion = GlassNetClient.version();
            localMessage = null;
            refresh();
        }
    }

    /** A problem found before asking the server (empty field, passwords that do not match). */
    protected void warn(Component message)
    {
        localMessage = message;
    }

    protected void clearWarning()
    {
        localMessage = null;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        if(keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
        {
            onEnter();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick)
    {
        g.fill(0, 0, width, height, 0xB0010509);
        for(int gx = (width / 2) % 24; gx < width; gx += 24)
        {
            g.fill(gx, 0, gx + 1, height, 0x0A3CE6FF);
        }
        for(int gy = (height / 2) % 24; gy < height; gy += 24)
        {
            g.fill(0, gy, width, gy + 1, 0x0A3CE6FF);
        }
        Holo.panel(g, left, top, panelW, panelH, age() / 0.18F);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick)
    {
        if(age() < 0.18F)
        {
            return; //panel still opening
        }
        super.render(g, mouseX, mouseY, partialTick);
        Holo.header(g, font, left, top, panelW, title, subtitle());
        GlassNetworking.ActionResult result = GlassNetClient.recentResult(RESULT_SHOW_MILLIS);
        if(localMessage != null)
        {
            g.drawString(font, Component.literal("⚠ ").append(localMessage), left + 10, top + panelH - 13, Holo.WARN, false);
        }
        else if(result != null)
        {
            Component text = Component.literal(result.ok() ? "▶ " : "⚠ ").append(GlassNetClient.describe(result));
            g.drawString(font, text, left + 10, top + panelH - 13, result.ok() ? Holo.OK : Holo.ERROR, false);
        }
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
