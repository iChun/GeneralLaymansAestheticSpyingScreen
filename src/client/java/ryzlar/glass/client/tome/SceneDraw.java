package ryzlar.glass.client.tome;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * What a tome scene draws with: real block and item models, and simple glowing shapes standing in for GLASS's own effects
 * (screens, cloak walls, scan waves), all in block coordinates.
 */
final class SceneDraw
{
    private static final int LIGHT = LightTexture.FULL_BRIGHT;

    final PoseStack pose;
    private final MultiBufferSource.BufferSource buffers;
    private final Minecraft mc = Minecraft.getInstance();
    private final ItemStackRenderState itemState = new ItemStackRenderState();
    /** See-through things (glow, lines, holograms), drawn after the solid blocks by {@link #finish()}. */
    private final java.util.List<Runnable> later = new java.util.ArrayList<>();

    SceneDraw(PoseStack pose, MultiBufferSource.BufferSource buffers)
    {
        this.pose = pose;
        this.buffers = buffers;
    }

    void block(BlockState state, float x, float y, float z)
    {
        block(state, x, y, z, 1F);
    }

    /** A block, grown from its middle by {@code grow} (0..1), so it pops in. */
    void block(BlockState state, float x, float y, float z, float grow)
    {
        if(grow <= 0F)
        {
            return;
        }
        float g = Math.min(1F, grow);
        pose.pushPose();
        pose.translate(x + 0.5F, y + 0.5F, z + 0.5F);
        pose.scale(g, g, g);
        pose.translate(-0.5F, -0.5F, -0.5F);
        mc.getBlockRenderer().renderSingleBlock(state, pose, buffers, LIGHT, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /** Draws what is see-through, now that every solid block is in (so they show through it). */
    void finish()
    {
        buffers.endBatch();
        for(Runnable draw : later)
        {
            draw.run();
        }
        later.clear();
    }

    /** Runs a drawing step after the solid blocks, with the pose as it is now. */
    private void deferred(java.util.function.Consumer<PoseStack> draw)
    {
        PoseStack.Pose now = pose.last().copy();
        later.add(() -> {
            pose.pushPose();
            pose.last().pose().set(now.pose());
            pose.last().normal().set(now.normal());
            draw.accept(pose);
            pose.popPose();
        });
    }

    /** A block drawn as a see-through hologram in one colour (ARGB), {@code scale} times its size round its corner. */
    void holo(BlockState state, float x, float y, float z, float scale, int argb)
    {
        deferred(p -> holoNow(state, x, y, z, scale, argb));
    }

    private void holoNow(BlockState state, float x, float y, float z, float scale, int argb)
    {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale(scale, scale, scale);
        VertexConsumer target = buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        mc.getBlockRenderer().renderSingleBlock(state, pose, type -> new Tint(target, argb), LIGHT, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /** An item's model standing at a spot, {@code size} blocks big, turned by {@code spin} degrees. */
    void item(ItemStack stack, float x, float y, float z, float size, float spin)
    {
        mc.getItemModelResolver().updateForTopItem(itemState, stack, ItemDisplayContext.FIXED, null, null, 0);
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(spin));
        pose.scale(size, size, size);
        itemState.render(pose, buffers, LIGHT, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /** A flat four-cornered shape, seen from both sides: solid colour, or glowing (added light). */
    void quad(float[] a, float[] b, float[] c, float[] d, int argb, boolean glow)
    {
        if(glow)
        {
            deferred(p -> quadNow(a, b, c, d, argb, true));
        }
        else
        {
            quadNow(a, b, c, d, argb, false);
        }
    }

    private void quadNow(float[] a, float[] b, float[] c, float[] d, int argb, boolean glow)
    {
        VertexConsumer out = buffers.getBuffer(glow ? RenderType.lightning() : RenderType.debugQuads());
        PoseStack.Pose p = pose.last();
        for(float[] v : new float[][] { a, b, c, d, d, c, b, a })
        {
            out.addVertex(p, v[0], v[1], v[2]).setColor(argb);
        }
    }

    /** A rectangle in the plane z = {@code z}, facing along z. */
    void faceZ(float x0, float y0, float x1, float y1, float z, int argb, boolean glow)
    {
        quad(new float[] { x0, y0, z }, new float[] { x1, y0, z }, new float[] { x1, y1, z }, new float[] { x0, y1, z }, argb, glow);
    }

    /** A rectangle in the plane x = {@code x}. */
    void faceX(float z0, float y0, float z1, float y1, float x, int argb, boolean glow)
    {
        quad(new float[] { x, y0, z0 }, new float[] { x, y0, z1 }, new float[] { x, y1, z1 }, new float[] { x, y1, z0 }, argb, glow);
    }

    /** A rectangle in the plane y = {@code y}. */
    void faceY(float x0, float z0, float x1, float z1, float y, int argb, boolean glow)
    {
        quad(new float[] { x0, y, z0 }, new float[] { x1, y, z0 }, new float[] { x1, y, z1 }, new float[] { x0, y, z1 }, argb, glow);
    }

    void box(AABB box, int argb)
    {
        deferred(p -> boxNow(box, argb));
    }

    private void boxNow(AABB box, int argb)
    {
        ShapeRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), box,
                ARGB.red(argb) / 255F, ARGB.green(argb) / 255F, ARGB.blue(argb) / 255F, ARGB.alpha(argb) / 255F);
    }

    void line(float x0, float y0, float z0, float x1, float y1, float z1, int argb)
    {
        deferred(p -> lineNow(x0, y0, z0, x1, y1, z1, argb));
    }

    private void lineNow(float x0, float y0, float z0, float x1, float y1, float z1, int argb)
    {
        VertexConsumer out = buffers.getBuffer(RenderType.lines());
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if(len < 1.0E-4F)
        {
            return;
        }
        PoseStack.Pose p = pose.last();
        out.addVertex(p, x0, y0, z0).setColor(argb).setNormal(p, dx / len, dy / len, dz / len);
        out.addVertex(p, x1, y1, z1).setColor(argb).setNormal(p, dx / len, dy / len, dz / len);
    }

    /** A wire ball: rings round its upright axis and its latitudes. */
    void sphere(float cx, float cy, float cz, float r, int argb)
    {
        if(r <= 0.01F)
        {
            return;
        }
        int segments = 32;
        for(int m = 0; m < 6; m++)
        {
            float angle = (float)Math.PI * m / 6F;
            float sx = Mth.cos(angle), sz = Mth.sin(angle);
            for(int i = 0; i < segments; i++)
            {
                float a0 = (float)Math.PI * 2F * i / segments, a1 = (float)Math.PI * 2F * (i + 1) / segments;
                line(cx + r * Mth.cos(a0) * sx, cy + r * Mth.sin(a0), cz + r * Mth.cos(a0) * sz,
                        cx + r * Mth.cos(a1) * sx, cy + r * Mth.sin(a1), cz + r * Mth.cos(a1) * sz, argb);
            }
        }
        for(int l = -2; l <= 2; l++)
        {
            float lat = (float)Math.PI * l / 6F;
            float ry = r * Mth.sin(lat), rr = r * Mth.cos(lat);
            for(int i = 0; i < segments; i++)
            {
                float a0 = (float)Math.PI * 2F * i / segments, a1 = (float)Math.PI * 2F * (i + 1) / segments;
                line(cx + rr * Mth.cos(a0), cy + ry, cz + rr * Mth.sin(a0), cx + rr * Mth.cos(a1), cy + ry, cz + rr * Mth.sin(a1), argb);
            }
        }
    }

    /** A row of blocks of grass as the ground (top at y 0), {@code w} by {@code d} from the origin. */
    void ground(int x0, int z0, int w, int d, BlockState state)
    {
        for(int x = x0; x < x0 + w; x++)
        {
            for(int z = z0; z < z0 + d; z++)
            {
                block(state, x, -1, z);
            }
        }
    }

    /** Passes vertices on with their colour multiplied by one colour (a hologram's tint). */
    private record Tint(VertexConsumer out, int argb) implements VertexConsumer
    {
        @Override
        public VertexConsumer addVertex(float x, float y, float z)
        {
            out.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a)
        {
            out.setColor(r * ARGB.red(argb) / 255, g * ARGB.green(argb) / 255, b * ARGB.blue(argb) / 255, a * ARGB.alpha(argb) / 255);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v)
        {
            out.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v)
        {
            out.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v)
        {
            out.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z)
        {
            out.setNormal(x, y, z);
            return this;
        }
    }
}
