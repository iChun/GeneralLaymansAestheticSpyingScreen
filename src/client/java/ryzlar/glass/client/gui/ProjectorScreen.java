package ryzlar.glass.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.GlassConfig;
import ryzlar.glass.blockentity.GlassProjectorBlockEntity;
import ryzlar.glass.client.GlassNetClient;
import ryzlar.glass.client.gui.holo.Holo;
import ryzlar.glass.client.gui.holo.HoloButton;
import ryzlar.glass.client.gui.holo.HoloList;
import ryzlar.glass.client.gui.holo.HoloScreen;
import ryzlar.glass.network.GlassNetworking;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Chooses what a projector shows: a channel, then one of its cameras or all of them in turn.
 */
public class ProjectorScreen extends HoloScreen
{
    /** A row in the camera list: one camera, or (camera null) "cycle through all". */
    private record CameraRow(@Nullable GlassNetworking.CameraInfo camera)
    {
        @Override
        public boolean equals(Object o)
        {
            return o instanceof CameraRow other && Objects.equals(id(), other.id());
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(id());
        }

        String id()
        {
            return camera == null ? "" : camera.id();
        }
    }

    private static final int[] INTERVALS = { 3, 5, 10, 15, 30, 60, 120 };

    private final GlassProjectorBlockEntity projector;
    private HoloList<GlassNetworking.ChannelInfo> channels;
    private HoloList<CameraRow> cameras;
    private HoloButton apply;
    private int cycleSeconds;
    private boolean initialised;

    public ProjectorScreen(GlassProjectorBlockEntity projector)
    {
        super(Component.translatable("glass.ui.projector"), 370, 222);
        this.projector = projector;
        this.cycleSeconds = projector.cycleSeconds;
    }

    @Override
    protected Component subtitle()
    {
        return Component.translatable(projector.active ? "glass.ui.projector.online" : "glass.ui.projector.standby");
    }

    @Override
    protected void build()
    {
        channels = new HoloList<GlassNetworking.ChannelInfo>(left + 10, top + 52, 124, 132,
                c -> Component.literal(c.name()),
                c -> Component.literal(Integer.toString(c.cameras().size())),
                Component.translatable("glass.ui.noChannels"))
                .keyedBy(GlassNetworking.ChannelInfo::id)
                .onSelect(c -> {
                    fillCameras(c);
                    cameras.selectIndex(c.cameras().isEmpty() ? -1 : 1);
                    updateApply();
                });
        addRenderableWidget(channels);

        cameras = new HoloList<CameraRow>(left + 140, top + 52, 130, 132,
                row -> row.camera() == null ? Component.translatable("glass.ui.autoCycle") : Component.literal(row.camera().name()),
                row -> row.camera() == null ? Component.literal("⟳") : Component.literal(shortDimension(row.camera().dimension())),
                Component.translatable("glass.ui.noCameras"))
                .onSelect(row -> updateApply())
                .onActivate(row -> applySettings());
        addRenderableWidget(cameras);

        int rx = left + 278;
        int rw = panelW - 288;
        addRenderableWidget(new HoloButton(rx, top + 64, 18, 14, Component.literal("−"), b -> stepInterval(-1)));
        addRenderableWidget(new HoloButton(rx + rw - 18, top + 64, 18, 14, Component.literal("+"), b -> stepInterval(1)));
        apply = addRenderableWidget(new HoloButton(rx, top + 136, rw, 16, Component.translatable("glass.ui.apply"), b -> applySettings()));
        addRenderableWidget(new HoloButton(rx, top + 156, rw, 14, Component.translatable("glass.ui.wireless"), b -> minecraft.setScreen(new WirelessOrderScreen(projector, this))));
        addRenderableWidget(new HoloButton(left + 10, top + panelH - 32, 124, 14, Component.translatable("glass.ui.openNetwork"), b -> minecraft.setScreen(new NetworkScreen(this))));
    }

