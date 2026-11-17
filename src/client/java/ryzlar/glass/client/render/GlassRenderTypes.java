package ryzlar.glass.client.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
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
    private static RenderPipeline screenPipeline(String name, boolean cull)
    {
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Glass.id("pipeline/" + name))
                .withVertexShader(Glass.id("core/glass_screen"))
                .withFragmentShader(Glass.id("core/glass_screen"))
                .withSampler("Sampler0")
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                .withCull(cull)
                .build();
    }

    public static final RenderPipeline SCREEN_PIPELINE = screenPipeline("glass_screen", true);
    public static final RenderPipeline SCREEN_NO_CULL_PIPELINE = screenPipeline("glass_screen_no_cull", false);

    public static final RenderType PLANES = RenderType.create("glass_planes", 1536, false, true, PLANES_PIPELINE,
            RenderType.CompositeState.builder().createCompositeState(false));
    public static final RenderType PLANES_NO_CULL = RenderType.create("glass_planes_no_cull", 1536, false, true, PLANES_NO_CULL_PIPELINE,
            RenderType.CompositeState.builder().createCompositeState(false));
    public static final RenderType SOLID_PLANES = RenderType.create("glass_solid_planes", 1536, false, false, SOLID_PLANES_PIPELINE,
            RenderType.CompositeState.builder().createCompositeState(false));

    private static final Map<ResourceLocation, RenderType> SCREENS = new HashMap<>();
    private static final Map<ResourceLocation, RenderType> SCREENS_NO_CULL = new HashMap<>();

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

    public static void forget(ResourceLocation texture)
    {
        SCREENS.remove(texture);
        SCREENS_NO_CULL.remove(texture);
    }
}
