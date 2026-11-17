package ryzlar.glass.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import ryzlar.glass.blockentity.GlassTerminalBlockEntity;
import ryzlar.glass.client.GlassNetClient;
import ryzlar.glass.client.gui.holo.Holo;
import ryzlar.glass.client.gui.holo.HoloButton;
import ryzlar.glass.client.gui.holo.HoloField;
import ryzlar.glass.client.gui.holo.HoloList;
import ryzlar.glass.client.gui.holo.HoloScreen;
import ryzlar.glass.network.GlassNetworkManager;
import ryzlar.glass.network.GlassNetworking;

/**
 * A terminal is a camera: give it a name and put it on one of your channels.
 */
public class CameraScreen extends HoloScreen
{
    private final GlassTerminalBlockEntity terminal;
    private HoloList<GlassNetworking.ChannelInfo> channels;
    private HoloField name;
    private HoloButton link;
    private HoloButton unlink;
    private String nameValue;

    public CameraScreen(GlassTerminalBlockEntity terminal)
    {
        super(Component.translatable("glass.ui.camera"), 330, 210);
        this.terminal = terminal;
        BlockPos pos = terminal.getBlockPos();
        this.nameValue = terminal.cameraName.isEmpty() ? "CAM-" + Math.floorMod(pos.getX() * 31 + pos.getZ() * 17 + pos.getY(), 1000) : terminal.cameraName;
    }

    @Override
    protected Component subtitle()
    {
        BlockPos pos = terminal.getBlockPos();
        return Component.translatable("glass.ui.camera.sub", pos.getX(), pos.getY(), pos.getZ(), terminal.facing.getSerializedName().toUpperCase());
    }

    @Override
    protected void build()
    {
        channels = new HoloList<GlassNetworking.ChannelInfo>(left + 10, top + 52, 150, 118,
                c -> Component.literal(c.name()),
                c -> Component.literal(c.owner() ? "★ " + c.cameras().size() : Integer.toString(c.cameras().size())),
                Component.translatable("glass.ui.noChannels"))
                .keyedBy(GlassNetworking.ChannelInfo::id)
                .onActivate(c -> doLink());
        addRenderableWidget(channels);

        int rx = left + 172;
        int rw = panelW - 182;
        name = new HoloField(font, rx, top + 52, rw, 16, Component.translatable("glass.ui.cameraName"));
        name.setMaxLength(GlassNetworkManager.MAX_CAMERA_NAME);
        name.setValue(nameValue);
        name.setResponder(v -> nameValue = v);
        addRenderableWidget(name);

        link = addRenderableWidget(new HoloButton(rx, top + 124, rw, 16, Component.translatable("glass.ui.linkCamera"), b -> doLink()));
        unlink = addRenderableWidget(new HoloButton(rx, top + 144, rw, 16, Component.translatable("glass.ui.unlinkCamera"), b -> doUnlink(), true));
        addRenderableWidget(new HoloButton(left + 10, top + panelH - 34, 150, 16, Component.translatable("glass.ui.openNetwork"), b -> minecraft.setScreen(new NetworkScreen(this))));
    }

    @Override
    protected void refresh()
    {
        channels.setItems(GlassNetClient.channels());
        if(channels.getSelected() == null)
        {
            channels.selectWhere(c -> c.id().equals(terminal.channelId));
            if(channels.getSelected() == null)
            {
                channels.selectIndex(0);
            }
        }
        unlink.active = terminal.isLinked();
    }

    private void doLink()
    {
        GlassNetworking.ChannelInfo channel = channels.getSelected();
        if(channel == null)
        {
            warn(Component.translatable("glass.msg.pickChannel"));
            return;
        }
        if(name.getValue().isBlank())
        {
            warn(Component.translatable("glass.msg.badCameraName"));
            return;
        }
        clearWarning();
        ClientPlayNetworking.send(new GlassNetworking.LinkCamera(terminal.getBlockPos(), channel.id(), name.getValue().trim()));
    }

    private void doUnlink()
    {
        ClientPlayNetworking.send(new GlassNetworking.LinkCamera(terminal.getBlockPos(), "", ""));
    }

    @Override
    protected void onEnter()
    {
        doLink();
    }

    @Override
    public void tick()
    {
        super.tick();
        if(unlink != null)
        {
            unlink.active = terminal.isLinked();
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
        Holo.label(g, font, Component.translatable("glass.ui.channels"), left + 10, top + 41);
        int rx = left + 172;
        Holo.label(g, font, Component.translatable("glass.ui.cameraName"), rx, top + 41);
        Holo.label(g, font, Component.translatable("glass.ui.status"), rx, top + 78);
        if(terminal.isLinked())
        {
            GlassNetworking.ChannelInfo channel = GlassNetClient.channel(terminal.channelId);
            Component channelName = channel != null ? Component.literal(channel.name()) : Component.translatable("glass.ui.restricted");
            g.drawString(font, Component.translatable("glass.ui.linked").withColor(Holo.OK), rx, top + 90, Holo.OK, false);
            g.drawString(font, Component.literal("▸ ").append(channelName), rx, top + 101, Holo.TEXT, false);
            g.drawString(font, Component.literal("▸ " + terminal.cameraName), rx, top + 111, Holo.MUTED, false);
        }
        else
        {
            float blink = Holo.pulse(5F);
            g.drawString(font, Component.translatable("glass.ui.unlinked"), rx, top + 90, Holo.withAlpha(Holo.WARN, 0.5F + 0.5F * blink), false);
            g.drawString(font, Component.translatable("glass.ui.unlinkedHint"), rx, top + 101, Holo.MUTED, false);
        }
    }
}
