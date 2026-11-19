package ryzlar.glass.client.gadget;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.client.GlassNetClient;
import ryzlar.glass.client.TabletPlacement;
import ryzlar.glass.client.gui.holo.Holo;
import ryzlar.glass.client.gui.holo.HoloButton;
import ryzlar.glass.client.gui.holo.HoloList;
import ryzlar.glass.client.gui.holo.HoloScreen;
import ryzlar.glass.client.render.SpyViewRenderer;
import ryzlar.glass.gadget.GadgetNetworking;
import ryzlar.glass.gadget.Gadgets;
import ryzlar.glass.gadget.TabletLink;
import ryzlar.glass.network.GlassNetworking;

import java.util.ArrayList;
import java.util.List;

/**
 * The GLASS tablet: the live view of the channel it is tuned to (it builds up out of nano pixels, and flashes when a
 * motion sensor on that camera sees someone), or the list of your channels to tune it to.
 */
public class TabletScreen extends HoloScreen
{
    /** Render targets are stored bottom row first: drawn as they are, the picture would be upside down. */
    private static final boolean FLIP = true;
    private static final long CAMERA_RETRY_MILLIS = 2000L;
    private static final int CELLS_X = 24, CELLS_Y = 14;

    private final InteractionHand hand;
    private boolean choosing;
    private HoloList<GlassNetworking.ChannelInfo> list;
    @Nullable
    private TabletPlacement placement;
    private float viewSince = -1F;
    private long cameraAskedAt;
    @Nullable
    private String cameraAskedFor;

    public TabletScreen(InteractionHand hand)
    {
        super(Component.translatable("glass.ui.tablet"), 340, 236);
        this.hand = hand;
    }

    @Nullable
    private TabletLink link()
    {
        return minecraft == null || minecraft.player == null ? null : minecraft.player.getItemInHand(hand).get(Gadgets.TABLET_LINK);
    }

    @Override
    protected Component subtitle()
    {
        TabletLink link = link();
        return choosing || link == null ? Component.translatable("glass.ui.tablet.pick") : Component.translatable("glass.ui.tablet.watching", link.channelName());
    }

    @Override
    protected void build()
    {
        if(choosing || link() == null)
        {
            choosing = true;
            list = addRenderableWidget(new HoloList<GlassNetworking.ChannelInfo>(left + 10, top + 52, panelW - 20, panelH - 92,
                    c -> Component.literal(c.name()), c -> Component.literal(c.ownerName()).withColor(c.online() ? Holo.OK : Holo.MUTED),
                    Component.translatable("glass.ui.tablet.noChannels")).keyedBy(GlassNetworking.ChannelInfo::id).onActivate(c -> tune()));
            addRenderableWidget(new HoloButton(left + panelW - 110, top + panelH - 30, 100, 16, Component.translatable("glass.ui.tablet.tune"), b -> tune()));
            if(link() != null)
            {
                addRenderableWidget(new HoloButton(left + panelW - 214, top + panelH - 30, 100, 16, Component.translatable("glass.ui.back"), b -> {
                    choosing = false;
                    rebuildWidgets();
                }));
            }
        }
        else
        {
            addRenderableWidget(new HoloButton(left + 10, top + panelH - 30, 100, 16, Component.translatable("glass.ui.tablet.channel"), b -> {
                choosing = true;
                rebuildWidgets();
            }));
            addRenderableWidget(new HoloButton(left + panelW - 110, top + panelH - 30, 100, 16, Component.translatable("glass.ui.close"), b -> onClose()));
        }
    }

    @Override
    protected void refresh()
    {
        if(list != null && choosing)
        {
            List<GlassNetworking.ChannelInfo> open = new ArrayList<>();
            for(GlassNetworking.ChannelInfo channel : GlassNetClient.channels())
            {
                if(channel.access())
                {
                    open.add(channel);
                }
            }
            list.setItems(open);
        }
    }

    private void tune()
    {
        GlassNetworking.ChannelInfo channel = list == null ? null : list.getSelected();
        if(channel == null)
        {
            warn(Component.translatable("glass.msg.pickChannel"));
            return;
        }
        GadgetsClient.forgetCamera(channel.id());
        ClientPlayNetworking.send(new GadgetNetworking.LinkTablet(hand == InteractionHand.OFF_HAND, channel.id()));
        cameraAskedFor = channel.id();
        cameraAskedAt = Util.getMillis();
        choosing = false;
        placement = null;
        viewSince = -1F;
        rebuildWidgets();
    }

