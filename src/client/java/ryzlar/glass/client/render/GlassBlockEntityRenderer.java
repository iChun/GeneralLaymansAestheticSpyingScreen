package ryzlar.glass.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EndCrystalModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import ryzlar.glass.GlassUtil;
import ryzlar.glass.blockentity.GlassBaseBlockEntity;
import ryzlar.glass.blockentity.GlassProjectorBlockEntity;
import ryzlar.glass.blockentity.GlassWirelessBlockEntity;
import ryzlar.glass.client.GlassClientState;
import ryzlar.glass.client.TerminalPlacement;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a GLASS block's screen faces, the spinning beacon in a projector and the crystal in a wireless extension.
 * Port of TileEntityGlassRenderer.
 */
public class GlassBlockEntityRenderer implements BlockEntityRenderer<GlassBaseBlockEntity>
{
    private static final ResourceLocation END_CRYSTAL_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/end_crystal/end_crystal.png");
    private static final RenderType END_CRYSTAL_RENDER_TYPE = RenderType.entityCutoutNoCull(END_CRYSTAL_TEXTURE);

    private final EndCrystalModel crystalModel;
    private final EndCrystalRenderState crystalState = new EndCrystalRenderState();

    public GlassBlockEntityRenderer(BlockEntityRendererProvider.Context context)
    {
        crystalModel = new EndCrystalModel(context.bakeLayer(ModelLayers.END_CRYSTAL));
        crystalState.showsBottom = false;
    }

    @Override
    public void render(GlassBaseBlockEntity te, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay, Vec3 cameraPos)
    {
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);

        boolean isMaster = te instanceof GlassProjectorBlockEntity;
        if(te instanceof GlassProjectorBlockEntity master)
        {
            renderBeacon(master, partialTick, poseStack, buffers);
        }
        else if(te instanceof GlassWirelessBlockEntity wireless)
        {
            renderCrystal(wireless, partialTick, poseStack, buffers);
        }
        te.lastDraw = 8;

        if(te.isSwitchingOff())
        {
            drawScreen(te, te.offKey, te.offFaces, te.offWireless, true, poseStack, buffers, partialTick);
        }
        else if(te.active)
        {
            boolean wirelessScreen = isMaster && !((GlassProjectorBlockEntity)te).wirelessPos.isEmpty() && te.channel.equals(((GlassProjectorBlockEntity)te).screenKey());
            drawScreen(te, te.channel, te.activeFaces, wirelessScreen, false, poseStack, buffers, partialTick);
        }
        else
        {
            whiteFlash(te, poseStack, buffers, partialTick);
        }

