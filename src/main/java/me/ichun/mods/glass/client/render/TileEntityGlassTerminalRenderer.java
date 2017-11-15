package me.ichun.mods.glass.client.render;

import me.ichun.mods.glass.common.tileentity.TileEntityGlassMaster;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassTerminal;
import me.ichun.mods.ichunutil.common.core.util.EntityHelper;
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

            BlockRendererDispatcher blockrendererdispatcher = Minecraft.getMinecraft().getBlockRendererDispatcher();
            bufferbuilder.begin(7, DefaultVertexFormats.BLOCK);
            blockrendererdispatcher.getBlockModelRenderer().renderModel(te.getWorld(), blockrendererdispatcher.getModelForState(Blocks.OBSIDIAN.getDefaultState()), Blocks.OBSIDIAN.getDefaultState(), BlockPos.ORIGIN, bufferbuilder, false);
            tessellator.draw();

            GlStateManager.pushMatrix();
            float scale = 0.25F;
            GlStateManager.translate(0.5D, 0.5D, 0.5D);
            GlStateManager.rotate(45F, te.facing.getFrontOffsetX(), te.facing.getFrontOffsetY(), te.facing.getFrontOffsetZ());
            GlStateManager.translate(-0.5D, -0.5D, -0.5D);

            GlStateManager.translate(0.375D, 0.375D, 0.375D);
            GlStateManager.translate(te.facing.getFrontOffsetX() * -0.4D, te.facing.getFrontOffsetY() * -0.4D, te.facing.getFrontOffsetZ() * -0.4D);
            GlStateManager.scale(scale, scale, scale);

            bufferbuilder.begin(7, DefaultVertexFormats.BLOCK);
            blockrendererdispatcher.getBlockModelRenderer().renderModel(te.getWorld(), blockrendererdispatcher.getModelForState(Blocks.GLASS.getDefaultState()), Blocks.GLASS.getDefaultState(), BlockPos.ORIGIN, bufferbuilder, false);
            tessellator.draw();
            GlStateManager.popMatrix();

            GlStateManager.enableLighting();
            GlStateManager.popMatrix();
        }
    }
}
