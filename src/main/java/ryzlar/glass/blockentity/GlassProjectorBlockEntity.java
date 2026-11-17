package ryzlar.glass.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import ryzlar.glass.GlassRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * The GLASS projector: the block a channel starts from when it gets a redstone signal. Port of TileEntityGlassMaster.
 */
public class GlassProjectorBlockEntity extends GlassBaseBlockEntity
{
    public static final int MAX_WIRELESS = 64;

    public boolean powered;
    public String setChannel = "";
    public Direction placingFace = Direction.NORTH;
    public List<BlockPos> wirelessPos = new ArrayList<>();

    public float rotationBeacon, rotationBeaconPrev;
    public int wirelessTime;

    public GlassProjectorBlockEntity(BlockPos pos, BlockState state)
    {
        super(GlassRegistry.GLASS_PROJECTOR_BE, pos, state);
    }

    /** True when the projector shows its own channel through wireless extensions instead of attached glass. */
    public boolean isWirelessActive()
    {
        return active && channel.equalsIgnoreCase(setChannel) && !wirelessPos.isEmpty();
    }

    @Override
    public void update()
    {
        super.update(); //Glass master is also a glass base. Let it do it's job.
        float rotationFactor = active && channel.equalsIgnoreCase(setChannel) ? (1.0F - (1.0F * (float)fadeoutTime / FADEOUT_TIME)) : (1.0F * (float)fadeoutTime / FADEOUT_TIME);
        rotationBeaconPrev = rotationBeacon;
        rotationBeacon += 20F * rotationFactor;

        if(!level.isClientSide && wirelessPos.removeIf(pos -> level.isLoaded(pos) && !(level.getBlockEntity(pos) instanceof GlassWirelessBlockEntity)))
        {
            sync();
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

    public void changeRedstoneState(boolean newState)
    {
        if(!setChannel.isEmpty() && (!active || channel.equalsIgnoreCase(setChannel)))
        {
            if(newState)
            {
                active = true;
                channel = setChannel;
                distance = 1;
                if(!activeFaces.contains(placingFace))
                {
                    activeFaces.add(placingFace);
                }
            }
            else
            {
                active = false;
            }
            fadeoutTime = FADEOUT_TIME;
            propagateTime = PROPAGATE_TIME;
            sync();
        }
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
        output.putString("setChannel", setChannel);
        output.store("placingFace", Direction.CODEC, placingFace);
        output.store("wirelessPos", BlockPos.CODEC.listOf(), List.copyOf(wirelessPos));
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        powered = input.getBooleanOr("powered", false);
        setChannel = input.getStringOr("setChannel", "");
        placingFace = input.read("placingFace", Direction.CODEC).orElse(Direction.NORTH);
        wirelessPos = new ArrayList<>(input.read("wirelessPos", BlockPos.CODEC.listOf()).orElse(List.of()));
        onClientDataReceived(); //again, now the projector's own fields are loaded too
    }
}
