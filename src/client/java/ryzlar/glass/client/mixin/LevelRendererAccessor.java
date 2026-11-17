package ryzlar.glass.client.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the terminal views swap in their own set of visible sections while they render. */
@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor
{
    @Accessor("sectionOcclusionGraph")
    SectionOcclusionGraph glass$getSectionOcclusionGraph();

    @Mutable
    @Accessor("sectionOcclusionGraph")
    void glass$setSectionOcclusionGraph(SectionOcclusionGraph graph);

    @Accessor("visibleSections")
    ObjectArrayList<SectionRenderDispatcher.RenderSection> glass$getVisibleSections();

    @Mutable
    @Accessor("visibleSections")
    void glass$setVisibleSections(ObjectArrayList<SectionRenderDispatcher.RenderSection> list);

    @Accessor("nearbyVisibleSections")
    ObjectArrayList<SectionRenderDispatcher.RenderSection> glass$getNearbyVisibleSections();

    @Mutable
    @Accessor("nearbyVisibleSections")
    void glass$setNearbyVisibleSections(ObjectArrayList<SectionRenderDispatcher.RenderSection> list);

    @Accessor("viewArea")
    ViewArea glass$getViewArea();

    @Accessor("lastCameraSectionX")
    int glass$getLastCameraSectionX();

    @Accessor("lastCameraSectionX")
    void glass$setLastCameraSectionX(int value);

    @Accessor("lastCameraSectionY")
    int glass$getLastCameraSectionY();

    @Accessor("lastCameraSectionY")
    void glass$setLastCameraSectionY(int value);

    @Accessor("lastCameraSectionZ")
    int glass$getLastCameraSectionZ();

    @Accessor("lastCameraSectionZ")
    void glass$setLastCameraSectionZ(int value);

    @Accessor("prevCamX")
    double glass$getPrevCamX();

    @Accessor("prevCamX")
    void glass$setPrevCamX(double value);

    @Accessor("prevCamY")
    double glass$getPrevCamY();

    @Accessor("prevCamY")
    void glass$setPrevCamY(double value);

    @Accessor("prevCamZ")
    double glass$getPrevCamZ();

    @Accessor("prevCamZ")
    void glass$setPrevCamZ(double value);

    @Accessor("prevCamRotX")
    double glass$getPrevCamRotX();

    @Accessor("prevCamRotX")
    void glass$setPrevCamRotX(double value);

    @Accessor("prevCamRotY")
    double glass$getPrevCamRotY();

    @Accessor("prevCamRotY")
    void glass$setPrevCamRotY(double value);
}
