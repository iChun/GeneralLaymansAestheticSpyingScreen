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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Orders the wireless extensions a projector draws its floating screen between, with a rotating wireframe preview
 * (drag to rotate, scroll to zoom, click a corner to select it). Port of GuiWirelessOrder, extended with an analysis of
 * the triangle strip the projector draws (overlap, coverage, size), an auto-order that finds a clean strip, and undo.
 */
public class WirelessOrderScreen extends HoloScreen
{
    /** Auto-order tries every order, so it stops being instant past this many extensions. */
    private static final int AUTO_MAX = 8;
    private static final int HISTORY = 50;
    private static final float EPSILON = 0.01F;

    private final GlassProjectorBlockEntity master;
    @Nullable
    private final Screen parent;
    private List<BlockPos> order;
    private final Deque<List<BlockPos>> history = new ArrayDeque<>();
    private HoloList<BlockPos> list;
    private HoloButton undoButton;
    private HoloButton revertButton;
    private HoloButton autoButton;
    private HoloButton saveButton;
    private HoloButton fillButton;

    private float rotateX = 0F;
    private float rotateY = 0F;
    private float scale = 1F;
    private boolean hasClicked = false;
    private boolean fill = true;
    /** Where the projector (0) and each extension (1..) were drawn last frame, for clicking and hovering them. */
    private float[][] drawnPoints = new float[0][];

    public WirelessOrderScreen(GlassProjectorBlockEntity master, @Nullable Screen parent)
    {
        super(Component.translatable("glass.ui.wirelessOrder"), 400, 250);
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
        list = addRenderableWidget(new HoloList<BlockPos>(left + 10, top + 52, 164, 108,
                pos -> Component.literal("#" + (order.indexOf(pos) + 1) + "  " + posLabel(pos)).withColor(colourOf(pos)),
                pos -> Component.literal(String.format(Locale.ROOT, "%.1fm", Math.sqrt(pos.distSqr(master.getBlockPos())))),
                Component.translatable("glass.ui.noWireless")));
        list.setItems(order);
        list.selectIndex(selected);

        int bx = left + 10;
        addRenderableWidget(new HoloButton(bx, top + 164, 26, 14, Component.literal("▲"), b -> move(-1)));
        addRenderableWidget(new HoloButton(bx + 28, top + 164, 26, 14, Component.literal("▼"), b -> move(1)));
        addRenderableWidget(new HoloButton(bx + 56, top + 164, 26, 14, Component.literal("⇅"), b -> reverse()));
        addRenderableWidget(new HoloButton(bx + 84, top + 164, 80, 14, Component.translatable("glass.ui.remove"), b -> removeSelected(), true));
        autoButton = addRenderableWidget(new HoloButton(bx, top + 181, 80, 14, Component.translatable("glass.ui.autoOrder"), b -> autoOrder()));
        undoButton = addRenderableWidget(new HoloButton(bx + 82, top + 181, 40, 14, Component.translatable("glass.ui.undo"), b -> undo()));
        revertButton = addRenderableWidget(new HoloButton(bx + 124, top + 181, 40, 14, Component.translatable("glass.ui.revert"), b -> revert()));

        int px = previewX() + previewW();
        fillButton = addRenderableWidget(new HoloButton(px - 54, top + 39, 34, 11, Component.translatable("glass.ui.fill"), b -> fill = !fill));
        addRenderableWidget(new HoloButton(px - 18, top + 39, 18, 11, Component.literal("⟲"), b -> resetView()));

        saveButton = addRenderableWidget(new HoloButton(left + panelW - 110, top + panelH - 34, 100, 16, Component.translatable("glass.ui.save"), b -> save()));
        addRenderableWidget(new HoloButton(left + panelW - 214, top + panelH - 34, 100, 16, Component.translatable("glass.ui.back"), b -> onClose()));
        updateButtons();
    }

    private void updateButtons()
    {
        boolean dirty = isDirty();
        saveButton.active = !master.active && dirty;
        revertButton.active = dirty;
        undoButton.active = !history.isEmpty();
        autoButton.active = order.size() >= 3 && order.size() <= AUTO_MAX;
        fillButton.selected(fill);
    }