        poseStack.popPose();
    }

    private void renderBeacon(GlassProjectorBlockEntity master, float partialTick, PoseStack poseStack, MultiBufferSource buffers)
    {
        poseStack.pushPose();
        Direction face = master.placingFace;
        if(face == Direction.DOWN)
        {
            poseStack.mulPose(Axis.XP.rotationDegrees(180F));
        }
        else if(face.getAxis().isHorizontal())
        {
            poseStack.mulPose(Axis.YP.rotationDegrees(-face.get2DDataValue() * 90F));
            poseStack.mulPose(Axis.XP.rotationDegrees(90F));
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(GlassUtil.interpolateRotation(master.rotationBeaconPrev, master.rotationBeacon, partialTick)));
        poseStack.translate(-0.25D, -0.35D, -0.25D);
        poseStack.scale(0.5F, 0.5F, 0.5F);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.BEACON.defaultBlockState(), poseStack, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    private void renderCrystal(GlassWirelessBlockEntity wireless, float partialTick, PoseStack poseStack, MultiBufferSource buffers)
    {
        poseStack.pushPose();
        poseStack.translate(0F, -0.15F, 0F);
        poseStack.scale(0.3F, 0.3F, 0.3F);
        //Same transforms as the vanilla end crystal renderer.
        poseStack.scale(2.0F, 2.0F, 2.0F);
        poseStack.translate(0.0F, -0.5F, 0.0F);
        //1.12 spun the crystal by (ticks * speed) degrees; the 1.21 model spins 3 degrees per age tick.
        crystalState.ageInTicks = (wireless.ticks + partialTick) * (1.2F + wireless.users * 10F) / 3F;
        crystalModel.setupAnim(crystalState);
        crystalModel.renderToBuffer(poseStack, buffers.getBuffer(END_CRYSTAL_RENDER_TYPE), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    /** The 1.12 white flash, for when there is no camera image to show (no camera, or seen from inside a camera view). */
    private static void whiteFlash(GlassBaseBlockEntity te, PoseStack poseStack, MultiBufferSource buffers, float partialTick)
    {
        boolean wirelessMaster = te instanceof GlassProjectorBlockEntity master && !master.wirelessPos.isEmpty();
        drawPlanes(te, poseStack, buffers.getBuffer(GlassRenderTypes.planes(!wirelessMaster)), 1F, 1F, 1F, -1F, wirelessMaster ? 0.501D : 0.502D, partialTick);
    }

    // ---- The "nano" screen ----

    /**
     * Draws the terminal's view on this block. Switching on, the image assembles out of "nano" pixels in one wave that
     * starts at the projector and spreads over the whole screen, with refraction along the wave front; switching off, the
     * wave shrinks back into the projector. Punching the glass makes it ripple. The effect is in the glass_screen shader.
     */
    private void drawScreen(GlassBaseBlockEntity te, String key, List<Direction> faces, boolean wireless, boolean switchingOff,
                            PoseStack poseStack, MultiBufferSource buffers, float partialTick)
    {
        SpyViewRenderer spy = SpyViewRenderer.INSTANCE;
        Level level = te.getLevel();
        BlockPos masterPos = masterPos(key);
        if(spy.isRendering() || level == null || masterPos == null)
        {
            if(!switchingOff)
            {
                whiteFlash(te, poseStack, buffers, partialTick);
            }
            return;
        }

        //Where the images come from: one view for a camera screen, one per side for a cloak.
        TerminalPlacement placement = null;
        boolean cloak;
        if(switchingOff)
        {
            cloak = te.offCloak;
        }
        else
        {
            placement = GlassClientState.INSTANCE.getTerminalPlacement(key);
            if(placement == null)
            {
                whiteFlash(te, poseStack, buffers, partialTick);
                return;
            }
            placement.playAmbienceOnce();
            cloak = placement.cloak;
        }
        //Switching feed: the old images, which the new feed wipes over.
        GlassClientState.Transition transition = switchingOff ? null : GlassClientState.INSTANCE.transitionFor(key);

        //The wave is centred on the projector (its position is part of the screen key) and shared by the whole screen.
        float radius;
        if(switchingOff)
        {
            radius = te.offWaveRadius(partialTick);
        }
        else if(transition != null)
        {
            radius = transition.waveRadius(level.getGameTime(), partialTick);
        }
        else
        {
            radius = level.getBlockEntity(masterPos) instanceof GlassProjectorBlockEntity master ? master.onRadius(partialTick) : 1000F;
        }
        if(radius <= 0F && transition == null)
        {
            return;
        }

        //A punch sends a ring across the whole screen from where it hit.
        Vec3 punchPoint = null;
        int[] ring = { 0, 0 };
        GlassClientState.Punch punch = switchingOff ? null : GlassClientState.INSTANCE.punchFor(key);
        if(punch != null)
        {
            float age = level.getGameTime() - punch.time() + partialTick;
            float strength = 1F - age / PUNCH_TICKS;
            if(strength > 0F)
            {
                punchPoint = punch.point().subtract(Vec3.atCenterOf(masterPos));
                ring = new int[] { Mth.clamp((int)(age * PUNCH_SPEED * 64F), 0, 32767), Mth.clamp((int)(strength * 1000F), 0, 1000) };
            }
        }

        PoseStack.Pose pose = poseStack.last();
        if(wireless && te instanceof GlassProjectorBlockEntity master)
        {
            ScreenImage image = image(spy, key, placement, cloak, master.placingFace, switchingOff, transition);
            if(image != null)
            {
                drawNanoWireless(master, pose, image.consumer(buffers, false), image.wave(radius), punchPoint, ring);
            }
            return;
        }

        BlockPos pos = te.getBlockPos();
        Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        //This block's centre, seen from the projector's centre.
        Vector3f offset = new Vector3f(pos.getX() - masterPos.getX(), pos.getY() - masterPos.getY(), pos.getZ() - masterPos.getZ());
        float h = 0.501F;
        boolean drewAny = false;
        //A cloak also covers the glass's edges (the sides of a wall), with the image of the side the block faces: the edges
        //would otherwise show as plain glass and give the cloak away.
        List<Direction> drawFaces = cloak && !faces.isEmpty() ? withEdges(faces) : faces;
        for(Direction face : drawFaces)
        {
            if(!Block.shouldRenderFace(te.getBlockState(), level.getBlockState(pos.relative(face)), face))
            {
                continue;
            }
            if(cloak && !faceSeen(pos, face, eye))
            {
                continue; //a cloak side seen from behind is culled anyway, so do not draw (or render the view for) it
            }
            Direction viewSide = faces.contains(face) ? face : faces.getFirst();
            ScreenImage image = image(spy, key, placement, cloak, viewSide, switchingOff, transition);
            if(image == null)
            {
                continue;
            }
            drewAny = true;
            VertexConsumer consumer = image.consumer(buffers, true);
            int[] wave = image.wave(radius);
            Vector3f n = new Vector3f(face.getStepX(), face.getStepY(), face.getStepZ());
            Vector3f u = face.getAxis() == Direction.Axis.Y ? new Vector3f(1F, 0F, 0F) : new Vector3f(0F, 1F, 0F);
            Vector3f v = new Vector3f(n).cross(u);
            Vector3f c = new Vector3f(n).mul(0.501F);
            int[] hit = punchOnScreen(punchPoint, u, v);
            nanoVertex(consumer, pose, c, u, v, n, -h, -h, offset, wave, hit, ring);
            nanoVertex(consumer, pose, c, u, v, n, h, -h, offset, wave, hit, ring);
            nanoVertex(consumer, pose, c, u, v, n, h, h, offset, wave, hit, ring);
            nanoVertex(consumer, pose, c, u, v, n, -h, h, offset, wave, hit, ring);
        }
        if(!drewAny && !switchingOff && !cloak)
        {
            whiteFlash(te, poseStack, buffers, partialTick);
        }
    }

    /** The screen faces plus the faces across them (a wall's edges); not the back, which faces into what is hidden. */
    private static List<Direction> withEdges(List<Direction> faces)
    {
        List<Direction> all = new ArrayList<>(faces);
        Direction.Axis main = faces.getFirst().getAxis();
        for(Direction direction : Direction.values())
        {
            if(direction.getAxis() != main && !all.contains(direction))
            {
                all.add(direction);
            }
        }
        return all;
    }

    /** True when the outside of this face points towards the eye. */
    private static boolean faceSeen(BlockPos pos, Direction face, Vec3 eye)
    {
        Vec3 centre = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5D, face.getStepY() * 0.5D, face.getStepZ() * 0.5D);
        return (eye.x - centre.x) * face.getStepX() + (eye.y - centre.y) * face.getStepY() + (eye.z - centre.z) * face.getStepZ() > 0D;
    }

    /**
     * What one face of the screen shows: the image, and while switching feed the old image the wave wipes over (outside
     * the wave). {@code full}: the new image is not drawn yet, so the old one is shown whole.
     */
    private record ScreenImage(ResourceLocation texture, @org.jetbrains.annotations.Nullable ResourceLocation old, boolean full)
    {
        VertexConsumer consumer(MultiBufferSource buffers, boolean cull)
        {
            return buffers.getBuffer(old != null && !full ? GlassRenderTypes.wipe(texture, old, cull) : GlassRenderTypes.screen(texture, cull));
        }

        int[] wave(float radius)
        {
            return encodeWave(full ? 1000F : radius, old != null && !full);
        }
    }

    @org.jetbrains.annotations.Nullable
    private static ScreenImage image(SpyViewRenderer spy, String key, @org.jetbrains.annotations.Nullable TerminalPlacement placement, boolean cloak,
                                     Direction side, boolean switchingOff, @org.jetbrains.annotations.Nullable GlassClientState.Transition transition)
    {
        String side_ = "/" + side.getSerializedName();
        ResourceLocation texture;
        if(switchingOff || placement == null)
        {
            texture = spy.textureFor(cloak ? key + side_ : key);
        }
        else
        {
            texture = spy.request(placement.forFace(side));
        }
        ResourceLocation old = null;
        if(transition != null)
        {
            old = spy.textureFor((transition.oldCloak() ? key + side_ : key) + SpyViewRenderer.OLD_SUFFIX);
        }
        if(texture == null)
        {
            return old == null ? null : new ScreenImage(old, null, true);
        }
        return new ScreenImage(texture, old, false);
    }

    /** How long a punch ripple lasts (ticks), and how fast its ring travels (blocks per tick). */
    private static final float PUNCH_TICKS = 30F;
    private static final float PUNCH_SPEED = 0.55F;

    /** The projector's position, from a screen key ("channel@position"). */
    @org.jetbrains.annotations.Nullable
    private static BlockPos masterPos(String key)
    {
        int at = key.lastIndexOf('@');
        if(at < 0)
        {
            return null;
        }
        try
        {
            return BlockPos.of(Long.parseLong(key.substring(at + 1)));
        }
        catch(NumberFormatException e)
        {
            return null;
        }
    }

    /** Wave radius as 16 bits in red and green (0..256 blocks). Blue: the old image shows outside the wave (a wipe). */
    private static int[] encodeWave(float radius, boolean wipe)
    {
        int value = Mth.clamp((int)(radius / 256F * 65535F), 0, 65535);
        return new int[] { value >> 8, value & 0xFF, wipe ? 255 : 0, 255 };
    }

    /** The punch point in this face's screen coordinates, in 1/32 blocks (or far away if there is no punch). */
    private static int[] punchOnScreen(@org.jetbrains.annotations.Nullable Vec3 punchPoint, Vector3f u, Vector3f v)
    {
        if(punchPoint == null)
        {
            return new int[] { 32767, 32767 };
        }
        Vector3f p = new Vector3f((float)punchPoint.x, (float)punchPoint.y, (float)punchPoint.z);
        return new int[] { Mth.clamp(Math.round(p.dot(u) * 32F), -32767, 32767), Mth.clamp(Math.round(p.dot(v) * 32F), -32767, 32767) };
    }

    /**
     * A screen corner. UV: its position on the screen in blocks from the projector (wave and pixel grid). Colour: wave
     * radius. UV1: the punch point. UV2: punch ring radius (1/64 blocks) and strength (0..1000).
     */
    private static void nanoVertex(VertexConsumer consumer, PoseStack.Pose pose, Vector3f c, Vector3f u, Vector3f v, Vector3f n, float du, float dv,
                                   Vector3f offset, int[] wave, int[] hit, int[] ring)
    {
        float x = c.x + u.x * du + v.x * dv;
        float y = c.y + u.y * du + v.y * dv;
        float z = c.z + u.z * du + v.z * dv;
        Vector3f onScreen = new Vector3f(offset).add(x, y, z);
        consumer.addVertex(pose, x, y, z)
                .setColor(wave[0], wave[1], wave[2], wave[3])
                .setUv(onScreen.dot(u), onScreen.dot(v))
                .setUv1(hit[0], hit[1])
                .setUv2(ring[0], ring[1])
                .setNormal(pose, n.x, n.y, n.z);
    }

    /** The floating screen between wireless extensions (its corners are already relative to the projector). */
    private static void drawNanoWireless(GlassProjectorBlockEntity master, PoseStack.Pose pose, VertexConsumer consumer, int[] wave,
                                         @org.jetbrains.annotations.Nullable Vec3 punchPoint, int[] ring)
    {
        if(master.wirelessPos.size() < 2)
        {
            return;
        }
        Direction face = master.placingFace;
        Vector3f n = new Vector3f(face.getStepX(), face.getStepY(), face.getStepZ());
        Vector3f u = face.getAxis() == Direction.Axis.Y ? new Vector3f(1F, 0F, 0F) : new Vector3f(0F, 1F, 0F);
        Vector3f v = new Vector3f(n).cross(u);
        int[] hit = punchOnScreen(punchPoint, u, v);
        double pushback = 0.501D;
        BlockPos masterPos = master.getBlockPos();
        List<Vector3f> points = new ArrayList<>();
        points.add(new Vector3f((float)(face.getStepX() * pushback), (float)(face.getStepY() * pushback), (float)(face.getStepZ() * pushback)));
        for(BlockPos pos : master.wirelessPos)
        {
            float pX = (float)(face.getStepX() * pushback * (face.getStepX() > 0 && pos.getX() > masterPos.getX() ? -1D : 1D));
            float pY = (float)(face.getStepY() * pushback * (face.getStepY() > 0 && pos.getY() > masterPos.getY() ? -1D : 1D));
            float pZ = (float)(face.getStepZ() * pushback * (face.getStepZ() > 0 && pos.getZ() > masterPos.getZ() ? -1D : 1D));
            points.add(new Vector3f(pos.getX() - masterPos.getX() + pX, pos.getY() - masterPos.getY() + pY, pos.getZ() - masterPos.getZ() + pZ));
        }
        for(int i = 0; i + 2 < points.size(); i++)
        {
            Vector3f[] tri = { points.get(i), points.get(i + 1), points.get(i + 2), points.get(i + 2) };
            for(Vector3f p : tri)
            {
                consumer.addVertex(pose, p.x, p.y, p.z)
                        .setColor(wave[0], wave[1], wave[2], wave[3])
                        .setUv(p.dot(u), p.dot(v))
                        .setUv1(hit[0], hit[1])
                        .setUv2(ring[0], ring[1])
                        .setNormal(pose, n.x, n.y, n.z);
            }
        }
    }

    /**
     * Port of TileEntityGlassRenderer.drawPlanes. With alpha -1 the alpha is worked out from the fade timers
     * (the white flash when glass turns on or off, or is punched).
     */
    public static void drawPlanes(GlassBaseBlockEntity te, PoseStack poseStack, VertexConsumer consumer, float r, float g, float b, float alpha, double pushback, float partialTick)
    {
        boolean wirelessScreen = te instanceof GlassProjectorBlockEntity master && !master.wirelessPos.isEmpty() && master.channel.equals(master.screenKey());
        drawPlanes(te, te.activeFaces, wirelessScreen, poseStack, consumer, r, g, b, alpha, pushback, partialTick);
    }

    /** As above, with the faces to draw (or, for a wireless projector, the floating screen) given explicitly. */
    public static void drawPlanes(GlassBaseBlockEntity te, List<Direction> faces, boolean wirelessScreen, PoseStack poseStack, VertexConsumer consumer, float r, float g, float b, float alpha, double pushback, float partialTick)
    {
        boolean calcAlpha = alpha == -1F;
        if(calcAlpha)
        {
            alpha = (float)Math.pow(Mth.clamp((te.fadeoutTime - partialTick) / (float)GlassBaseBlockEntity.FADEOUT_TIME, 0F, 1F), 0.5D);
        }
        PoseStack.Pose pose = poseStack.last();

        if(wirelessScreen && te instanceof GlassProjectorBlockEntity master)
        {
            if(master.wirelessPos.size() > 1)
            {
                drawWirelessScreen(master, pose, consumer, r, g, b, alpha, calcAlpha, pushback, partialTick);
            }
            return;
        }

        Level level = te.getLevel();
        BlockPos pos = te.getBlockPos();
        if(alpha <= 0F || level == null)
        {
            return;
        }
        float halfSize = 0.501F;
        for(Direction face : faces)
        {
            if(!Block.shouldRenderFace(te.getBlockState(), level.getBlockState(pos.relative(face)), face))
            {
                continue;
            }
            Vector3f n = new Vector3f(face.getStepX(), face.getStepY(), face.getStepZ());
            Vector3f u = face.getAxis() == Direction.Axis.Y ? new Vector3f(1F, 0F, 0F) : new Vector3f(0F, 1F, 0F);
            Vector3f v = new Vector3f(n).cross(u);
            //u x v = n, so these corners go anticlockwise seen from outside the face.
            Vector3f c = new Vector3f(n).mul((float)pushback);
            vertex(consumer, pose, c, u, v, -halfSize, -halfSize, r, g, b, alpha);
            vertex(consumer, pose, c, u, v, halfSize, -halfSize, r, g, b, alpha);
            vertex(consumer, pose, c, u, v, halfSize, halfSize, r, g, b, alpha);
            vertex(consumer, pose, c, u, v, -halfSize, halfSize, r, g, b, alpha);
        }
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Vector3f c, Vector3f u, Vector3f v, float du, float dv, float r, float g, float b, float a)
    {
        consumer.addVertex(pose, c.x + u.x * du + v.x * dv, c.y + u.y * du + v.y * dv, c.z + u.z * du + v.z * dv).setColor(r, g, b, a);
    }

    /** The floating screen spanned by a projector's wireless extensions, drawn as a triangle strip in 1.12. */
    private static void drawWirelessScreen(GlassProjectorBlockEntity master, PoseStack.Pose pose, VertexConsumer consumer, float r, float g, float b, float alpha, boolean calcAlpha, double pushback, float partialTick)
    {
        Direction face = master.placingFace;
        List<BlockPos> wirelessPos = master.wirelessPos;
        BlockPos masterPos = master.getBlockPos();

        int count = wirelessPos.size() + 1;
        float[][] verts = new float[count][];
        verts[0] = new float[] { (float)(face.getStepX() * pushback), (float)(face.getStepY() * pushback), (float)(face.getStepZ() * pushback), alpha };
        for(int i = 0; i < wirelessPos.size(); i++)
        {
            float a;
            if(master.active && master.wirelessTime > (i - 1) * GlassBaseBlockEntity.PROPAGATE_TIME)
            {
                a = calcAlpha ? (float)Math.pow(Mth.clamp(1.0F - (master.wirelessTime + partialTick - ((i - 1) * GlassBaseBlockEntity.PROPAGATE_TIME)) / (float)GlassBaseBlockEntity.FADEOUT_TIME, 0F, 1F), 0.5D) : alpha;
            }
            else if(!master.active)
            {
                a = alpha;
            }
            else
            {
                a = 0F;
            }
            BlockPos pos = wirelessPos.get(i);
            float pX = (float)(face.getStepX() * pushback * (face.getStepX() > 0 && pos.getX() > masterPos.getX() ? -1D : 1D));
            float pY = (float)(face.getStepY() * pushback * (face.getStepY() > 0 && pos.getY() > masterPos.getY() ? -1D : 1D));
            float pZ = (float)(face.getStepZ() * pushback * (face.getStepZ() > 0 && pos.getZ() > masterPos.getZ() ? -1D : 1D));
            verts[i + 1] = new float[] { pos.getX() - masterPos.getX() + pX, pos.getY() - masterPos.getY() + pY, pos.getZ() - masterPos.getZ() + pZ, a };
        }

        //Triangle strip as quads (the last corner repeated): (0,1,2), (1,2,3), ...
        for(int i = 0; i + 2 < count; i++)
        {
            float[] v0 = verts[i];
            float[] v1 = verts[i + 1];
            float[] v2 = verts[i + 2];
            if(v0[3] <= 0F && v1[3] <= 0F && v2[3] <= 0F)
            {
                continue;
            }
            consumer.addVertex(pose, v0[0], v0[1], v0[2]).setColor(r, g, b, v0[3]);
            consumer.addVertex(pose, v1[0], v1[1], v1[2]).setColor(r, g, b, v1[3]);
            consumer.addVertex(pose, v2[0], v2[1], v2[2]).setColor(r, g, b, v2[3]);
            consumer.addVertex(pose, v2[0], v2[1], v2[2]).setColor(r, g, b, v2[3]);
        }
    }

    @Override
    public boolean shouldRenderOffScreen()
    {
        return true;
    }

    @Override
    public int getViewDistance()
    {
        return 256;
    }
}
