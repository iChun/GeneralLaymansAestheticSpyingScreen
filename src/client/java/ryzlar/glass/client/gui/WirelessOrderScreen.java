package ryzlar.glass.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import ryzlar.glass.blockentity.GlassProjectorBlockEntity;
import ryzlar.glass.client.gui.holo.Holo;
import ryzlar.glass.client.gui.holo.HoloButton;
import ryzlar.glass.client.gui.holo.HoloList;
import ryzlar.glass.client.gui.holo.HoloScreen;
import ryzlar.glass.network.GlassNetworking;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Orders the wireless extensions a projector draws its floating screen between, with a rotating wireframe preview
 * (drag to rotate, scroll to zoom). Port of GuiWirelessOrder.
 */
public class WirelessOrderScreen extends HoloScreen
{
    private final GlassProjectorBlockEntity master;
    @Nullable
    private final Screen parent;
    private List<BlockPos> order;
    private HoloList<BlockPos> list;

    private float rotateX = 0F;
    private float rotateY = 0F;
    private float scale = 1F;
    private boolean hasClicked = false;

    public WirelessOrderScreen(GlassProjectorBlockEntity master, @Nullable Screen parent)
    {
        super(Component.translatable("glass.ui.wirelessOrder"), 340, 210);
        this.master = master;
        this.parent = parent;
        this.order = new ArrayList<>(master.wirelessPos);
    }

    @Override
    protected Component subtitle()
    {
        return Component.translatable(master.active ? "glass.ui.wirelessOrder.locked" : "glass.ui.wirelessOrder.sub", order.size());
    }

    @Override
    protected void build()
    {
        int selected = list != null ? list.getSelectedIndex() : -1;
        list = addRenderableWidget(new HoloList<BlockPos>(left + 10, top + 52, 150, 104,
                pos -> Component.literal(posLabel(pos)).withColor(colourOf(pos)),
                null, Component.translatable("glass.ui.noWireless")));
        list.setItems(order);
        list.selectIndex(selected);

        int bx = left + 10;
        addRenderableWidget(new HoloButton(bx, top + 160, 36, 14, Component.literal("▲"), b -> move(-1)));
        addRenderableWidget(new HoloButton(bx + 38, top + 160, 36, 14, Component.literal("▼"), b -> move(1)));
        addRenderableWidget(new HoloButton(bx + 76, top + 160, 74, 14, Component.translatable("glass.ui.remove"), b -> removeSelected(), true));
        HoloButton save = addRenderableWidget(new HoloButton(left + panelW - 110, top + panelH - 34, 100, 16, Component.translatable("glass.ui.save"), b -> save()));
        save.active = !master.active;
        addRenderableWidget(new HoloButton(left + panelW - 214, top + panelH - 34, 100, 16, Component.translatable("glass.ui.back"), b -> onClose()));
    }