    private boolean isDirty()
    {
        return !order.equals(master.wirelessPos);
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

    /** Keeps the current order for undo, before changing it. */
    private void remember()
    {
        history.push(new ArrayList<>(order));
        while(history.size() > HISTORY)
        {
            history.removeLast();
        }
    }

    /** Shows a changed order, with the given row selected. */
    private void changed(int select)
    {
        list.setItems(order);
        list.selectIndex(select);
        list.ensureVisible(select);
        updateButtons();
    }

    private void select(int index)
    {
        if(index >= 0 && index < order.size())
        {
            list.selectIndex(index);
            list.ensureVisible(index);
        }
    }

    private void move(int by)
    {
        int index = list.getSelectedIndex();
        int target = index + by;
        if(index >= 0 && index < order.size() && target >= 0 && target < order.size())
        {
            remember();
            order.add(target, order.remove(index));
            changed(target);
        }
    }

    private void reverse()
    {
        if(order.size() > 1)
        {
            int index = list.getSelectedIndex();
            remember();
            Collections.reverse(order);
            changed(index < 0 ? -1 : order.size() - 1 - index);
        }
    }

    private void removeSelected()
    {
        int index = list.getSelectedIndex();
        if(index >= 0 && index < order.size())
        {
            remember();
            order.remove(index);
            changed(Math.min(index, order.size() - 1));
        }
    }

    private void undo()
    {
        if(!history.isEmpty())
        {
            BlockPos selected = list.getSelected();
            order = history.pop();
            changed(selected == null ? -1 : order.indexOf(selected));
        }
    }

    private void revert()
    {
        if(isDirty())
        {
            BlockPos selected = list.getSelected();
            remember();
            order = new ArrayList<>(master.wirelessPos);
            changed(selected == null ? -1 : order.indexOf(selected));
        }
    }

    /** Tries every order and keeps the one whose strip overlaps least and covers most (the current one wins ties). */
    private void autoOrder()
    {
        int n = order.size();
        if(n < 3 || n > AUTO_MAX)
        {
            return;
        }
        float[][] flat = flatten(points3d(order));
        int[] perm = new int[n];
        int[] best = new int[n];
        for(int i = 0; i < n; i++)
        {
            best[i] = i;
        }
        float[] bestScore = { Float.MAX_VALUE, 0F };
        Analysis current = analyse(flat);
        bestScore[0] = current.overlap;
        bestScore[1] = current.covered();
        permute(flat, perm, new boolean[n], 0, best, bestScore);

        List<BlockPos> sorted = new ArrayList<>();
        for(int i : best)
        {
            sorted.add(order.get(i));
        }
        if(!sorted.equals(order))
        {
            BlockPos selected = list.getSelected();
            remember();
            order = sorted;
            changed(selected == null ? -1 : order.indexOf(selected));
        }
    }

    private static void permute(float[][] flat, int[] perm, boolean[] used, int depth, int[] best, float[] bestScore)
    {
        int n = perm.length;
        if(depth == n)
        {
            float[][] verts = new float[n + 1][];
            verts[0] = flat[0];
            for(int i = 0; i < n; i++)
            {
                verts[i + 1] = flat[perm[i] + 1];
            }
            Analysis a = analyse(verts);
            if(a.overlap < bestScore[0] - EPSILON || (Math.abs(a.overlap - bestScore[0]) <= EPSILON && a.covered() > bestScore[1] + EPSILON))
            {
                bestScore[0] = a.overlap;
                bestScore[1] = a.covered();
                System.arraycopy(perm, 0, best, 0, n);
            }
            return;
        }
        for(int i = 0; i < n; i++)
        {
            if(!used[i])
            {
                used[i] = true;
                perm[depth] = i;
                permute(flat, perm, used, depth + 1, best, bestScore);
                used[i] = false;
            }
        }
    }

    private void save()
    {
        ClientPlayNetworking.send(new GlassNetworking.WirelessOrder(master.getBlockPos(), List.copyOf(order)));
        onClose();
    }

    @Override
    protected void onEnter()
    {
        if(saveButton.active)
        {
            save();
        }
    }

    @Override
    public void onClose()
    {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        int index = list.getSelectedIndex();
        switch(keyCode)
        {
            case GLFW.GLFW_KEY_DELETE ->
            {
                removeSelected();
                return true;
            }
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN ->
            {
                int by = keyCode == GLFW.GLFW_KEY_UP ? -1 : 1;
                if(hasShiftDown())
                {
                    move(by);
                }
                else
                {
                    select(index < 0 ? 0 : index + by);
                }
                return true;
            }
            case GLFW.GLFW_KEY_Z ->
            {
                if(hasControlDown())
                {
                    undo();
                    return true;
                }
            }
            case GLFW.GLFW_KEY_R ->
            {
                resetView();
                return true;
            }
            default -> {}
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void resetView()
    {
        rotateX = 0F;
        rotateY = 0F;
        scale = 1F;
        hasClicked = false;
    }

    private int previewX()
    {
        return left + 184;
    }

    private int previewY()
    {
        return top + 52;
    }

    private int previewW()
    {
        return panelW - 194;
    }

    private int previewH()
    {
        return 122;
    }

    private boolean isOverPreview(double mouseX, double mouseY)
    {
        return mouseX >= previewX() && mouseX < previewX() + previewW() && mouseY >= previewY() && mouseY < previewY() + previewH();
    }

    /** The drawn corner under the mouse: 0 the projector, 1.. the extensions, or -1. */
    private int pointAt(double mouseX, double mouseY)
    {
        if(!isOverPreview(mouseX, mouseY))
        {
            return -1;
        }
        int found = -1;
        double nearest = 25D;
        for(int i = 0; i < drawnPoints.length; i++)
        {
            double dx = drawnPoints[i][0] - mouseX;
            double dy = drawnPoints[i][1] - mouseY;
            if(dx * dx + dy * dy <= nearest)
            {
                nearest = dx * dx + dy * dy;
                found = i;
            }
        }
        return found;
    }

    @Override
    public void tick()
    {
        super.tick();
        updateButtons(); //the projector can be switched on while this is open
        if(!hasClicked)
        {
            rotateX += 2.5F;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        int point = button == 0 ? pointAt(mouseX, mouseY) : -1;
        if(point > 0)
        {
            select(point - 1);
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
            scale = Math.clamp(scale * (vertical > 0 ? 1.1F : 1F / 1.1F), 0.2F, 8F);
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
        int px = previewX();
        int py = previewY();
        int pw = previewW();
        int ph = previewH();
        Holo.inset(g, px, py, pw, ph);
        if(order.size() > 1)
        {
            List<Vector3f> points = points3d(order);
            Analysis analysis = analyse(flatten(points));
            g.enableScissor(px + 1, py + 1, px + pw - 1, py + ph - 1);
            drawPreview(g, points, analysis, partialTick, mouseX, mouseY);
            g.disableScissor();
            drawStats(g, analysis);
        }
        else
        {
            drawnPoints = new float[0][];
            g.drawCenteredString(font, Component.translatable("glass.ui.needTwo"), px + pw / 2, py + ph / 2 - 4, 0xFF3E6670);
        }
        if(isDirty())
        {
            g.drawString(font, Component.translatable("glass.ui.unsaved"), left + 10, top + panelH - 30, Holo.withAlpha(Holo.WARN, 0.6F + 0.4F * Holo.pulse(5F)), false);
        }
    }

    /** The projector's corner and then each extension's, relative to the projector, as the renderer places them. */
    private List<Vector3f> points3d(List<BlockPos> positions)
    {
        Direction face = master.placingFace;
        float pushback = 0.501F;
        BlockPos masterPos = master.getBlockPos();
        List<Vector3f> points = new ArrayList<>();
        points.add(new Vector3f(face.getStepX() * pushback, face.getStepY() * pushback, face.getStepZ() * pushback));
        for(BlockPos pos : positions)
        {
            float pX = face.getStepX() * pushback * (face.getStepX() > 0 && pos.getX() > masterPos.getX() ? -1F : 1F);
            float pY = face.getStepY() * pushback * (face.getStepY() > 0 && pos.getY() > masterPos.getY() ? -1F : 1F);
            float pZ = face.getStepZ() * pushback * (face.getStepZ() > 0 && pos.getZ() > masterPos.getZ() ? -1F : 1F);
            points.add(new Vector3f(pos.getX() - masterPos.getX() + pX, pos.getY() - masterPos.getY() + pY, pos.getZ() - masterPos.getZ() + pZ));
        }
        return points;
    }

    /** The points in the screen's own plane (the renderer's texture axes), for areas. */
    private float[][] flatten(List<Vector3f> points)
    {
        Direction face = master.placingFace;
        Vector3f n = new Vector3f(face.getStepX(), face.getStepY(), face.getStepZ());
        Vector3f u = face.getAxis() == Direction.Axis.Y ? new Vector3f(1F, 0F, 0F) : new Vector3f(0F, 1F, 0F);
        Vector3f v = new Vector3f(n).cross(u);
        float[][] flat = new float[points.size()][];
        for(int i = 0; i < points.size(); i++)
        {
            flat[i] = new float[] { points.get(i).dot(u), points.get(i).dot(v) };
        }
        return flat;
    }

    private Matrix4f rotation(float partialTick)
    {
        Direction face = master.placingFace;
        float spin = rotateX + (hasClicked ? 0F : 2.5F * partialTick);
        Matrix4f m = new Matrix4f();
        int horiOrient = (face.getAxis() == Direction.Axis.Y ? Direction.UP : face).getOpposite().get2DDataValue();
        m.rotateY((float)Math.toRadians((face.get3DDataValue() > 0 ? 180F : 0F) + -horiOrient * 90F));
        if(face.getAxis() == Direction.Axis.Y)
        {
            m.rotateX((float)Math.toRadians(face == Direction.UP ? -90F : 90F));
        }
        m.rotateY((float)Math.toRadians(spin));
        m.rotateZ((float)Math.toRadians(rotateY));
        return m;
    }

    private void drawPreview(GuiGraphics g, List<Vector3f> points3d, Analysis analysis, float partialTick, int mouseX, int mouseY)
    {
        int px = previewX();
        int py = previewY();
        int pw = previewW();
        int ph = previewH();

        //Fit the whole array in the box, whatever its size (the rotation is about the projector, so use the farthest corner).
        float reach = 1F;
        for(Vector3f p : points3d)
        {
            reach = Math.max(reach, p.length());
        }
        float pixels = Math.min(pw, ph) * 0.42F / reach * scale;
        Matrix4f rot = rotation(partialTick);
        Matrix4f m = new Matrix4f().translate(px + pw / 2F, py + ph / 2F, 0F).scale(-pixels, -pixels, pixels).mul(rot);

        float[][] points = new float[points3d.size()][];
        for(int i = 0; i < points.length; i++)
        {
            Vector3f v = m.transformPosition(new Vector3f(points3d.get(i)));
            points[i] = new float[] { v.x, v.y, i == 0 ? Holo.ACCENT : colourOf(order.get(i - 1)) };
        }
        drawnPoints = points;

        if(fill)
        {
            for(int i = 0; i + 2 < points.length; i++)
            {
                int colour = analysis.overlapping[i] ? Holo.withAlpha(Holo.ERROR, 0.25F) : Holo.withAlpha(Holo.ACCENT, 0.14F);
                fillTriangle(g, points[i], points[i + 1], points[i + 2], colour, py, py + ph);
            }
        }

        int selected = list.getSelectedIndex() + 1;
        for(int i = 0; i + 1 < points.length; i++)
        {
            for(int j = i + 1; j <= i + 2 && j < points.length; j++)
            {
                float alpha = selected <= 0 || i == selected || j == selected ? 0.85F : 0.3F;
                line(g, points[i], points[j], alpha);
            }
        }

        int hovered = pointAt(mouseX, mouseY);
        for(int i = 0; i < points.length; i++)
        {
            int x = Math.round(points[i][0]);
            int y = Math.round(points[i][1]);
            int colour = (int)points[i][2];
            g.fill(x - 1, y - 1, x + 2, y + 2, colour);
            if(i == selected || i == hovered)
            {
                int ring = i == selected ? Holo.withAlpha(colour, 0.5F + 0.5F * Holo.pulse(6F)) : Holo.withAlpha(colour, 0.6F);
                Holo.frame(g, x - 3, y - 3, 7, 7, ring);
            }
            g.drawString(font, i == 0 ? "P" : String.valueOf(i), x + 4, y - 9, Holo.withAlpha(colour, 0.9F), false);
        }

        if(hovered >= 0)
        {
            String text = hovered == 0 ? "P  " + posLabel(master.getBlockPos()) : "#" + hovered + "  " + posLabel(order.get(hovered - 1));
            g.drawString(font, text, px + 4, py + 4, Holo.TEXT, false);
        }
        else if(!hasClicked)
        {
            g.drawString(font, Component.translatable("glass.ui.preview.hint"), px + 4, py + ph - 11, 0xFF3E6670, false);
        }
        String zoom = Math.round(scale * 100) + "%";
        g.drawString(font, zoom, px + pw - 4 - font.width(zoom), py + 4, Holo.MUTED, false);
        drawAxes(g, rot, px + pw - 16, py + ph - 16);
    }

    /** A small X/Y/Z gizmo turning with the preview, so you can tell which way is which. */
    private void drawAxes(GuiGraphics g, Matrix4f rot, int cx, int cy)
    {
        Matrix4f m = new Matrix4f().translate(cx, cy, 0F).scale(-9F, -9F, 9F).mul(rot);
        float[] origin = { cx, cy, 0 };
        String[] names = { "X", "Y", "Z" };
        int[] colours = { 0xFFFF5C5C, 0xFF5CFF7A, 0xFF5C8CFF };
        for(int axis = 0; axis < 3; axis++)
        {
            Vector3f v = m.transformPosition(new Vector3f(axis == 0 ? 1F : 0F, axis == 1 ? 1F : 0F, axis == 2 ? 1F : 0F));
            float[] end = { v.x, v.y, colours[axis] };
            line(g, origin, end, 0.9F);
            g.drawString(font, names[axis], Math.round(v.x + (v.x - cx) * 0.4F) - 2, Math.round(v.y + (v.y - cy) * 0.4F) - 4, colours[axis], false);
        }
    }

    private void drawStats(GuiGraphics g, Analysis a)
    {
        int x = previewX();
        int y = previewY() + previewH() + 5;
        Component size = Component.translatable("glass.ui.stats.size", value(a.width), value(a.height), value(a.covered()));
        g.drawString(font, size, x, y, Holo.MUTED, false);

        boolean field = GlassProjectorBlockEntity.fieldCorners(order) != null;
        Component mesh = Component.translatable("glass.ui.stats.mesh",
                Component.literal(String.valueOf(a.triangles)).withColor(Holo.TEXT),
                Component.literal(String.valueOf(Math.round(a.coverage() * 100))).withColor(Holo.TEXT),
                Component.literal(field ? "✔" : "✘").withColor(field ? Holo.OK : 0xFF3E6670));
        g.drawString(font, mesh, x, y + 11, Holo.MUTED, false);

        if(a.overlap > EPSILON)
        {
            g.drawString(font, Component.translatable("glass.ui.stats.overlap", value(a.overlap)), x, y + 22, Holo.ERROR, false);
        }
        else
        {
            g.drawString(font, Component.translatable("glass.ui.stats.clean"), x, y + 22, Holo.OK, false);
        }
    }

    private static Component value(float f)
    {
        return Component.literal(String.format(Locale.ROOT, "%.1f", f)).withColor(Holo.TEXT);
    }

    /**
     * How the strip (0,1,2), (1,2,3), ... lies in the screen's plane: its size, the area its triangles cover, how much
     * of that is covered twice, and the area of the outline around all corners.
     */
    private record Analysis(float width, float height, int triangles, float sum, float overlap, float hull, boolean[] overlapping)
    {
        float covered()
        {
            return Math.max(0F, sum - overlap);
        }

        float coverage()
        {
            return hull <= EPSILON ? 0F : Math.min(1F, covered() / hull);
        }
    }

    private static Analysis analyse(float[][] verts)
    {
        float minU = Float.MAX_VALUE, maxU = -Float.MAX_VALUE, minV = Float.MAX_VALUE, maxV = -Float.MAX_VALUE;
        for(float[] p : verts)
        {
            minU = Math.min(minU, p[0]);
            maxU = Math.max(maxU, p[0]);
            minV = Math.min(minV, p[1]);
            maxV = Math.max(maxV, p[1]);
        }
        int triangles = Math.max(0, verts.length - 2);
        float[][][] tris = new float[triangles][][];
        float sum = 0F;
        for(int i = 0; i < triangles; i++)
        {
            tris[i] = new float[][] { verts[i], verts[i + 1], verts[i + 2] };
            sum += Math.abs(signedArea(tris[i]));
        }
        float overlap = 0F;
        boolean[] overlapping = new boolean[triangles];
        for(int i = 0; i < triangles; i++)
        {
            for(int j = i + 1; j < triangles; j++)
            {
                float shared = intersection(tris[i], tris[j]);
                if(shared > EPSILON)
                {
                    overlap += shared;
                    overlapping[i] = true;
                    overlapping[j] = true;
                }
            }
        }
        return new Analysis(maxU - minU, maxV - minV, triangles, sum, overlap, Math.abs(signedArea(hull(verts))), overlapping);
    }

    private static float signedArea(float[][] polygon)
    {
        float area = 0F;
        for(int i = 0; i < polygon.length; i++)
        {
            float[] a = polygon[i];
            float[] b = polygon[(i + 1) % polygon.length];
            area += a[0] * b[1] - b[0] * a[1];
        }
        return area / 2F;
    }

    /** The area two triangles share (clipping one by the other, both being convex). */
    private static float intersection(float[][] subject, float[][] clip)
    {
        float clipArea = signedArea(clip);
        if(Math.abs(clipArea) <= EPSILON || Math.abs(signedArea(subject)) <= EPSILON)
        {
            return 0F;
        }
        float side = Math.signum(clipArea);
        List<float[]> output = new ArrayList<>(List.of(subject));
        for(int e = 0; e < clip.length && !output.isEmpty(); e++)
        {
            float[] a = clip[e];
            float[] b = clip[(e + 1) % clip.length];
            List<float[]> input = output;
            output = new ArrayList<>();
            for(int i = 0; i < input.size(); i++)
            {
                float[] p = input.get(i);
                float[] q = input.get((i + 1) % input.size());
                float dp = side * cross(a, b, p);
                float dq = side * cross(a, b, q);
                if(dp >= 0F)
                {
                    output.add(p);
                }
                if((dp >= 0F) != (dq >= 0F))
                {
                    float t = dp / (dp - dq);
                    output.add(new float[] { p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t });
                }
            }
        }
        return output.size() < 3 ? 0F : Math.abs(signedArea(output.toArray(new float[0][])));
    }

    /** Positive if p is left of the line a to b. */
    private static float cross(float[] a, float[] b, float[] p)
    {
        return (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0]);
    }

    /** Convex hull (monotone chain). */
    private static float[][] hull(float[][] points)
    {
        float[][] sorted = points.clone();
        java.util.Arrays.sort(sorted, (a, b) -> a[0] != b[0] ? Float.compare(a[0], b[0]) : Float.compare(a[1], b[1]));
        float[][] hull = new float[sorted.length * 2][];
        int k = 0;
        for(float[] p : sorted)
        {
            while(k >= 2 && cross(hull[k - 2], hull[k - 1], p) <= 0F)
            {
                k--;
            }
            hull[k++] = p;
        }
        for(int i = sorted.length - 2, lower = k + 1; i >= 0; i--)
        {
            while(k >= lower && cross(hull[k - 2], hull[k - 1], sorted[i]) <= 0F)
            {
                k--;
            }
            hull[k++] = sorted[i];
        }
        return java.util.Arrays.copyOf(hull, Math.max(0, k - 1));
    }

    /** Fills a triangle row by row (GuiGraphics only fills rectangles), within the rows minY..maxY. */
    private static void fillTriangle(GuiGraphics g, float[] a, float[] b, float[] c, int colour, int minY, int maxY)
    {
        float[][] edges = { a, b, b, c, c, a };
        int from = Math.max(minY, (int)Math.floor(Math.min(a[1], Math.min(b[1], c[1]))));
        int to = Math.min(maxY, (int)Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))));
        for(int y = from; y < to; y++)
        {
            float sy = y + 0.5F;
            float xl = Float.MAX_VALUE;
            float xr = -Float.MAX_VALUE;
            for(int e = 0; e < 6; e += 2)
            {
                float[] p = edges[e];
                float[] q = edges[e + 1];
                if((p[1] <= sy && q[1] > sy) || (q[1] <= sy && p[1] > sy))
                {
                    float x = p[0] + (sy - p[1]) / (q[1] - p[1]) * (q[0] - p[0]);
                    xl = Math.min(xl, x);
                    xr = Math.max(xr, x);
                }
            }
            if(xl < xr)
            {
                g.fill(Math.round(xl), y, Math.round(xr), y + 1, colour);
            }
        }
    }

    private static void line(GuiGraphics g, float[] from, float[] to, float alpha)
    {
        int colour = Holo.withAlpha((int)to[2], alpha);
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
