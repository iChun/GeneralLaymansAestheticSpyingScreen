package ryzlar.glass.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ryzlar.glass.holo.HoloBubbles;

/**
 * A running holo cube simulation's ground can be stood on where the real world is empty (where there is something
 * real, that is what counts, and the real world is never changed): every collision check reads its shapes here. The
 * same on server and client, so movement agrees.
 */
@Mixin(BlockCollisions.class)
public abstract class BlockCollisionsMixin
{
    @WrapOperation(method = "computeNext", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/shapes/CollisionContext;getCollisionShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/CollisionGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private VoxelShape glass$scannedGround(CollisionContext context, BlockState state, CollisionGetter getter, BlockPos pos, Operation<VoxelShape> original)
    {
        VoxelShape real = original.call(context, state, getter, pos);
        if(real.isEmpty() && getter instanceof Level level && HoloBubbles.in(level).length > 0)
        {
            VoxelShape ground = HoloBubbles.groundAt(level, pos);
            if(ground != null)
            {
                return ground;
            }
        }
        return real;
    }
}