    @Override
    protected void onEnter()
    {
        if(choosing)
        {
            tune();
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
        if(choosing)
        {
            Holo.label(g, font, Component.translatable("glass.ui.tablet.channels"), left + 10, top + 41);
            return;
        }
        int x = left + 10, y = top + 40, w = panelW - 20, h = panelH - 78;
        Holo.inset(g, x, y, w, h);
        drawView(g, x + 1, y + 1, w - 2, h - 2, partialTick);
        Holo.brackets(g, x - 2, y - 2, w + 4, h + 4, 8, Holo.ACCENT);
    }

    private void drawView(GuiGraphics g, int x, int y, int w, int h, float partialTick)
    {
        TabletLink link = link();
        if(link == null)
        {
            centred(g, Component.translatable("glass.ui.tablet.pick"), x, y, w, h, Holo.MUTED);
            return;
        }
        GadgetsClient.Camera camera = GadgetsClient.camera(link.channelId());
        long now = Util.getMillis();
        if(camera == null)
        {
            if(!link.channelId().equals(cameraAskedFor) || now - cameraAskedAt > CAMERA_RETRY_MILLIS)
            {
                cameraAskedFor = link.channelId();
                cameraAskedAt = now;
                ClientPlayNetworking.send(new GadgetNetworking.RequestCamera(link.channelId()));
            }
            centred(g, Component.translatable("glass.ui.tablet.connecting"), x, y, w, h, Holo.ACCENT);
            return;
        }
        if(!camera.ok())
        {
            noise(g, x, y, w, h);
            centred(g, Component.translatable("glass.ui.tablet.noCamera"), x, y, w, h, Holo.WARN);
            return;
        }
        boolean here = minecraft.level != null && minecraft.level.dimension().location().toString().equals(camera.dimension())
                && minecraft.level.hasChunkAt(camera.pos());
        if(!here)
        {
            noise(g, x, y, w, h);
            centred(g, Component.translatable("glass.ui.tablet.outOfRange"), x, y, w, h, Holo.WARN);
            return;
        }
        if(placement == null || !placement.shows(camera.pos(), camera.facing()))
        {
            placement = new TabletPlacement(link.channelId(), camera.pos(), camera.facing());
            viewSince = -1F;
        }
        placement.keep();
        ResourceLocation texture = SpyViewRenderer.INSTANCE.request(placement);
        if(texture == null)
        {
            centred(g, Component.translatable("glass.ui.tablet.connecting"), x, y, w, h, Holo.ACCENT);
            return;
        }
        if(viewSince < 0F)
        {
            viewSince = Holo.seconds();
        }
        blitView(g, texture, x, y, w, h);
        reveal(g, x, y, w, h, Holo.seconds() - viewSince);
        float alert = GadgetsClient.alertFlash(link.channelId(), partialTick);
        if(alert > 0F)
        {
            int red = (int)(alert * 200) << 24 | 0xFF2A40;
            for(int i = 0; i < 4; i++)
            {
                Holo.frame(g, x + i, y + i, w - i * 2, h - i * 2, red);
            }
            g.fill(x, y + h - 16, x + w, y + h, (int)(alert * 150) << 24 | 0x40000A);
            g.drawCenteredString(font, Component.translatable("glass.ui.tablet.alert"), x + w / 2, y + h - 12, 0xFFFF6070);
        }
        BlockPos pos = camera.pos();
        g.drawString(font, Component.literal("CAM " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + "  " + camera.facing().getSerializedName().toUpperCase()),
                x + 4, y + 4, Holo.withAlpha(Holo.TEXT, 0.75F), false);
        if((int)(Holo.seconds() * 2F) % 2 == 0)
        {
            g.fill(x + w - 14, y + 5, x + w - 8, y + 11, 0xFFFF3040);
        }
    }

    /** The middle of the camera's picture, as much of it as fits this shape. */
    private void blitView(GuiGraphics g, ResourceLocation texture, int x, int y, int w, int h)
    {
        RenderTarget main = minecraft.getMainRenderTarget();
        float aspect = (float)w / h;
        float textureAspect = (float)main.width / Math.max(1, main.height);
        float u0 = 0F, u1 = 1F, top = 0F, bottom = 1F;
        if(aspect < textureAspect)
        {
            float f = aspect / textureAspect;
            u0 = 0.5F - f / 2F;
            u1 = 0.5F + f / 2F;
        }
        else
        {
            float f = textureAspect / aspect;
            top = 0.5F - f / 2F;
            bottom = 0.5F + f / 2F;
        }
        float v0 = FLIP ? 1F - top : top, v1 = FLIP ? 1F - bottom : bottom;
        g.blit(texture, x, y, x + w, y + h, u0, u1, v0, v1);
    }

    /** The picture builds up out of nano pixels from the middle, the front glowing. */
    private void reveal(GuiGraphics g, int x, int y, int w, int h, float age)
    {
        float progress = age / 0.9F;
        if(progress > 1.3F)
        {
            return;
        }
        for(int cy = 0; cy < CELLS_Y; cy++)
        {
            for(int cx = 0; cx < CELLS_X; cx++)
            {
                float dx = (cx + 0.5F) / CELLS_X - 0.5F, dy = (cy + 0.5F) / CELLS_Y - 0.5F;
                float at = (float)Math.sqrt(dx * dx + dy * dy) / 0.71F * 0.85F + hash(cx, cy) * 0.15F;
                int x0 = x + cx * w / CELLS_X, x1 = x + (cx + 1) * w / CELLS_X;
                int y0 = y + cy * h / CELLS_Y, y1 = y + (cy + 1) * h / CELLS_Y;
                if(at > progress)
                {
                    g.fill(x0, y0, x1, y1, Holo.FIELD);
                }
                else if(at > progress - 0.12F)
                {
                    g.fill(x0, y0, x1, y1, Holo.withAlpha(Holo.ACCENT, 0.55F));
                }
            }
        }
    }

    private static float hash(int x, int y)
    {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535F;
    }

    /** No picture: static. */
    private void noise(GuiGraphics g, int x, int y, int w, int h)
    {
        g.fill(x, y, x + w, y + h, 0xFF05080B);
        long seed = Util.getMillis() / 50L;
        java.util.Random random = new java.util.Random(seed);
        for(int i = 0; i < 260; i++)
        {
            int px = x + random.nextInt(w), py = y + random.nextInt(h);
            int grey = 40 + random.nextInt(120);
            g.fill(px, py, px + 2, py + 1, 0xFF000000 | grey << 16 | grey << 8 | grey);
        }
    }

    private void centred(GuiGraphics g, Component text, int x, int y, int w, int h, int colour)
    {
        g.drawCenteredString(font, text, x + w / 2, y + h / 2 - 4, colour);
    }
}
