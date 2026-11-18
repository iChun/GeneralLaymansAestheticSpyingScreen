package ryzlar.glass.client.holo;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import ryzlar.glass.client.gui.holo.Holo;
import ryzlar.glass.client.gui.holo.HoloButton;
import ryzlar.glass.client.gui.holo.HoloField;
import ryzlar.glass.client.gui.holo.HoloScreen;
import ryzlar.glass.holo.HoloNetworking;
import ryzlar.glass.holo.HoloProjectionData;
import ryzlar.glass.holo.HoloScannerBlockEntity;

/**
 * The scanner's panel: where the area starts (X, Y, Z from the scanner) and how big it is, with a grid in the world
 * showing it, a name, and SCAN. Shift-click a +/- for steps of 5.
 */
public class HoloScannerScreen extends HoloScreen
{
    /** How long the grid stays after the panel closes, in ticks. */
    private static final int PREVIEW_AFTER_CLOSE = 200;

    private final HoloScannerBlockEntity scanner;
    private final int[] values = new int[6]; //offset x, y, z, size x, y, z
    private HoloField name;
    private String typedName;

    public HoloScannerScreen(HoloScannerBlockEntity scanner)
    {
        super(Component.translatable("glass.ui.scanner"), 300, 190);
        this.scanner = scanner;
        values[0] = scanner.offsetX;
        values[1] = scanner.offsetY;
        values[2] = scanner.offsetZ;
        values[3] = scanner.sizeX;
        values[4] = scanner.sizeY;
        values[5] = scanner.sizeZ;
        typedName = scanner.name;
    }

    @Override
    protected Component subtitle()
    {
        return Component.translatable("glass.ui.scanner.sub");
    }

    @Override
    protected void build()
    {
        for(int i = 0; i < 6; i++)
        {
            int column = i < 3 ? 0 : 1;
            int row = i % 3;
            int x = left + 12 + column * 144;
            int y = top + 54 + row * 20;
            int index = i;
            addRenderableWidget(new HoloButton(x + 20, y, 16, 16, Component.literal("-"), b -> change(index, Screen.hasShiftDown() ? -5 : -1)));
            addRenderableWidget(new HoloButton(x + 96, y, 16, 16, Component.literal("+"), b -> change(index, Screen.hasShiftDown() ? 5 : 1)));
        }
        name = addRenderableWidget(new HoloField(font, left + 12, top + 130, panelW - 24, 16, Component.translatable("glass.ui.scan.name")));
        name.setMaxLength(HoloScannerBlockEntity.MAX_NAME);
        name.setHint(Component.translatable("glass.ui.scan.nameHint").withColor(0xFF3E6670));
        name.setValue(typedName);
        name.setResponder(v -> {
            typedName = v;
            sendConfig();
        });
        addRenderableWidget(new HoloButton(left + 12, top + 152, panelW - 24, 16, Component.translatable("glass.ui.scan.scan"), b -> {
            sendConfig();
            ClientPlayNetworking.send(new HoloNetworking.ScannerScan(scanner.getBlockPos()));
        }));
        updatePreview(PREVIEW_AFTER_CLOSE);
    }

    private void change(int index, int delta)
    {
        int min = index < 3 ? -HoloScannerBlockEntity.MAX_OFFSET : 1;
        int max = index < 3 ? HoloScannerBlockEntity.MAX_OFFSET : HoloProjectionData.MAX_SIZE;
        values[index] = Math.clamp(values[index] + delta, min, max);
        sendConfig();
        updatePreview(PREVIEW_AFTER_CLOSE);
    }

    private void sendConfig()
    {
        ClientPlayNetworking.send(new HoloNetworking.ScannerConfig(scanner.getBlockPos(), values[0], values[1], values[2], values[3], values[4], values[5], typedName));
    }

    private AABB box()
    {
        int x = scanner.getBlockPos().getX() + values[0];
        int y = scanner.getBlockPos().getY() + values[1];
        int z = scanner.getBlockPos().getZ() + values[2];
        return new AABB(x, y, z, x + values[3], y + values[4], z + values[5]);
    }

    private void updatePreview(int ticks)
    {
        HoloClient.preview(box(), ticks);
    }

    @Override
    public void tick()
    {
        super.tick();
        updatePreview(PREVIEW_AFTER_CLOSE);
        if(scanner.isRemoved())
        {
            onClose();
        }
    }

    @Override
    protected void onEnter()
    {
        sendConfig();
        ClientPlayNetworking.send(new HoloNetworking.ScannerScan(scanner.getBlockPos()));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        if(age() < 0.18F)
        {
            return;
        }
        Holo.label(g, font, Component.translatable("glass.ui.scan.offset"), left + 12, top + 42);
        Holo.label(g, font, Component.translatable("glass.ui.scan.size"), left + 156, top + 42);
        String[] axes = { "X", "Y", "Z", "X", "Y", "Z" };
        for(int i = 0; i < 6; i++)
        {
            int column = i < 3 ? 0 : 1;
            int row = i % 3;
            int x = left + 12 + column * 144;
            int y = top + 54 + row * 20;
            g.drawString(font, axes[i], x + 4, y + 4, Holo.MUTED, false);
            String value = Integer.toString(values[i]);
            g.drawString(font, value, x + 66 - font.width(value) / 2, y + 4, Holo.TEXT, false);
        }
        Holo.label(g, font, Component.translatable("glass.ui.scan.name"), left + 12, top + 119);
        Component blocks = Component.translatable("glass.ui.scan.blocks", values[3] * values[4] * values[5]);
        g.drawString(font, blocks, left + panelW - 12 - font.width(blocks), top + 119, Holo.MUTED, false);
    }
}
