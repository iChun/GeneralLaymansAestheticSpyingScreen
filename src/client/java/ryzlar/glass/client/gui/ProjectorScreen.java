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
    private HoloButton cloak;
    private HoloButton field;
    private String query = "";
    /** Show this channel as soon as the server confirms the password. */
    @Nullable
    private String showAfterUnlock;
    private boolean selectedCurrent;

    public ProjectorScreen(GlassProjectorBlockEntity projector)
    {
        super(Component.translatable("glass.ui.projector"), 350, 256);
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
        //Cloak: show the world behind the screen as if the blocks right behind it were not there.
        cloak = addRenderableWidget(new HoloButton(rx, top + 180, rw, 14, cloakLabel(), b -> sendCloak(!projector.cloak, projector.cloakDepth)));
        addRenderableWidget(new HoloButton(rx, top + 198, 16, 14, Component.literal("-"), b -> sendCloak(projector.cloak, projector.cloakDepth - 1)));
        addRenderableWidget(new HoloButton(rx + rw - 16, top + 198, 16, 14, Component.literal("+"), b -> sendCloak(projector.cloak, projector.cloakDepth + 1)));
        //Cloak field: walls rising from four wireless extensions in the corners, hiding everything between them.
        int fw = rw / 2 - 2;
        field = addRenderableWidget(new HoloButton(rx, top + 216, fw, 14, fieldLabel(), b -> sendField(!projector.cloakField, projector.fieldHeight)));
        addRenderableWidget(new HoloButton(rx + fw + 4, top + 216, 14, 14, Component.literal("-"), b -> sendField(projector.cloakField, projector.fieldHeight - 1)));
        addRenderableWidget(new HoloButton(rx + rw - 14, top + 216, 14, 14, Component.literal("+"), b -> sendField(projector.cloakField, projector.fieldHeight + 1)));
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

    /** Switching channel while the projector is on is fine (the new feed wipes over the old one). */
    private boolean channelLocked(GlassNetworking.ChannelInfo channel)
    {
        return false;
    }

    private Component cloakLabel()
    {
        return Component.translatable(projector.cloak ? "glass.ui.cloakOn" : "glass.ui.cloakOff");
    }

    private void sendCloak(boolean on, int depth)
    {
        int clamped = Math.clamp(depth, 1, projector.cloakLimits().depth());
        ClientPlayNetworking.send(new GlassNetworking.ConfigureCloak(projector.getBlockPos(), on, clamped, projector.cloakField, projector.fieldHeight));
    }

    private Component fieldLabel()
    {
        return Component.translatable(projector.cloakField ? "glass.ui.fieldOn" : "glass.ui.fieldOff");
    }

    /** A field is a cloak too: switching it on switches cloak on. */
    private void sendField(boolean on, int height)
    {
        if(on && !projector.hasFieldCorners())
        {
            warn(Component.translatable("glass.msg.fieldNeedsCorners"));
            return;
        }
        clearWarning();
        int clamped = Math.clamp(height, 1, projector.cloakLimits().height());
        ClientPlayNetworking.send(new GlassNetworking.ConfigureCloak(projector.getBlockPos(), projector.cloak || on, projector.cloakDepth, on, clamped));
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
        cloak.setMessage(cloakLabel());
        cloak.selected(projector.cloak);
        field.setMessage(fieldLabel());
        field.selected(projector.cloakField);
        Component height = Component.translatable("glass.ui.fieldHeight", projector.fieldHeight);
        int fw = rw / 2 - 2;
        int hx = rx + fw + 18;
        g.drawString(font, height, hx + (rw - fw - 32 - font.width(height)) / 2, top + 219, projector.cloakField ? Holo.TEXT : Holo.MUTED, false);
        Component depth = Component.translatable("glass.ui.cloakDepth", projector.cloakDepth);
        g.drawString(font, depth, rx + (rw - font.width(depth)) / 2, top + 201, projector.cloak ? Holo.TEXT : Holo.MUTED, false);
        if(projector.cloak)
        {
            g.drawString(font, font.plainSubstrByWidth(Component.translatable("glass.ui.cloakFeed", projector.cloakDepth).getString(), rw), rx, top + 53, Holo.OK, false);
            //How big it may be, and whether it is too big.
            GlassProjectorBlockEntity.CloakLimits limits = projector.cloakLimits();
            Component problem = projector.cloakProblem();
            Component line = problem != null ? problem : Component.translatable("glass.ui.cloakLimits", limits.footprint(), limits.footprint(), limits.height(),
                    limits.amplifiers(), GlassProjectorBlockEntity.MAX_AMPLIFIERS);
            int colour = problem != null ? Holo.withAlpha(Holo.ERROR, 0.6F + 0.4F * Holo.pulse(4F)) : Holo.MUTED;
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(line, rw);
            for(int i = 0; i < Math.min(3, lines.size()); i++)
            {
                g.drawString(font, lines.get(i), rx, top + 64 + 9 * i, colour, false);
            }
        }
        else if(!projector.channelId.isEmpty())
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
