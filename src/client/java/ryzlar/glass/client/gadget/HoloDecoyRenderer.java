package ryzlar.glass.client.gadget;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.gadget.HoloDecoyEntity;

import java.util.UUID;

/**
 * A holo decoy: its owner, with their skin, drawn see-through and blue, flickering now and then like a hologram, and
 * breathing a little as if they stood there. It fades in when it appears and out as its time runs out.
 */
public class HoloDecoyRenderer extends LivingEntityRenderer<HoloDecoyEntity, HoloDecoyRenderer.State, PlayerModel>
{
    public static class State extends PlayerRenderState
    {
        float alpha = 1F;
        float glitch;
    }

    public HoloDecoyRenderer(EntityRendererProvider.Context context)
    {
        super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0F);
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(HoloDecoyEntity decoy, State state, float partialTick)
    {
        super.extractRenderState(decoy, state, partialTick);
        state.skin = skinOf(decoy.ownerId());
        state.isInvisibleToPlayer = false;
        float age = decoy.age() + partialTick;
        float in = Math.clamp(age / 15F, 0F, 1F);
        float out = Math.clamp((HoloDecoyEntity.LIFETIME - age) / 30F, 0F, 1F);
        //A flicker now and then, more often as it runs out.
        long beat = (long)(age / 3F);
        float flicker = hash(decoy.getId() * 31L + beat) < (0.06F + (1F - out) * 0.25F) ? 0.35F : 1F;
        state.alpha = 0.62F * in * out * flicker;
        state.glitch = flicker < 1F ? (hash(beat * 7L + 3) - 0.5F) * 0.12F : 0F;
        //Breathing: the arms sway a little.
        state.walkAnimationPos = age * 0.02F;
        state.walkAnimationSpeed = 0.05F;
    }

    private static net.minecraft.client.resources.PlayerSkin skinOf(String ownerId)
    {
        try
        {
            UUID id = UUID.fromString(ownerId);
            PlayerInfo info = Minecraft.getInstance().getConnection() == null ? null : Minecraft.getInstance().getConnection().getPlayerInfo(id);
            return info != null ? info.getSkin() : DefaultPlayerSkin.get(id);
        }
        catch(IllegalArgumentException e)
        {
            return DefaultPlayerSkin.getDefaultSkin();
        }
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffers, int light)
    {
        if(state.alpha <= 0.01F)
        {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(state.glitch, 0F, 0F);
        super.render(state, poseStack, buffers, 0xF000F0);
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(State state)
    {
        return state.skin.texture();
    }

    @Nullable
    @Override
    protected RenderType getRenderType(State state, boolean bodyVisible, boolean translucent, boolean glowing)
    {
        return RenderType.entityTranslucentEmissive(getTextureLocation(state));
    }

    /** See-through, in the hologram's blue. */
    @Override
    protected int getModelTint(State state)
    {
        int alpha = Math.clamp((int)(state.alpha * 255F), 0, 255);
        return alpha << 24 | 0x8CE8FF;
    }

    @Override
    protected boolean shouldShowName(HoloDecoyEntity decoy, double distance)
    {
        return false;
    }

    private static float hash(long seed)
    {
        long h = seed * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h & 0xFFFFFF) / (float)0x1000000;
    }
}
