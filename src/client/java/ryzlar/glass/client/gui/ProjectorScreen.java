package ryzlar.glass.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.blockentity.GlassProjectorBlockEntity;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Picks the channel a projector shows: search channels by name, enter the password the first time, show it.
 */
public class ProjectorScreen extends HoloScreen
{
    private final GlassProjectorBlockEntity projector;
    private HoloField search;
    private HoloList<GlassNetworking.ChannelInfo> results;
    private HoloPasswordField password;
    private HoloButton unlock;
    private HoloButton show;
    private String query = "";
    /** Show this channel as soon as the server confirms the password. */
    @Nullable
    private String showAfterUnlock;
    private boolean selectedCurrent;

    public ProjectorScreen(GlassProjectorBlockEntity projector)
    {
        super(Component.translatable("glass.ui.projector"), 350, 214);
        this.projector = projector;
    }

    @Override
    protected Component subtitle()
    {
        return Component.translatable(projector.active ? "glass.ui.projector.online" : "glass.ui.projector.standby");
    }

    @Override
    protected void build()
    {
        int lx = left + 12;
        int lw = 176;
        search = addRenderableWidget(new HoloField(font, lx, top + 52, lw, 16, Component.translatable("glass.ui.search")));
        search.setMaxLength(24);
        search.setHint(Component.translatable("glass.ui.searchHint").withColor(0xFF3E6670));
        search.setValue(query);
        search.setResponder(v -> {
            query = v;
            refresh();
        });
        results = addRenderableWidget(new HoloList<GlassNetworking.ChannelInfo>(lx, top + 74, lw, 108,
                c -> Component.literal(c.name()),
                c -> !c.access() ? Component.translatable("glass.ui.locked") : c.online() ? Component.translatable("glass.ui.online") : Component.translatable("glass.ui.offline"),
                Component.translatable("glass.ui.noMatches"))
                .keyedBy(GlassNetworking.ChannelInfo::id)
                .onSelect(c -> updateButtons())
                .onActivate(c -> primaryAction()));

        int rx = left + 200;
        int rw = panelW - 212;
        password = addRenderableWidget(new HoloPasswordField(font, rx, top + 118, rw, 16, Component.translatable("glass.ui.password"), GlassNetworkManager.MAX_PASSWORD));
        unlock = addRenderableWidget(new HoloButton(rx, top + 138, rw, 16, Component.translatable("glass.ui.unlock"), b -> doUnlock()));
        show = addRenderableWidget(new HoloButton(rx, top + 138, rw, 16, Component.translatable("glass.ui.show"), b -> doShow()));
        addRenderableWidget(new HoloButton(rx, top + 160, rw, 14, Component.translatable("glass.ui.wireless"), b -> minecraft.setScreen(new WirelessOrderScreen(projector, this))));
        setInitialFocus(search);
    }

    @Override
    protected void refresh()
    {
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<GlassNetworking.ChannelInfo> matches = new ArrayList<>();
        for(GlassNetworking.ChannelInfo channel : GlassNetClient.channels())
        {
            if(q.isEmpty() || channel.name().toLowerCase(Locale.ROOT).contains(q) || channel.ownerName().toLowerCase(Locale.ROOT).contains(q))
            {
                matches.add(channel);
            }
        }
        //Your own and unlocked channels first, then by name.
        matches.sort(Comparator.comparing((GlassNetworking.ChannelInfo c) -> !c.access()).thenComparing(c -> c.name().toLowerCase(Locale.ROOT)));
        results.setItems(matches);
        if(!selectedCurrent && !projector.channelId.isEmpty())
        {
            results.selectWhere(c -> c.id().equals(projector.channelId));
            selectedCurrent = results.getSelected() != null;
        }

        GlassNetworking.ChannelInfo pending = showAfterUnlock == null ? null : GlassNetClient.channel(showAfterUnlock);
        if(pending != null && pending.access())
        {
            showAfterUnlock = null;
            send(pending);
        }
        updateButtons();
    }

    private void updateButtons()
    {
        GlassNetworking.ChannelInfo selected = results.getSelected();
        boolean locked = selected != null && !selected.access();
        password.visible = locked;
        unlock.visible = locked;
        show.visible = !locked;
        show.active = selected != null && !channelLocked(selected);
    }

    /** The projector is on: it can only switch channel once powered down. */
    private boolean channelLocked(GlassNetworking.ChannelInfo channel)
    {
        return projector.active && !channel.id().equals(projector.channelId);
    }

    private void primaryAction()
    {
        GlassNetworking.ChannelInfo selected = results.getSelected();
        if(selected != null && !selected.access())
        {
            doUnlock();
        }
        else
        {
            doShow();
        }
    }

    private void doUnlock()
    {
        GlassNetworking.ChannelInfo selected = results.getSelected();
        if(selected == null)
        {
            return;
        }
        if(password.getValue().isEmpty())
        {
            warn(Component.translatable("glass.msg.fillIn"));
            return;
        }
        clearWarning();
        showAfterUnlock = selected.id();
        GlassNetClient.send(GlassNetworking.Action.UNLOCK, selected.id(), password.getValue());
        password.setValue("");
    }

    private void doShow()
    {
        GlassNetworking.ChannelInfo selected = results.getSelected();
        if(selected == null)
        {
            warn(Component.translatable("glass.msg.pickChannel"));
            return;
        }
        if(channelLocked(selected))
        {
            warn(Component.translatable("glass.msg.powerDown"));
            return;
        }
        clearWarning();
        send(selected);
    }

    private void send(GlassNetworking.ChannelInfo channel)
    {
        ClientPlayNetworking.send(new GlassNetworking.ConfigureProjector(projector.getBlockPos(), channel.id()));
    }

    @Override
    protected void onEnter()
    {
        primaryAction();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        if(age() < 0.18F)
        {
            return;
        }
        Holo.label(g, font, Component.translatable("glass.ui.findChannel"), left + 12, top + 41);
        int rx = left + 200;
        int rw = panelW - 212;

        Holo.label(g, font, Component.translatable("glass.ui.feed"), rx, top + 41);
        if(!projector.channelId.isEmpty())
        {
            g.drawString(font, font.plainSubstrByWidth(projector.channelName, rw), rx, top + 53, Holo.TEXT, false);
            g.drawString(font, Component.translatable(projector.camValid ? "glass.ui.online" : "glass.ui.offline"), rx, top + 64,
                    projector.camValid ? Holo.OK : Holo.withAlpha(Holo.WARN, 0.5F + 0.5F * Holo.pulse(5F)), false);
        }
        else
        {
            g.drawString(font, Component.translatable("glass.ui.noSignal"), rx, top + 53, Holo.withAlpha(Holo.WARN, 0.5F + 0.5F * Holo.pulse(5F)), false);
        }

        GlassNetworking.ChannelInfo selected = results.getSelected();
        if(selected != null)
        {
            Holo.label(g, font, Component.translatable("glass.ui.selected"), rx, top + 80);
            g.drawString(font, font.plainSubstrByWidth(selected.name(), rw), rx, top + 92, Holo.TEXT, false);
            g.drawString(font, font.plainSubstrByWidth(Component.translatable("glass.ui.ownedBy", selected.ownerName()).getString(), rw), rx, top + 103, Holo.MUTED, false);
            if(channelLocked(selected))
            {
                g.drawString(font, Component.translatable("glass.msg.powerDown"), left + 12, top + panelH - 29, Holo.WARN, false);
            }
        }
    }
}
