package ryzlar.glass.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.GlassClientBridge;
import ryzlar.glass.GlassRegistry;
import ryzlar.glass.GlassUtil;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A GLASS extension block. Port of TileEntityGlassBase.
 * <p>
 * When a projector is powered, it turns on and passes the channel on to neighbouring glass ("propagation"),
 * one block every {@link #PROPAGATE_TIME} ticks, up to 40 blocks away.
 */
public class GlassBaseBlockEntity extends BlockEntity
{
    public static final Map<Direction, List<Direction>> PROPAGATION_FACES = new EnumMap<>(Direction.class);

    static
    {
        for(Direction face : Direction.values())
        {
            List<Direction> faces = new ArrayList<>();
            for(Direction face1 : Direction.values())
            {
                if(face1.getAxis() != face.getAxis())
                {
                    faces.add(face1);
                }
            }
            PROPAGATION_FACES.put(face, faces);
        }
    }

    public static final int FADEOUT_TIME = 12;
    public static final int PROPAGATE_TIME = 2;

    public int fadeoutTime = 0;

    public List<Direction> activeFaces = new ArrayList<>();
    public boolean active = false;
    public String channel = "";
    public int distance = 0; //distance = 0 also means off
    public int propagateTime = 0;

    //Client-only state
    public int fadePropagate;
    public int fadeDistance;
    public int lastDraw;

    /**
     * Client: the screen appears and disappears as one wave across the whole screen, centred on the projector. It grows
     * by this many blocks per tick, a little slower than glass turns on (one block per {@link #PROPAGATE_TIME} ticks, and
     * diagonally further), so every block is on before the wave reaches it.
     */
    public static final float WAVE_SPEED = 0.33F;

    /**
     * Client: switching off. The wave shrinks back into the projector (the screen keeps the camera image until the wave
     * passes), then the block is plain glass again. {@link #offKey} is empty when not switching off.
     */
    public String offKey = "";
    /** Game time the wave starts shrinking (the same for the whole screen). */
    public long offStart;
    /** How far from the projector the wave starts shrinking, in blocks. */
    public float offRadius = 1F;
    public List<Direction> offFaces = List.of();
    public boolean offWireless;
    /** The screen switching off was a cloak (one image per side). */
    public boolean offCloak;

    /** Client: game time this block switched on. For the projector, also when the wave starts growing. */
    public long onStart = Long.MIN_VALUE / 2;

    /** 0 when the block switched on, 1 once {@link #FADEOUT_TIME} ticks have passed. */
    public float onProgress(float partialTick)
    {
        return level == null ? 1F : (level.getGameTime() - onStart + partialTick) / (float)FADEOUT_TIME;
    }

    /** Radius of the turn-on wave, in blocks from the projector. */
    public float onRadius(float partialTick)
    {
        long ticks = level == null ? Long.MAX_VALUE / 2 : level.getGameTime() - onStart;
        return ticks > 100_000L ? 1000F : (ticks + partialTick) * WAVE_SPEED;
    }

    public boolean isSwitchingOff()
    {
        return !offKey.isEmpty();
    }

    /**
     * Radius of the switch-off wave, shrinking from {@link #offRadius} to 0. It eases in and out (slow, faster, slow)
     * over the time a steady wave would take, which looks smoother than a constant speed.
     */
    public float offWaveRadius(float partialTick)
    {
        if(level == null)
        {
            return 0F;
        }
        float duration = Math.max(1F, offRadius / WAVE_SPEED);
        float x = Math.clamp((level.getGameTime() - offStart + partialTick) / duration, 0F, 1F);
        float eased = x * x * x * (x * (x * 6F - 15F) + 10F); //smootherstep
        return offRadius * (1F - eased);
    }

    /** True once the switch-off wave has shrunk all the way back into the projector. */
    public boolean isSwitchOffOver()
    {
        return level == null || level.getGameTime() - offStart > Math.max(1F, offRadius / WAVE_SPEED);
    }

    private void clearSwitchOff()
    {
        offKey = "";
        offFaces = List.of();
    }

    public GlassBaseBlockEntity(BlockPos pos, BlockState state)
    {
        this(GlassRegistry.GLASS_EXTENSION_BE, pos, state);
    }

    protected GlassBaseBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state)
    {
        super(type, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, GlassBaseBlockEntity glass)
    {
        glass.update();
        if(level.isClientSide)
        {
            GlassClientBridge.get().syncGlass(glass);
        }
    }

    @Override
    public void setRemoved()
    {
        super.setRemoved();
        if(level != null && level.isClientSide)
        {
            GlassClientBridge.get().removeGlass(this);
        }
    }

    /** The channel this block shows on the client, or an empty string if it is off. */
    public String getDisplayedChannel()
    {
        return active ? channel : "";
    }

    public void update()
    {
        if(fadeoutTime > 0)
        {
            fadeoutTime--;
            if(fadeoutTime == 0)
            {
                if(!active)
                {
                    activeFaces.clear();
                }
                if(fadeDistance > 0)
                {
                    fadeDistance = 0;
                }
            }
        }
        if(propagateTime > 0)
        {
            propagateTime--;
            if(!level.isClientSide && propagateTime == 0)
            {
                propagate();
            }
        }
        if(fadePropagate > 0)
        {
            fadePropagate--;
            if(level.isClientSide && fadePropagate == 0)
            {
                fadePropagate();
            }
        }
        if(lastDraw > 0)
        {
            lastDraw--;
        }
        //The switch-off animation is over once the wave has shrunk into the projector, this block is off on the server too,
        //and the server's own fade timer has run out (blocks far from the projector can hear about the switch-off late).
        //Until then the block keeps drawing the switch-off, so it cannot flash the old image.
        if(isSwitchingOff() && !active && fadeoutTime == 0 && isSwitchOffOver())
        {
            clearSwitchOff();
        }
    }

    @Nullable
    public GlassBaseBlockEntity getGlass(BlockPos pos)
    {
        if(level == null || !level.isLoaded(pos))
        {
            return null;
        }
        return level.getBlockEntity(pos) instanceof GlassBaseBlockEntity glass ? glass : null;
    }

    public Set<Direction> getPropagationFaces()
    {
        Set<Direction> propagationFaces = new HashSet<>();
        for(Direction facing : activeFaces)
        {
            propagationFaces.addAll(PROPAGATION_FACES.get(facing));
        }
        return propagationFaces;
    }

    /** Client side: the white flash that ripples outwards when you punch active glass. */
    public void fadePropagate()
    {
        if(fadeDistance <= 0 || !active)
        {
            return;
        }
        for(Direction facing : getPropagationFaces())
        {
            GlassBaseBlockEntity base = getGlass(getBlockPos().relative(facing));
            if(base != null && base.active && base.channel.equalsIgnoreCase(channel) && base.fadeDistance <= fadeDistance)
            {
                base.fadeoutTime = FADEOUT_TIME;
                base.fadePropagate = PROPAGATE_TIME;
                base.fadeDistance = fadeDistance - 1;
            }
        }
    }

    public boolean canPropagate()
    {
        return distance < 40;
    }

    public void propagate()
    {
        if(!canPropagate())
        {
            return;
        }
        for(Direction facing : getPropagationFaces())
        {
            GlassBaseBlockEntity base = getGlass(getBlockPos().relative(facing));
            if(base != null)
            {
                base.bePropagatedTo(this, channel, active);
            }
        }
        if(!active)
        {
            channel = "";
            distance = 0;
            sync();
        }
    }

    public void bePropagatedTo(GlassBaseBlockEntity base, String newChannel, boolean activate)
    {
        boolean flag = false;
        if(active && activate && channel.equalsIgnoreCase(newChannel)) //same channel and both activated but this is further than the other from master.
        {
            if(distance > base.distance + 1)
            {
                distance = base.distance + 1;
                checkFacesToTurnOn(base);
                flag = true;
            }
        }
        if(activate && !active && (distance > base.distance || distance == 0)) //turn on
        {
            active = true;
            channel = newChannel;
            distance = base.distance + 1;
            checkFacesToTurnOn(base);
            flag = true;
        }
        if(!activate && active && channel.equalsIgnoreCase(newChannel)) //turn off
        {
            if(distance > base.distance || base == this)
            {
                active = false;
                flag = true;
            }
            else
            {
                propagateTime = PROPAGATE_TIME + 1;
                sync();
            }
            //do not set channel or distance as we're still propagating
        }
        if(flag)
        {
            fadeoutTime = FADEOUT_TIME;
            propagateTime = PROPAGATE_TIME;
            sync();
        }
    }

    public void checkFacesToTurnOn(GlassBaseBlockEntity origin)
    {
        if(origin == this)
        {
            return;
        }
        BlockPos pos = getBlockPos();

        activeFaces.clear();
        activeFaces.addAll(origin.activeFaces); //check origin location and remove that active face.
        if(activeFaces.size() > 1)
        {
            for(int i = activeFaces.size() - 1; i >= 0; i--)
            {
                Direction facing = activeFaces.get(i);
                GlassBaseBlockEntity te = getGlass(pos.relative(facing, -1));
                if(te != null && te.active && te.channel.equalsIgnoreCase(channel) && te.distance < distance)
                {
                    activeFaces.remove(i);
                    continue;
                }
                te = getGlass(pos.relative(facing));
                if(te != null && te.active && te.channel.equalsIgnoreCase(channel) && te.distance < distance)
                {
                    activeFaces.remove(i);
                }
            }
        }

        Set<Direction> newFaces = new HashSet<>();
        for(Direction facing : activeFaces)
        {
            BlockPos facePos = pos.relative(facing);
            if(getGlass(facePos) != null) //inner corner
            {
                BlockPos originPos = origin.getBlockPos().relative(facing);
                newFaces.add(Direction.getApproximateNearest(originPos.getX() - facePos.getX(), originPos.getY() - facePos.getY(), originPos.getZ() - facePos.getZ()));
            }
            else //outer corner
            {
                facePos = pos.relative(facing, -1);
                if(getGlass(facePos) != null)
                {
                    if(origin.getBlockPos().getY() != pos.getY())
                    {
                        // maybe the origin is from below but we prefer horizontals
                        for(Direction newFacing : PROPAGATION_FACES.get(facing))
                        {
                            if(newFacing.getAxis() != Direction.Axis.Y)
                            {
                                GlassBaseBlockEntity te1 = getGlass(pos.relative(newFacing));
                                if(te1 != null && te1.activeFaces.contains(facing))
                                {
                                    origin = te1;
                                }
                            }
                        }
                    }
                    BlockPos originPos = origin.getBlockPos().relative(facing, -1);
                    newFaces.add(Direction.getApproximateNearest(facePos.getX() - originPos.getX(), facePos.getY() - originPos.getY(), facePos.getZ() - originPos.getZ()));
                }
            }
        }
        for(Direction face : newFaces)
        {
            if(!activeFaces.contains(face))
            {
                activeFaces.add(face);
            }
        }

        sync();
    }

    /** Saves the block entity and sends the new state to clients (1.12's notifyBlockUpdate, without a block update). */
    public void sync()
    {
        setChanged();
        if(level != null && !level.isClientSide)
        {
            GlassUtil.sendToTracking(this);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        super.saveAdditional(output);
        output.store("activeFaces", Direction.CODEC.listOf(), List.copyOf(activeFaces));
        output.putBoolean("active", active);
        output.putString("channel", channel);
        output.putInt("distance", distance);
        output.putInt("propagateTime", propagateTime);
        output.putInt("fadeoutTime", fadeoutTime);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        boolean wasActive = active;
        super.loadAdditional(input);
        activeFaces = new ArrayList<>(input.read("activeFaces", Direction.CODEC.listOf()).orElse(List.of()));
        active = input.getBooleanOr("active", false);
        channel = input.getStringOr("channel", "");
        distance = input.getIntOr("distance", 0);
        propagateTime = input.getIntOr("propagateTime", 0);
        fadeoutTime = input.getIntOr("fadeoutTime", 0);
        if(level != null && level.isClientSide && active && !wasActive && fadeoutTime > 0)
        {
            onStart = level.getGameTime(); //just switched on (not a screen that was already on when the chunk loaded)
            clearSwitchOff(); //switched back on: forget any switch-off still playing (a lever flicked quickly)
        }
        onClientDataReceived();
    }

    /**
     * Registers the new state with the client right away. Waiting for the next tick meant the glass was drawn as on
     * before its channel knew about it, which showed black for a moment when a screen turned on.
     */
    protected void onClientDataReceived()
    {
        if(level != null && level.isClientSide)
        {
            GlassClientBridge.get().syncGlass(this);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries)
    {
        return saveCustomOnly(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
