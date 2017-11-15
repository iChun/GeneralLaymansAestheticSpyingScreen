package me.ichun.mods.glass.client.render;

import me.ichun.mods.glass.common.tileentity.TileEntityGlassTerminal;
import me.ichun.mods.ichunutil.common.module.worldportals.client.render.WorldPortalRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;

public class TileEntityGlassTerminalRenderer extends TileEntitySpecialRenderer<TileEntityGlassTerminal>
{
    @Override
    public void render(TileEntityGlassTerminal te, double x, double y, double z, float partialTick, int destroyStage, float alpha1)
    {
        if(WorldPortalRenderer.renderLevel == 0)
        {
            bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            GlStateManager.pushMatrix();
            GlStateManager.disableLighting();

            GlStateManager.color(1F, 1F, 1F, 1F);

            GlStateManager.translate(x, y, z);

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder bufferbuilder = tessellator.getBuffer();

            bufferbuilder.begin(7, DefaultVertexFormats.BLOCK);
            BlockRendererDispatcher blockrendererdispatcher = Minecraft.getMinecraft().getBlockRendererDispatcher();
            blockrendererdispatcher.getBlockModelRenderer().renderModel(te.getWorld(), blockrendererdispatcher.getModelForState(Blocks.OBSIDIAN.getDefaultState()), Blocks.OBSIDIAN.getDefaultState(), BlockPos.ORIGIN, bufferbuilder, false);
            tessellator.draw();

            GlStateManager.enableLighting();
            GlStateManager.popMatrix();
        }
    }
}
