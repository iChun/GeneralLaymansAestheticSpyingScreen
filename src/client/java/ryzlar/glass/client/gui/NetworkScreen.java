package ryzlar.glass.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.client.GlassNetClient;
import ryzlar.glass.client.gui.holo.Holo;
import ryzlar.glass.client.gui.holo.HoloButton;
import ryzlar.glass.client.gui.holo.HoloField;
import ryzlar.glass.client.gui.holo.HoloList;
import ryzlar.glass.client.gui.holo.HoloPasswordField;
import ryzlar.glass.client.gui.holo.HoloScreen;
import ryzlar.glass.network.GlassNetworkManager;
import ryzlar.glass.network.GlassNetworking;

import java.util.ArrayList;
import java.util.List;

/**
 * Your GLASS channels: create one (name + password), join one by its password, and for channels you own change the
 * password, remove members or cameras, or delete it.
 */
public class NetworkScreen extends HoloScreen
{
    private enum Mode
    {
        DETAILS,
        CREATE,
        JOIN,
        PASSWORD,
        MEMBERS,
        CAMERAS
    }

    @Nullable
    private final Screen parent;
    private Mode mode = Mode.DETAILS;
    @Nullable
    private String selectedId;
    private boolean confirmDelete;

    private HoloList<GlassNetworking.ChannelInfo> channels;
    @Nullable
    private HoloList<String> members;
    @Nullable
    private HoloList<GlassNetworking.CameraInfo> cameraList;
    private HoloField nameField;
    private HoloPasswordField passwordField;
    private HoloPasswordField confirmField;
    private final List<HoloButton> ownerButtons = new ArrayList<>();
    private HoloButton leave;

    public NetworkScreen(@Nullable Screen parent)
    {
        super(Component.translatable("glass.ui.network"), 370, 232);
        this.parent = parent;
    }

    @Override
    protected Component subtitle()
    {
        return Component.translatable("glass.ui.network.sub", GlassNetClient.channels().size());
    }

    @Nullable
    private GlassNetworking.ChannelInfo selected()
    {
        return selectedId == null ? null : GlassNetClient.channel(selectedId);
    }

