package ryzlar.glass.client.holo;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.Glass;
import ryzlar.glass.holo.CubeScan;
import ryzlar.glass.holo.HoloBubbles;
import ryzlar.glass.holo.HoloCubeEntity;
import ryzlar.glass.holo.HoloProjectionData;
import ryzlar.glass.holo.HoloSphere;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The client's side of a running holo cube simulation (see {@link HoloBubbles}), a shrunk picture of the scan over the
 * real world: the scan's chests, signs, beds, animals and mobs rebuilt as stand-ins to draw, a look into a scanned chest
 * by aiming at it and using it, and glowing pixels splashing off the scan where someone walks through it.
 */
public final class SimulationView
{
    private SimulationView() {}

    public static void init()
    {
        ClientTickEvents.END_CLIENT_TICK.register(SimulationView::splashes);
        //Using a scanned chest (nearer than whatever real block is aimed at) looks into it; the real world is left alone.
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> level.isClientSide && hand == InteractionHand.MAIN_HAND
                && openAimed(player, hit.getLocation().distanceTo(player.getEyePosition())) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseItemCallback.EVENT.register((player, level, hand) -> level.isClientSide && hand == InteractionHand.MAIN_HAND
                && openAimed(player, Double.MAX_VALUE) ? InteractionResult.FAIL : InteractionResult.PASS);
    }

    public static void reset()
    {
        HoloBubbles.clearClientSide();
        SCENES.clear();
    }

    // ---- Scanned containers ----

