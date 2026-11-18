package ryzlar.glass.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import ryzlar.glass.blockentity.GlassProjectorBlockEntity;
import ryzlar.glass.blockentity.GlassSceneSelectorBlockEntity;
import ryzlar.glass.client.GlassNetClient;
import ryzlar.glass.client.gui.holo.Holo;
import ryzlar.glass.client.gui.holo.HoloButton;
import ryzlar.glass.client.gui.holo.HoloField;
import ryzlar.glass.client.gui.holo.HoloList;
import ryzlar.glass.client.gui.holo.HoloScreen;
import ryzlar.glass.network.GlassNetworking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The scene selector's panel: the scenes of a simulation chamber (left), and your channels to add as scenes (right).
 * Showing a scene while the chamber is on wipes it over the current one.
 */
public class SceneSelectorScreen extends HoloScreen
{
    private final GlassSceneSelectorBlockEntity selector;
    private int seenSelectorVersion = -1;

    private HoloList<SceneRow> scenes;
    private HoloButton show;
    private HoloButton up;
    private HoloButton down;
    private HoloButton remove;
    private HoloField search;
    private HoloList<GlassNetworking.ChannelInfo> channels;
    private HoloButton add;
    private String query = "";

    private record SceneRow(int index, GlassSceneSelectorBlockEntity.Scene scene) {}

    public SceneSelectorScreen(GlassSceneSelectorBlockEntity selector)
    {
        super(Component.translatable("glass.ui.selector"), 350, 230);
        this.selector = selector;
    }

    @Override
    protected Component subtitle()
    {
        GlassProjectorBlockEntity projector = selector.projector();
        if(projector == null)
        {
            return Component.translatable("glass.ui.selector.unlinked");
        }
        return Component.translatable(projector.active ? "glass.ui.projector.online" : "glass.ui.projector.standby");
    }

    @Override
    protected void build()
    {
        int lx = left + 12;
        int lw = 176;
        scenes = addRenderableWidget(new HoloList<SceneRow>(lx, top + 52, lw, 104,
                r -> Component.literal((r.index() + 1) + ". " + r.scene().name()),
                r -> r.index() == selector.current ? Component.translatable("glass.ui.live") : Component.empty(),
                Component.translatable("glass.ui.noScenes"))
                .keyedBy(r -> r.scene().channelId())
                .onSelect(r -> updateButtons())
                .onActivate(r -> send(GlassNetworking.SelectorOp.SHOW, r.index(), "")));
        show = addRenderableWidget(new HoloButton(lx, top + 160, 56, 16, Component.translatable("glass.ui.showScene"), b -> onSelected(GlassNetworking.SelectorOp.SHOW)));
        up = addRenderableWidget(new HoloButton(lx + 58, top + 160, 20, 16, Component.translatable("glass.ui.up"), b -> onSelected(GlassNetworking.SelectorOp.UP)));
        down = addRenderableWidget(new HoloButton(lx + 80, top + 160, 20, 16, Component.translatable("glass.ui.down"), b -> onSelected(GlassNetworking.SelectorOp.DOWN)));
        remove = addRenderableWidget(new HoloButton(lx + 102, top + 160, lw - 102, 16, Component.translatable("glass.ui.remove"), b -> onSelected(GlassNetworking.SelectorOp.REMOVE), true));

        int rx = left + 200;
        int rw = panelW - 212;
        search = addRenderableWidget(new HoloField(font, rx, top + 52, rw, 16, Component.translatable("glass.ui.search")));
        search.setMaxLength(24);
        search.setHint(Component.translatable("glass.ui.searchHint").withColor(0xFF3E6670));
        search.setValue(query);
        search.setResponder(v -> {
            query = v;
            refresh();
        });
        channels = addRenderableWidget(new HoloList<GlassNetworking.ChannelInfo>(rx, top + 72, rw, 78,
                c -> Component.literal(c.name()),
                c -> c.online() ? Component.translatable("glass.ui.online") : Component.translatable("glass.ui.offline"),
                Component.translatable("glass.ui.noMatches"))
                .keyedBy(GlassNetworking.ChannelInfo::id)
                .onSelect(c -> updateButtons())
                .onActivate(c -> doAdd()));
        add = addRenderableWidget(new HoloButton(rx, top + 154, rw, 16, Component.translatable("glass.ui.add"), b -> doAdd()));
        addRenderableWidget(new HoloButton(rx, top + 176, rw, 16, Component.translatable("glass.ui.linkNearest"), b -> send(GlassNetworking.SelectorOp.LINK, 0, "")));
    }

