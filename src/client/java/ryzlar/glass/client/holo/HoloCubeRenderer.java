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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.client.render.GlassRenderTypes;
import ryzlar.glass.holo.CubeScan;
import ryzlar.glass.holo.HoloCubeEntity;
import ryzlar.glass.holo.HoloProjectorBlockEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws a holo cube on the ground (its Blockbench model: it rises a little while it projects, its frame turning and its core
 * breathing) and its room: a solid 1:1
 * simulation laid around it. The nano wave grows slowly out of the cube, and shrinks back into it when switched off. The
 * room's edge falls apart into blue nano pixels (glass_holo.fsh) and sheds little blue shards, so it is plain where the
 * simulation ends.
 */
public class HoloCubeRenderer extends EntityRenderer<HoloCubeEntity, HoloCubeRenderer.State>
{
    private static final float CUBE_SCALE = 0.55F;
    /** The cube model's bottom, from its centre, in blocks (it spans 3..13 of 16). */
    private static final float CUBE_BOTTOM = 5F / 16F;
    /** How high it floats while projecting, in blocks, and how long it takes to rise or settle, in ticks. */
    private static final float HOVER = 0.35F;
    private static final float HOVER_TICKS = 20F;
    /** Where the wave starts, from the cube's block corner: the cube itself, floating above the floor. */
    private static final Vec3 WAVE_ORIGIN = new Vec3(0.5D, 0.55D, 0.5D);

    public static class State extends EntityRenderState
    {
        int entityId;
        BlockPos anchor = BlockPos.ZERO;
        int turn;
        float radius;
        float brightness = 1F;
        float spin;
        @Nullable
        CubeScan scan;
        @Nullable
        HoloCubeEntity entity;
        final net.minecraft.client.renderer.item.ItemStackRenderState frame = new net.minecraft.client.renderer.item.ItemStackRenderState();
        final net.minecraft.client.renderer.item.ItemStackRenderState core = new net.minecraft.client.renderer.item.ItemStackRenderState();
        float hover;
        float coreSpin;
        float pulse;
        float time;
        boolean scanning;
        boolean showBounds;
        float scanRadius;
        float scanFade;
        float scanFlash;
        @Nullable
        ryzlar.glass.holo.RoomScan.Room room;
        long scanStart;
    }

    /** How long the scan's light takes to fade once the scan is done, and how long its flash lasts (ticks). */
    private static final float SCAN_FADE_TICKS = 30F;
    private static final float SCAN_FLASH_TICKS = 12F;
    private static final int BEAMS = 6;
    private final Map<Integer, ScanEffect> scans = new HashMap<>();

    /** The cube's parts, drawn from their own models (made in Blockbench): the frame, and the core turning inside it. */
    private static final net.minecraft.world.item.ItemStack FRAME = part("holo_cube_frame");
    private static final net.minecraft.world.item.ItemStack CORE = part("holo_cube_core");
    private final net.minecraft.client.renderer.item.ItemModelResolver itemModels;

    private static net.minecraft.world.item.ItemStack part(String model)
    {
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(ryzlar.glass.GlassRegistry.HOLO_CUBE);
        stack.set(net.minecraft.core.component.DataComponents.ITEM_MODEL, ryzlar.glass.Glass.id(model));
        return stack;
    }

    private record Entry(String key, HologramRenderer.Hologram hologram) {}

    private final Map<Integer, Entry> holograms = new HashMap<>();