    static String shortDimension(String dimension)
    {
        String path = dimension.substring(dimension.indexOf(':') + 1);
        return switch(path)
        {
            case "overworld" -> "OW";
            case "the_nether" -> "NE";
            case "the_end" -> "END";
            default -> path.length() > 4 ? path.substring(0, 4).toUpperCase() : path.toUpperCase();
        };
    }

    private void fillCameras(@Nullable GlassNetworking.ChannelInfo channel)
    {
        List<CameraRow> rows = new ArrayList<>();
        if(channel != null)
        {
            if(!channel.cameras().isEmpty())
            {
                rows.add(new CameraRow(null));
            }
            for(GlassNetworking.CameraInfo camera : channel.cameras())
            {
                rows.add(new CameraRow(camera));
            }
        }
        cameras.setItems(rows);
    }

    @Override
    protected void refresh()
    {
        channels.setItems(GlassNetClient.channels());
        if(!initialised)
        {
            initialised = true;
            channels.selectWhere(c -> c.id().equals(projector.channelId));
            fillCameras(channels.getSelected());
            if(projector.cycle)
            {
                cameras.selectIndex(0);
            }
            else
            {
                cameras.selectWhere(row -> row.id().equals(projector.cameraId));
            }
        }
        else
        {
            fillCameras(channels.getSelected());
        }
        updateApply();
    }

    private boolean channelLocked()
    {
        GlassNetworking.ChannelInfo channel = channels.getSelected();
        return projector.active && channel != null && !channel.id().equals(projector.channelId);
    }

    private void updateApply()
    {
        if(apply != null)
        {
            apply.active = channels.getSelected() != null && cameras.getSelected() != null && !channelLocked();
        }
    }

    private void stepInterval(int step)
    {
        int index = 0;
        for(int i = 0; i < INTERVALS.length; i++)
        {
            if(INTERVALS[i] <= cycleSeconds)
            {
                index = i;
            }
        }
        index = Math.max(0, Math.min(INTERVALS.length - 1, index + step));
        cycleSeconds = Math.max(GlassConfig.get().minCycleSeconds, INTERVALS[index]);
    }

    private void applySettings()
    {
        GlassNetworking.ChannelInfo channel = channels.getSelected();
        CameraRow row = cameras.getSelected();
        if(channel == null || row == null)
        {
            warn(Component.translatable("glass.msg.pickCamera"));
            return;
        }
        if(channelLocked())
        {
            warn(Component.translatable("glass.msg.powerDown"));
            return;
        }
        clearWarning();
        ClientPlayNetworking.send(new GlassNetworking.ConfigureProjector(projector.getBlockPos(), channel.id(), row.id(), row.camera() == null, cycleSeconds));
    }

    @Override
    protected void onEnter()
    {
        applySettings();
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
        Holo.label(g, font, Component.translatable("glass.ui.cameras"), left + 140, top + 41);
        int rx = left + 278;
        int rw = panelW - 288;
        Holo.label(g, font, Component.translatable("glass.ui.interval"), rx, top + 52);
        Component interval = Component.literal(cycleSeconds + "s");
        g.drawCenteredString(font, interval, rx + rw / 2, top + 67, Holo.TEXT);

        Holo.label(g, font, Component.translatable("glass.ui.feed"), rx, top + 88);
        if(projector.camValid)
        {
            g.drawString(font, font.plainSubstrByWidth(projector.camName, rw), rx, top + 100, Holo.OK, false);
            g.drawString(font, font.plainSubstrByWidth(projector.channelName, rw), rx, top + 111, Holo.MUTED, false);
        }
        else
        {
            g.drawString(font, Component.translatable("glass.ui.noSignal"), rx, top + 100, Holo.withAlpha(Holo.WARN, 0.5F + 0.5F * Holo.pulse(5F)), false);
        }
        if(channelLocked())
        {
            g.drawString(font, Component.translatable("glass.msg.powerDown"), left + 140, top + panelH - 29, Holo.WARN, false);
        }
    }
}
