package ryzlar.glass.client.holo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.client.render.GlassRenderTypes;
import ryzlar.glass.holo.DroneScan;
import ryzlar.glass.holo.HoloDroneEntity;
import ryzlar.glass.holo.HoloProjectorBlockEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws a holo drone lying on the ground (a little core, slowly turning while it projects) and its room: a solid 1:1
 * simulation laid around it. The nano wave grows slowly out of the drone, and shrinks back into it when switched off.
 */
public class DroneRenderer extends EntityRenderer<HoloDroneEntity, DroneRenderer.State>
{
    private static final float DRONE_SCALE = 0.4F;
    /** Where the wave starts, from the drone's block corner: the drone itself, on the floor. */
    private static final Vec3 WAVE_ORIGIN = new Vec3(0.5D, 0.2D, 0.5D);

    public static class State extends EntityRenderState
    {
        int entityId;
        BlockPos anchor = BlockPos.ZERO;
        int turn;
        float radius;
        float brightness = 1F;
        float spin;
        @Nullable
        DroneScan scan;
        @Nullable
        HoloDroneEntity entity;
    }

    private record Entry(String key, HologramRenderer.Hologram hologram) {}

    private final Map<Integer, Entry> holograms = new HashMap<>();

    public DroneRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(HoloDroneEntity entity, State state, float partialTick)
    {
        super.extractRenderState(entity, state, partialTick);
        state.entityId = entity.getId();
        state.anchor = entity.anchor();
        state.turn = entity.turn();
        state.scan = entity.scan();
        state.entity = entity;
        state.radius = entity.waveRadius(partialTick);
        //Lit like a block where the drone lies (day, night, torches).
        state.brightness = 0.25F + 0.75F * entity.level().getMaxLocalRawBrightness(entity.anchor()) / 15F;
        state.spin = entity.getYRot() + (entity.projecting() ? (entity.level().getGameTime() + partialTick) * 1.5F : 0F);
    }

    /** The room is big and the drone small: keep drawing while the room may be on screen. */
    @Override
    public boolean shouldRender(HoloDroneEntity entity, Frustum frustum, double x, double y, double z)
    {
        return entity.shouldRender(x, y, z);
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffers, int light)
    {
        //The drone: a little core on the ground.
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.spin));
        poseStack.scale(DRONE_SCALE, DRONE_SCALE, DRONE_SCALE);
        poseStack.translate(-0.5D, 0D, -0.5D);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.HEAVY_CORE.defaultBlockState(), poseStack, buffers, light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        ClientLevel level = Minecraft.getInstance().level;
        if(state.scan == null || state.radius <= 0F || level == null)
        {
            return;
        }
        HoloClient.ClientProjection projection = HoloClient.projection(state.scan.id());
        if(projection == null)
        {
            return;
        }
        HologramRenderer.Hologram hologram = hologram(level, state, projection);
        if(state.entity != null)
        {
            state.entity.clientReach = hologram.reach;
        }
        poseStack.pushPose();
        poseStack.translate(state.anchor.getX() - state.x, state.anchor.getY() - state.y, state.anchor.getZ() - state.z);
        hologram.replay(buffers.getBuffer(GlassRenderTypes.hologram(HoloProjectorBlockEntity.Look.SOLID.ordinal())), poseStack.last(), state.radius, state.brightness);
        poseStack.popPose();
    }

    /** The room laid around the drone's block: where the scanner stood goes there, turned the way the player faced. */
    private HologramRenderer.Hologram hologram(ClientLevel level, State state, HoloClient.ClientProjection projection)
    {
        DroneScan scan = state.scan;
        String key = scan.id() + "|" + state.anchor.asLong() + "|" + state.turn;
        Entry entry = holograms.get(state.entityId);
        if(entry == null || !entry.key().equals(key))
        {
            if(holograms.size() > 16)
            {
                holograms.clear();
            }
            HologramRenderer.Placement placement = new HologramRenderer.Placement(scan.originX() + 0.5F, scan.originY(), scan.originZ() + 0.5F, state.turn, 1F, new Vec3(0.5D, 0D, 0.5D));
            HologramRenderer.Hologram hologram = HologramRenderer.buildMesh(level, projection, placement, null, WAVE_ORIGIN, state.anchor);
            entry = new Entry(key, hologram);
            holograms.put(state.entityId, entry);
        }
        return entry.hologram();
    }
}
