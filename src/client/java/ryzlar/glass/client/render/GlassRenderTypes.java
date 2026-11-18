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