    @Override
    protected void build()
    {
        ownerButtons.clear();
        members = null;
        cameraList = null;
        channels = new HoloList<GlassNetworking.ChannelInfo>(left + 10, top + 52, 150, 140,
                c -> Component.literal(c.name()),
                c -> c.owner() ? Component.translatable("glass.ui.owner") : Component.translatable("glass.ui.member"),
                Component.translatable("glass.ui.noChannels"))
                .keyedBy(GlassNetworking.ChannelInfo::id)
                .onSelect(c -> {
                    selectedId = c.id();
                    confirmDelete = false;
                    if(mode != Mode.CREATE && mode != Mode.JOIN)
                    {
                        setMode(Mode.DETAILS);
                    }
                });
        addRenderableWidget(channels);
        addRenderableWidget(new HoloButton(left + 10, top + panelH - 32, 72, 14, Component.translatable("glass.ui.back"), b -> onClose()));

        int rx = left + 172;
        int rw = panelW - 182;
        switch(mode)
        {
            case DETAILS ->
            {
                int half = (rw - 4) / 2;
                addRenderableWidget(new HoloButton(rx, top + 52, half, 16, Component.translatable("glass.ui.create"), b -> setMode(Mode.CREATE)));
                addRenderableWidget(new HoloButton(rx + half + 4, top + 52, half, 16, Component.translatable("glass.ui.join"), b -> setMode(Mode.JOIN)));
                int y = top + 132;
                ownerButtons.add(addRenderableWidget(new HoloButton(rx, y, half, 14, Component.translatable("glass.ui.changePassword"), b -> setMode(Mode.PASSWORD))));
                ownerButtons.add(addRenderableWidget(new HoloButton(rx + half + 4, y, half, 14, Component.translatable("glass.ui.members"), b -> setMode(Mode.MEMBERS))));
                ownerButtons.add(addRenderableWidget(new HoloButton(rx, y + 18, half, 14, Component.translatable("glass.ui.cameras"), b -> setMode(Mode.CAMERAS))));
                ownerButtons.add(addRenderableWidget(new HoloButton(rx + half + 4, y + 18, half, 14,
                        Component.translatable(confirmDelete ? "glass.ui.confirmDelete" : "glass.ui.delete"), b -> delete(), true)));
                leave = addRenderableWidget(new HoloButton(rx, y + 36, rw, 14, Component.translatable("glass.ui.leave"), b -> {
                    GlassNetworking.ChannelInfo c = selected();
                    if(c != null)
                    {
                        GlassNetClient.send(GlassNetworking.Action.LEAVE_CHANNEL, c.id());
                    }
                }, true));
            }
            case CREATE, JOIN ->
            {
                nameField = new HoloField(font, rx, top + 62, rw, 16, Component.translatable("glass.ui.channelName"));
                nameField.setMaxLength(24);
                addRenderableWidget(nameField);
                passwordField = addRenderableWidget(new HoloPasswordField(font, rx, top + 94, rw, 16, Component.translatable("glass.ui.password"), GlassNetworkManager.MAX_PASSWORD));
                if(mode == Mode.CREATE)
                {
                    confirmField = addRenderableWidget(new HoloPasswordField(font, rx, top + 126, rw, 16, Component.translatable("glass.ui.confirmPassword"), GlassNetworkManager.MAX_PASSWORD));
                }
                addConfirmCancel(rx, rw, Component.translatable(mode == Mode.CREATE ? "glass.ui.create" : "glass.ui.join"));
                setInitialFocus(nameField);
            }
            case PASSWORD ->
            {
                passwordField = addRenderableWidget(new HoloPasswordField(font, rx, top + 62, rw, 16, Component.translatable("glass.ui.newPassword"), GlassNetworkManager.MAX_PASSWORD));
                confirmField = addRenderableWidget(new HoloPasswordField(font, rx, top + 94, rw, 16, Component.translatable("glass.ui.confirmPassword"), GlassNetworkManager.MAX_PASSWORD));
                addConfirmCancel(rx, rw, Component.translatable("glass.ui.set"));
                setInitialFocus(passwordField);
            }
            case MEMBERS ->
            {
                members = addRenderableWidget(new HoloList<String>(rx, top + 52, rw, 108, Component::literal, null, Component.translatable("glass.ui.noMembers")));
                addConfirmCancel(rx, rw, Component.translatable("glass.ui.kick"));
            }
            case CAMERAS ->
            {
                cameraList = addRenderableWidget(new HoloList<GlassNetworking.CameraInfo>(rx, top + 52, rw, 108,
                        c -> Component.literal(c.name()),
                        c -> Component.literal(ProjectorScreen.shortDimension(c.dimension()) + " " + c.pos().getX() + "," + c.pos().getY() + "," + c.pos().getZ()),
                        Component.translatable("glass.ui.noCameras")).keyedBy(GlassNetworking.CameraInfo::id));
                addConfirmCancel(rx, rw, Component.translatable("glass.ui.remove"));
            }
        }
    }

    private void addConfirmCancel(int rx, int rw, Component confirm)
    {
        int half = (rw - 4) / 2;
        boolean danger = mode == Mode.MEMBERS || mode == Mode.CAMERAS;
        addRenderableWidget(new HoloButton(rx, top + 168, half, 16, confirm, b -> onEnter(), danger));
        addRenderableWidget(new HoloButton(rx + half + 4, top + 168, half, 16, Component.translatable("glass.ui.cancel"), b -> setMode(Mode.DETAILS)));
    }

    private void setMode(Mode mode)
    {
        this.mode = mode;
        clearWarning();
        rebuildWidgets();
    }

    @Override
    protected void refresh()
    {
        channels.setItems(GlassNetClient.channels());
        if(selectedId != null)
        {
            channels.selectWhere(c -> c.id().equals(selectedId));
        }
        if(selectedId == null && !channels.items().isEmpty())
        {
            selectedId = channels.items().getFirst().id();
            channels.selectIndex(0);
        }
        GlassNetworking.ChannelInfo channel = selected();
        if(channel == null)
        {
            selectedId = null;
            if(mode != Mode.CREATE && mode != Mode.JOIN && mode != Mode.DETAILS)
            {
                mode = Mode.DETAILS;
                rebuildWidgets();
                return;
            }
        }
        boolean owner = channel != null && channel.owner();
        for(HoloButton button : ownerButtons)
        {
            button.active = owner;
        }
        if(leave != null)
        {
            leave.active = channel != null && !owner;
        }
        if(members != null && channel != null)
        {
            List<String> names = new ArrayList<>(channel.members());
            names.removeIf(name -> name.equalsIgnoreCase(channel.ownerName()));
            members.setItems(names);
        }
        if(cameraList != null && channel != null)
        {
            cameraList.setItems(channel.cameras());
        }
    }

