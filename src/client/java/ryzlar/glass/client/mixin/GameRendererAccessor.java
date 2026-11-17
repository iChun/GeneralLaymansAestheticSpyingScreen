package ryzlar.glass.client.mixin;

import net.minecraft.client.Camera;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor
{
    @Accessor("resourcePool")
    CrossFrameResourcePool glass$getResourcePool();

    @Accessor("fogRenderer")
    FogRenderer glass$getFogRenderer();

    @Invoker("getFov")
    float glass$getFov(Camera camera, float partialTick, boolean useFovSetting);
}
