package ryzlar.glass.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.GlassRegistry;
import ryzlar.glass.network.GlassNetworkData;
import ryzlar.glass.network.GlassNetworkManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The GLASS projector: shows a camera of a GLASS channel on the glass connected to it, once it gets a redstone signal.
 * <p>
 * The camera it shows (position, dimension, facing) is worked out on the server and sent to clients with the block
 * entity, so clients do not need the terminal itself.
 */
public class GlassProjectorBlockEntity extends GlassBaseBlockEntity
{
    public static final int MAX_WIRELESS = 64;
    /** How far (blocks, each way) a wireless extension may be from the projector it is linked to. */
    public static final int WIRELESS_RANGE = 64;
    private static final int RESOLVE_INTERVAL = 20;

    public boolean powered;
    public Direction placingFace = Direction.NORTH;
    public List<BlockPos> wirelessPos = new ArrayList<>();

    //What to show
    public String channelId = "";
    public String channelName = "";
    private int resolveTimer;

    /**
     * Cloak mode: instead of a camera, the glass shows the world right behind it as if the {@link #cloakDepth} blocks
     * behind the screen were not there (an invisible base). Needs no channel.
     */
    public boolean cloak;
    public int cloakDepth = 4;
    /**
     * Cloak field: with wireless extensions in the four corners of a rectangle, the cloak is a box of virtual walls (and a
     * roof) rising from them, {@link #fieldHeight} blocks high, hiding everything inside.
     */
    public boolean cloakField;
    public int fieldHeight = 8;
    public static final int MAX_FIELD_HEIGHT = 32;
    public static final int MAX_CLOAK_DEPTH = 32;

    /**
     * How big a cloak may be, so a whole village cannot be cloaked and every client drawing it slowed to a crawl (the
     * ground under and beside a cloak is drawn again by each client, and that grows with its size): a cloak field's
     * floor (blocks each way), its height, a glass cloak's number of glass blocks, and how deep a cloak hides.
     * Cloak amplifiers next to the projector raise them.
     */
    public record CloakLimits(int amplifiers, int footprint, int height, int glass, int depth) {}

    public static final int MAX_AMPLIFIERS = 2;
    private static final CloakLimits[] LIMITS = {
            new CloakLimits(0, 32, 16, 256, 16),
            new CloakLimits(1, 48, 24, 576, 24),
            new CloakLimits(2, 64, 32, 1024, 32),
    };
    /** How often a running cloak checks it still fits (an amplifier may have been taken away), in ticks. */
    private static final int LIMIT_CHECK_INTERVAL = 20;
    private int limitTimer;

    //The camera being shown, resolved by the server
    public boolean camValid;
    public ResourceKey<Level> camDimension = Level.OVERWORLD;
    public BlockPos camPos = BlockPos.ZERO;
    public Direction camFacing = Direction.NORTH;

    public float rotationBeacon, rotationBeaconPrev;
    public int wirelessTime;

    public GlassProjectorBlockEntity(BlockPos pos, BlockState state)
    {
        super(GlassRegistry.GLASS_PROJECTOR_BE, pos, state);
    }

    /**
     * The key the glass of this projector's screen carries: unique per projector, so screens never mix, and the same
     * whatever it shows, so switching scene (channel) or cloak while it is on keeps the screen up and only swaps the image.
     */
    public String screenKey()
    {
        return channelId.isEmpty() && !cloak ? "" : "screen@" + getBlockPos().asLong();
    }

    /**
     * True if the wireless extensions are the four corners of a rectangle on one level (at least two blocks each way):
     * where a cloak field can rise.
     */
    public boolean hasFieldCorners()
    {
        return fieldCorners(wirelessPos) != null;
    }

    /** The cloak field's box, or null when it is off or the extensions are not in a rectangle. */
    @Nullable
    public AABB cloakFieldBox()
    {
        if(!cloak || !cloakField)
        {
            return null;
        }
        int[] corners = fieldCorners(wirelessPos);
        if(corners == null)
        {
            return null;
        }
        return new AABB(corners[0], corners[1], corners[2], corners[3] + 1, corners[1] + fieldHeight, corners[4] + 1);
    }