    private static String posLabel(BlockPos pos)
    {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    /** Port of iChunUtil's RendererHelper.getRandomColourFromString, brightened for the dark panel. */
    private static int colourOf(BlockPos pos)
    {
        int c = new Random(("(" + posLabel(pos) + ")").hashCode()).nextInt(0xFFFFFF);
        return 0xFF000000 | (c | 0x404040);
    }

    private void move(int by)
    {
        int index = list.getSelectedIndex();
        int target = index + by;
        if(index >= 0 && index < order.size() && target >= 0 && target < order.size())
        {
            order.add(target, order.remove(index));
            list.setItems(order);
            list.selectIndex(target);
        }
    }

    private void removeSelected()
    {
        int index = list.getSelectedIndex();
        if(index >= 0 && index < order.size())
        {
            order.remove(index);
            list.setItems(order);
            list.selectIndex(Math.min(index, order.size() - 1));
        }
    }

    private void save()
    {
        ClientPlayNetworking.send(new GlassNetworking.WirelessOrder(master.getBlockPos(), List.copyOf(order)));
        onClose();
    }

    @Override
    public void onClose()
    {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        if(keyCode == GLFW.GLFW_KEY_DELETE)
        {
            removeSelected();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private int previewX()
    {
        return left + 170;
    }

    private int previewY()
    {
        return top + 52;
    }

    private int previewSize()
    {
        return Math.min(panelW - 180, 122);
    }

    private boolean isOverPreview(double mouseX, double mouseY)
    {
        int s = previewSize();
        return mouseX >= previewX() && mouseX < previewX() + s * 1.3 && mouseY >= previewY() && mouseY < previewY() + s;
    }

    @Override
    public void tick()
    {
        super.tick();
        if(!hasClicked)
        {
            rotateX += 2.5F;
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY)
    {
        if(button == 0 && order.size() > 1 && isOverPreview(mouseX, mouseY))
        {
            hasClicked = true;
            rotateX += (float)dragX;
            rotateY += (float)dragY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical)
    {
        if(isOverPreview(mouseX, mouseY))
        {
            scale = Math.max(0.1F, scale + 0.1F * (float)vertical);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        if(age() < 0.18F)
        {
            return;
        }
        Holo.label(g, font, Component.translatable("glass.ui.extensions"), left + 10, top + 41);
        Holo.label(g, font, Component.translatable("glass.ui.preview"), previewX(), top + 41);
        int pw = panelW - 180;
        int ph = 122;
        Holo.inset(g, previewX(), previewY(), pw, ph);
        if(order.size() > 1)
        {
            g.enableScissor(previewX() + 1, previewY() + 1, previewX() + pw - 1, previewY() + ph - 1);
            drawPreview(g, partialTick, previewX() + pw / 2, previewY() + ph / 2);
            g.disableScissor();
        }
        else
        {
            g.drawCenteredString(font, Component.translatable("glass.ui.needTwo"), previewX() + pw / 2, previewY() + ph / 2 - 4, 0xFF3E6670);
        }
    }

    private void drawPreview(GuiGraphics g, float partialTick, int cx, int cy)
    {
        Direction face = master.placingFace;
        float spin = rotateX + (hasClicked ? 0F : 2.5F * partialTick);

        Matrix4f m = new Matrix4f().translate(cx, cy, 0F).scale(-6F * scale, -6F * scale, 6F * scale);
        int horiOrient = (face.getAxis() == Direction.Axis.Y ? Direction.UP : face).getOpposite().get2DDataValue();
        m.rotateY((float)Math.toRadians((face.get3DDataValue() > 0 ? 180F : 0F) + -horiOrient * 90F));
        if(face.getAxis() == Direction.Axis.Y)
        {
            m.rotateX((float)Math.toRadians(face == Direction.UP ? -90F : 90F));
        }
        m.rotateY((float)Math.toRadians(spin));
        m.rotateZ((float)Math.toRadians(rotateY));

        float pushback = 0.501F;
        BlockPos masterPos = master.getBlockPos();
        List<float[]> points = new ArrayList<>();
        points.add(project(m, face.getStepX() * pushback, face.getStepY() * pushback, face.getStepZ() * pushback, Holo.ACCENT));
        for(BlockPos pos : order)
        {
            float pX = face.getStepX() * pushback * (face.getStepX() > 0 && pos.getX() > masterPos.getX() ? -1F : 1F);
            float pY = face.getStepY() * pushback * (face.getStepY() > 0 && pos.getY() > masterPos.getY() ? -1F : 1F);
            float pZ = face.getStepZ() * pushback * (face.getStepZ() > 0 && pos.getZ() > masterPos.getZ() ? -1F : 1F);
            points.add(project(m, pos.getX() - masterPos.getX() + pX, pos.getY() - masterPos.getY() + pY, pos.getZ() - masterPos.getZ() + pZ, colourOf(pos)));
        }
        for(int i = 0; i + 1 < points.size(); i++)
        {
            line(g, points.get(i), points.get(i + 1));
            if(i + 2 < points.size())
            {
                line(g, points.get(i), points.get(i + 2));
            }
        }
        for(float[] p : points)
        {
            int x = Math.round(p[0]);
            int y = Math.round(p[1]);
            g.fill(x - 1, y - 1, x + 2, y + 2, (int)p[2]);
        }
    }

    private static float[] project(Matrix4f m, float x, float y, float z, int colour)
    {
        Vector3f v = m.transformPosition(new Vector3f(x, y, z));
        return new float[] { v.x, v.y, colour };
    }

    private static void line(GuiGraphics g, float[] from, float[] to)
    {
        int colour = Holo.withAlpha((int)to[2], 0.8F);
        float dx = to[0] - from[0];
        float dy = to[1] - from[1];
        int steps = (int)Math.min(300, Math.max(Math.abs(dx), Math.abs(dy)));
        for(int s = 0; s <= steps; s++)
        {
            float t = steps == 0 ? 0F : (float)s / steps;
            int x = Math.round(from[0] + dx * t);
            int y = Math.round(from[1] + dy * t);
            g.fill(x, y, x + 1, y + 1, colour);
        }
    }
}
