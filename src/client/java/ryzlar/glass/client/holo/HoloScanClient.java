package ryzlar.glass.client.holo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ryzlar.glass.Glass;
import ryzlar.glass.GlassRegistry;
import ryzlar.glass.client.gui.holo.Holo;
import ryzlar.glass.client.render.GlassRenderTypes;
import ryzlar.glass.holo.HoloNetworking;
import ryzlar.glass.holo.HoloScanSessions;

/**
 * The scanning player's view of a room scan: LiDAR dots appearing where they look (the client casts its own rays for
 * these; what goes into the scan is decided by the server), and the progress above the hotbar.
 */
public final class HoloScanClient
{
    private static final int MAX_DOTS = 9000;
    private static final int RAYS_PER_TICK = 30;
    private static final double SPREAD = 0.45D;
    private static final float DOT_SIZE = 0.035F;
    /** How long the dots stay after the scan ends, in ticks. */
    private static final int FADE_TICKS = 60;

    private static boolean active;
    private static float progress;
    private static BlockPos min = BlockPos.ZERO;
    private static BlockPos max = BlockPos.ZERO;
    private static long endedAt = Long.MIN_VALUE / 2;

    //The dots, as a ring buffer: position, face, and the tick they appeared.
    private static final float[] dotX = new float[MAX_DOTS];
    private static final float[] dotY = new float[MAX_DOTS];
    private static final float[] dotZ = new float[MAX_DOTS];
    private static final byte[] dotFace = new byte[MAX_DOTS];
    private static final long[] dotTime = new long[MAX_DOTS];
    private static int dotCount;
    private static int dotNext;
    private static final RandomSource RANDOM = RandomSource.create();

    private HoloScanClient() {}

