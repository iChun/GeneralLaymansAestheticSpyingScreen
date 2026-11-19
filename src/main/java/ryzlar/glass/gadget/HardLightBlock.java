package ryzlar.glass.gadget;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import ryzlar.glass.GlassSounds;

/**
 * Hard light: a wall of solid light, put up by a hard-light emitter. It stops players, mobs and arrows, cannot be mined,
 * drops nothing, and ripples where something hits it.
 */
public class HardLightBlock extends HalfTransparentBlock
{
    public static final MapCodec<HardLightBlock> CODEC = simpleCodec(HardLightBlock::new);

    public HardLightBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HalfTransparentBlock> codec()
    {
        return CODEC;
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile)
    {
        ripple(level, hit.getLocation(), 1F);
    }

    /** A ring of light where something hit the wall, and its sound. */
    public static void ripple(Level level, Vec3 at, float strength)
    {
        if(level instanceof ServerLevel server)
        {
            server.sendParticles(new DustParticleOptions(0x6CF0FF, 0.9F), at.x, at.y, at.z, (int)(10 * strength), 0.25D, 0.25D, 0.25D, 0.01D);
            level.playSound(null, at.x, at.y, at.z, GlassSounds.RIPPLE, SoundSource.BLOCKS, 0.6F * strength, 1.2F + level.random.nextFloat() * 0.2F);
        }
        else
        {
            for(int i = 0; i < 8 * strength; i++)
            {
                level.addParticle(new DustParticleOptions(0x6CF0FF, 0.7F), at.x + (level.random.nextDouble() - 0.5D) * 0.5D,
                        at.y + (level.random.nextDouble() - 0.5D) * 0.5D, at.z + (level.random.nextDouble() - 0.5D) * 0.5D, 0D, 0D, 0D);
            }
            level.playLocalSound(at.x, at.y, at.z, GlassSounds.RIPPLE, SoundSource.BLOCKS, 0.35F * strength, 1.25F + level.random.nextFloat() * 0.2F, false);
        }
    }

    /** Placed by an emitter only: it never stays where no emitter holds it (see HardLightEmitterBlockEntity). */
    public static boolean isHardLight(BlockState state)
    {
        return state.getBlock() instanceof HardLightBlock;
    }

    public static boolean canFill(Level level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || (state.canBeReplaced() && state.getFluidState().isEmpty());
    }
}
