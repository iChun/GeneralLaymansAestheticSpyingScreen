package ryzlar.glass.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.blockentity.GlassTerminalBlockEntity;
import ryzlar.glass.client.GlassNetClient;
import ryzlar.glass.client.gui.holo.Holo;
import ryzlar.glass.client.gui.holo.HoloButton;
import ryzlar.glass.client.gui.holo.HoloField;
import ryzlar.glass.client.gui.holo.HoloList;
import ryzlar.glass.client.gui.holo.HoloPasswordField;
import ryzlar.glass.client.gui.holo.HoloScreen;
import ryzlar.glass.network.GlassNetworkManager;
import ryzlar.glass.network.GlassNetworking;

import java.util.List;

/**
 * The terminal is a camera. Here you make a channel for it (a name nobody has used, and a password), or, as its owner,
 * change the channel's password, stop using this terminal, or delete the channel.
 */
public class CameraScreen extends HoloScreen
{
    private final GlassTerminalBlockEntity terminal;
    private boolean linkedView;
    private boolean confirmDelete;

    @Nullable
    private HoloField nameField;
    @Nullable
    private HoloPasswordField passwordField;
    @Nullable
    private HoloPasswordField confirmField;
    @Nullable
    private HoloList<GlassNetworking.ChannelInfo> ownChannels;
    @Nullable
    private HoloButton useHere;

    public CameraScreen(GlassTerminalBlockEntity terminal)
    {
        super(Component.translatable("glass.ui.camera"), 330, 214);
        this.terminal = terminal;
    }

    @Override
    protected Component subtitle()
    {
        BlockPos pos = terminal.getBlockPos();
        return Component.translatable("glass.ui.camera.sub", pos.getX(), pos.getY(), pos.getZ(), terminal.facing.getSerializedName().toUpperCase());
    }

    @Nullable
    private GlassNetworking.ChannelInfo channel()
    {
        return GlassNetClient.channel(terminal.channelId);
    }

    @Override
    protected void build()
    {
        linkedView = terminal.isLinked();
        nameField = null;
        passwordField = null;
        confirmField = null;
        ownChannels = null;
        useHere = null;
        int lx = left + 12;
        int lw = 150;
        int rx = left + 174;
        int rw = panelW - 186;

        if(!linkedView)
        {
            //Left: make a new channel here.
            nameField = addRenderableWidget(new HoloField(font, lx, top + 62, lw, 16, Component.translatable("glass.ui.channelName")));
            nameField.setMaxLength(24);
            passwordField = addRenderableWidget(new HoloPasswordField(font, lx, top + 94, lw, 16, Component.translatable("glass.ui.password"), GlassNetworkManager.MAX_PASSWORD));
            confirmField = addRenderableWidget(new HoloPasswordField(font, lx, top + 126, lw, 16, Component.translatable("glass.ui.confirmPassword"), GlassNetworkManager.MAX_PASSWORD));
            addRenderableWidget(new HoloButton(lx, top + 150, lw, 18, Component.translatable("glass.ui.createChannel"), b -> create()));
            setInitialFocus(nameField);

            //Right: or move one of your own channels to this terminal.
            ownChannels = addRenderableWidget(new HoloList<GlassNetworking.ChannelInfo>(rx, top + 52, rw, 96,
                    c -> Component.literal(c.name()),
                    c -> c.online() ? Component.translatable("glass.ui.online") : Component.translatable("glass.ui.offline"),
                    Component.translatable("glass.ui.noOwnChannels"))
                    .keyedBy(GlassNetworking.ChannelInfo::id));
            useHere = addRenderableWidget(new HoloButton(rx, top + 152, rw, 16, Component.translatable("glass.ui.useHere"), b -> useHere()));
            return;
        }

        GlassNetworking.ChannelInfo channel = channel();
        if(channel != null && channel.owner())
        {
            passwordField = addRenderableWidget(new HoloPasswordField(font, rx, top + 62, rw, 16, Component.translatable("glass.ui.newPassword"), GlassNetworkManager.MAX_PASSWORD));
            confirmField = addRenderableWidget(new HoloPasswordField(font, rx, top + 94, rw, 16, Component.translatable("glass.ui.confirmPassword"), GlassNetworkManager.MAX_PASSWORD));
            addRenderableWidget(new HoloButton(rx, top + 116, rw, 14, Component.translatable("glass.ui.setPassword"), b -> changePassword()));
            addRenderableWidget(new HoloButton(lx, top + 134, lw, 14, Component.translatable("glass.ui.detach"), b ->
                    ClientPlayNetworking.send(GlassNetworking.TerminalAction.of(terminal.getBlockPos(), GlassNetworking.TerminalCommand.DETACH, "", "")), true));
            addRenderableWidget(new HoloButton(lx, top + 152, lw, 14,
                    Component.translatable(confirmDelete ? "glass.ui.confirmDelete" : "glass.ui.deleteChannel"), b -> delete(), true));
        }
    }