    public static void init()
    {
        ClientPlayNetworking.registerGlobalReceiver(HoloNetworking.ScanStatus.TYPE, (payload, context) -> {
            boolean wasActive = active;
            active = payload.active();
            progress = payload.progress();
            if(payload.active())
            {
                if(!wasActive)
                {
                    dotCount = 0;
                    dotNext = 0;
                }
                min = payload.min();
                max = payload.max();
            }
            else if(wasActive && context.client().level != null)
            {
                endedAt = context.client().level.getGameTime();
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(HoloScanClient::tick);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(context -> {
            if(context.consumers() != null)
            {
                drawDots(context.matrixStack(), context.consumers().getBuffer(GlassRenderTypes.planes(false)), context.camera().getPosition());
            }
        });
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Glass.id("holo_scan"), (graphics, tickCounter) -> drawHud(graphics));
    }

    public static void reset()
    {
        active = false;
        dotCount = 0;
        dotNext = 0;
    }

    public static boolean isScanning()
    {
        return active;
    }

    private static boolean holdingEmptyDrone(Player player)
    {
        for(ItemStack stack : new ItemStack[] { player.getMainHandItem(), player.getOffhandItem() })
        {
            if(stack.is(GlassRegistry.HOLO_DRONE) && stack.get(GlassRegistry.DRONE_SCAN) == null)
            {
                return true;
            }
        }
        return false;
    }

    private static void tick(Minecraft mc)
    {
        if(!active || mc.player == null || mc.level == null || !holdingEmptyDrone(mc.player))
        {
            return;
        }
        Vec3 eye = mc.player.getEyePosition();
        float yaw = mc.player.getYRot() * Mth.DEG_TO_RAD;
        float pitch = mc.player.getXRot() * Mth.DEG_TO_RAD;
        long now = mc.level.getGameTime();
        for(int i = 0; i < RAYS_PER_TICK; i++)
        {
            double a = Math.sqrt(RANDOM.nextDouble()) * SPREAD;
            double b = RANDOM.nextDouble() * Math.PI * 2D;
            Vec3 direction = HoloScanSessions.direction(yaw + (float)(Math.cos(b) * a), pitch + (float)(Math.sin(b) * a));
            BlockHitResult hit = mc.level.clip(new ClipContext(eye, eye.add(direction.scale(24D)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
            if(hit.getType() != HitResult.Type.BLOCK)
            {
                continue;
            }
            BlockPos pos = hit.getBlockPos();
            if(pos.getX() < min.getX() || pos.getY() < min.getY() || pos.getZ() < min.getZ() || pos.getX() > max.getX() || pos.getY() > max.getY() || pos.getZ() > max.getZ())
            {
                continue;
            }
            Vec3 at = hit.getLocation();
            dotX[dotNext] = (float)at.x;
            dotY[dotNext] = (float)at.y;
            dotZ[dotNext] = (float)at.z;
            dotFace[dotNext] = (byte)hit.getDirection().ordinal();
            dotTime[dotNext] = now;
            dotNext = (dotNext + 1) % MAX_DOTS;
            dotCount = Math.min(MAX_DOTS, dotCount + 1);
        }
    }

    /** Each dot is a tiny square on the surface it hit: white when new, settling to cyan. */
    private static void drawDots(PoseStack poseStack, VertexConsumer consumer, Vec3 camera)
    {
        Minecraft mc = Minecraft.getInstance();
        if(dotCount == 0 || mc.level == null)
        {
            return;
        }
        long now = mc.level.getGameTime();
        float fade = active ? 1F : 1F - Math.clamp((now - endedAt) / (float)FADE_TICKS, 0F, 1F);
        if(fade <= 0F)
        {
            dotCount = 0;
            return;
        }
        PoseStack.Pose pose = poseStack.last();
        for(int i = 0; i < dotCount; i++)
        {
            Direction face = Direction.values()[dotFace[i]];
            float age = Math.clamp((now - dotTime[i]) / 10F, 0F, 1F);
            float r = Mth.lerp(age, 1F, 0.24F);
            float g = Mth.lerp(age, 1F, 0.9F);
            float a = fade * (0.95F - age * 0.25F);
            float cx = (float)(dotX[i] - camera.x) + face.getStepX() * 0.01F;
            float cy = (float)(dotY[i] - camera.y) + face.getStepY() * 0.01F;
            float cz = (float)(dotZ[i] - camera.z) + face.getStepZ() * 0.01F;
            //Two directions along the surface: (u along x or z, v along y or z).
            float ux = face.getAxis() == Direction.Axis.X ? 0F : DOT_SIZE;
            float uz = face.getAxis() == Direction.Axis.X ? DOT_SIZE : 0F;
            float vy = face.getAxis() == Direction.Axis.Y ? 0F : DOT_SIZE;
            float vz = face.getAxis() == Direction.Axis.Y ? DOT_SIZE : 0F;
            consumer.addVertex(pose, cx - ux, cy - vy, cz - uz - vz).setColor(r, g, 1F, a);
            consumer.addVertex(pose, cx + ux, cy - vy, cz + uz - vz).setColor(r, g, 1F, a);
            consumer.addVertex(pose, cx + ux, cy + vy, cz + uz + vz).setColor(r, g, 1F, a);
            consumer.addVertex(pose, cx - ux, cy + vy, cz - uz + vz).setColor(r, g, 1F, a);
        }
    }

    private static void drawHud(GuiGraphics graphics)
    {
        Minecraft mc = Minecraft.getInstance();
        if(!active || mc.player == null || mc.options.hideGui)
        {
            return;
        }
        int width = graphics.guiWidth();
        //Above the hearts and the held item's name.
        int y = graphics.guiHeight() - 96;
        Component title = Component.translatable("glass.ui.drone.scanning", Math.round(progress * 100F));
        graphics.drawString(mc.font, title, (width - mc.font.width(title)) / 2, y, Holo.ACCENT, true);
        int barW = 120;
        int x = (width - barW) / 2;
        graphics.fill(x, y + 11, x + barW, y + 14, 0x80031018);
        graphics.fill(x, y + 11, x + Math.round(barW * Math.min(1F, progress / HoloScanSessions.DONE_AT)), y + 14, Holo.ACCENT);
        Component hint = Component.translatable("glass.ui.drone.hint");
        graphics.drawString(mc.font, hint, (width - mc.font.width(hint)) / 2, y + 18, Holo.MUTED, true);
    }
}
