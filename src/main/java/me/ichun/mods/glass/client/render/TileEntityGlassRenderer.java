package me.ichun.mods.glass.client.render;

import me.ichun.mods.glass.common.tileentity.TileEntityGlassBase;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassMaster;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;

public class TileEntityGlassRenderer extends TileEntitySpecialRenderer<TileEntityGlassBase>
{
    @Override
    public void render(TileEntityGlassBase te, double x, double y, double z, float partialTick, int destroyStage, float alpha1)
    {
        GlStateManager.pushMatrix();
        
        GlStateManager.translate(x + 0.5D, y + 0.5D, z + 0.5D);

        GlStateManager.color(1F, 1F, 1F, 1F);

        float alpha = MathHelper.clamp((te.fadeoutTime - partialTick) / (float)TileEntityGlassBase.FADEOUT_TIME, 0F, 1F);
        if(te.active)
        {
            alpha = 1F - alpha;
        }
        //Render Plane
//        if(te.active)
        {
            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.disableNormalize();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.00625F);
            GlStateManager.enableCull();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
            double pushback = 0.501D;

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder bufferbuilder = tessellator.getBuffer();
            
            for(EnumFacing face : te.activeFaces)
            {
                GlStateManager.pushMatrix();
                int horiOrient = (face.getAxis() == EnumFacing.Axis.Y ? EnumFacing.UP : face).getOpposite().getHorizontalIndex();
                GlStateManager.rotate((face.getIndex() > 0 ? 180F : 0F) + -horiOrient * 90F, 0F, 1F, 0F);
                if(face.getAxis() == EnumFacing.Axis.Y)
                {
                    GlStateManager.rotate(face == EnumFacing.UP ? -90F : 90F, 1F, 0F, 0F);
                }
                GlStateManager.translate(0F, 0F, pushback);

                float halfSize = 0.5F;
                bufferbuilder.begin(7, DefaultVertexFormats.POSITION_COLOR);
                bufferbuilder.pos(-halfSize,  halfSize, 0F).color(1F, 1F, 1F, alpha).endVertex();
                bufferbuilder.pos(-halfSize, -halfSize, 0F).color(1F, 1F, 1F, alpha).endVertex();
                bufferbuilder.pos( halfSize, -halfSize, 0F).color(1F, 1F, 1F, alpha).endVertex();
                bufferbuilder.pos( halfSize,  halfSize, 0F).color(1F, 1F, 1F, alpha).endVertex();
                tessellator.draw();

                GlStateManager.popMatrix();
            }
            int i = te.getWorld().getCombinedLight(te.getPos(), 0);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (float)(i % 65536) / 1.0F, (float)(i / 65536) / 1.0F);
            GlStateManager.disableBlend();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
            GlStateManager.enableNormalize();
            GlStateManager.enableLighting();
            GlStateManager.enableTexture2D();
        }
        
        GlStateManager.popMatrix();
    }
}