    /** minX, y, minZ, maxX, maxZ of four positions forming a rectangle's corners on one level, or null. */
    @Nullable
    public static int[] fieldCorners(List<BlockPos> positions)
    {
        if(positions.size() != 4)
        {
            return null;
        }
        int y = positions.getFirst().getY();
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for(BlockPos pos : positions)
        {
            if(pos.getY() != y)
            {
                return null;
            }
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        if(maxX - minX < 1 || maxZ - minZ < 1)
        {
            return null;
        }
        for(int[] corner : new int[][] { { minX, minZ }, { minX, maxZ }, { maxX, minZ }, { maxX, maxZ } })
        {
            if(!positions.contains(new BlockPos(corner[0], y, corner[1])))
            {
                return null;
            }
        }
        return new int[] { minX, y, minZ, maxX, maxZ };
    }

    public static boolean inWirelessRange(BlockPos projector, BlockPos wireless)
    {
        return Math.abs(projector.getX() - wireless.getX()) <= WIRELESS_RANGE && Math.abs(projector.getY() - wireless.getY()) <= WIRELESS_RANGE
                && Math.abs(projector.getZ() - wireless.getZ()) <= WIRELESS_RANGE;
    }

    /** The limits this projector's cloak has, with the amplifiers next to it now. */
    public CloakLimits cloakLimits()
    {
        int amplifiers = 0;
        if(level != null)
        {
            for(Direction direction : Direction.values())
            {
                if(level.getBlockState(worldPosition.relative(direction)).is(GlassRegistry.CLOAK_AMPLIFIER))
                {
                    amplifiers++;
                }
            }
        }
        return LIMITS[Math.min(amplifiers, MAX_AMPLIFIERS)];
    }

    /** Why this projector's cloak may not run (it is too big for its limits), or null if it may. */
    @Nullable
    public net.minecraft.network.chat.Component cloakProblem()
    {
        if(!cloak || level == null)
        {
            return null;
        }
        CloakLimits limits = cloakLimits();
        if(cloakField)
        {
            int[] corners = fieldCorners(wirelessPos);
            if(corners != null)
            {
                int width = corners[3] - corners[0] + 1, length = corners[4] - corners[2] + 1;
                if(width > limits.footprint() || length > limits.footprint())
                {
                    return net.minecraft.network.chat.Component.translatable("glass.msg.cloakTooBig", width + "x" + length,
                            limits.footprint() + "x" + limits.footprint());
                }
            }
            return null;
        }
        if(wirelessPos.isEmpty() && countGlass(limits.glass() + 1) > limits.glass())
        {
            return net.minecraft.network.chat.Component.translatable("glass.msg.cloakTooMuchGlass", limits.glass());
        }
        return null;
    }

    /** The glass blocks joined to this projector (itself included), counted up to {@code stopAt}. */
    private int countGlass(int stopAt)
    {
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        seen.add(worldPosition);
        queue.add(worldPosition);
        while(!queue.isEmpty() && seen.size() < stopAt)
        {
            BlockPos pos = queue.poll();
            for(Direction direction : Direction.values())
            {
                BlockPos next = pos.relative(direction);
                if(!seen.contains(next) && level.isLoaded(next) && level.getBlockState(next).getBlock() instanceof ryzlar.glass.block.GlassBlock glass
                        && glass.getKind() != ryzlar.glass.block.GlassBlock.Kind.WIRELESS)
                {
                    seen.add(next);
                    queue.add(next);
                }
            }
        }
        return seen.size();
    }

    /** Tells the players near the projector why its cloak will not run. */
    private void tellNearby(net.minecraft.network.chat.Component message)
    {
        if(level instanceof ServerLevel server)
        {
            for(net.minecraft.server.level.ServerPlayer player : server.players())
            {
                if(player.blockPosition().closerThan(worldPosition, 24D))
                {
                    player.displayClientMessage(message.copy().withStyle(net.minecraft.ChatFormatting.RED), true);
                }
            }
        }
    }

    /** True when there is something to show once powered: a channel, or cloak mode. */
    public boolean hasFeed()
    {
        return cloak || !channelId.isEmpty();
    }

    public boolean isShowingOwnScreen()
    {
        return active && channel.equals(screenKey());
    }

    /** True when the projector shows its own channel through wireless extensions instead of attached glass. */
    public boolean isWirelessActive()
    {
        return isShowingOwnScreen() && !wirelessPos.isEmpty();
    }

    @Override
    public void update()
    {
        super.update(); //Glass master is also a glass base. Let it do it's job.
        float rotationFactor = isShowingOwnScreen() ? (1.0F - (1.0F * (float)fadeoutTime / FADEOUT_TIME)) : (1.0F * (float)fadeoutTime / FADEOUT_TIME);
        rotationBeaconPrev = rotationBeacon;
        rotationBeacon += 20F * rotationFactor;

        if(!level.isClientSide)
        {
            //A screen started with an older key (or whose feed went away) is switched off; powering it again restarts it.
            if(active && distance == 1 && !channel.equals(screenKey()))
            {
                turnOff();
            }
            if(wirelessPos.removeIf(pos -> level.isLoaded(pos) && !(level.getBlockEntity(pos) instanceof GlassWirelessBlockEntity)))
            {
                sync();
            }
            //A cloak keeps to its limits: its height and depth are cut down, and it goes off if it no longer fits (an
            //amplifier was taken away).
            if(cloak && ++limitTimer >= LIMIT_CHECK_INTERVAL)
            {
                limitTimer = 0;
                CloakLimits limits = cloakLimits();
                if(fieldHeight > limits.height() || cloakDepth > limits.depth())
                {
                    fieldHeight = Math.min(fieldHeight, limits.height());
                    cloakDepth = Math.min(cloakDepth, limits.depth());
                    sync();
                }
                net.minecraft.network.chat.Component problem = active && distance == 1 ? cloakProblem() : null;
                if(problem != null)
                {
                    tellNearby(problem);
                    turnOff();
                }
            }
            if(++resolveTimer >= RESOLVE_INTERVAL)
            {
                resolveTimer = 0;
                resolveCamera(GlassNetworkManager.data(((ServerLevel)level).getServer()), false);
            }
        }

        if(isWirelessActive())
        {
            wirelessTime++;
        }
        else
        {
            wirelessTime = 0;
        }
    }

    /** Server: works out which camera to show from the network, and sends it to clients if it changed. */
    public void resolveCamera(GlassNetworkData data, boolean forceSync)
    {
        boolean changed = forceSync;
        GlassNetworkData.Channel channel = channelId.isEmpty() ? null : data.channels.get(channelId);
        if(channel == null && !channelId.isEmpty())
        {
            //The channel was deleted.
            if(active && !cloak)
            {
                turnOff();
            }
            channelId = "";
            channelName = "";
            changed = true;
        }
        if(channel != null && !channel.name.equals(channelName))
        {
            channelName = channel.name;
            changed = true;
        }

        if(channel != null && level instanceof ServerLevel serverLevel)
        {
            GlassNetworkManager.dropMissingCamera(serverLevel.getServer(), channel);
        }
        changed |= setCamera(channel == null ? null : channel.camera);
        if(changed)
        {
            sync();
        }
    }

    private boolean setCamera(@Nullable GlassNetworkData.Camera camera)
    {
        boolean valid = camera != null;
        ResourceKey<Level> dimension = valid ? camera.dimension() : Level.OVERWORLD;
        BlockPos pos = valid ? camera.pos() : BlockPos.ZERO;
        Direction facing = valid ? camera.facing() : Direction.NORTH;
        if(valid == camValid && dimension.equals(camDimension) && pos.equals(camPos) && facing == camFacing)
        {
            return false;
        }
        camValid = valid;
        camDimension = dimension;
        camPos = pos;
        camFacing = facing;
        return true;
    }

    public void changeRedstoneState(boolean newState)
    {
        if(newState)
        {
            net.minecraft.network.chat.Component problem = active ? null : cloakProblem();
            if(problem != null)
            {
                tellNearby(problem);
                return;
            }
            if(!active && hasFeed())
            {
                active = true;
                channel = screenKey();
                distance = 1;
                if(!activeFaces.contains(placingFace))
                {
                    activeFaces.add(placingFace);
                }
                fadeoutTime = FADEOUT_TIME;
                propagateTime = PROPAGATE_TIME;
                sync();
            }
        }
        else if(active)
        {
            turnOff();
        }
    }

    public void turnOff()
    {
        active = false;
        fadeoutTime = FADEOUT_TIME;
        propagateTime = PROPAGATE_TIME;
        sync();
    }

    @Override
    public boolean canPropagate()
    {
        return wirelessPos.isEmpty() && super.canPropagate();
    }

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        super.saveAdditional(output);
        output.putBoolean("powered", powered);
        output.store("placingFace", Direction.CODEC, placingFace);
        output.store("wirelessPos", BlockPos.CODEC.listOf(), List.copyOf(wirelessPos));
        output.putString("channelId", channelId);
        output.putString("channelName", channelName);
        output.putBoolean("cloak", cloak);
        output.putInt("cloakDepth", cloakDepth);
        output.putBoolean("cloakField", cloakField);
        output.putInt("fieldHeight", fieldHeight);
        output.putBoolean("camValid", camValid);
        output.store("camDimension", Level.RESOURCE_KEY_CODEC, camDimension);
        output.store("camPos", BlockPos.CODEC, camPos);
        output.store("camFacing", Direction.CODEC, camFacing);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        powered = input.getBooleanOr("powered", false);
        placingFace = input.read("placingFace", Direction.CODEC).orElse(Direction.NORTH);
        wirelessPos = new ArrayList<>(input.read("wirelessPos", BlockPos.CODEC.listOf()).orElse(List.of()));
        channelId = input.getStringOr("channelId", "");
        channelName = input.getStringOr("channelName", "");
        cloak = input.getBooleanOr("cloak", false);
        cloakDepth = Math.clamp(input.getIntOr("cloakDepth", 4), 1, MAX_CLOAK_DEPTH);
        cloakField = input.getBooleanOr("cloakField", false);
        fieldHeight = Math.clamp(input.getIntOr("fieldHeight", 8), 1, MAX_FIELD_HEIGHT);
        camValid = input.getBooleanOr("camValid", false);
        camDimension = input.read("camDimension", Level.RESOURCE_KEY_CODEC).orElse(Level.OVERWORLD);
        camPos = input.read("camPos", BlockPos.CODEC).orElse(BlockPos.ZERO);
        camFacing = input.read("camFacing", Direction.CODEC).orElse(Direction.NORTH);
        onClientDataReceived(); //again, now the projector's own fields are loaded too
        if(justSwitchedOn && level != null && level.isClientSide)
        {
            //The screen builds itself; a cloak bends the light; a cloak field rises out of the ground.
            net.minecraft.sounds.SoundEvent sound = cloakFieldBox() != null ? ryzlar.glass.GlassSounds.FIELD_RISE
                    : cloak ? ryzlar.glass.GlassSounds.CLOAK_ENGAGE : ryzlar.glass.GlassSounds.NANO_ASSEMBLE;
            BlockPos pos = getBlockPos();
            level.playLocalSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, sound, net.minecraft.sounds.SoundSource.BLOCKS, 0.9F, 1F, false);
        }
        justSwitchedOn = false;
    }

    /** Identifies the camera currently shown, so the client can tell when it switches. */
    public String cameraSignature()
    {
        if(cloak)
        {
            AABB field = cloakFieldBox();
            return field != null ? "field|" + field : "cloak|" + cloakDepth;
        }
        return camValid ? camDimension.location() + "|" + camPos.asLong() + "|" + camFacing : "";
    }
}