    private void create()
    {
        if(nameField == null || passwordField == null || confirmField == null)
        {
            return;
        }
        if(nameField.getValue().isBlank() || passwordField.getValue().isEmpty())
        {
            warn(Component.translatable("glass.msg.fillIn"));
            return;
        }
        if(!passwordField.getValue().equals(confirmField.getValue()))
        {
            warn(Component.translatable("glass.msg.passwordsDiffer"));
            return;
        }
        String name = nameField.getValue().trim();
        for(GlassNetworking.ChannelInfo existing : GlassNetClient.channels())
        {
            if(existing.name().equalsIgnoreCase(name))
            {
                warn(Component.translatable("glass.msg.channelTaken"));
                return;
            }
        }
        clearWarning();
        ClientPlayNetworking.send(GlassNetworking.TerminalAction.of(terminal.getBlockPos(), GlassNetworking.TerminalCommand.CREATE, name, passwordField.getValue()));
    }

    private void useHere()
    {
        GlassNetworking.ChannelInfo channel = ownChannels == null ? null : ownChannels.getSelected();
        if(channel == null)
        {
            warn(Component.translatable("glass.msg.pickChannel"));
            return;
        }
        ClientPlayNetworking.send(GlassNetworking.TerminalAction.of(terminal.getBlockPos(), GlassNetworking.TerminalCommand.MOVE_HERE, channel.id(), ""));
    }

    private void changePassword()
    {
        GlassNetworking.ChannelInfo channel = channel();
        if(channel == null || passwordField == null || confirmField == null)
        {
            return;
        }
        if(passwordField.getValue().isEmpty() || !passwordField.getValue().equals(confirmField.getValue()))
        {
            warn(Component.translatable("glass.msg.passwordsDiffer"));
            return;
        }
        GlassNetClient.send(GlassNetworking.Action.CHANGE_PASSWORD, channel.id(), passwordField.getValue());
        passwordField.setValue("");
        confirmField.setValue("");
    }

    private void delete()
    {
        GlassNetworking.ChannelInfo channel = channel();
        if(channel == null)
        {
            return;
        }
        if(!confirmDelete)
        {
            confirmDelete = true;
            rebuildWidgets();
            return;
        }
        confirmDelete = false;
        GlassNetClient.send(GlassNetworking.Action.DELETE_CHANNEL, channel.id());
    }

    @Override
    protected void onEnter()
    {
        if(!linkedView)
        {
            create();
        }
        else if(passwordField != null && !passwordField.getValue().isEmpty())
        {
            changePassword();
        }
    }

    @Override
    protected void refresh()
    {
        if(ownChannels != null)
        {
            List<GlassNetworking.ChannelInfo> own = GlassNetClient.channels().stream().filter(GlassNetworking.ChannelInfo::owner).toList();
            ownChannels.setItems(own);
        }
        if(useHere != null)
        {
            useHere.active = ownChannels != null && !ownChannels.items().isEmpty();
        }
    }

    @Override
    public void tick()
    {
        super.tick();
        //The terminal got linked or unlinked (by us or someone else): switch layouts.
        if(linkedView != terminal.isLinked())
        {
            confirmDelete = false;
            rebuildWidgets();
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
        int lx = left + 12;
        int rx = left + 174;
        int rw = panelW - 186;
        if(!linkedView)
        {
            Holo.label(g, font, Component.translatable("glass.ui.newChannel"), lx, top + 41);
            g.drawString(font, Component.translatable("glass.ui.channelName"), lx, top + 52, Holo.MUTED, false);
            g.drawString(font, Component.translatable("glass.ui.password"), lx, top + 84, Holo.MUTED, false);
            g.drawString(font, Component.translatable("glass.ui.confirmPassword"), lx, top + 116, Holo.MUTED, false);
            Holo.label(g, font, Component.translatable("glass.ui.orYourChannels"), rx, top + 41);
            return;
        }

        GlassNetworking.ChannelInfo channel = channel();
        Holo.label(g, font, Component.translatable("glass.ui.channel"), lx, top + 41);
        if(channel == null)
        {
            g.drawString(font, Component.translatable("glass.ui.restricted"), lx, top + 56, Holo.MUTED, false);
            return;
        }
        g.drawString(font, font.plainSubstrByWidth(channel.name(), 150), lx, top + 56, Holo.TEXT, false);
        g.drawString(font, Component.translatable("glass.ui.ownedBy", channel.ownerName()), lx, top + 70, Holo.MUTED, false);
        g.drawString(font, Component.translatable("glass.ui.broadcasting"), lx, top + 88, Holo.withAlpha(Holo.OK, 0.6F + 0.4F * Holo.pulse(4F)), false);
        if(channel.owner())
        {
            Holo.label(g, font, Component.translatable("glass.ui.changePassword"), rx, top + 41);
            g.drawString(font, Component.translatable("glass.ui.newPassword"), rx, top + 52, Holo.MUTED, false);
            g.drawString(font, Component.translatable("glass.ui.confirmPassword"), rx, top + 84, Holo.MUTED, false);
            g.drawString(font, font.plainSubstrByWidth(Component.translatable("glass.ui.passwordResetNote").getString(), rw), rx, top + 136, Holo.WARN, false);
            Holo.label(g, font, Component.translatable("glass.ui.manage"), lx, top + 122);
        }
        else
        {
            g.drawString(font, Component.translatable("glass.ui.notOwner"), lx, top + 106, Holo.MUTED, false);
        }
    }
}
