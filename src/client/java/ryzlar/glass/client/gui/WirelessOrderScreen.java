package ryzlar.glass.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import ryzlar.glass.Glass;
import ryzlar.glass.GlassRegistry;
import ryzlar.glass.blockentity.GlassProjectorBlockEntity;
import ryzlar.glass.network.GlassNetworking;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Orders the wireless extensions a projector draws its floating screen between, with a small rotating preview.
 * Port of GuiWirelessOrder. (1.21 GUIs are flat, so the preview is drawn as projected lines instead of a 3D triangle strip.)
 */
public class WirelessOrderScreen extends Screen
{
    public static final ResourceLocation TEX_BACKGROUND = Glass.id("textures/gui/wireless_order.png");

    private final int xSize = 170;
    private final int ySize = 75;
    private int guiLeft;
    private int guiTop;

    private final GlassProjectorBlockEntity master;
    private List<BlockPos> channels;
    private MiniList<BlockPos> list;

    private float rotateX = 0F;
    private float rotateY = 0F;
    private float scale = 1F;
    private boolean hasClicked = false;

    public WirelessOrderScreen(GlassProjectorBlockEntity master)
    {
        super(Component.translatable("glass.gui.wirelessExtensionOrder"));
        this.master = master;
    }

    @Override
    protected void init()
    {
        guiLeft = (width - xSize) / 2;
        guiTop = (height - ySize) / 2;

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> confirmSelection()).bounds(guiLeft + (xSize - 50) / 2, guiTop + ySize + 2, 50, 20).build());
        addRenderableWidget(Button.builder(Component.empty(), b -> minecraft.setScreen(new ProjectorChannelScreen(master)))
                .bounds(guiLeft + xSize + 1, guiTop, 20, 20)
                .tooltip(Tooltip.create(Component.translatable("glass.gui.channelsButton")))
                .build());
        addRenderableWidget(Button.builder(Component.literal("^"), b -> move(-1)).bounds(guiLeft + xSize + 1, guiTop + 21, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("v"), b -> move(1)).bounds(guiLeft + xSize + 1, guiTop + 41, 20, 20).build());

        if(channels == null)
        {
            channels = new ArrayList<>(master.wirelessPos);
        }
        int selected = list != null ? list.selected : -1;
        list = new MiniList<>(guiLeft + 5, guiTop + 11, 93 - 10, ySize - 16, channels, WirelessOrderScreen::posLabel, i -> colourOf(channels.get(i)));
        list.selected = selected;
    }

    private static String posLabel(BlockPos pos)
    {
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    /** Port of iChunUtil's RendererHelper.getRandomColourFromString. */
    private static int colourOf(BlockPos pos)
    {
        return 0xFF000000 | new Random(posLabel(pos).hashCode()).nextInt(0xFFFFFF);
    }

    private void move(int by)
    {
        int index = list.selected;
        int target = index + by;
        if(index >= 0 && index < channels.size() && target >= 0 && target < channels.size())
        {
            BlockPos pos = channels.remove(index);
            channels.add(target, pos);
            list.selected = target;
        }
    }

    private void confirmSelection()
    {
        ClientPlayNetworking.send(new GlassNetworking.WirelessOrder(master.getBlockPos(), List.copyOf(channels)));
        onClose();
    }

    private boolean isOverPreview(double mouseX, double mouseY)
    {
        return mouseX > guiLeft + 100 && mouseX < guiLeft + 165 && mouseY > guiTop + 5 && mouseY < guiTop + 70;
    }

    @Override
    public void tick()
    {
        if(!hasClicked)
        {
            rotateX += 2.5F;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        if(keyCode == GLFW.GLFW_KEY_DELETE && list.selected >= 0 && list.selected < channels.size())
        {
            channels.remove(list.selected);
            list.selected--;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if(list.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY)
    {
        if(button == 0 && channels.size() > 1 && isOverPreview(mouseX, mouseY))
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
        return list.mouseScrolled(mouseX, mouseY, vertical) || super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEX_BACKGROUND, guiLeft, guiTop, 0F, 0F, xSize, ySize, 256, 256);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        super.render(graphics, mouseX, mouseY, partialTick);
        list.render(graphics, font);

        graphics.fill(guiLeft + 100, guiTop + 5, guiLeft + 165, guiTop + 70, 0xFF444444);
        if(channels.size() > 1)
        {
            graphics.enableScissor(guiLeft + 100, guiTop + 5, guiLeft + 165, guiTop + 70);
            drawPreview(graphics, partialTick);
            graphics.disableScissor();
        }

        graphics.renderItem(new ItemStack(GlassRegistry.GLASS_PROJECTOR), guiLeft + xSize + 3, guiTop + 2);

        graphics.pose().pushMatrix();
        graphics.pose().translate(guiLeft + 5, guiTop + 5);
        graphics.pose().scale(0.5F, 0.5F);
        graphics.drawString(font, Component.translatable("glass.gui.wirelessExtensionOrder"), 0, 0, 0xFFFFFFFF, true);
        graphics.pose().popMatrix();
    }

    private void drawPreview(GuiGraphics graphics, float partialTick)
    {
        Direction face = master.placingFace;
        float spin = rotateX + (hasClicked ? 0F : 2.5F * partialTick);

        Matrix4f m = new Matrix4f().translate(guiLeft + 135, guiTop + 40, 0F).scale(-5F * scale, -5F * scale, 5F * scale);
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
        points.add(project(m, face.getStepX() * pushback, face.getStepY() * pushback, face.getStepZ() * pushback, 0xFFFFFFFF));
        for(BlockPos pos : channels)
        {
            float pX = face.getStepX() * pushback * (face.getStepX() > 0 && pos.getX() > masterPos.getX() ? -1F : 1F);
            float pY = face.getStepY() * pushback * (face.getStepY() > 0 && pos.getY() > masterPos.getY() ? -1F : 1F);
            float pZ = face.getStepZ() * pushback * (face.getStepZ() > 0 && pos.getZ() > masterPos.getZ() ? -1F : 1F);
            points.add(project(m, pos.getX() - masterPos.getX() + pX, pos.getY() - masterPos.getY() + pY, pos.getZ() - masterPos.getZ() + pZ, colourOf(pos)));
        }
        //Outline of the triangle strip: every point joins the next two.
        for(int i = 0; i + 1 < points.size(); i++)
        {
            line(graphics, points.get(i), points.get(i + 1));
            if(i + 2 < points.size())
            {
                line(graphics, points.get(i), points.get(i + 2));
            }
        }
    }

    private static float[] project(Matrix4f m, float x, float y, float z, int colour)
    {
        Vector3f v = m.transformPosition(new Vector3f(x, y, z));
        return new float[] { v.x, v.y, colour };
    }

    private static void line(GuiGraphics graphics, float[] from, float[] to)
    {
        int colour = (int)to[2];
        float dx = to[0] - from[0];
        float dy = to[1] - from[1];
        int steps = (int)Math.min(200, Math.max(Math.abs(dx), Math.abs(dy)));
        for(int s = 0; s <= steps; s++)
        {
            float t = steps == 0 ? 0F : (float)s / steps;
            int x = Math.round(from[0] + dx * t);
            int y = Math.round(from[1] + dy * t);
            graphics.fill(x, y, x + 1, y + 1, colour);
        }
    }

    @Override
    public boolean isPauseScreen()
    {
        return true;
    }
}
