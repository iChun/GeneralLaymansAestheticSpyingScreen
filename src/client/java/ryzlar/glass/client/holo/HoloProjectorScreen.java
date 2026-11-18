package ryzlar.glass.client.holo;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import ryzlar.glass.client.gui.holo.Holo;
import ryzlar.glass.client.gui.holo.HoloButton;
import ryzlar.glass.client.gui.holo.HoloScreen;
import ryzlar.glass.holo.HoloNetworking;
import ryzlar.glass.holo.HoloProjectorBlockEntity;
import ryzlar.glass.holo.ProjectionRef;

/** The holo projector's panel: the crystal, how it looks, its scale and rotation, and its emitters. */
public class HoloProjectorScreen extends HoloScreen
{
    private static final String[] SCALE_NAMES = { "", "1 : 16", "1 : 8", "1 : 4", "1 : 2", "1 : 1" };

    private final HoloProjectorBlockEntity projector;
    private HoloButton look;
    private HoloButton scale;
    private HoloButton rotation;
    private HoloButton eject;

    public HoloProjectorScreen(HoloProjectorBlockEntity projector)
    {
        super(Component.translatable("glass.ui.holo"), 300, 200);
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
        int x = left + 110;
        int w = panelW - 122;
        look = addRenderableWidget(new HoloButton(x, top + 72, w, 16, Component.empty(),
                b -> send(projector.look.ordinal() + 1, projector.scale, projector.rotation)));
        scale = addRenderableWidget(new HoloButton(x, top + 92, w, 16, Component.empty(),
                b -> send(projector.look.ordinal(), projector.scale + 1, projector.rotation)));
        rotation = addRenderableWidget(new HoloButton(x, top + 112, w, 16, Component.empty(),
                b -> send(projector.look.ordinal(), projector.scale, projector.rotation + 1)));
        addRenderableWidget(new HoloButton(x, top + 132, w, 16, Component.translatable("glass.ui.holo.link"),
                b -> ClientPlayNetworking.send(new HoloNetworking.ProjectorAction(projector.getBlockPos(), HoloNetworking.ProjectorOp.LINK_EMITTERS.ordinal()))));
        eject = addRenderableWidget(new HoloButton(x, top + 48, w, 16, Component.translatable("glass.ui.holo.eject"),
                b -> ClientPlayNetworking.send(new HoloNetworking.ProjectorAction(projector.getBlockPos(), HoloNetworking.ProjectorOp.EJECT.ordinal())), true));
        updateLabels();
    }

    private void send(int newLook, int newScale, int newRotation)
    {
        ClientPlayNetworking.send(new HoloNetworking.ProjectorConfig(projector.getBlockPos(), newLook, newScale, newRotation));
    }

    private void updateLabels()
    {
        look.setMessage(Component.translatable("glass.ui.holo.look." + projector.look.name().toLowerCase(java.util.Locale.ROOT)));
        scale.setMessage(projector.scale == 0 ? Component.translatable("glass.ui.holo.scale.fit") : Component.literal(SCALE_NAMES[projector.scale]));
        rotation.setMessage(Component.literal(projector.rotation * 90 + "°"));
        eject.active = !projector.crystal.isEmpty();
    }

    @Override
    public void tick()
    {
        super.tick();
        if(projector.isRemoved())
        {
            onClose();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick)
    {
        updateLabels();
        super.render(g, mouseX, mouseY, partialTick);
        if(age() < 0.18F)
        {
            return;
        }
        int lx = left + 12;
        Holo.label(g, font, Component.translatable("glass.ui.holo.crystal"), lx, top + 42);
        ProjectionRef ref = projector.projection();
        if(ref != null)
        {
            g.drawString(font, font.plainSubstrByWidth(ref.name(), 92), lx, top + 54, Holo.TEXT, false);
            g.drawString(font, ref.sizeX() + " x " + ref.sizeY() + " x " + ref.sizeZ(), lx, top + 65, Holo.MUTED, false);
        }
        else
        {
            g.drawString(font, font.plainSubstrByWidth(Component.translatable("glass.ui.holo.noCrystal").getString(), 92), lx, top + 54,
                    Holo.withAlpha(Holo.WARN, 0.5F + 0.5F * Holo.pulse(5F)), false);
        }
        Holo.label(g, font, Component.translatable("glass.ui.holo.look"), lx, top + 76);
        Holo.label(g, font, Component.translatable("glass.ui.holo.scale"), lx, top + 96);
        Holo.label(g, font, Component.translatable("glass.ui.holo.rotation"), lx, top + 116);
        Holo.label(g, font, Component.translatable("glass.ui.holo.emitters"), lx, top + 136);
        g.drawString(font, Component.translatable("glass.ui.holo.emitterCount", projector.emitters.size()), lx + 56, top + 136, Holo.TEXT, false);
        g.drawWordWrap(font, Component.translatable("glass.ui.holo.hint"), lx, top + 156, panelW - 24, Holo.MUTED, false);
    }
}
