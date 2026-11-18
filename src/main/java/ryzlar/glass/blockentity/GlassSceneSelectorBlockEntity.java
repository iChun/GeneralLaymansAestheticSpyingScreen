package ryzlar.glass.blockentity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.GlassRegistry;
import ryzlar.glass.GlassUtil;
import ryzlar.glass.network.GlassNetworkData;
import ryzlar.glass.network.GlassNetworkManager;
import ryzlar.glass.network.GlassNetworking;

import java.util.ArrayList;
import java.util.List;

/**
 * The scene selector: a control panel for one projector, holding a list of scenes (channels). Showing a scene points
 * the projector at it; while the projector is on, the new scene wipes over the old one. A redstone pulse shows the next
 * scene. Anyone can use a selector; adding a scene needs access to its channel.
 */
public class GlassSceneSelectorBlockEntity extends BlockEntity
{
    public static final int MAX_SCENES = 8;
    /** How far from the selector a projector can be linked, in blocks (each way). */
    public static final int LINK_RANGE = 16;

    public record Scene(String channelId, String name)
    {
        public static final Codec<Scene> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Scene::channelId),
                Codec.STRING.fieldOf("name").forGetter(Scene::name)
        ).apply(i, Scene::new));
    }

    public List<Scene> scenes = new ArrayList<>();
    /** The scene shown last, or -1. */
    public int current = -1;
    @Nullable
    public BlockPos projectorPos;
    public boolean powered;
    /** Client: bumped whenever new data arrives, so an open screen can refresh. */
    public int clientVersion;

    public GlassSceneSelectorBlockEntity(BlockPos pos, BlockState state)
    {
        super(GlassRegistry.GLASS_SCENE_SELECTOR_BE, pos, state);
    }

    @Nullable
    public GlassProjectorBlockEntity projector()
    {
        if(level == null || projectorPos == null || !level.isLoaded(projectorPos))
        {
            return null;
        }
        return level.getBlockEntity(projectorPos) instanceof GlassProjectorBlockEntity projector ? projector : null;
    }

    // ---- Server ----

    /** Links the nearest projector in range. Returns false if there is none. */
    public boolean linkNearest()
    {
        if(level == null)
        {
            return false;
        }
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for(BlockPos pos : BlockPos.betweenClosed(worldPosition.offset(-LINK_RANGE, -LINK_RANGE, -LINK_RANGE), worldPosition.offset(LINK_RANGE, LINK_RANGE, LINK_RANGE)))
        {
            if(level.isLoaded(pos) && level.getBlockEntity(pos) instanceof GlassProjectorBlockEntity)
            {
                double distance = pos.distSqr(worldPosition);
                if(distance < bestDistance)
                {
                    bestDistance = distance;
                    best = pos.immutable();
                }
            }
        }
        if(best == null)
        {
            return false;
        }
        projectorPos = best;
        sync();
        return true;
    }

    public boolean addScene(String channelId, String name)
    {
        if(scenes.size() >= MAX_SCENES || scenes.stream().anyMatch(s -> s.channelId().equals(channelId)))
        {
            return false;
        }
        scenes.add(new Scene(channelId, name));
        sync();
        return true;
    }

    public void removeScene(int index)
    {
        if(index < 0 || index >= scenes.size())
        {
            return;
        }
        scenes.remove(index);
        if(current == index)
        {
            current = -1;
        }
        else if(current > index)
        {
            current--;
        }
        sync();
    }

    public void moveScene(int index, int delta)
    {
        int to = index + delta;
        if(index < 0 || index >= scenes.size() || to < 0 || to >= scenes.size())
        {
            return;
        }
        Scene scene = scenes.remove(index);
        scenes.add(to, scene);
        if(current == index)
        {
            current = to;
        }
        else if(current == to)
        {
            current = index;
        }
        sync();
    }

    /** Points the linked projector at a scene. Returns false if it could not (no projector, or the channel is gone). */
    public boolean showScene(int index)
    {
        GlassProjectorBlockEntity projector = projector();
        if(projector == null || index < 0 || index >= scenes.size() || !(level instanceof ServerLevel serverLevel))
        {
            return false;
        }
        GlassNetworkData data = GlassNetworkManager.data(serverLevel.getServer());
        Scene scene = scenes.get(index);
        GlassNetworkData.Channel channel = data.channels.get(scene.channelId());
        if(channel == null)
        {
            return false;
        }
        if(!channel.name.equals(scene.name()))
        {
            scenes.set(index, new Scene(scene.channelId(), channel.name));
        }
        GlassNetworking.showChannel(projector, data, channel.id, channel.name);
        current = index;
        sync();
        return true;
    }

    /** A redstone pulse: the next scene (skipping scenes whose channel is gone). */
    public void nextScene()
    {
        for(int tries = 0; tries < scenes.size(); tries++)
        {
            if(showScene((current + 1 + tries) % scenes.size()))
            {
                return;
            }
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

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        super.saveAdditional(output);
        output.store("scenes", Scene.CODEC.listOf(), List.copyOf(scenes));
        output.putInt("current", current);
        output.storeNullable("projector", BlockPos.CODEC, projectorPos);
        output.putBoolean("powered", powered);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        scenes = new ArrayList<>(input.read("scenes", Scene.CODEC.listOf()).orElse(List.of()));
        current = input.getIntOr("current", -1);
        projectorPos = input.read("projector", BlockPos.CODEC).orElse(null);
        powered = input.getBooleanOr("powered", false);
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