    public HoloCubeRenderer(EntityRendererProvider.Context context)
    {
        super(context);
        itemModels = context.getItemModelResolver();
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(HoloCubeEntity entity, State state, float partialTick)
    {
        super.extractRenderState(entity, state, partialTick);
        state.entityId = entity.getId();
        state.anchor = entity.anchor();
        state.turn = entity.turn();
        state.scan = entity.scan();
        state.entity = entity;
        state.radius = entity.waveRadius(partialTick);
        //Lit like a block where the cube lies (day, night, torches).
        state.brightness = 0.25F + 0.75F * entity.level().getMaxLocalRawBrightness(entity.anchor()) / 15F;
        long now = entity.level().getGameTime();
        float time = now + partialTick;
        state.time = time;
        //Projecting or scanning, it rises off the ground and its frame turns; done, it settles back down and stops.
        float up;
        if(entity.projecting())
        {
            up = (time - entity.clientOnStart) / HOVER_TICKS;
        }
        else if(entity.scanning())
        {
            up = (time - entity.clientScanStart) / HOVER_TICKS;
        }
        else
        {
            up = 1F - (time - Math.max(entity.clientOffStart, entity.clientScanEnd)) / HOVER_TICKS;
        }
        float x = Math.clamp(up, 0F, 1F);
        state.hover = x * x * (3F - 2F * x);
        float turned = entity.projecting() ? time - entity.clientOnStart : entity.clientOffStart - entity.clientOnStart;
        state.spin = entity.getYRot() + (entity.clientOnStart > Long.MIN_VALUE / 4 ? Math.max(0F, turned) * 1.2F : 0F);
        //The core turns the other way all the time, and breathes while it projects; scanning, it all whirls.
        state.coreSpin = -time * 2.5F;
        state.pulse = 1F + state.hover * 0.08F * (float)Math.sin(time * 0.2F);
        state.scanning = entity.scanning();
        //Looking at a cube that has not scanned yet: show how far its scan can reach.
        state.showBounds = entity.scan() == null && !entity.scanning() && Minecraft.getInstance().crosshairPickEntity == entity;
        state.room = entity.clientRoom;
        state.scanStart = entity.clientScanStart;
        state.scanRadius = entity.scanRadius(partialTick);
        if(state.scanning)
        {
            float whirl = time - entity.clientScanStart;
            state.spin += whirl * 9F;
            state.coreSpin = -time * 14F;
            state.pulse = 1F + 0.16F * (float)Math.sin(time * 0.8F);
            state.scanFade = 1F;
            state.scanFlash = 0F;
        }
        else
        {
            float since = time - entity.clientScanEnd;
            boolean done = entity.clientScanEnd >= entity.clientScanStart;
            state.scanFade = done ? Math.clamp(1F - since / SCAN_FADE_TICKS, 0F, 1F) : 0F;
            state.scanFlash = done ? Math.clamp(1F - since / SCAN_FLASH_TICKS, 0F, 1F) : 0F;
        }
        itemModels.updateForNonLiving(state.frame, FRAME, net.minecraft.world.item.ItemDisplayContext.NONE, entity);
        itemModels.updateForNonLiving(state.core, CORE, net.minecraft.world.item.ItemDisplayContext.NONE, entity);
    }

    /** The room is big and the cube small: keep drawing while the room may be on screen. */
    @Override
    public boolean shouldRender(HoloCubeEntity entity, Frustum frustum, double x, double y, double z)
    {
        return entity.shouldRender(x, y, z);
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffers, int light)
    {
        //The cube on the ground (item models are drawn centred on the origin), floating up a little while it projects.
        float bob = state.hover * 0.03F * (float)Math.sin(state.ageInTicks * 0.1F);
        poseStack.pushPose();
        poseStack.translate(0D, CUBE_BOTTOM * CUBE_SCALE + state.hover * HOVER + bob, 0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.spin));
        poseStack.scale(CUBE_SCALE, CUBE_SCALE, CUBE_SCALE);
        state.frame.render(poseStack, buffers, 0xF000F0, OverlayTexture.NO_OVERLAY);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.coreSpin));
        poseStack.scale(state.pulse, state.pulse, state.pulse);
        state.core.render(poseStack, buffers, 0xF000F0, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        drawScan(state, poseStack, buffers, CUBE_BOTTOM * CUBE_SCALE + state.hover * HOVER + bob);
        if(state.showBounds)
        {
            drawBounds(state, poseStack, buffers);
        }

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
        drawShards(buffers.getBuffer(GlassRenderTypes.hologram(HoloProjectorBlockEntity.Look.BLUE.ordinal())), poseStack.last(), hologram,
                   state.time, state.radius);
        drawDome(state, hologram, poseStack.last(), buffers);
        poseStack.popPose();
    }

    /** How far round the room the dome lies, and over how many blocks inside the room it fades in (blocks). */
    private static final float DOME_MARGIN = 0.6F;
    private static final float DOME_FADE = 1.5F;

    /**
     * Standing inside the simulation, a dome round the room shows the scan's panorama (see {@link Panoramas}) in place of
     * the real world: the sky and the land around where the room was scanned. It fades in as you walk in, and with the
     * nano wave. From outside the room it is not there, and the room stands in the real world.
     */
    private static void drawDome(State state, HologramRenderer.Hologram hologram, PoseStack.Pose pose, MultiBufferSource buffers)
    {
        if(state.scan == null)
        {
            return;
        }
        Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(state.anchor.getX(), state.anchor.getY(), state.anchor.getZ());
        org.joml.Vector3f min = hologram.boxMin, max = hologram.boxMax;
        float inside = (float)Math.min(Math.min(eye.x - min.x, max.x - eye.x), Math.min(Math.min(eye.z - min.z, max.z - eye.z), Math.min(eye.y - min.y, max.y - eye.y)));
        if(inside <= 0F)
        {
            return;
        }
        float fade = Math.clamp(inside / DOME_FADE, 0F, 1F) * Math.clamp(state.radius / (hologram.reach + 2F), 0F, 1F);
        if(fade <= 0.01F)
        {
            return;
        }
        net.minecraft.resources.ResourceLocation panorama = Panoramas.texture(state.scan.id());
        if(panorama == null)
        {
            return;
        }
        float x0 = min.x - DOME_MARGIN, y0 = min.y - DOME_MARGIN, z0 = min.z - DOME_MARGIN;
        float x1 = max.x + DOME_MARGIN, y1 = max.y + DOME_MARGIN, z1 = max.z + DOME_MARGIN;
        float[][][] faces = {
                { { x0, y0, z0 }, { x1, y0, z0 }, { x1, y1, z0 }, { x0, y1, z0 } },
                { { x0, y0, z1 }, { x0, y1, z1 }, { x1, y1, z1 }, { x1, y0, z1 } },
                { { x0, y0, z0 }, { x0, y1, z0 }, { x0, y1, z1 }, { x0, y0, z1 } },
                { { x1, y0, z0 }, { x1, y0, z1 }, { x1, y1, z1 }, { x1, y1, z0 } },
                { { x0, y1, z0 }, { x1, y1, z0 }, { x1, y1, z1 }, { x0, y1, z1 } },
                { { x0, y0, z0 }, { x0, y0, z1 }, { x1, y0, z1 }, { x1, y0, z0 } },
        };
        int alpha = Math.clamp(Math.round(fade * 255F), 0, 255);
        com.mojang.blaze3d.vertex.VertexConsumer consumer = buffers.getBuffer(GlassRenderTypes.dome(panorama));
        for(float[][] face : faces)
        {
            for(float[] c : face)
            {
                consumer.addVertex(pose, c[0], c[1], c[2]).setColor(255, 255, 255, alpha);
            }
        }
    }

    /** The box a scan can reach (the room is found inside it), pulsing faintly, with its floor marked. */
    private static void drawBounds(State state, PoseStack poseStack, MultiBufferSource buffers)
    {
        com.mojang.blaze3d.vertex.VertexConsumer lines = buffers.getBuffer(net.minecraft.client.renderer.RenderType.lines());
        poseStack.pushPose();
        poseStack.translate(state.anchor.getX() - state.x, state.anchor.getY() - state.y, state.anchor.getZ() - state.z);
        PoseStack.Pose pose = poseStack.last();
        float pulse = 0.5F + 0.5F * (float)Math.sin(state.time * 0.15F);
        int colour = (int)(90 + 90 * pulse) << 24 | 0x3CE6FF;
        float x0 = -ryzlar.glass.holo.RoomScan.HALF_WIDTH, x1 = ryzlar.glass.holo.RoomScan.HALF_WIDTH;
        float y0 = -ryzlar.glass.holo.RoomScan.BELOW, y1 = ryzlar.glass.holo.RoomScan.ABOVE + 1;
        float z0 = x0, z1 = x1;
        float[][] corners = { { x0, z0 }, { x1, z0 }, { x1, z1 }, { x0, z1 } };
        for(int i = 0; i < 4; i++)
        {
            float[] a = corners[i], b = corners[(i + 1) % 4];
            boundsLine(lines, pose, a[0], y0, a[1], b[0], y0, b[1], colour);
            boundsLine(lines, pose, a[0], y1, a[1], b[0], y1, b[1], colour);
            boundsLine(lines, pose, a[0], y0, a[1], a[0], y1, a[1], colour);
            //The cube's own floor level, round the box.
            boundsLine(lines, pose, a[0], 0F, a[1], b[0], 0F, b[1], (colour & 0xFFFFFF) | (int)(40 + 50 * pulse) << 24);
        }
        poseStack.popPose();
    }

    private static void boundsLine(com.mojang.blaze3d.vertex.VertexConsumer lines, PoseStack.Pose pose, float ax, float ay, float az, float bx, float by, float bz, int colour)
    {
        float nx = bx - ax, ny = by - ay, nz = bz - az;
        float length = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= length;
        ny /= length;
        nz /= length;
        lines.addVertex(pose, ax, ay, az).setColor(colour).setNormal(pose, nx, ny, nz);
        lines.addVertex(pose, bx, by, bz).setColor(colour).setNormal(pose, nx, ny, nz);
    }

    /** The scan running over the real room (see {@link ScanEffect}), and laser lines from the cube to its front. */
    private void drawScan(State state, PoseStack poseStack, MultiBufferSource buffers, float cubeHeight)
    {
        if(Panoramas.capturing())
        {
            return; //the panorama is of the room, not of the scan
        }
        if(state.room == null || state.scanFade <= 0F)
        {
            if(!state.scanning)
            {
                scans.remove(state.entityId);
            }
            return;
        }
        ScanEffect effect = scans.get(state.entityId);
        if(effect == null || effect.builtFor != state.scanStart)
        {
            effect = new ScanEffect(state.room, state.anchor, new org.joml.Vector3f((float)WAVE_ORIGIN.x, (float)WAVE_ORIGIN.y, (float)WAVE_ORIGIN.z), state.scanStart);
            scans.put(state.entityId, effect);
        }
        poseStack.pushPose();
        poseStack.translate(state.anchor.getX() - state.x, state.anchor.getY() - state.y, state.anchor.getZ() - state.z);
        effect.draw(buffers.getBuffer(GlassRenderTypes.SCAN), poseStack.last(), state.scanRadius, state.scanFade, state.scanFlash);
        if(state.scanning)
        {
            //From the cube's core: the entity stands at the block's centre, so the core is half a block in.
            org.joml.Vector3f core = new org.joml.Vector3f(0.5F, cubeHeight, 0.5F);
            effect.beams(buffers.getBuffer(net.minecraft.client.renderer.RenderType.lines()), poseStack.last(), core, state.scanRadius,
                    state.entityId * 7919 + (int)(state.time / 2F), BEAMS);
        }
        poseStack.popPose();
    }

    /** Shards per block of the room's outline, and at most this many. */
    private static final float SHARDS_PER_BLOCK = 2.5F;
    private static final int MAX_SHARDS = 200;
    private static final net.minecraft.resources.ResourceLocation SHARD_TEXTURE = ryzlar.glass.Glass.id("block/holo_glow");

    /**
     * Little blue shards coming off the room's walls: each starts just inside the edge, drifts out and up, and fades
     * (shrinks) away, then starts again somewhere else. Drawn as holograms, so the nano wave brings and takes them too.
     */
    private static void drawShards(com.mojang.blaze3d.vertex.VertexConsumer consumer, PoseStack.Pose pose, HologramRenderer.Hologram hologram, float time, float radius)
    {
        org.joml.Vector3f min = hologram.boxMin, max = hologram.boxMax;
        float w = max.x - min.x, d = max.z - min.z, h = max.y - min.y;
        float perimeter = 2F * (w + d);
        if(!(perimeter > 0F) || !(h > 0F))
        {
            return;
        }
        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).apply(SHARD_TEXTURE);
        float u0 = sprite.getU(0.3F), u1 = sprite.getU(0.7F), v0 = sprite.getV(0.3F), v1 = sprite.getV(0.7F);
        int encodedRadius = HologramRenderer.Hologram.encodeRadius(radius);
        int count = Math.min(MAX_SHARDS, (int)(perimeter * SHARDS_PER_BLOCK));
        for(int i = 0; i < count; i++)
        {
            float period = 50F + random(i, 1) * 70F;
            float cycles = time / period + random(i, 2);
            int cycle = (int)Math.floor(cycles);
            float age = cycles - cycle;
            int seed = i * 7919 + cycle * 104729;
            //A spot on the walls: along the outline, at some height, and the way out of the room there.
            float along = random(seed, 3) * perimeter;
            float px, pz, nx = 0F, nz = 0F;
            if(along < w)
            {
                px = min.x + along;
                pz = min.z;
                nz = -1F;
            }
            else if(along < w + d)
            {
                px = max.x;
                pz = min.z + (along - w);
                nx = 1F;
            }
            else if(along < 2F * w + d)
            {
                px = max.x - (along - w - d);
                pz = max.z;
                nz = 1F;
            }
            else
            {
                px = min.x;
                pz = max.z - (along - 2F * w - d);
                nx = -1F;
            }
            float out = -0.2F + age * (0.5F + random(seed, 5) * 1.0F);
            float x = px + nx * out;
            float z = pz + nz * out;
            float y = min.y + random(seed, 4) * h + age * (0.3F + random(seed, 6) * 0.6F);
            float size = (0.025F + random(seed, 7) * 0.06F) * (float)Math.sin(age * Math.PI);
            if(size > 0.004F)
            {
                cube(consumer, pose, hologram, x, y, z, size, u0, u1, v0, v1, encodedRadius);
            }
        }
    }

    private static void cube(com.mojang.blaze3d.vertex.VertexConsumer consumer, PoseStack.Pose pose, HologramRenderer.Hologram hologram,
                             float x, float y, float z, float half, float u0, float u1, float v0, float v1, int encodedRadius)
    {
        float a = x - half, b = x + half, c = y - half, e = y + half, f = z - half, g = z + half;
        float[][] faces = {
                { a, c, f, b, c, f, b, e, f, a, e, f }, //north
                { b, c, g, a, c, g, a, e, g, b, e, g }, //south
                { a, c, g, a, c, f, a, e, f, a, e, g }, //west
                { b, c, f, b, c, g, b, e, g, b, e, f }, //east
                { a, e, f, b, e, f, b, e, g, a, e, g }, //up
                { a, c, g, b, c, g, b, c, f, a, c, f }, //down
        };
        float[] us = { u0, u1, u1, u0 };
        float[] vs = { v1, v1, v0, v0 };
        for(float[] face : faces)
        {
            for(int k = 0; k < 4; k++)
            {
                hologram.vertex(consumer, pose, face[k * 3], face[k * 3 + 1], face[k * 3 + 2], us[k], vs[k], 0xFFFFFFFF, k, encodedRadius);
            }
        }
    }

    /** A steady pseudo-random number in [0, 1). */
    private static float random(int seed, int salt)
    {
        int h = seed * 0x9E3779B1 + salt * 0x85EBCA6B;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return (h >>> 8) / (float)(1 << 24);
    }

    /** The room laid around the cube's block: where the scanner stood goes there, turned the way the player faced. */
    private HologramRenderer.Hologram hologram(ClientLevel level, State state, HoloClient.ClientProjection projection)
    {
        CubeScan scan = state.scan;
        String key = scan.id() + "|" + state.anchor.asLong() + "|" + state.turn;
        Entry entry = holograms.get(state.entityId);
        if(entry == null || !entry.key().equals(key))
        {
            if(holograms.size() > 16)
            {
                holograms.clear();
            }
            HologramRenderer.Placement placement = new HologramRenderer.Placement(scan.originX() + 0.5F, scan.originY(), scan.originZ() + 0.5F, state.turn, 1F, new Vec3(0.5D, 0D, 0.5D));
            HologramRenderer.Hologram hologram = HologramRenderer.buildMesh(level, projection, placement, null, WAVE_ORIGIN, state.anchor, true);
            entry = new Entry(key, hologram);
            holograms.put(state.entityId, entry);
        }
        return entry.hologram();
    }
}