    @Override
    public void tick()
    {
        super.tick();
        if(selector.isRemoved())
        {
            onClose();
            return;
        }
        if(seenSelectorVersion != selector.clientVersion)
        {
            seenSelectorVersion = selector.clientVersion;
            refresh();
        }
    }

    @Override
    protected void refresh()
    {
        List<SceneRow> rows = new ArrayList<>();
        for(int i = 0; i < selector.scenes.size(); i++)
        {
            rows.add(new SceneRow(i, selector.scenes.get(i)));
        }
        scenes.setItems(rows);

        //Only channels you can use, that are not a scene yet.
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<GlassNetworking.ChannelInfo> matches = new ArrayList<>();
        for(GlassNetworking.ChannelInfo channel : GlassNetClient.channels())
        {
            boolean isScene = selector.scenes.stream().anyMatch(s -> s.channelId().equals(channel.id()));
            if(channel.access() && !isScene && (q.isEmpty() || channel.name().toLowerCase(Locale.ROOT).contains(q)))
            {
                matches.add(channel);
            }
        }
        matches.sort(Comparator.comparing(c -> c.name().toLowerCase(Locale.ROOT)));
        channels.setItems(matches);
        updateButtons();
    }

    private void updateButtons()
    {
        SceneRow row = scenes.getSelected();
        show.active = row != null && selector.projector() != null;
        up.active = row != null && row.index() > 0;
        down.active = row != null && row.index() < selector.scenes.size() - 1;
        remove.active = row != null;
        add.active = channels.getSelected() != null && selector.scenes.size() < GlassSceneSelectorBlockEntity.MAX_SCENES;
    }

    private void onSelected(GlassNetworking.SelectorOp op)
    {
        SceneRow row = scenes.getSelected();
        if(row == null)
        {
            warn(Component.translatable("glass.msg.pickChannel"));
            return;
        }
        clearWarning();
        send(op, row.index(), "");
    }

    private void doAdd()
    {
        GlassNetworking.ChannelInfo channel = channels.getSelected();
        if(channel == null)
        {
            warn(Component.translatable("glass.msg.pickChannel"));
            return;
        }
        clearWarning();
        send(GlassNetworking.SelectorOp.ADD, 0, channel.id());
    }

    private void send(GlassNetworking.SelectorOp op, int index, String channelId)
    {
        ClientPlayNetworking.send(new GlassNetworking.SelectorAction(selector.getBlockPos(), op.ordinal(), index, channelId));
    }

    @Override
    protected void onEnter()
    {
        if(search.isFocused())
        {
            doAdd();
        }
        else
        {
            onSelected(GlassNetworking.SelectorOp.SHOW);
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
        Holo.label(g, font, Component.translatable("glass.ui.scenes"), left + 12, top + 41);
        Holo.label(g, font, Component.translatable("glass.ui.addScene"), left + 200, top + 41);
        if(selector.projectorPos != null)
        {
            g.drawString(font, Component.translatable("glass.ui.selector.linked", selector.projectorPos.getX(), selector.projectorPos.getY(), selector.projectorPos.getZ()),
                    left + 12, top + 182, selector.projector() != null ? Holo.TEXT : Holo.WARN, false);
        }
        else
        {
            g.drawString(font, Component.translatable("glass.ui.selector.unlinked"), left + 12, top + 182, Holo.withAlpha(Holo.WARN, 0.5F + 0.5F * Holo.pulse(5F)), false);
        }
        g.drawString(font, Component.translatable("glass.ui.redstoneHint"), left + 12, top + 195, Holo.MUTED, false);
    }
}
