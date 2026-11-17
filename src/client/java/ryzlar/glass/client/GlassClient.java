package ryzlar.glass.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import ryzlar.glass.GlassClientBridge;
import ryzlar.glass.GlassRegistry;
import ryzlar.glass.client.render.GlassBlockEntityRenderer;
import ryzlar.glass.client.render.GlassTerminalRenderer;

public class GlassClient implements ClientModInitializer
{
    @Override
    public void onInitializeClient()
    {
        GlassClientBridge.set(GlassClientState.INSTANCE);

        BlockRenderLayerMap.putBlocks(ChunkSectionLayer.CUTOUT, GlassRegistry.GLASS_EXTENSION, GlassRegistry.GLASS_PROJECTOR, GlassRegistry.GLASS_WIRELESS_EXTENSION);

        BlockEntityRenderers.register(GlassRegistry.GLASS_EXTENSION_BE, GlassBlockEntityRenderer::new);
        BlockEntityRenderers.register(GlassRegistry.GLASS_PROJECTOR_BE, GlassBlockEntityRenderer::new);
        BlockEntityRenderers.register(GlassRegistry.GLASS_WIRELESS_BE, GlassBlockEntityRenderer::new);
        BlockEntityRenderers.register(GlassRegistry.GLASS_TERMINAL_BE, GlassTerminalRenderer::new);

        ClientTickEvents.END_CLIENT_TICK.register(GlassClientState.INSTANCE::clientTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(GlassClientState.INSTANCE::clear));
    }
}
