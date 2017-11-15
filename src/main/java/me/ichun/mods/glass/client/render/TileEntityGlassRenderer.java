package me.ichun.mods.glass.client.render;

import me.ichun.mods.glass.common.GeneralLaymansAestheticSpyingScreen;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassBase;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassMaster;
import me.ichun.mods.glass.common.tileentity.TileEntityGlassWireless;
import me.ichun.mods.ichunutil.common.core.util.EntityHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelEnderCrystal;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;

import java.util.HashSet;

public class TileEntityGlassRenderer extends TileEntitySpecialRenderer<TileEntityGlassBase>
{
    public static final ResourceLocation ENDER_CRYSTAL_TEXTURES = new ResourceLocation("textures/entity/endercrystal/endercrystal.png");
    public ModelEnderCrystal modelEnderCrystal;

    public TileEntityGlassRenderer()
    {
        modelEnderCrystal = new ModelEnderCrystal(0F, false);
    }

    @Override
    public void render(TileEntityGlassBase te, double x, double y, double z, float partialTick, int destroyStage, float alpha1)
    {
        GlStateManager.pushMatrix();

        GlStateManager.translate(x + 0.5D, y + 0.5D, z + 0.5D);

        GlStateManager.color(1F, 1F, 1F, 1F);

        //Render Plane
        GlStateManager.disableLighting();
        GlStateManager.disableNormalize();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.00625F);
        GlStateManager.enableCull();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);

        boolean isMaster = te instanceof TileEntityGlassMaster;

        if(isMaster)
        {
            GlStateManager.pushMatrix();
            float scale = 0.5F;
            GlStateManager.rotate(EntityHelper.interpolateRotation(((TileEntityGlassMaster)te).rotationBeacon, ((TileEntityGlassMaster)te).rotationBeaconPrev, partialTick), 0F, 1F, 0F);
            GlStateManager.translate(-0.25D, -0.35D, -0.25D);
            GlStateManager.scale(scale, scale, scale);
            bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder bufferbuilder = tessellator.getBuffer();

            bufferbuilder.begin(7, DefaultVertexFormats.BLOCK);
            BlockRendererDispatcher blockrendererdispatcher = Minecraft.getMinecraft().getBlockRendererDispatcher();
            blockrendererdispatcher.getBlockModelRenderer().renderModel(te.getWorld(), blockrendererdispatcher.getModelForState(Blocks.BEACON.getDefaultState()), Blocks.BEACON.getDefaultState(), BlockPos.ORIGIN, bufferbuilder, false);
            tessellator.draw();
            GlStateManager.popMatrix();
        }
        else if(te instanceof TileEntityGlassWireless)
        {
            GlStateManager.pushMatrix();
            float scale = 0.3F;
            GlStateManager.translate(0F, -0.15F, 0F);
            GlStateManager.scale(scale, scale, scale);
            bindTexture(ENDER_CRYSTAL_TEXTURES);
            modelEnderCrystal.render(null, 0F, (((TileEntityGlassWireless)te).ticks + partialTick) * 1.2F, 0F, 0F, 0F, 0.0625F);
            GlStateManager.popMatrix();
        }
        te.lastDraw = 8;

        GlStateManager.disableTexture2D();

        float alpha = (float)Math.pow(MathHelper.clamp((te.fadeoutTime - partialTick) / (float)TileEntityGlassBase.FADEOUT_TIME, 0F, 1F), 0.5D);
        if(te.active)
        {
            drawScene(te);
        }

        if(alpha > 0D)
        {
            drawPlanes(te, 1F, 1F, 1F, alpha, 0.502D);
        }

        GlStateManager.enableTexture2D();

        int i = te.getWorld().getCombinedLight(te.getPos(), 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (float)(i % 65536) / 1.0F, (float)(i / 65536) / 1.0F);
        GlStateManager.disableBlend();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.enableNormalize();
        GlStateManager.enableLighting();

        GlStateManager.popMatrix();
    }

    public void drawScene(TileEntityGlassBase te)
    {
        if(!GeneralLaymansAestheticSpyingScreen.eventHandlerClient.drawnChannels.contains(te.channel))
        {
            //Draw scene
            GeneralLaymansAestheticSpyingScreen.eventHandlerClient.drawnChannels.add(te.channel);

            //Apply the stencil

            HashSet<TileEntityGlassBase> bases = GeneralLaymansAestheticSpyingScreen.eventHandlerClient.getActiveGlass(te.channel);
            for(TileEntityGlassBase base : bases)
            {
                if(base.active && base.lastDraw > 0)
                {
                    GlStateManager.pushMatrix();
                    GlStateManager.translate(base.getPos().getX() - te.getPos().getX(), base.getPos().getY() - te.getPos().getY(), base.getPos().getZ() - te.getPos().getZ());

                    drawPlanes(base, 1F, 1F, 1F, 1F, 0.501D);

                    GlStateManager.popMatrix();
                }
            }

        }
    }


    public void drawPlanes(TileEntityGlassBase te, float r, float g, float b, float alpha, double pushback)
    {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferbuilder = tessellator.getBuffer();

        for(EnumFacing face : te.activeFaces)
        {
            if(!GeneralLaymansAestheticSpyingScreen.blockGlass.shouldSideBeRendered(te.getWorld().getBlockState(te.getPos()), te.getWorld(), te.getPos(), face))
            {
                continue;
            }
            GlStateManager.pushMatrix();
            int horiOrient = (face.getAxis() == EnumFacing.Axis.Y ? EnumFacing.UP : face).getOpposite().getHorizontalIndex();
            GlStateManager.rotate((face.getIndex() > 0 ? 180F : 0F) + -horiOrient * 90F, 0F, 1F, 0F);
            if(face.getAxis() == EnumFacing.Axis.Y)
            {
                GlStateManager.rotate(face == EnumFacing.UP ? -90F : 90F, 1F, 0F, 0F);
            }
            GlStateManager.translate(0F, 0F, pushback);

            float halfSize = 0.5001F;
            bufferbuilder.begin(7, DefaultVertexFormats.POSITION_COLOR);
            bufferbuilder.pos(-halfSize,  halfSize, 0F).color(r, g, b, alpha).endVertex();
            bufferbuilder.pos(-halfSize, -halfSize, 0F).color(r, g, b, alpha).endVertex();
            bufferbuilder.pos( halfSize, -halfSize, 0F).color(r, g, b, alpha).endVertex();
            bufferbuilder.pos( halfSize,  halfSize, 0F).color(r, g, b, alpha).endVertex();
            tessellator.draw();

            GlStateManager.popMatrix();
        }
    }
}