    private void delete()
    {
        GlassNetworking.ChannelInfo channel = selected();
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
        rebuildWidgets();
    }

    @Override
    protected void onEnter()
    {
        GlassNetworking.ChannelInfo channel = selected();
        switch(mode)
        {
            case CREATE, JOIN ->
            {
                if(nameField.getValue().isBlank() || passwordField.getValue().isEmpty())
                {
                    warn(Component.translatable("glass.msg.fillIn"));
                    return;
                }
                if(mode == Mode.CREATE && !passwordField.getValue().equals(confirmField.getValue()))
                {
                    warn(Component.translatable("glass.msg.passwordsDiffer"));
                    return;
                }
                GlassNetClient.send(mode == Mode.CREATE ? GlassNetworking.Action.CREATE_CHANNEL : GlassNetworking.Action.JOIN_CHANNEL,
                        nameField.getValue().trim(), passwordField.getValue());
                setMode(Mode.DETAILS);
            }
            case PASSWORD ->
            {
                if(channel == null)
                {
                    return;
                }
                if(!passwordField.getValue().equals(confirmField.getValue()) || passwordField.getValue().isEmpty())
                {
                    warn(Component.translatable("glass.msg.passwordsDiffer"));
                    return;
                }
                GlassNetClient.send(GlassNetworking.Action.CHANGE_PASSWORD, channel.id(), passwordField.getValue());
                setMode(Mode.DETAILS);
            }
            case MEMBERS ->
            {
                String member = members == null ? null : members.getSelected();
                if(channel != null && member != null)
                {
                    GlassNetClient.send(GlassNetworking.Action.KICK_MEMBER, channel.id(), member);
                }
            }
            case CAMERAS ->
            {
                GlassNetworking.CameraInfo camera = cameraList == null ? null : cameraList.getSelected();
                if(channel != null && camera != null)
                {
                    GlassNetClient.send(GlassNetworking.Action.REMOVE_CAMERA, channel.id(), camera.id());
                }
            }
            default ->
            {
            }
        }
    }

    @Override
    public void onClose()
    {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        if(age() < 0.18F)
        {
            return;
        }
        Holo.label(g, font, Component.translatable("glass.ui.channels"), left + 10, top + 41);
        int rx = left + 172;
        int rw = panelW - 182;
        GlassNetworking.ChannelInfo channel = selected();
        switch(mode)
        {
            case DETAILS ->
            {
                Holo.label(g, font, Component.translatable("glass.ui.details"), rx, top + 76);
                if(channel == null)
                {
                    g.drawString(font, Component.translatable("glass.ui.selectChannel"), rx, top + 89, Holo.MUTED, false);
                }
                else
                {
                    g.drawString(font, font.plainSubstrByWidth(channel.name(), rw), rx, top + 89, Holo.TEXT, false);
                    g.drawString(font, Component.translatable("glass.ui.ownedBy", channel.ownerName()), rx, top + 100, Holo.MUTED, false);
                    Component counts = channel.owner()
                            ? Component.translatable("glass.ui.counts", channel.cameras().size(), channel.members().size())
                            : Component.translatable("glass.ui.cameraCount", channel.cameras().size());
                    g.drawString(font, counts, rx, top + 111, Holo.MUTED, false);
                }
            }
            case CREATE, JOIN ->
            {
                Holo.label(g, font, Component.translatable(mode == Mode.CREATE ? "glass.ui.newChannel" : "glass.ui.joinChannel"), rx, top + 41);
                g.drawString(font, Component.translatable("glass.ui.channelName"), rx, top + 52, Holo.MUTED, false);
                g.drawString(font, Component.translatable("glass.ui.password"), rx, top + 84, Holo.MUTED, false);
                if(mode == Mode.CREATE)
                {
                    g.drawString(font, Component.translatable("glass.ui.confirmPassword"), rx, top + 116, Holo.MUTED, false);
                }
            }
            case PASSWORD ->
            {
                Holo.label(g, font, Component.translatable("glass.ui.changePassword"), rx, top + 41);
                g.drawString(font, Component.translatable("glass.ui.passwordResetNote"), rx, top + 120, Holo.WARN, false);
                g.drawString(font, Component.translatable("glass.ui.passwordResetNote2"), rx, top + 131, Holo.WARN, false);
            }
            case MEMBERS -> Holo.label(g, font, Component.translatable("glass.ui.members"), rx, top + 41);
            case CAMERAS -> Holo.label(g, font, Component.translatable("glass.ui.cameras"), rx, top + 41);
        }
    }
}