    /**
     * Opens the read-only view of the scanned container the player aims at, if one is in reach and nearer than
     * {@code realDistance} (the real block aimed at). False if there is none.
     */
    private static boolean openAimed(Player player, double realDistance)
    {
        Minecraft mc = Minecraft.getInstance();
        if(mc.level == null || player != mc.player)
        {
            return false;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1F).scale(player.blockInteractionRange()));
        for(HoloBubbles.Bubble bubble : HoloBubbles.in(mc.level))
        {
            if(!(mc.level.getEntity(bubble.id) instanceof HoloCubeEntity cube))
            {
                continue;
            }
            Scene scene = scene(cube, bubble);
            if(scene == null)
            {
                continue;
            }
            //Along the same line through the scan (shrunk and turned, it is still a straight line there).
            Vec3 from = bubble.toScan(eye), to = bubble.toScan(end);
            ClipContext context = new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player);
            BlockHitResult hit = BlockGetter.traverseBlocks(from, to, context, (ctx, pos) -> {
                BlockState state = bubble.scanned(pos.getX(), pos.getY(), pos.getZ());
                return state.isAir() ? null : state.getShape(EmptyBlockGetter.INSTANCE, pos).clip(from, to, pos);
            }, ctx -> null);
            if(hit == null || hit.getType() != HitResult.Type.BLOCK)
            {
                continue;
            }
            Vec3 at = bubble.toWorld(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z);
            if(at.distanceTo(eye) >= realDistance)
            {
                continue;
            }
            for(Placed placed : scene.blockEntities)
            {
                if(placed.scanPos.equals(hit.getBlockPos()) && placed.blockEntity instanceof Container container)
                {
                    open(placed.blockEntity, container);
                    return true;
                }
            }
        }
        return false;
    }

    private static void open(BlockEntity blockEntity, Container container)
    {
        List<ItemStack> items = new ArrayList<>();
        for(int i = 0; i < container.getContainerSize(); i++)
        {
            items.add(container.getItem(i).copy());
        }
        Component name = blockEntity instanceof Nameable nameable ? nameable.getDisplayName() : blockEntity.getBlockState().getBlock().getName();
        Minecraft.getInstance().setScreen(new HoloContainerScreen(name, items));
    }

    /** Tests: opens the first scanned container of this cube's simulation. */
    public static boolean openFirstContainer(HoloCubeEntity cube)
    {
        HoloBubbles.Bubble bubble = HoloBubbles.byId(cube.level(), cube.getId());
        Scene scene = bubble == null ? null : scene(cube, bubble);
        if(scene != null)
        {
            for(Placed placed : scene.blockEntities)
            {
                if(placed.blockEntity instanceof Container container)
                {
                    open(placed.blockEntity, container);
                    return true;
                }
            }
        }
        return false;
    }

    // ---- Stand-ins ----

    /** A scanned block entity, rebuilt, and where it is in the scan's box. */
    record Placed(BlockEntity blockEntity, BlockPos scanPos) {}

    /** A scanned entity, rebuilt (standing where it shows in the world), and where it is in the scan's box. */
    record PlacedEntity(Entity entity, Vec3 scanPos) {}

    /** A simulation's chests, signs, beds and the like, and its animals, mobs, frames and stands. */
    static final class Scene
    {
        final String key;
        final List<Placed> blockEntities = new ArrayList<>();
        final List<PlacedEntity> entities = new ArrayList<>();
        final HoloClient.ClientProjection projection;
        final HoloBubbles.Bubble bubble;

        Scene(String key, HoloClient.ClientProjection projection, HoloBubbles.Bubble bubble)
        {
            this.key = key;
            this.projection = projection;
            this.bubble = bubble;
        }

        /** The scanned light at a place in the scan's box, packed for drawing (as LightTexture.pack). */
        int light(int x, int y, int z)
        {
            int packed = projection.light(x, y, z);
            return net.minecraft.client.renderer.LightTexture.pack(packed >> 4, packed & 15);
        }
    }

    private static final Map<Integer, Scene> SCENES = new HashMap<>();

    /** The stand-ins of a cube's simulation, made once its scan is here (and again if it moves or turns). */
    @Nullable
    static Scene scene(HoloCubeEntity cube, HoloBubbles.Bubble bubble)
    {
        CubeScan scan = cube.scan();
        if(scan == null)
        {
            return null;
        }
        String key = scan.id() + "|" + bubble.anchor.asLong() + "|" + bubble.turn;
        Scene scene = SCENES.get(cube.getId());
        if(scene != null && scene.key.equals(key))
        {
            return scene;
        }
        HoloClient.ClientProjection projection = HoloClient.projection(scan.id());
        if(projection == null || !(cube.level() instanceof ClientLevel level))
        {
            return null;
        }
        if(SCENES.size() > 16)
        {
            SCENES.clear();
        }
        scene = new Scene(key, projection, bubble);
        build(scene, level, bubble, projection);
        SCENES.put(cube.getId(), scene);
        return scene;
    }

    private static void build(Scene scene, ClientLevel level, HoloBubbles.Bubble bubble, HoloClient.ClientProjection projection)
    {
        Rotation rotation = HoloBubbles.rotation(bubble.turn);
        for(CompoundTag saved : projection.blockEntities())
        {
            int[] local = saved.getIntArray(HoloProjectionData.Projection.LOCAL_POS).orElse(null);
            if(local == null || local.length != 3)
            {
                continue;
            }
            CompoundTag tag = saved.copy();
            tag.remove(HoloProjectionData.Projection.LOCAL_POS);
            BlockPos world = BlockPos.containing(bubble.toWorld(local[0] + 0.5D, local[1] + 0.5D, local[2] + 0.5D));
            BlockState state = projection.get(local[0], local[1], local[2]).rotate(rotation);
            try
            {
                BlockEntity blockEntity = BlockEntity.loadStatic(world, state, tag, level.registryAccess());
                if(blockEntity != null)
                {
                    blockEntity.setLevel(level);
                    scene.blockEntities.add(new Placed(blockEntity, new BlockPos(local[0], local[1], local[2])));
                }
            }
            catch(RuntimeException e)
            {
                Glass.LOGGER.debug("Could not rebuild a scanned block entity at {}", world, e);
            }
        }
        for(CompoundTag saved : projection.entities())
        {
            CompoundTag tag = saved.copy();
            Vec3 scanPos = new Vec3(tag.getDoubleOr(HoloProjectionData.Projection.LOCAL_X, 0D), tag.getDoubleOr(HoloProjectionData.Projection.LOCAL_Y, 0D),
                    tag.getDoubleOr(HoloProjectionData.Projection.LOCAL_Z, 0D));
            tag.remove(HoloProjectionData.Projection.LOCAL_X);
            tag.remove(HoloProjectionData.Projection.LOCAL_Y);
            tag.remove(HoloProjectionData.Projection.LOCAL_Z);
            try
            {
                Entity entity = EntityType.create(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag), level, EntitySpawnReason.LOAD).orElse(null);
                if(entity == null)
                {
                    continue;
                }
                float yaw = entity.rotate(rotation);
                float headTurn = entity instanceof LivingEntity living ? living.getYHeadRot() - entity.getYRot() : 0F;
                entity.setYRot(yaw);
                Vec3 world = bubble.toWorld(scanPos.x, scanPos.y, scanPos.z);
                entity.setPos(world.x, world.y, world.z);
                if(entity instanceof LivingEntity living)
                {
                    living.setYBodyRot(yaw);
                    living.setYHeadRot(yaw + headTurn);
                    living.yBodyRotO = living.yBodyRot;
                    living.yHeadRotO = living.yHeadRot;
                }
                entity.setOldPosAndRot();
                scene.entities.add(new PlacedEntity(entity, scanPos));
            }
            catch(RuntimeException e)
            {
                Glass.LOGGER.debug("Could not rebuild a scanned entity", e);
            }
        }
    }

    // ---- Walking through the scan ----

    private static final DustParticleOptions CYAN = new DustParticleOptions(0x3CE6FF, 0.55F);
    private static final DustParticleOptions WHITE = new DustParticleOptions(0xD9FBFF, 0.4F);
    /** Points tried in a body each tick, each way. */
    private static final int SAMPLES = 3;

    /**
     * The scan is walked through (all but its ground); where someone or something goes through it, glowing nano
     * pixels splash off it: more the faster they go.
     */
    private static void splashes(Minecraft mc)
    {
        ClientLevel level = mc.level;
        if(level == null || mc.isPaused())
        {
            return;
        }
        HoloBubbles.Bubble[] bubbles = HoloBubbles.in(level);
        if(bubbles.length == 0)
        {
            return;
        }
        RandomSource random = level.random;
        for(HoloBubbles.Bubble bubble : bubbles)
        {
            if(!bubble.solid)
            {
                continue;
            }
            for(Entity entity : level.getEntities((Entity)null, bubble.box(), e -> e instanceof LivingEntity && !e.isSpectator()))
            {
                double moved = entity.position().distanceTo(new Vec3(entity.xo, entity.yo, entity.zo));
                //Standing still in a wall it only glitters now and then; moving through, it sprays.
                int count = moved > 0.02D ? 1 + (int)Math.min(4D, moved * 12D) : random.nextInt(6) == 0 ? 1 : 0;
                if(count == 0)
                {
                    continue;
                }
                AABB box = entity.getBoundingBox();
                Vec3 middle = box.getCenter();
                List<Vec3> touching = new ArrayList<>();
                for(int i = 0; i <= SAMPLES; i++)
                {
                    for(int j = 0; j <= SAMPLES; j++)
                    {
                        for(int k = 0; k <= SAMPLES; k++)
                        {
                            double x = box.minX + box.getXsize() * i / SAMPLES;
                            double y = box.minY + 0.05D + (box.getYsize() - 0.1D) * j / SAMPLES;
                            double z = box.minZ + box.getZsize() * k / SAMPLES;
                            Vec3 at = bubble.toScan(x, y, z);
                            int sx = (int)Math.floor(at.x), sy = (int)Math.floor(at.y), sz = (int)Math.floor(at.z);
                            if(sx < 0 || sy < 0 || sz < 0 || sx >= HoloSphere.SIZE || sy >= HoloSphere.SIZE || sz >= HoloSphere.SIZE)
                            {
                                continue;
                            }
                            BlockState state = bubble.scanned(sx, sy, sz);
                            if(state.isAir() || bubble.isGround(sy, state) || state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty())
                            {
                                continue;
                            }
                            touching.add(new Vec3(x, y, z));
                        }
                    }
                }
                //Where the body is in the scan, pixels fly off, outwards from the body: a few each tick, more the faster.
                for(int n = 0; n < count * 2 && !touching.isEmpty(); n++)
                {
                    Vec3 p = touching.get(random.nextInt(touching.size()));
                    Vec3 out = new Vec3(p.x - middle.x + random.nextGaussian() * 0.05D, 0D, p.z - middle.z + random.nextGaussian() * 0.05D);
                    out = out.lengthSqr() < 1.0E-6D ? new Vec3(random.nextGaussian(), 0D, random.nextGaussian()) : out;
                    out = out.normalize().scale(0.04D + random.nextDouble() * 0.06D);
                    level.addParticle(random.nextInt(4) == 0 ? WHITE : CYAN, p.x + random.nextGaussian() * 0.05D, p.y + random.nextGaussian() * 0.05D,
                            p.z + random.nextGaussian() * 0.05D, out.x, 0.02D + random.nextDouble() * 0.04D, out.z);
                }
            }
        }
    }
}
