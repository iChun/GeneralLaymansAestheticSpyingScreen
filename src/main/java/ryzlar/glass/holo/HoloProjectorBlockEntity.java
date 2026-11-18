package ryzlar.glass.holo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.GlassRegistry;
import ryzlar.glass.GlassUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * The holo projector: holds a projection crystal and, while powered, projects it. Each side of the projected blocks
 * only shows where the projector or one of its emitters can "see" it, so a complete hologram needs emitters around it.
 */
public class HoloProjectorBlockEntity extends BlockEntity
{
    public static final int MAX_EMITTERS = 16;
    public static final int LINK_RANGE = 16;
    private static final int VALIDATE_INTERVAL = 20;

    /** How the projection looks. */
    public enum Look { HOLO, BLUE, SOLID }

    /** Scale steps: 0 fits the projection in the space between the emitters, the rest are fixed. */
    public static final float[] SCALES = { 0F, 1F / 16F, 1F / 8F, 1F / 4F, 1F / 2F, 1F };

    public ItemStack crystal = ItemStack.EMPTY;
    public Look look = Look.HOLO;
    public int scale = 0;
    /** Quarter turns around the vertical axis. */
    public int rotation = 0;
    public List<BlockPos> emitters = new ArrayList<>();
    public boolean powered;
    public boolean active;
    private int validateTimer;

    /** How fast the nano wave spreads over the hologram, in blocks per tick (the same as the GLASS screens). */
    public static final float WAVE_SPEED = 0.33F;

    /** Client: game time it switched on (the wave grows from the projector). */
    public long onStart = Long.MIN_VALUE / 2;
    /** Client: game time it switched off, and how far the wave had got (it shrinks back from there). */
    public long offStart = Long.MIN_VALUE / 2;
    public float offRadius;
    /** Client: how far the hologram reaches from the projector, set by the renderer. */
    public float clientReach = 8F;
    private boolean clientLoaded;
    /** Client: bumped whenever new data arrives (screens refresh, the hologram rebuilds). */
    public int clientVersion;

    public HoloProjectorBlockEntity(BlockPos pos, BlockState state)
    {
        super(GlassRegistry.HOLO_PROJECTOR_BE, pos, state);
    }

    @Nullable
    public ProjectionRef projection()
    {
        return crystal.get(GlassRegistry.PROJECTION);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HoloProjectorBlockEntity projector)
    {
        if(++projector.validateTimer >= VALIDATE_INTERVAL)
        {
            projector.validateTimer = 0;
            if(projector.emitters.removeIf(p -> level.isLoaded(p) && !level.getBlockState(p).is(GlassRegistry.HOLO_EMITTER)))
            {
                projector.sync();
            }
        }
    }

    /** Links the emitters in range (replacing the old list). Returns how many. */
    public int linkEmitters()
    {
        if(level == null)
        {
            return 0;
        }
        List<BlockPos> found = new ArrayList<>();
        for(BlockPos pos : BlockPos.betweenClosed(worldPosition.offset(-LINK_RANGE, -LINK_RANGE, -LINK_RANGE), worldPosition.offset(LINK_RANGE, LINK_RANGE, LINK_RANGE)))
        {
            if(found.size() >= MAX_EMITTERS)
            {
                break;
            }
            if(level.isLoaded(pos) && level.getBlockState(pos).is(GlassRegistry.HOLO_EMITTER))
            {
                found.add(pos.immutable());
            }
        }
        emitters = found;
        sync();
        return found.size();
    }

    public void configure(int newLook, int newScale, int newRotation)
    {
        look = Look.values()[Math.floorMod(newLook, Look.values().length)];
        scale = Math.floorMod(newScale, SCALES.length);
        rotation = Math.floorMod(newRotation, 4);
        sync();
    }

    public void setPowered(boolean signal)
    {
        powered = signal;
        boolean on = signal && !crystal.isEmpty();
        if(on != active)
        {
            active = on;
            sync();
        }
        setChanged();
    }

    /** Takes the crystal out, or returns empty. */
    public ItemStack takeCrystal()
    {
        ItemStack out = crystal;
        crystal = ItemStack.EMPTY;
        active = false;
        sync();
        return out;
    }

    public void putCrystal(ItemStack stack)
    {
        crystal = stack.copyWithCount(1);
        active = powered;
        sync();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        super.preRemoveSideEffects(pos, state);
        if(level != null && !level.isClientSide && !crystal.isEmpty())
        {
            Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, crystal);
            crystal = ItemStack.EMPTY;
        }
    }

    public void sync()
    {
        setChanged();
        if(level != null && !level.isClientSide)
        {
            GlassUtil.sendToTracking(this);
        }
    }

    /** Client: how far the switch-on wave has spread (capped just past the hologram's reach). */
    public float onRadius(float partialTick)
    {
        long ticks = level == null ? Long.MAX_VALUE / 2 : level.getGameTime() - onStart;
        return ticks > 100_000L ? clientReach + 2F : Math.min(clientReach + 2F, (ticks + partialTick) * WAVE_SPEED);
    }

    /** Client: the switch-off wave, shrinking back into the projector (eased like the screens'), or below 0 once done. */
    public float offWaveRadius(float partialTick)
    {
        if(level == null)
        {
            return -1F;
        }
        float duration = Math.max(1F, offRadius / WAVE_SPEED);
        float x = (level.getGameTime() - offStart + partialTick) / duration;
        if(x >= 1F)
        {
            return -1F;
        }
        x = Math.clamp(x, 0F, 1F);
        float eased = x * x * x * (x * (x * 6F - 15F) + 10F);
        return offRadius * (1F - eased);
    }

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        super.saveAdditional(output);
        if(!crystal.isEmpty())
        {
            output.store("crystal", ItemStack.CODEC, crystal);
        }
        output.putInt("look", look.ordinal());
        output.putInt("scale", scale);
        output.putInt("rotation", rotation);
        output.store("emitters", BlockPos.CODEC.listOf(), List.copyOf(emitters));
        output.putBoolean("powered", powered);
        output.putBoolean("active", active);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        boolean wasActive = active;
        super.loadAdditional(input);
        crystal = input.read("crystal", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        look = Look.values()[Math.floorMod(input.getIntOr("look", 0), Look.values().length)];
        scale = Math.floorMod(input.getIntOr("scale", 0), SCALES.length);
        rotation = Math.floorMod(input.getIntOr("rotation", 0), 4);
        emitters = new ArrayList<>(input.read("emitters", BlockPos.CODEC.listOf()).orElse(List.of()));
        powered = input.getBooleanOr("powered", false);
        active = input.getBooleanOr("active", false);
        if(level != null && level.isClientSide)
        {
            //Not on the first load (a hologram that was already on is just there).
            if(clientLoaded && active && !wasActive)
            {
                onStart = level.getGameTime();
            }
            else if(clientLoaded && !active && wasActive)
            {
                offRadius = onRadius(0F);
                offStart = level.getGameTime();
            }
            clientLoaded = true;
        }
        clientVersion++;
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
