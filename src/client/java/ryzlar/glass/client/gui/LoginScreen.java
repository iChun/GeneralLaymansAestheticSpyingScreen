package ryzlar.glass.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ryzlar.glass.client.GlassNetClient;
import ryzlar.glass.client.gui.holo.Holo;
import ryzlar.glass.client.gui.holo.HoloButton;
import ryzlar.glass.client.gui.holo.HoloField;
import ryzlar.glass.client.gui.holo.HoloPasswordField;
import ryzlar.glass.client.gui.holo.HoloScreen;
import ryzlar.glass.network.GlassNetworkManager;
import ryzlar.glass.network.GlassNetworking;

import java.util.function.Supplier;

/**
 * Log in to (or create) a GLASS account. GLASS accounts are separate from Minecraft accounts: anyone who knows the
 * username and password can use one. Opens the screen it was guarding once logged in.
 */
public class LoginScreen extends HoloScreen
{
    private final Supplier<Screen> next;
    private boolean register;
    private boolean remember = true;

    private HoloField username;
    private HoloPasswordField password;
    private HoloPasswordField confirm;
    private String usernameValue = "";

    public LoginScreen(Supplier<Screen> next)
    {
        super(Component.translatable("glass.ui.access"), 280, 210);
        this.next = next;
    }

    /** The screen itself if already logged in, otherwise the login screen in front of it. */
    public static Screen gate(Supplier<Screen> next)
    {
        return GlassNetClient.loggedIn() ? next.get() : new LoginScreen(next);
    }

    @Override
    protected boolean requiresLogin()
    {
        return false;
    }

    @Override
    protected Component subtitle()
    {
        return Component.translatable(register ? "glass.ui.access.registerSub" : "glass.ui.access.loginSub");
    }

    @Override
    protected void build()
    {
        GlassNetClient.send(GlassNetworking.Action.REQUEST_STATE);
        int x = left + 12;
        int w = panelW - 24;

        addRenderableWidget(new HoloButton(x, top + 40, 80, 14, Component.translatable("glass.ui.tab.login"), b -> setMode(false)).selected(!register));
        addRenderableWidget(new HoloButton(x + 84, top + 40, 80, 14, Component.translatable("glass.ui.tab.register"), b -> setMode(true)).selected(register));

        username = new HoloField(font, x, top + 74, w, 16, Component.translatable("glass.ui.username"));
        username.setMaxLength(16);
        username.setValue(usernameValue);
        username.setResponder(v -> usernameValue = v);
        addRenderableWidget(username);

        password = new HoloPasswordField(font, x, top + 106, w, 16, Component.translatable("glass.ui.password"), GlassNetworkManager.MAX_PASSWORD);
        addRenderableWidget(password);

        int y = top + 130;
        if(register)
        {
            confirm = new HoloPasswordField(font, x, top + 138, w, 16, Component.translatable("glass.ui.confirmPassword"), GlassNetworkManager.MAX_PASSWORD);
            addRenderableWidget(confirm);
            y = top + 160;
        }
        addRenderableWidget(new HoloButton(x, y, 130, 14, rememberLabel(), b -> {
            remember = !remember;
            b.setMessage(rememberLabel());
            ((HoloButton)b).selected(remember);
        }).selected(remember));

        addRenderableWidget(new HoloButton(left + panelW - 132, top + panelH - 34, 120, 18,
                Component.translatable(register ? "glass.ui.createAccount" : "glass.ui.authenticate"), b -> submit()));

        setInitialFocus(usernameValue.isEmpty() ? username : password);
    }

    private Component rememberLabel()
    {
        return Component.literal(remember ? "■ " : "□ ").append(Component.translatable("glass.ui.remember"));
    }

    private void setMode(boolean register)
    {
        this.register = register;
        clearWarning();
        GlassNetClient.clearResult();
        rebuildWidgets();
    }

    private void submit()
    {
        String user = username.getValue().trim();
        if(user.isEmpty() || password.getValue().isEmpty())
        {
            warn(Component.translatable("glass.msg.fillIn"));
            return;
        }
        if(register && !password.getValue().equals(confirm.getValue()))
        {
            warn(Component.translatable("glass.msg.passwordsDiffer"));
            return;
        }
        clearWarning();
        GlassNetClient.send(register ? GlassNetworking.Action.REGISTER : GlassNetworking.Action.LOGIN, user, password.getValue(), remember ? "1" : "0");
    }

    @Override
    protected void onEnter()
    {
        submit();
    }

    @Override
    public void tick()
    {
        super.tick();
        if(GlassNetClient.loggedIn())
        {
            minecraft.setScreen(next.get());
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        if(age() < 0.18F)
        {
            return;
        }
        int x = left + 12;
        Holo.label(g, font, Component.translatable("glass.ui.username"), x, top + 63);
        Holo.label(g, font, Component.translatable("glass.ui.password"), x, top + 95);
        if(register)
        {
            Holo.label(g, font, Component.translatable("glass.ui.confirmPassword"), x, top + 127);
        }
        g.drawString(font, Component.translatable("glass.ui.passwordNote"), x, top + panelH - 29, 0xFF4F7C88, false);
    }
}
