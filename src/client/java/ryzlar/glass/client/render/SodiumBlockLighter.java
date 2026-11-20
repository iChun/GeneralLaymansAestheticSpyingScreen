package ryzlar.glass.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.caffeinemc.mods.sodium.client.model.light.LightMode;
import net.caffeinemc.mods.sodium.client.model.light.LightPipeline;
import net.caffeinemc.mods.sodium.client.model.light.LightPipelineProvider;
import net.caffeinemc.mods.sodium.client.model.light.data.HashLightDataCache;
import net.caffeinemc.mods.sodium.client.model.light.data.QuadLightData;
import net.caffeinemc.mods.sodium.client.model.quad.BakedQuadView;
import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Lights blocks the way Sodium lights its terrain (its smooth lighting differs from vanilla's, most of all on blocks
 * that are not full, like paths), so a cloak's rebuilt ground matches the Sodium-drawn world around it. Only loaded
 * with the Sodium view backend.
 */
final class SodiumBlockLighter implements CloakFill.BlockLighter
{
    private static final Direction[] DIRECTIONS = Direction.values();

    private final BlockAndTintGetter level;
    private final LightPipelineProvider lighters;
    private final QuadLightData light = new QuadLightData();
    private final BlockColors colors = Minecraft.getInstance().getBlockColors();

    private SodiumBlockLighter(BlockAndTintGetter level)
    {
        this.level = level;
        this.lighters = new LightPipelineProvider(new HashLightDataCache(level));
    }

    static CloakFill.BlockLighter create(BlockAndTintGetter level)
    {
        return new SodiumBlockLighter(level);
    }

    @Override
    public void render(BlockState state, BlockPos pos, PoseStack.Pose pose, VertexConsumer out, List<BlockModelPart> parts)
    {
        boolean ao = Minecraft.useAmbientOcclusion() && state.getLightEmission() == 0;
        for(BlockModelPart part : parts)
        {
            LightPipeline lighter = lighters.getLighter(ao && part.useAmbientOcclusion() ? LightMode.SMOOTH : LightMode.FLAT);
            for(Direction direction : DIRECTIONS)
            {
                if(Block.shouldRenderFace(state, level.getBlockState(pos.relative(direction)), direction))
                {
                    quads(state, pos, pose, out, part.getQuads(direction), direction, lighter);
                }
            }
            quads(state, pos, pose, out, part.getQuads(null), null, lighter);
        }
    }

    private void quads(BlockState state, BlockPos pos, PoseStack.Pose pose, VertexConsumer out, List<BakedQuad> quads, Direction cullFace, LightPipeline lighter)
    {
        for(BakedQuad quad : quads)
        {
            ModelQuadView view = (ModelQuadView)(Object)quad;
            lighter.calculate(view, pos, light, cullFace, view.getLightFace(), ((BakedQuadView)(Object)quad).hasShade(), false);
            int tint = quad.isTinted() ? colors.getColor(state, level, pos, quad.tintIndex()) : -1;
            float r = (tint >> 16 & 0xFF) / 255F, g = (tint >> 8 & 0xFF) / 255F, b = (tint & 0xFF) / 255F;
            for(int i = 0; i < 4; i++)
            {
                float brightness = light.br[i];
                int quadLight = view.getLight(i);
                int lm = light.lm[i];
                int block = Math.max(quadLight & 0xFFFF, lm & 0xFFFF);
                int sky = Math.max(quadLight >>> 16, lm >>> 16);
                out.addVertex(pose, view.getX(i), view.getY(i), view.getZ(i))
                        .setColor(r * brightness, g * brightness, b * brightness, 1F)
                        .setUv(view.getTexU(i), view.getTexV(i))
                        .setUv2(block, sky)
                        .setNormal(pose, 0F, 1F, 0F);
            }
        }
    }
}
