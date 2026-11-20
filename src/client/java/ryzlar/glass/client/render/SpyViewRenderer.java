package ryzlar.glass.client.render;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4fStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import ryzlar.glass.Glass;
import ryzlar.glass.client.TerminalPlacement;
import ryzlar.glass.client.mixin.GameRendererAccessor;
import ryzlar.glass.client.mixin.LevelRendererAccessor;
import ryzlar.glass.client.mixin.SectionOcclusionGraphAccessor;
import net.minecraft.client.renderer.culling.Frustum;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Renders what each terminal sees into its own offscreen image, which the glass then shows (sampled in screen space).
 * <p>
 * This replaces iChunUtil's WorldPortalRenderer, which drew the other view straight into the stencilled glass.
 * 1.21 has no supported way to render the world twice, so each view runs a second {@link LevelRenderer#renderLevel}
 * pass before the main one, with the main render target swapped for the view's own, and with its own copy of the
 * section visibility state so the two passes do not undo each other's work.
 */
public final class SpyViewRenderer
{
    public static final SpyViewRenderer INSTANCE = new SpyViewRenderer();

    /** Views drawn per frame at most. Each one costs about as much as drawing the world again. */
    private static final int MAX_VIEWS_PER_FRAME = 6;
    /** Suffix of a view that is being replaced: it keeps drawing until the new view has wiped over it. */
    public static final String OLD_SUFFIX = "#old";
    /** Frames a view is kept after the glass stops asking for it. */
    private static final int RELEASE_AFTER_FRAMES = 200;

    private final Map<String, SpyView> views = new HashMap<>();
    private long frame;
    private int nextViewId;

    @Nullable
    private SpyView rendering;
    @Nullable
    private RenderTarget targetOverride;
    @Nullable
    private Vec3 mainCameraPosition;
    @Nullable
    private SectionOcclusionGraph mainGraph;
    /** While a view renders: its projection, and the same without the clip plane (for the sky, see SkyRendererMixin). */
    @Nullable
    private GpuBufferSlice viewProjection;
    @Nullable
    private GpuBufferSlice skyProjection;

    private SpyViewRenderer() {}

    private static final class SpyView
    {
        final String channel;
        final ResourceLocation textureId;
        final SpyTexture texture = new SpyTexture();
        final SpyCamera camera = new SpyCamera();
        final PerspectiveProjectionMatrixBuffer projection;
        /** The projection without the clip plane, for drawing a cloak's ground back in. */
        final PerspectiveProjectionMatrixBuffer floorProjection;
        final SectionOcclusionGraph graph = new SectionOcclusionGraph();
        final ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections = new ObjectArrayList<>(10000);
        final ObjectArrayList<SectionRenderDispatcher.RenderSection> nearbyVisibleSections = new ObjectArrayList<>(50);
        @Nullable
        ViewArea viewArea;
        @Nullable
        TextureTarget target;
        TerminalPlacement placement;
        long requestedFrame = -1;
        boolean rendered;
        boolean needsPrime = true;
        long keepAliveUntil = Long.MIN_VALUE;
        Vector4f fogColor = new Vector4f(0F, 0F, 0F, 1F);
        double prevCamX = Double.MIN_VALUE, prevCamY = Double.MIN_VALUE, prevCamZ = Double.MIN_VALUE;
        double prevCamRotX = Double.MIN_VALUE, prevCamRotY = Double.MIN_VALUE;

        SpyView(String channel, int index)
        {
            this.channel = channel;
            this.textureId = Glass.id("spy_view/" + index + "_" + Integer.toHexString(channel.hashCode()).toLowerCase(Locale.ROOT));
            this.projection = new PerspectiveProjectionMatrixBuffer("glass_spy_view");
            this.floorProjection = new PerspectiveProjectionMatrixBuffer("glass_spy_view_floor");
        }
    }

    /** Hands the render target's colour image to the texture manager without owning it. */
    private static final class SpyTexture extends AbstractTexture
    {
        void set(TextureTarget target)
        {
            this.texture = target.getColorTexture();
            this.textureView = target.getColorTextureView();
        }

        @Override
        public void close()
        {
            this.texture = null;
            this.textureView = null;
        }
    }

    // ---- State the mixins read ----

    public boolean isRendering()
    {
        return rendering != null;
    }

    @Nullable
    public RenderTarget targetOverride()
    {
        return targetOverride;
    }

    /** A holo cube's panorama is being drawn (see Panoramas): the world draws into its target, and no views are drawn. */
    private boolean capturing;

    public void beginCapture(RenderTarget target)
    {
        capturing = true;
        targetOverride = target;
    }

    public void endCapture()
    {
        capturing = false;
        targetOverride = null;
    }

    @Nullable
    public Vec3 mainCameraPosition()
    {
        return mainCameraPosition;
    }

    /** Called around sky and cloud drawing: during a view, switch to the projection without the clip plane and back. */
    public void useSkyProjection(boolean sky)
    {
        if(rendering != null && viewProjection != null && skyProjection != null)
        {
            RenderSystem.setProjectionMatrix(sky ? skyProjection : viewProjection, ProjectionType.PERSPECTIVE);
        }
    }

    public Vector4f currentFogColor()
    {
        return rendering != null ? rendering.fogColor : new Vector4f(0F, 0F, 0F, 1F);
    }

    /** Sends a section-visibility event to every occlusion graph except the one LevelRenderer is using right now. */
    public void forEachOtherGraph(SectionOcclusionGraph current, Consumer<SectionOcclusionGraph> action)
    {
        if(mainGraph != null && mainGraph != current)
        {
            action.accept(mainGraph);
        }
        for(SpyView view : views.values())
        {
            if(view.graph != current && view.viewArea != null)
            {
                action.accept(view.graph);
            }
        }
    }

    // ---- Called by the glass renderer ----

    /** Asks for this channel's view to be drawn next frame. Returns the texture holding the last drawn view, if any. */
    @Nullable
    public ResourceLocation request(TerminalPlacement placement)
    {
        SpyView view = views.get(placement.channel);
        if(view == null)
        {
            view = new SpyView(placement.channel, nextViewId++);
            views.put(placement.channel, view);
        }
        view.placement = placement;
        view.requestedFrame = frame;
        return view.rendered ? view.textureId : null;
    }

    /** The last image drawn for this screen, without asking for a new one (for the switch-off animation). */
    @Nullable
    public ResourceLocation textureFor(String channel)
    {
        SpyView view = views.get(channel);
        return view != null && view.rendered ? view.textureId : null;
    }

    public Vector4f lastFogColor(String channel)
    {
        SpyView view = views.get(channel);
        return view != null ? view.fogColor : new Vector4f(0F, 0F, 0F, 1F);
    }

    // ---- Rendering ----

    /**
     * Called by GameRendererMixin each frame, before the world is drawn, with the world's projection matrix (view bobbing
     * and all). The glass shows the views in screen space, so they must use that exact projection.
     */
    public void renderViews(GameRenderer gameRenderer, Camera mainCamera, DeltaTracker deltaTracker, Matrix4f mainProjection)
    {
        Minecraft mc = Minecraft.getInstance();
        long thisFrame = frame++;
        if(mc.level == null || views.isEmpty() || rendering != null || capturing)
        {
            return;
        }

        int drawn = 0;
        Iterator<SpyView> ite = views.values().iterator();
        List<SpyView> toRender = new ArrayList<>();
        while(ite.hasNext())
        {
            SpyView view = ite.next();
            //While a screen switches off, its image stays live until the wave has shrunk back into the projector. The
            //glass only reads the image then (it does not request it), so it must not count as unused: at a high frame
            //rate the release below came before the wave was done, and the rest of the screen vanished at once.
            boolean switchingOff = view.placement != null && view.keepAliveUntil >= mc.level.getGameTime();
            if(!switchingOff && thisFrame - view.requestedFrame > RELEASE_AFTER_FRAMES)
            {
                destroy(mc, view);
                ite.remove();
                continue;
            }
            boolean wanted = view.requestedFrame >= thisFrame - 1 && view.placement != null && view.placement.isValid();
            if((wanted || switchingOff) && drawn < MAX_VIEWS_PER_FRAME)
            {
                toRender.add(view);
                drawn++;
            }
        }
        for(SpyView view : toRender)
        {
            try
            {
                render(mc, gameRenderer, mainCamera, deltaTracker, view, mainProjection);
            }
            catch(RuntimeException e)
            {
                Glass.LOGGER.error("Failed to render the view for GLASS channel {}", view.channel, e);
                view.rendered = false;
            }
        }
    }

    private void render(Minecraft mc, GameRenderer gameRenderer, Camera mainCamera, DeltaTracker deltaTracker, SpyView view, Matrix4f mainProjection)
    {
        ClientLevel level = mc.level;
        LevelRenderer levelRenderer = mc.levelRenderer;
        LevelRendererAccessor lr = (LevelRendererAccessor)levelRenderer;
        GameRendererAccessor gr = (GameRendererAccessor)gameRenderer;
        ViewArea viewArea = lr.glass$getViewArea();
        if(viewArea == null)
        {
            return;
        }

        //Offscreen target, the same size as the screen so screen-space sampling lines up.
        RenderTarget main = mc.getMainRenderTarget();
        if(view.target == null)
        {
            view.target = new TextureTarget("GLASS view " + view.channel, main.width, main.height, true);
            view.target.setFilterMode(FilterMode.LINEAR);
            view.texture.set(view.target);
            mc.getTextureManager().register(view.textureId, view.texture);
        }
        else if(view.target.width != main.width || view.target.height != main.height)
        {
            view.target.resize(main.width, main.height);
            view.target.setFilterMode(FilterMode.LINEAR);
            view.texture.set(view.target);
        }

        if(view.viewArea != viewArea)
        {
            view.viewArea = viewArea;
            view.needsPrime = true;
            view.graph.waitAndReset(viewArea);
            view.prevCamX = view.prevCamY = view.prevCamZ = Double.MIN_VALUE;
            view.prevCamRotX = view.prevCamRotY = Double.MIN_VALUE;
        }

        //Camera: the player's view, carried over from the projector to the terminal.
        TerminalPlacement placement = view.placement;
        float partialTick = mainCamera.getPartialTickTime();
        Vec3 position = placement.mapPosition(mainCamera.getPosition());
        Quaternionf rotation = placement.mapRotation(mainCamera.rotation());
        Entity entity = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
        view.camera.configure(level, entity, position, rotation, partialTick);

        float fov = gr.glass$getFov(mainCamera, partialTick, true);
        Matrix4f cullProjection = gameRenderer.getProjectionMatrix(Math.max(fov, mc.options.fov().get().intValue()));
        //Same projection as the world (it includes view bobbing): rebuilding it from the FOV made the view sway against the glass while walking.
        //Its far plane is pushed much further out, because the clipping below tilts the far plane towards the camera: with
        //the terminal right against blocks and the screen seen at an angle, it cut the view off diagonally.
        Matrix4f projection = withFarPlaneFurther(mainProjection, gameRenderer.getProjectionMatrix(fov));
        Matrix4f unclipped = new Matrix4f(projection);
        Matrix4f modelView = new Matrix4f().rotation(rotation.conjugate(new Quaternionf()));
        if(placement.cullRender())
        {
            applyObliqueClip(projection, modelView, position, placement);
        }

        FogRenderer fogRenderer = gr.glass$getFogRenderer();
        boolean foggy = level.effects().isFoggyAt(view.camera.getBlockPosition().getX(), view.camera.getBlockPosition().getZ())
                || mc.gui.getBossOverlay().shouldCreateWorldFog();
        Vector4f fogColor = fogRenderer.setupFog(view.camera, mc.options.getEffectiveRenderDistance(), foggy, deltaTracker, gameRenderer.getDarkenWorldAmount(partialTick), level);
        view.fogColor = new Vector4f(fogColor);
        GpuBufferSlice fogBuffer = fogRenderer.getBuffer(FogRenderer.FogMode.WORLD);

        skyProjection = view.floorProjection.getBuffer(unclipped);
        viewProjection = view.projection.getBuffer(projection);
        RenderSystem.setProjectionMatrix(viewProjection, ProjectionType.PERSPECTIVE);
        levelRenderer.prepareCullFrustum(position, modelView, cullProjection);

        //Swap in this view's section visibility state.
        SectionOcclusionGraph savedGraph = lr.glass$getSectionOcclusionGraph();
        ObjectArrayList<SectionRenderDispatcher.RenderSection> savedVisible = lr.glass$getVisibleSections();
        ObjectArrayList<SectionRenderDispatcher.RenderSection> savedNearby = lr.glass$getNearbyVisibleSections();
        int savedSectionX = lr.glass$getLastCameraSectionX();
        int savedSectionY = lr.glass$getLastCameraSectionY();
        int savedSectionZ = lr.glass$getLastCameraSectionZ();
        double savedPrevCamX = lr.glass$getPrevCamX();
        double savedPrevCamY = lr.glass$getPrevCamY();
        double savedPrevCamZ = lr.glass$getPrevCamZ();
        double savedPrevCamRotX = lr.glass$getPrevCamRotX();
        double savedPrevCamRotY = lr.glass$getPrevCamRotY();

        lr.glass$setSectionOcclusionGraph(view.graph);
        lr.glass$setVisibleSections(view.visibleSections);
        lr.glass$setNearbyVisibleSections(view.nearbyVisibleSections);
        lr.glass$setPrevCamX(view.prevCamX);
        lr.glass$setPrevCamY(view.prevCamY);
        lr.glass$setPrevCamZ(view.prevCamZ);
        lr.glass$setPrevCamRotX(view.prevCamRotX);
        lr.glass$setPrevCamRotY(view.prevCamRotY);

        mainGraph = savedGraph;
        mainCameraPosition = mainCamera.getPosition();
        targetOverride = view.target;
        rendering = view;
        //The camera sits behind the terminal, often inside blocks; occlusion culling from there hides sections the screen
        //should show, so terminal views only use frustum culling.
        boolean smartCull = mc.smartCull;
        mc.smartCull = false;
        try
        {
            if(view.needsPrime)
            {
                primeGraph(mc, level, view, modelView, cullProjection, position);
            }
            levelRenderer.renderLevel(gr.glass$getResourcePool(), deltaTracker, false, view.camera, modelView, projection, fogBuffer, fogColor, !foggy);
            CloakFill fill = placement.cloakFill();
            if(fill != null && view.target != null)
            {
                drawCloakGround(mc, level, fill, view, unclipped, projection, modelView, position);
            }
            view.rendered = true;
        }
        finally
        {
            mc.smartCull = smartCull;
            rendering = null;
            viewProjection = null;
            skyProjection = null;
            targetOverride = null;
            mainCameraPosition = null;

            view.prevCamX = lr.glass$getPrevCamX();
            view.prevCamY = lr.glass$getPrevCamY();
            view.prevCamZ = lr.glass$getPrevCamZ();
            view.prevCamRotX = lr.glass$getPrevCamRotX();
            view.prevCamRotY = lr.glass$getPrevCamRotY();

            lr.glass$setSectionOcclusionGraph(savedGraph);
            lr.glass$setVisibleSections(savedVisible);
            lr.glass$setNearbyVisibleSections(savedNearby);
            lr.glass$setLastCameraSectionX(savedSectionX);
            lr.glass$setLastCameraSectionY(savedSectionY);
            lr.glass$setLastCameraSectionZ(savedSectionZ);
            lr.glass$setPrevCamX(savedPrevCamX);
            lr.glass$setPrevCamY(savedPrevCamY);
            lr.glass$setPrevCamZ(savedPrevCamZ);
            lr.glass$setPrevCamRotX(savedPrevCamRotX);
            lr.glass$setPrevCamRotY(savedPrevCamRotY);
        }
    }

    /**
     * The world projection (with view bobbing etc. folded in) with its far plane moved 16 times further out. The extra
     * transforms are recovered as base^-1 * main and put back on top of a base projection with the new far plane.
     */
    private static Matrix4f withFarPlaneFurther(Matrix4f mainProjection, Matrix4f base)
    {
        float m22 = base.m22();
        float m32 = base.m32();
        float near = m32 / (m22 - 1F);
        float far = m32 / (m22 + 1F);
        if(!Float.isFinite(near) || !Float.isFinite(far) || near <= 0F || far <= near)
        {
            return new Matrix4f(mainProjection);
        }
        Matrix4f extra = new Matrix4f(base).invert().mul(mainProjection);
        float newFar = Math.max(far * 16F, 4096F);
        Matrix4f farBase = new Matrix4f(base);
        farBase.m22((newFar + near) / (near - newFar));
        farBase.m32(2F * newFar * near / (near - newFar));
        return farBase.mul(extra);
    }

    /**
     * Cloak: puts the ground back (see {@link CloakFill}). First the real ground just behind the hidden part, drawn
     * again without the hidden blocks' shading, tested against the view's own depth (same projection). Then the depth is
     * cleared and the ground under and beside the hidden part is drawn without the clip plane: it is nearer than anything
     * else the cloak shows there, so it only has to sort itself out.
     */
    private static void drawCloakGround(Minecraft mc, ClientLevel level, CloakFill fill, SpyView view,
                                        Matrix4f unclipped, Matrix4f clipped, Matrix4f modelView, Vec3 camera)
    {
        fill.refresh(level);
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        stack.mul(modelView);
        try
        {
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            RenderSystem.setProjectionMatrix(view.projection.getBuffer(clipped), ProjectionType.PERSPECTIVE);
            fill.drawRing(buffers.getBuffer(GlassRenderTypes.CLOAK_GROUND), camera);
            buffers.endBatch(GlassRenderTypes.CLOAK_GROUND);

            RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(view.target.getDepthTexture(), 1.0D);
            RenderSystem.setProjectionMatrix(view.floorProjection.getBuffer(unclipped), ProjectionType.PERSPECTIVE);
            fill.drawFill(buffers.getBuffer(GlassRenderTypes.CLOAK_GROUND), camera);
            buffers.endBatch(GlassRenderTypes.CLOAK_GROUND);
        }
        finally
        {
            stack.popMatrix();
        }
    }

    /** Keeps a screen's images (one per side for a cloak) updating while it switches off. */
    public void keepAlive(String screen, long untilGameTime)
    {
        for(Map.Entry<String, SpyView> e : views.entrySet())
        {
            if(isLiveViewOf(e.getKey(), screen))
            {
                e.getValue().keepAliveUntil = untilGameTime;
            }
        }
    }

    /** The screen's own view, or the view of one side of its cloak (not a view being replaced). */
    private static boolean isLiveViewOf(String key, String screen)
    {
        return (key.equals(screen) || key.startsWith(screen + "/")) && !key.endsWith(OLD_SUFFIX);
    }

    private static boolean isAnyViewOf(String key, String screen)
    {
        return key.equals(screen) || key.startsWith(screen + "/") || key.startsWith(screen + OLD_SUFFIX);
    }

    /**
     * The screen switches to another feed: its current views become the "old" views, which keep drawing until the given
     * game time so the new feed can wipe over them. Returns false if there was no image to wipe over.
     */
    public boolean retire(String screen, long untilGameTime)
    {
        releaseRetired(screen);
        Minecraft mc = Minecraft.getInstance();
        boolean hadImage = false;
        Map<String, SpyView> moved = new HashMap<>();
        Iterator<Map.Entry<String, SpyView>> ite = views.entrySet().iterator();
        while(ite.hasNext())
        {
            Map.Entry<String, SpyView> e = ite.next();
            if(isLiveViewOf(e.getKey(), screen))
            {
                ite.remove();
                SpyView view = e.getValue();
                if(view.rendered)
                {
                    hadImage = true;
                    view.keepAliveUntil = untilGameTime;
                    moved.put(e.getKey() + OLD_SUFFIX, view);
                }
                else
                {
                    destroy(mc, view);
                }
            }
        }
        views.putAll(moved);
        return hadImage;
    }

    /** Drops the views a screen was wiping away from. */
    public void releaseRetired(String screen)
    {
        Minecraft mc = Minecraft.getInstance();
        views.entrySet().removeIf(e -> {
            if(isAnyViewOf(e.getKey(), screen) && e.getKey().endsWith(OLD_SUFFIX))
            {
                destroy(mc, e.getValue());
                return true;
            }
            return false;
        });
    }

    /**
     * A new view's graph of visible sections is normally built on a background thread, so its first frames would show
     * no terrain. Build it now and wait for it (a few milliseconds, once per view) so the first frame is complete.
     */
    private static void primeGraph(Minecraft mc, ClientLevel level, SpyView view, Matrix4f modelView, Matrix4f cullProjection, Vec3 position)
    {
        view.needsPrime = false;
        Frustum frustum = new Frustum(modelView, cullProjection);
        frustum.prepare(position.x, position.y, position.z);
        view.graph.update(false, view.camera, frustum, view.visibleSections, level.getChunkSource().getLoadedEmptySections());
        Future<?> task = ((SectionOcclusionGraphAccessor)view.graph).glass$getFullUpdateTask();
        if(task != null)
        {
            try
            {
                task.get(250L, TimeUnit.MILLISECONDS);
            }
            catch(Exception e)
            {
                Glass.LOGGER.debug("GLASS view graph for {} not ready in time", view.channel, e);
            }
        }
    }

    /**
     * Moves the near plane onto the terminal's front face (Eric Lengyel's oblique near-plane clipping), so blocks
     * behind the terminal do not cover the view. 1.12 did the same with a GL clip plane.
     */
    private static void applyObliqueClip(Matrix4f projection, Matrix4f modelView, Vec3 cameraPos, TerminalPlacement placement)
    {
        Vec3 planePoint = placement.clipPlanePoint();
        Vector3f point = modelView.transformPosition(new Vector3f((float)(planePoint.x - cameraPos.x), (float)(planePoint.y - cameraPos.y), (float)(planePoint.z - cameraPos.z)));
        Vector3f normal = modelView.transformDirection(placement.clipPlaneNormal());
        float distance = normal.dot(point);
        if(distance <= 0.05F)
        {
            return; //camera is not behind the plane (or too close to it): leave the normal near plane.
        }
        Vector4f clip = new Vector4f(normal.x, normal.y, normal.z, -distance);
        Vector4f q = new Matrix4f(projection).invert().transform(new Vector4f(Math.signum(clip.x), Math.signum(clip.y), 1F, 1F));
        Vector4f c = clip.mul(2F / clip.dot(q));
        projection.m02(c.x - projection.m03());
        projection.m12(c.y - projection.m13());
        projection.m22(c.z - projection.m23());
        projection.m32(c.w - projection.m33());
    }

    /** Drops every view of a screen (its own, its cloak sides', and any it was wiping away from). */
    public void release(String screen)
    {
        Minecraft mc = Minecraft.getInstance();
        views.entrySet().removeIf(e -> {
            if(isAnyViewOf(e.getKey(), screen))
            {
                destroy(mc, e.getValue());
                return true;
            }
            return false;
        });
    }

    public void releaseAll()
    {
        Minecraft mc = Minecraft.getInstance();
        for(SpyView view : views.values())
        {
            destroy(mc, view);
        }
        views.clear();
        mainGraph = null;
    }

    private static void destroy(Minecraft mc, SpyView view)
    {
        GlassRenderTypes.forget(view.textureId);
        if(view.target != null)
        {
            mc.getTextureManager().release(view.textureId);
            view.target.destroyBuffers();
            view.target = null;
        }
        view.projection.close();
        view.floorProjection.close();
        view.graph.waitAndReset(null);
        view.rendered = false;
    }
}
