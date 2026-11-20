package ryzlar.glass.client.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import ryzlar.glass.Glass;

import java.util.HashMap;
import java.util.Map;

/**
 * Render types for the screens. 1.12 drew these with raw GL calls and a stencil buffer; 1.21 needs render pipelines.
 */
public final class GlassRenderTypes
{
    /** Flat coloured faces (the fade flash, and the screen before the camera view is ready). */
    public static final RenderPipeline PLANES_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Glass.id("pipeline/glass_planes"))
            .withCull(true)
            .withDepthWrite(false)
            .build();
    public static final RenderPipeline PLANES_NO_CULL_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Glass.id("pipeline/glass_planes_no_cull"))
            .withCull(false)
            .withDepthWrite(false)
            .build();
    public static final RenderPipeline SOLID_PLANES_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Glass.id("pipeline/glass_solid_planes"))
            .withCull(false)
            .build();

    /** The terminal's camera view, sampled in screen space so it lines up with the world behind the glass. */
    private static RenderPipeline screenPipeline(String name, boolean cull, boolean wipe)
    {
        RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
                .withLocation(Glass.id("pipeline/" + name))
                .withVertexShader(Glass.id("core/glass_screen"))
                .withFragmentShader(Glass.id("core/glass_screen"))
                .withSampler("Sampler0")
                .withVertexFormat(DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS)
                .withCull(cull);
        if(wipe)
        {
            //Sampler1: the old image, shown outside the wave while the screen switches feed.
            builder.withShaderDefine("WIPE").withSampler("Sampler1");
        }
        return builder.build();
    }

    public static final RenderPipeline SCREEN_PIPELINE = screenPipeline("glass_screen", true, false);
    public static final RenderPipeline SCREEN_NO_CULL_PIPELINE = screenPipeline("glass_screen_no_cull", false, false);
    public static final RenderPipeline WIPE_PIPELINE = screenPipeline("glass_screen_wipe", true, true);
    public static final RenderPipeline WIPE_NO_CULL_PIPELINE = screenPipeline("glass_screen_wipe_no_cull", false, true);

    public static final RenderType PLANES = RenderType.create("glass_planes", 1536, false, true, PLANES_PIPELINE,
            RenderType.CompositeState.builder().createCompositeState(false));
    public static final RenderType PLANES_NO_CULL = RenderType.create("glass_planes_no_cull", 1536, false, true, PLANES_NO_CULL_PIPELINE,
            RenderType.CompositeState.builder().createCompositeState(false));
    public static final RenderType SOLID_PLANES = RenderType.create("glass_solid_planes", 1536, false, false, SOLID_PLANES_PIPELINE,
            RenderType.CompositeState.builder().createCompositeState(false));

    /**
     * Cloak ground: block tops lit with the lightmap (so day, night and torches look like the real ground), with a copy
     * of the terrain shader (the text shader reads the lightmap unfiltered, which came out a little brighter).
     */
    public static final RenderPipeline CLOAK_GROUND_PIPELINE = RenderPipeline.builder(RenderPipelines.TEXT_SNIPPET, RenderPipelines.FOG_SNIPPET)
            .withLocation(Glass.id("pipeline/glass_cloak_ground"))
            .withVertexShader(Glass.id("core/glass_ground"))
            .withFragmentShader(Glass.id("core/glass_ground"))
            .withShaderDefine("ALPHA_CUTOUT", 0.5F)
            .withSampler("Sampler0")
            .withSampler("Sampler2")
            .withCull(true)
            .build();
    /** Cloak ground: the made-up ground under the hidden part, and the real ground beside and just behind it. */
    public static final RenderType CLOAK_GROUND = RenderType.create("glass_cloak_ground", 1536, false, false, CLOAK_GROUND_PIPELINE,
            RenderType.CompositeState.builder()
                    //Mipmapped like the terrain, or the ground looks grainier (and lighter) than the real ground around it.
                    .setTextureState(new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS, true))
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .createCompositeState(false));
    /**
     * Cloak ground just behind the hidden part: drawn over the real ground at the same height, so it is pulled towards
     * the camera in depth instead of lifted (with the view's far plane pushed far out, a fixed lift was smaller than the
     * depth buffer's steps a few dozen blocks away, and the two grounds flickered through each other).
     */
    public static final RenderPipeline CLOAK_RING_PIPELINE = RenderPipeline.builder(RenderPipelines.TEXT_SNIPPET, RenderPipelines.FOG_SNIPPET)
            .withLocation(Glass.id("pipeline/glass_cloak_ring"))
            .withVertexShader(Glass.id("core/glass_ground"))
            .withFragmentShader(Glass.id("core/glass_ground"))
            .withShaderDefine("ALPHA_CUTOUT", 0.5F)
            .withSampler("Sampler0")
            .withSampler("Sampler2")
            .withCull(true)
            .withDepthBias(-1.0F, -10.0F)
            .build();
    public static final RenderType CLOAK_RING = RenderType.create("glass_cloak_ring", 1536, false, false, CLOAK_RING_PIPELINE,
            RenderType.CompositeState.builder()
                    .setTextureState(new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS, true))
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .createCompositeState(false));

    /**
     * Holograms, with the nano wave: glowing, see-through block textures sorted back to front (BLUE: in cyan only), or
     * SOLID: opaque, like real blocks.
     */
    private static RenderPipeline hologramPipeline(String name, String define)
    {
        RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
                .withLocation(Glass.id("pipeline/" + name))
                .withVertexShader(Glass.id("core/glass_holo"))
                .withFragmentShader(Glass.id("core/glass_holo"))
                .withSampler("Sampler0")
                .withVertexFormat(DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS)
                //A 1:1 projection lies right on top of real blocks (its floor on the real floor): pulled a hair towards
                //the eye it wins where they meet, instead of the two flickering through each other.
                .withDepthBias(-1.0F, -10.0F)
                .withCull(true);
        if(define.equals("SOLID"))
        {
            builder.withShaderDefine("SOLID");
        }
        else
        {
            builder.withBlend(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT).withDepthWrite(false);
            if(!define.isEmpty())
            {
                builder.withShaderDefine(define);
            }
        }
        return builder.build();
    }

    public static final RenderPipeline HOLOGRAM_PIPELINE = hologramPipeline("glass_hologram", "");
    public static final RenderPipeline HOLOGRAM_BLUE_PIPELINE = hologramPipeline("glass_hologram_blue", "BLUE");
    public static final RenderPipeline HOLOGRAM_SOLID_PIPELINE = hologramPipeline("glass_hologram_solid", "SOLID");
    private static final RenderType HOLOGRAM = hologramType("glass_hologram", HOLOGRAM_PIPELINE, true);
    private static final RenderType HOLOGRAM_BLUE = hologramType("glass_hologram_blue", HOLOGRAM_BLUE_PIPELINE, true);
    private static final RenderType HOLOGRAM_SOLID = hologramType("glass_hologram_solid", HOLOGRAM_SOLID_PIPELINE, false);

    private static RenderType hologramType(String name, RenderPipeline pipeline, boolean sort)
    {
        return RenderType.create(name, 1536, false, sort, pipeline, RenderType.CompositeState.builder()
                .setTextureState(new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS, true))
                .createCompositeState(false));
    }

    /** 0: hologram, 1: holo blue, 2: solid. */
    public static RenderType hologram(int look)
    {
        return look == 1 ? HOLOGRAM_BLUE : look == 2 ? HOLOGRAM_SOLID : HOLOGRAM;
    }

    /** The back of a screen and the inside of a cloak field: glowing edges, a nano grid close up, ripples (glass_back). */
    public static final RenderPipeline BACK_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Glass.id("pipeline/glass_back"))
            .withVertexShader(Glass.id("core/glass_back"))
            .withFragmentShader(Glass.id("core/glass_back"))
            .withVertexFormat(DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS)
            .withBlend(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT)
            .withDepthWrite(false)
            .withCull(false)
            .build();
    public static final RenderType BACK = RenderType.create("glass_back", 1536, false, true, BACK_PIPELINE,
            RenderType.CompositeState.builder().createCompositeState(false));

    /** A holo cube's scan: light and a grid running over the real room's faces, added on top (glass_scan). */
    public static final RenderPipeline SCAN_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Glass.id("pipeline/glass_scan"))
            .withVertexShader(Glass.id("core/glass_scan"))
            .withFragmentShader(Glass.id("core/glass_scan"))
            .withVertexFormat(DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS)
            .withBlend(com.mojang.blaze3d.pipeline.BlendFunction.LIGHTNING)
            .withDepthWrite(false)
            .withDepthBias(-1.0F, -10.0F)
            .withCull(true)
            .build();
    public static final RenderType SCAN = RenderType.create("glass_scan", 1536, false, false, SCAN_PIPELINE,
            RenderType.CompositeState.builder().createCompositeState(false));

    /** The dome round a holo cube's simulation, showing the scan's panorama (glass_dome). */
    public static final RenderPipeline DOME_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Glass.id("pipeline/glass_dome"))
            .withVertexShader(Glass.id("core/glass_dome"))
            .withFragmentShader(Glass.id("core/glass_dome"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withBlend(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();
    /** The dome round a holo cube's simulation, seen from inside: a black void with the BARF edge (glass_dome). */
    public static final RenderType DOME = RenderType.create("glass_dome", 1536, false, false, DOME_PIPELINE,
            RenderType.CompositeState.builder().createCompositeState(false));

    private static final Map<ResourceLocation, RenderType> SCREENS = new HashMap<>();
    private static final Map<ResourceLocation, RenderType> SCREENS_NO_CULL = new HashMap<>();
    private record WipeKey(ResourceLocation image, ResourceLocation old, boolean cull) {}
    private static final Map<WipeKey, RenderType> WIPES = new HashMap<>();

    private GlassRenderTypes() {}

    public static RenderType planes(boolean cull)
    {
        return cull ? PLANES : PLANES_NO_CULL;
    }

    public static RenderType screen(ResourceLocation texture, boolean cull)
    {
        return (cull ? SCREENS : SCREENS_NO_CULL).computeIfAbsent(texture, tex -> RenderType.create(
                "glass_screen" + (cull ? "" : "_no_cull"), 1536, false, false, cull ? SCREEN_PIPELINE : SCREEN_NO_CULL_PIPELINE,
                RenderType.CompositeState.builder()
                        .setTextureState(new RenderStateShard.TextureStateShard(tex, false))
                        .createCompositeState(false)));
    }

    /** The new image (Sampler0) wiping over the old one (Sampler1). */
    public static RenderType wipe(ResourceLocation image, ResourceLocation old, boolean cull)
    {
        return WIPES.computeIfAbsent(new WipeKey(image, old, cull), key -> RenderType.create(
                "glass_screen_wipe" + (cull ? "" : "_no_cull"), 1536, false, false, cull ? WIPE_PIPELINE : WIPE_NO_CULL_PIPELINE,
                RenderType.CompositeState.builder()
                        .setTextureState(RenderStateShard.MultiTextureStateShard.builder().add(image, false).add(old, false).build())
                        .createCompositeState(false)));
    }

    public static void forget(ResourceLocation texture)
    {
        SCREENS.remove(texture);
        SCREENS_NO_CULL.remove(texture);
        WIPES.keySet().removeIf(key -> key.image().equals(texture) || key.old().equals(texture));
    }
}
