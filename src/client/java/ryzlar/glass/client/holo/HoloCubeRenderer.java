package ryzlar.glass.client.holo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import ryzlar.glass.client.render.GlassRenderTypes;
import ryzlar.glass.holo.CubeScan;
import ryzlar.glass.holo.HoloBubbles;
import ryzlar.glass.holo.HoloCubeEntity;
import ryzlar.glass.holo.HoloProjectorBlockEntity;
import ryzlar.glass.holo.HoloSphere;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws a holo cube on the ground (its Blockbench model: it rises a little while it projects or scans, its frame turning
 * and its core breathing), its scan ("digitizing", see {@link ScanEffect}), and its simulation: the scanned ball, 1:1,
 * inside a glowing bubble that grows out of the cube.
 * <p>
 * From outside the bubble the scan shows through its wall in blue hologram colours; inside it, in its real colours and
 * light, with its chests, signs, beds, animals and mobs (stand-ins, frozen as they were). The real world in the ball is
 * hidden meanwhile (see {@link SimulationView}), and the sky is the scanned one ({@link SimulatedSky}).
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
    /** The bubble's centre, from the cube's block corner: the shrunk ball's centre (the scan stands on the cube's floor). */
    private static final Vec3 CENTRE = new Vec3(0.5D, 0.5D * HoloSphere.SCALE, 0.5D);
    /** The scanned ball's centre in the real world, from the cube's block corner (the cube's block centre). */
    private static final Vec3 SCAN_CENTRE = new Vec3(0.5D, 0.5D, 0.5D);

    public static class State extends EntityRenderState
    {
        int entityId;
        BlockPos anchor = BlockPos.ZERO;
        int turn;
        float radius;
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
        float partialTick;
        boolean scanning;
        boolean showBounds;
        float scanProgress;
        float scanFade;
        float scanFlash;
        @Nullable
        it.unimi.dsi.fastutil.longs.LongList surfaces;
        long scanStart;
    }

    /** How long the scan's pixels take to fade once the scan is done, and how long its flash lasts (ticks). */
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
        state.partialTick = partialTick;
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
        //Looking at a cube that has not scanned yet: show the ball its scan will take.
        state.showBounds = entity.scan() == null && !entity.scanning() && Minecraft.getInstance().crosshairPickEntity == entity;
        state.surfaces = entity.clientSurfaces;
        state.scanStart = entity.clientScanStart;
        state.scanProgress = entity.scanProgress(partialTick);
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

    /** The bubble is big and the cube small: keep drawing while the bubble may be on screen. */
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
        float grown = Math.clamp(state.radius / HoloCubeEntity.FULL_RADIUS, 0F, 1F);
        Vec3 centre = Vec3.atLowerCornerOf(state.anchor).add(CENTRE);
        //The scanned sky comes and goes with the bubble.
        SimulatedSky.offer(state.entityId, centre, HoloSphere.BUBBLE_RADIUS, grown, state.scan, (long)state.time);
        Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        boolean inside = eye.distanceTo(centre) < HoloSphere.BUBBLE_RADIUS;

        poseStack.pushPose();
        poseStack.translate(state.anchor.getX() - state.x, state.anchor.getY() - state.y, state.anchor.getZ() - state.z);
        //Inside: the place as it was. Outside: a blue hologram of it, seen through the bubble's wall.
        int look = inside ? HoloProjectorBlockEntity.Look.SOLID.ordinal() : HoloProjectorBlockEntity.Look.BLUE.ordinal();
        hologram.replay(buffers.getBuffer(GlassRenderTypes.hologram(look)), poseStack.last(), state.radius, 1F);
        poseStack.popPose();

        drawStandIns(state, poseStack, buffers, inside);

        poseStack.pushPose();
        poseStack.translate(state.anchor.getX() - state.x, state.anchor.getY() - state.y, state.anchor.getZ() - state.z);
        drawWall(state, poseStack.last(), buffers, Math.min(state.radius - 0.3F, HoloSphere.BUBBLE_RADIUS), grown, inside);
        poseStack.popPose();
    }

    // ---- The bubble's wall ----

    private static final int WALL_RINGS = 24;
    private static final int WALL_SEGMENTS = 48;

    /**
     * The bubble's glowing wall (the barrier look of glass_back: a faint blue sheen, a coarse grid, bands of light
     * rising, a nano grid close up), a ball round the cube's block as far as the bubble has grown. Its foot is where it
     * meets the ground the cube stands on.
     */
    private static void drawWall(State state, PoseStack.Pose pose, MultiBufferSource buffers, float radius, float fade, boolean inside)
    {
        if(radius <= 0.1F || fade <= 0.01F)
        {
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(GlassRenderTypes.BACK);
        int alpha = Mth.clamp(Math.round((inside ? 0.85F : 1F) * fade * 255F), 0, 255);
        float cx = (float)CENTRE.x, cy = (float)CENTRE.y, cz = (float)CENTRE.z;
        for(int r = 0; r < WALL_RINGS; r++)
        {
            double lat0 = Math.PI * r / WALL_RINGS - Math.PI / 2D, lat1 = Math.PI * (r + 1) / WALL_RINGS - Math.PI / 2D;
            for(int s = 0; s < WALL_SEGMENTS; s++)
            {
                double lon0 = Math.PI * 2D * s / WALL_SEGMENTS, lon1 = Math.PI * 2D * (s + 1) / WALL_SEGMENTS;
                //Surface coordinates in blocks: round the ball, and up it from its foot (for the grid and the rising bands).
                wallVertex(consumer, pose, cx, cy, cz, radius, lat0, lon0, alpha);
                wallVertex(consumer, pose, cx, cy, cz, radius, lat0, lon1, alpha);
                wallVertex(consumer, pose, cx, cy, cz, radius, lat1, lon1, alpha);
                wallVertex(consumer, pose, cx, cy, cz, radius, lat1, lon0, alpha);
            }
        }
    }

    private static void wallVertex(VertexConsumer consumer, PoseStack.Pose pose, float cx, float cy, float cz, float radius, double lat, double lon, int alpha)
    {
        float nx = (float)(Math.cos(lat) * Math.cos(lon)), ny = (float)Math.sin(lat), nz = (float)(Math.cos(lat) * Math.sin(lon));
        float su = (float)(lon * radius), sv = (float)((lat + Math.PI / 2D) * radius);
        consumer.addVertex(pose, cx + nx * radius, cy + ny * radius, cz + nz * radius)
                .setColor(0, 0, 255, alpha)
                .setUv(su, sv)
                .setUv1(32767, 32767)
                .setUv2(0, 0)
                .setNormal(pose, nx, ny, nz);
    }

    // ---- The scan's chests, signs, beds, animals and mobs ----

    /**
     * The scan's block entities and entities, as stand-ins frozen as they were (see SimulationView), shrunk with the
     * scan and lit with the scanned light; from outside tinted like the hologram. Only as far as the wave has grown.
     */
    private static void drawStandIns(State state, PoseStack poseStack, MultiBufferSource buffers, boolean inside)
    {
        if(state.entity == null)
        {
            return;
        }
        HoloBubbles.Bubble bubble = HoloBubbles.byId(state.entity.level(), state.entityId);
        SimulationView.Scene scene = bubble == null ? null : SimulationView.scene(state.entity, bubble);
        if(scene == null)
        {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 wave = Vec3.atLowerCornerOf(state.anchor).add(WAVE_ORIGIN);
        MultiBufferSource target = inside ? buffers : new TintedBuffers(buffers);
        float scale = HoloSphere.SCALE;
        for(SimulationView.Placed placed : scene.blockEntities)
        {
            BlockPos at = placed.scanPos();
            //The block's place in the world: shrunk and turned, still a box; drawn from its lowest corner.
            Vec3 a = bubble.toWorld(at.getX(), at.getY(), at.getZ()), b = bubble.toWorld(at.getX() + 1, at.getY() + 1, at.getZ() + 1);
            Vec3 corner = new Vec3(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z));
            if(corner.add(scale / 2D, scale / 2D, scale / 2D).distanceTo(wave) > state.radius)
            {
                continue;
            }
            BlockEntityRenderer<BlockEntity> renderer = mc.getBlockEntityRenderDispatcher().getRenderer(placed.blockEntity());
            if(renderer == null)
            {
                continue;
            }
            poseStack.pushPose();
            poseStack.translate(corner.x - state.x, corner.y - state.y, corner.z - state.z);
            poseStack.scale(scale, scale, scale);
            try
            {
                renderer.render(placed.blockEntity(), state.partialTick, poseStack, target, scene.light(at.getX(), at.getY(), at.getZ()), OverlayTexture.NO_OVERLAY, camera);
            }
            catch(RuntimeException ignored)
            {
                //A stand-in that cannot be drawn this way is left out.
            }
            poseStack.popPose();
        }
        for(SimulationView.PlacedEntity placed : scene.entities)
        {
            Entity entity = placed.entity();
            if(entity.position().distanceTo(wave) > state.radius)
            {
                continue;
            }
            Vec3 at = placed.scanPos();
            poseStack.pushPose();
            poseStack.translate(entity.getX() - state.x, entity.getY() - state.y, entity.getZ() - state.z);
            poseStack.scale(scale, scale, scale);
            try
            {
                mc.getEntityRenderDispatcher().render(entity, 0D, 0D, 0D, 0F, poseStack, target,
                        scene.light((int)Math.floor(at.x), (int)Math.floor(at.y), (int)Math.floor(at.z)));
            }
            catch(RuntimeException ignored)
            {
                //Likewise.
            }
            poseStack.popPose();
        }
    }

    /** Draws everything given to it in the hologram's cyan, for the stand-ins seen from outside the bubble. */
    private record TintedBuffers(MultiBufferSource inner) implements MultiBufferSource
    {
        @Override
        public VertexConsumer getBuffer(RenderType type)
        {
            return new Tint(inner.getBuffer(type));
        }
    }

    private record Tint(VertexConsumer inner) implements VertexConsumer
    {
        @Override
        public VertexConsumer addVertex(float x, float y, float z)
        {
            inner.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a)
        {
            //Its brightness, in the hologram's blue.
            float luma = (0.299F * r + 0.587F * g + 0.114F * b) / 255F;
            inner.setColor(Mth.clamp((int)(60 * luma + 20), 0, 255), Mth.clamp((int)(230 * luma + 25), 0, 255), 255, a);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v)
        {
            inner.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v)
        {
            inner.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v)
        {
            //Glowing, as holograms do.
            inner.setUv2(240, 240);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z)
        {
            inner.setNormal(x, y, z);
            return this;
        }
    }

    // ---- Before a scan ----

    /** The ball a scan will take, pulsing faintly: three great circles round the cube's block. */
    private static void drawBounds(State state, PoseStack poseStack, MultiBufferSource buffers)
    {
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        poseStack.pushPose();
        poseStack.translate(state.anchor.getX() - state.x, state.anchor.getY() - state.y, state.anchor.getZ() - state.z);
        PoseStack.Pose pose = poseStack.last();
        float pulse = 0.5F + 0.5F * (float)Math.sin(state.time * 0.15F);
        int colour = (int)(90 + 90 * pulse) << 24 | 0x3CE6FF;
        float r = HoloSphere.SCAN_RADIUS + 0.5F;
        float cx = (float)SCAN_CENTRE.x, cy = (float)SCAN_CENTRE.y, cz = (float)SCAN_CENTRE.z;
        int segments = 48;
        for(int s = 0; s < segments; s++)
        {
            double a0 = Math.PI * 2D * s / segments, a1 = Math.PI * 2D * (s + 1) / segments;
            float c0 = (float)Math.cos(a0) * r, s0 = (float)Math.sin(a0) * r, c1 = (float)Math.cos(a1) * r, s1 = (float)Math.sin(a1) * r;
            boundsLine(lines, pose, cx + c0, cy, cz + s0, cx + c1, cy, cz + s1, colour);
            boundsLine(lines, pose, cx + c0, cy + s0, cz, cx + c1, cy + s1, cz, colour);
            boundsLine(lines, pose, cx, cy + s0, cz + c0, cx, cy + s1, cz + c1, colour);
        }
        poseStack.popPose();
    }

    private static void boundsLine(VertexConsumer lines, PoseStack.Pose pose, float ax, float ay, float az, float bx, float by, float bz, int colour)
    {
        float nx = bx - ax, ny = by - ay, nz = bz - az;
        float length = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= length;
        ny /= length;
        nz /= length;
        lines.addVertex(pose, ax, ay, az).setColor(colour).setNormal(pose, nx, ny, nz);
        lines.addVertex(pose, bx, by, bz).setColor(colour).setNormal(pose, nx, ny, nz);
    }

    // ---- Scanning ----

    /** The scan on the real world (see {@link ScanEffect}): the grid ball, the faces coming apart, and laser lines. */
    private void drawScan(State state, PoseStack poseStack, MultiBufferSource buffers, float cubeHeight)
    {
        if(state.surfaces == null || state.scanFade <= 0F)
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
            effect = new ScanEffect(state.surfaces, state.anchor, new Vector3f((float)WAVE_ORIGIN.x, (float)WAVE_ORIGIN.y, (float)WAVE_ORIGIN.z), state.scanStart);
            scans.put(state.entityId, effect);
        }
        poseStack.pushPose();
        poseStack.translate(state.anchor.getX() - state.x, state.anchor.getY() - state.y, state.anchor.getZ() - state.z);
        effect.draw(buffers.getBuffer(GlassRenderTypes.SCAN), poseStack.last(), state.scanProgress, state.scanFade, state.scanFlash);
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        Vector3f core = new Vector3f(0.5F, cubeHeight, 0.5F);
        effect.grid(lines, poseStack.last(), new Vector3f((float)SCAN_CENTRE.x, (float)SCAN_CENTRE.y, (float)SCAN_CENTRE.z), state.scanProgress,
                state.scanning ? 1F : state.scanFade, state.time);
        if(state.scanning)
        {
            beams(lines, poseStack.last(), core, ScanEffect.front(state.scanProgress), state.entityId * 7919 + (int)(state.time / 2F));
        }
        poseStack.popPose();
    }

    /** Laser lines from the cube's core out to the grid ball's front, changing every couple of ticks. */
    private static void beams(VertexConsumer lines, PoseStack.Pose pose, Vector3f from, float radius, int seed)
    {
        if(radius <= 0.2F)
        {
            return;
        }
        for(int i = 0; i < BEAMS; i++)
        {
            double yaw = random(seed, i) * Math.PI * 2D;
            double pitch = (random(seed, i + 31) - 0.5D) * Math.PI;
            float tx = (float)(SCAN_CENTRE.x + Math.cos(pitch) * Math.cos(yaw) * radius);
            float ty = (float)(SCAN_CENTRE.y + Math.sin(pitch) * radius);
            float tz = (float)(SCAN_CENTRE.z + Math.cos(pitch) * Math.sin(yaw) * radius);
            float nx = tx - from.x, ny = ty - from.y, nz = tz - from.z;
            float length = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
            if(length < 1.0E-4F)
            {
                continue;
            }
            nx /= length;
            ny /= length;
            nz /= length;
            int colour = (int)(150 + random(seed, i + 97) * 105) << 24 | 0x6CF0FF;
            lines.addVertex(pose, from.x, from.y, from.z).setColor(colour).setNormal(pose, nx, ny, nz);
            lines.addVertex(pose, tx, ty, tz).setColor(colour).setNormal(pose, nx, ny, nz);
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

    // ---- The scanned ball ----

    /** The ball laid round the cube's block: where the scanner stood goes there, turned the way the player faced. */
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
            HologramRenderer.Placement placement = new HologramRenderer.Placement(scan.originX() + 0.5F, scan.originY(), scan.originZ() + 0.5F, state.turn,
                    HoloSphere.SCALE, new Vec3(0.5D, 0D, 0.5D));
            HologramRenderer.Hologram hologram = HologramRenderer.buildMesh(level, projection, placement, null, WAVE_ORIGIN, state.anchor, true,
                    HoloSphere.SCAN_RADIUS + 0.5F, SimulatedSky.scannedDaylight(scan));
            entry = new Entry(key, hologram);
            holograms.put(state.entityId, entry);
        }
        return entry.hologram();
    }
}
