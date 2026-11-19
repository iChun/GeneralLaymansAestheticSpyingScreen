package ryzlar.glass.client.gadget;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.Glass;
import ryzlar.glass.GlassSounds;
import ryzlar.glass.blockentity.GlassProjectorBlockEntity;
import ryzlar.glass.client.GlassClientState;
import ryzlar.glass.client.gui.holo.Holo;
import ryzlar.glass.gadget.GadgetNetworking;
import ryzlar.glass.gadget.Gadgets;
import ryzlar.glass.gadget.HardLightBlock;

import java.util.HashMap;
import java.util.Map;

/** The client side of the gadgets: their renderers, the tablet's cameras, motion alerts and optical camo. */
public final class GadgetsClient
{
    /** Where a channel's camera is, as the server last said (for tablets). */
    public record Camera(boolean ok, String dimension, BlockPos pos, Direction facing) {}

    private static final Map<String, Camera> CAMERAS = new HashMap<>();
    /** Channel id: until when (game time) its screens flash ALERT. */
    private static final Map<String, Long> ALERTS = new HashMap<>();
    /** Entity id: until when (game time) it wears optical camo. */
    private static final Map<Integer, Long> CAMO = new HashMap<>();
    private static int bumpCooldown;

    private GadgetsClient() {}

    public static void init()
    {
        BlockRenderLayerMap.putBlock(Gadgets.HARD_LIGHT, ChunkSectionLayer.TRANSLUCENT);
        BlockRenderLayerMap.putBlocks(ChunkSectionLayer.CUTOUT, Gadgets.HARD_LIGHT_EMITTER, Gadgets.MOTION_SENSOR, Gadgets.HOLO_MAP_TABLE);
        BlockEntityRenderers.register(Gadgets.HOLO_MAP_TABLE_BE, HoloMapRenderer::new);
        EntityRendererRegistry.register(Gadgets.HOLO_DECOY_ENTITY, HoloDecoyRenderer::new);

        ClientPlayNetworking.registerGlobalReceiver(GadgetNetworking.CameraAt.TYPE, (payload, context) ->
                CAMERAS.put(payload.channelId(), new Camera(payload.ok(), payload.dimension(), payload.pos(), payload.facing())));
        ClientPlayNetworking.registerGlobalReceiver(GadgetNetworking.ChannelAlert.TYPE, (payload, context) -> alert(payload.channelId(), payload.ticks()));
        ClientPlayNetworking.registerGlobalReceiver(GadgetNetworking.CamoState.TYPE, (payload, context) -> {
            Minecraft mc = Minecraft.getInstance();
            if(mc.level == null)
            {
                return;
            }
            if(payload.ticks() <= 0)
            {
                CAMO.remove(payload.entityId());
            }
            else
            {
                CAMO.put(payload.entityId(), mc.level.getGameTime() + payload.ticks());
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(GadgetsClient::tick);
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            net.minecraft.ChatFormatting grey = net.minecraft.ChatFormatting.GRAY;
            if(stack.is(Gadgets.GLASS_TABLET))
            {
                ryzlar.glass.gadget.TabletLink link = stack.get(Gadgets.TABLET_LINK);
                lines.add(link == null ? Component.translatable("glass.tooltip.tablet.empty").withStyle(grey)
                        : Component.translatable("glass.tooltip.tablet.linked", link.channelName()).withStyle(net.minecraft.ChatFormatting.AQUA));
            }
            else if(stack.is(Gadgets.HOLO_DECOY))
            {
                lines.add(Component.translatable("glass.tooltip.decoy").withStyle(grey));
            }
            else if(stack.is(Gadgets.OPTICAL_CAMO_MODULE))
            {
                lines.add(Component.translatable("glass.tooltip.camo").withStyle(grey));
            }
            else if(stack.is(Gadgets.HARD_LIGHT_EMITTER.asItem()))
            {
                lines.add(Component.translatable("glass.tooltip.emitter").withStyle(grey));
            }
            else if(stack.is(Gadgets.MOTION_SENSOR.asItem()))
            {
                lines.add(Component.translatable("glass.tooltip.sensor").withStyle(grey));
            }
            else if(stack.is(Gadgets.HOLO_MAP_TABLE.asItem()))
            {
                lines.add(Component.translatable("glass.tooltip.mapTable").withStyle(grey));
            }
        });
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Glass.id("optical_camo"), (graphics, tickCounter) -> drawCamoHud(graphics));
    }

    public static void reset()
    {
        CAMERAS.clear();
        ALERTS.clear();
        CAMO.clear();
    }

    public static void openTablet(InteractionHand hand)
    {
        Minecraft.getInstance().setScreen(new TabletScreen(hand));
    }

    @Nullable
    public static Camera camera(String channelId)
    {
        return CAMERAS.get(channelId);
    }

    public static void forgetCamera(String channelId)
    {
        CAMERAS.remove(channelId);
    }

    // ---- Motion alerts ----

    private static void alert(String channelId, int ticks)
    {
        Minecraft mc = Minecraft.getInstance();
        if(mc.level == null)
        {
            return;
        }
        long now = mc.level.getGameTime();
        Long until = ALERTS.get(channelId);
        boolean fresh = until == null || until < now;
        ALERTS.put(channelId, now + ticks);
        if(fresh)
        {
            //The alarm, at every screen showing that channel.
            for(GlassProjectorBlockEntity projector : GlassClientState.INSTANCE.loadedProjectors)
            {
                if(projector.active && channelId.equals(projector.channelId) && !projector.cloak)
                {
                    BlockPos pos = projector.getBlockPos();
                    mc.level.playLocalSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, GlassSounds.ALERT, SoundSource.BLOCKS, 0.8F, 1F, false);
                }
            }
        }
    }

    /** How strongly a channel's screens flash ALERT now: 0 when there is no alert, pulsing between about 0.3 and 1. */
    public static float alertFlash(String channelId, float partialTick)
    {
        Minecraft mc = Minecraft.getInstance();
        Long until = ALERTS.get(channelId);
        if(until == null || mc.level == null || channelId.isEmpty())
        {
            return 0F;
        }
        float left = until - mc.level.getGameTime() - partialTick;
        if(left <= 0F)
        {
            return 0F;
        }
        float time = mc.level.getGameTime() + partialTick;
        return Math.min(1F, left / 10F) * (0.65F + 0.35F * (float)Math.sin(time * 0.9F));
    }

    // ---- Optical camo ----

    /** True while this player wears camo and moves (then they shimmer faintly for others). */
    public static boolean shimmers(Entity entity)
    {
        Minecraft mc = Minecraft.getInstance();
        Long until = CAMO.get(entity.getId());
        if(until == null || mc.level == null || until < mc.level.getGameTime())
        {
            return false;
        }
        Vec3 motion = entity.getDeltaMovement();
        return motion.x * motion.x + motion.z * motion.z > 0.0004D || Math.abs(entity.getX() - entity.xo) + Math.abs(entity.getZ() - entity.zo) > 0.01D;
    }

    private static void drawCamoHud(GuiGraphics g)
    {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if(player == null || mc.options.hideGui)
        {
            return;
        }
        MobEffectInstance camo = player.getEffect(Gadgets.OPTICAL_CAMO);
        if(camo == null)
        {
            return;
        }
        int w = g.guiWidth(), h = g.guiHeight();
        float seconds = camo.getDuration() / 20F;
        float pulse = 0.6F + 0.4F * (float)Math.sin(Holo.seconds() * 3F);
        //A cyan glow round the edge of the view, breathing.
        for(int i = 0; i < 12; i++)
        {
            int alpha = (int)((12 - i) * 5 * pulse);
            int colour = alpha << 24 | 0x3CE6FF;
            g.fill(i, i, w - i, i + 1, colour);
            g.fill(i, h - i - 1, w - i, h - i, colour);
            g.fill(i, i + 1, i + 1, h - i - 1, colour);
            g.fill(w - i - 1, i + 1, w - i, h - i - 1, colour);
        }
        Component text = Component.translatable("glass.hud.camo", String.format("%.1f", seconds));
        g.drawCenteredString(mc.font, text, w / 2, 12, Holo.withAlpha(Holo.ACCENT, 0.7F + 0.3F * pulse));
    }

    // ---- Hard light ----

    /** Walking into hard light ripples it where you touch it. */
    private static void tick(Minecraft mc)
    {
        if(mc.level == null || mc.player == null)
        {
            return;
        }
        long now = mc.level.getGameTime();
        ALERTS.values().removeIf(until -> until < now - 40L);
        CAMO.values().removeIf(until -> until < now);
        if(bumpCooldown > 0)
        {
            bumpCooldown--;
            return;
        }
        Player player = mc.player;
        if(!player.horizontalCollision)
        {
            return;
        }
        Vec3 look = Vec3.directionFromRotation(0F, player.getYRot());
        for(double up : new double[] { 0.3D, 1.2D })
        {
            Vec3 probe = player.position().add(look.x * 0.5D, up, look.z * 0.5D);
            BlockPos pos = BlockPos.containing(probe);
            if(HardLightBlock.isHardLight(mc.level.getBlockState(pos)))
            {
                Vec3 contact = new Vec3(Math.clamp(probe.x, pos.getX(), pos.getX() + 1D), probe.y, Math.clamp(probe.z, pos.getZ(), pos.getZ() + 1D));
                HardLightBlock.ripple(mc.level, contact, 0.7F);
                bumpCooldown = 12;
                return;
            }
        }
    }
}
