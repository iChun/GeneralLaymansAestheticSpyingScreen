package ryzlar.glass.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.GlassClientBridge;
import ryzlar.glass.blockentity.GlassBaseBlockEntity;
import ryzlar.glass.blockentity.GlassProjectorBlockEntity;
import ryzlar.glass.blockentity.GlassTerminalBlockEntity;
import ryzlar.glass.blockentity.GlassWirelessBlockEntity;
import ryzlar.glass.client.gui.ChannelSetterScreen;
import ryzlar.glass.client.gui.ProjectorChannelScreen;
import ryzlar.glass.client.gui.WirelessProjectorScreen;
import ryzlar.glass.client.render.SpyViewRenderer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Client-side bookkeeping: which terminal and which glass blocks belong to each channel. Port of EventHandlerClient.
 * <p>
 * In 1.12 block entities registered themselves in onLoad / onChunkUnload / onDataPacket. Here each block entity reports
 * its current channel every client tick ({@link #syncGlass}) and this class works out what changed.
 */
public final class GlassClientState implements GlassClientBridge
{
    public static final GlassClientState INSTANCE = new GlassClientState();

    public BlockPos clickedPos = null;

    /** Channel name to the terminal currently using it. */
    public final Map<String, GlassTerminalBlockEntity> terminalLocations = new HashMap<>();
    /** Channel name to the active glass on it. A block can be counted twice (wireless extensions used by a projector). */
    private final Map<String, Map<GlassBaseBlockEntity, Integer>> activeGlass = new HashMap<>();
    public final Map<String, TerminalPlacement> terminalPlacements = new HashMap<>();
    public final Map<String, Integer> terminalPlacementCreationTimeout = new HashMap<>();
    /** Every projector loaded on the client, for the wireless extension screen (1.12 used world.loadedTileEntityList). */
    public final Set<GlassProjectorBlockEntity> loadedProjectors = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Channels whose terminal view is drawn even before the glass asks for it, for this many more ticks. */
    private final Map<String, Integer> prewarmTicks = new HashMap<>();
    private static final int PREWARM_TICKS = 40;

    private final Map<GlassBaseBlockEntity, Registration> glassRegistrations = new IdentityHashMap<>();
    private final Map<GlassTerminalBlockEntity, String> terminalRegistrations = new IdentityHashMap<>();

    private record Registration(String channel, List<GlassWirelessBlockEntity> wireless)
    {
        static final Registration NONE = new Registration("", List.of());

        boolean sameAs(Registration other)
        {
            if(!channel.equals(other.channel) || wireless.size() != other.wireless.size())
            {
                return false;
            }
            for(int i = 0; i < wireless.size(); i++)
            {
                if(wireless.get(i) != other.wireless.get(i))
                {
                    return false;
                }
            }
            return true;
        }
    }

    private GlassClientState() {}

    // ---- Bridge: screens and clicks ----

    @Override
    public void openTerminalScreen(GlassTerminalBlockEntity terminal)
    {
        Minecraft.getInstance().setScreen(new ChannelSetterScreen(terminal));
    }

    @Override
    public void openProjectorScreen(GlassProjectorBlockEntity projector)
    {
        Minecraft.getInstance().setScreen(new ProjectorChannelScreen(projector));
    }

    @Override
    public void openWirelessScreen(GlassWirelessBlockEntity wireless)
    {
        Minecraft.getInstance().setScreen(new WirelessProjectorScreen(wireless));
    }

    @Override
    public void onGlassClicked(BlockPos pos)
    {
        clickedPos = pos.immutable();
    }

    // ---- Bridge: block entity registration ----

    @Override
    public void syncGlass(GlassBaseBlockEntity glass)
    {
        if(glass instanceof GlassProjectorBlockEntity projector)
        {
            loadedProjectors.add(projector);
        }

        Registration wanted = wantedRegistration(glass);
        Registration current = glassRegistrations.getOrDefault(glass, Registration.NONE);
        if(current.sameAs(wanted))
        {
            return;
        }
        unregister(glass, current);
        register(glass, wanted);
    }

    @Override
    public void removeGlass(GlassBaseBlockEntity glass)
    {
        if(glass instanceof GlassProjectorBlockEntity projector)
        {
            loadedProjectors.remove(projector);
        }
        unregister(glass, glassRegistrations.getOrDefault(glass, Registration.NONE));
    }

    @Override
    public void syncTerminal(GlassTerminalBlockEntity terminal)
    {
        String current = terminalRegistrations.getOrDefault(terminal, "");
        if(current.equals(terminal.channelName))
        {
            return;
        }
        removeTerminal(terminal);
        if(!terminal.channelName.isEmpty())
        {
            terminalRegistrations.put(terminal, terminal.channelName);
            GlassTerminalBlockEntity previous = terminalLocations.put(terminal.channelName, terminal);
            if(previous != terminal)
            {
                releasePlacement(terminal.channelName);
            }
            channelChanged(terminal.channelName);
        }
    }

    @Override
    public void removeTerminal(GlassTerminalBlockEntity terminal)
    {
        String channel = terminalRegistrations.remove(terminal);
        if(channel != null && terminalLocations.get(channel) == terminal)
        {
            terminalLocations.remove(channel);
            releasePlacement(channel);
        }
    }

    private static Registration wantedRegistration(GlassBaseBlockEntity glass)
    {
        String channel = glass.getDisplayedChannel();
        if(channel.isEmpty())
        {
            return Registration.NONE;
        }
        List<GlassWirelessBlockEntity> wireless = List.of();
        if(glass instanceof GlassProjectorBlockEntity projector && !projector.wirelessPos.isEmpty() && glass.getLevel() != null)
        {
            wireless = new ArrayList<>();
            for(BlockPos pos : projector.wirelessPos)
            {
                if(glass.getLevel().isLoaded(pos) && glass.getLevel().getBlockEntity(pos) instanceof GlassWirelessBlockEntity w)
                {
                    wireless.add(w);
                }
            }
        }
        return new Registration(channel, wireless);
    }

    private void register(GlassBaseBlockEntity glass, Registration registration)
    {
        if(registration.channel.isEmpty())
        {
            glassRegistrations.remove(glass);
            return;
        }
        glassRegistrations.put(glass, registration);
        addGlass(glass, registration.channel);
        for(GlassWirelessBlockEntity wireless : registration.wireless)
        {
            wireless.users++;
            addGlass(wireless, registration.channel);
        }
    }

    private void unregister(GlassBaseBlockEntity glass, Registration registration)
    {
        glassRegistrations.remove(glass);
        if(registration.channel.isEmpty())
        {
            return;
        }
        removeGlassFromChannel(glass, registration.channel);
        for(GlassWirelessBlockEntity wireless : registration.wireless)
        {
            wireless.users--;
            removeGlassFromChannel(wireless, registration.channel);
        }
    }

    private void addGlass(GlassBaseBlockEntity base, String channel)
    {
        activeGlass.computeIfAbsent(channel, k -> new IdentityHashMap<>()).merge(base, 1, Integer::sum);
        channelChanged(channel);

        TerminalPlacement placement = terminalPlacements.get(channel);
        if(placement != null)
        {
            placement.onGlassChanged();
        }
    }

    private void removeGlassFromChannel(GlassBaseBlockEntity base, String channel)
    {
        Map<GlassBaseBlockEntity, Integer> bases = activeGlass.get(channel);
        if(bases == null)
        {
            return;
        }
        bases.computeIfPresent(base, (k, count) -> count > 1 ? count - 1 : null);
        if(bases.isEmpty())
        {
            activeGlass.remove(channel);
            releasePlacement(channel);
        }
        else
        {
            TerminalPlacement placement = terminalPlacements.get(channel);
            if(placement != null)
            {
                placement.onGlassChanged();
            }
        }
    }

    /**
     * Something on this channel changed, so a terminal placement may be possible now: drop the retry wait (1.12 waited
     * 13 ticks, which showed as a black screen when turning on) and draw the terminal's view ahead of time for a bit,
     * so it is ready by the time the glass shows it.
     */
    private void channelChanged(String channel)
    {
        terminalPlacementCreationTimeout.remove(channel);
        prewarmTicks.put(channel, PREWARM_TICKS);
    }

    private void releasePlacement(String channel)
    {
        terminalPlacements.remove(channel);
        terminalPlacementCreationTimeout.remove(channel);
        SpyViewRenderer.INSTANCE.release(channel);
    }

    public Set<GlassBaseBlockEntity> getActiveGlass(String channel)
    {
        Map<GlassBaseBlockEntity, Integer> bases = channel.isEmpty() ? null : activeGlass.get(channel);
        return bases == null ? Set.of() : bases.keySet();
    }

    /** Called from rendering, only for active glass. Port of EventHandlerClient.getTerminalPlacement. */
    @Nullable
    public TerminalPlacement getTerminalPlacement(String channel)
    {
        if(terminalPlacementCreationTimeout.containsKey(channel))
        {
            return null;
        }
        TerminalPlacement existing = terminalPlacements.get(channel);
        if(existing != null)
        {
            if(existing.isValid())
            {
                return existing;
            }
            releasePlacement(channel);
        }

        GlassTerminalBlockEntity terminal = terminalLocations.get(channel);
        if(terminal != null && !terminal.isRemoved())
        {
            //we have the terminal. now find the projector
            GlassProjectorBlockEntity master = null;
            for(GlassBaseBlockEntity base : getActiveGlass(channel))
            {
                if(base.active && base.distance == 1 && base.channel.equalsIgnoreCase(channel) && base instanceof GlassProjectorBlockEntity projector && !projector.isRemoved())
                {
                    master = projector;
                    break;
                }
            }

            if(master != null) //we have the projector. Now generate the terminal placement
            {
                TerminalPlacement placement = new TerminalPlacement(channel, master, terminal);
                terminalPlacements.put(channel, placement);
                return placement;
            }
        }

        terminalPlacementCreationTimeout.put(channel, 13); //13 tick wait before trying again.
        return null;
    }

    /** Channels the local player may tune a projector to, sorted. */
    public List<String> availableChannels(String playerName)
    {
        Set<String> channels = new LinkedHashSet<>();
        for(String s : terminalLocations.keySet())
        {
            if(s.startsWith("public:") || s.startsWith(playerName + ":"))
            {
                channels.add(s);
            }
        }
        List<String> sorted = new ArrayList<>(channels);
        Collections.sort(sorted);
        return sorted;
    }

    public void clientTick(Minecraft mc)
    {
        if(mc.level == null)
        {
            return;
        }
        if(clickedPos != null)
        {
            if(mc.level.getBlockEntity(clickedPos) instanceof GlassBaseBlockEntity base && base.active && mc.gameMode != null && mc.gameMode.isDestroying())
            {
                if(base.fadeoutTime < GlassBaseBlockEntity.FADEOUT_TIME - GlassBaseBlockEntity.PROPAGATE_TIME)
                {
                    base.fadeoutTime = GlassBaseBlockEntity.FADEOUT_TIME;
                    base.fadePropagate = GlassBaseBlockEntity.PROPAGATE_TIME;
                    base.fadeDistance = 2;
                    base.fadePropagate();
                }
            }
            else
            {
                clickedPos = null;
            }
        }

        Iterator<Map.Entry<String, Integer>> ite = terminalPlacementCreationTimeout.entrySet().iterator();
        while(ite.hasNext())
        {
            Map.Entry<String, Integer> e = ite.next();
            e.setValue(e.getValue() - 1);
            if(e.getValue() < 0)
            {
                ite.remove();
            }
        }

        Iterator<Map.Entry<String, Integer>> prewarm = prewarmTicks.entrySet().iterator();
        while(prewarm.hasNext())
        {
            Map.Entry<String, Integer> e = prewarm.next();
            e.setValue(e.getValue() - 1);
            if(e.getValue() < 0 || !activeGlass.containsKey(e.getKey()))
            {
                prewarm.remove();
                continue;
            }
            TerminalPlacement placement = getTerminalPlacement(e.getKey());
            if(placement != null)
            {
                SpyViewRenderer.INSTANCE.request(placement);
            }
        }

        //Drop anything left over from a world we are no longer in.
        glassRegistrations.keySet().removeIf(be -> {
            if(be.getLevel() != mc.level)
            {
                Registration reg = glassRegistrations.get(be);
                if(reg != null && !reg.channel.isEmpty())
                {
                    removeGlassFromChannel(be, reg.channel);
                    for(GlassWirelessBlockEntity wireless : reg.wireless)
                    {
                        removeGlassFromChannel(wireless, reg.channel);
                    }
                }
                return true;
            }
            return false;
        });
        terminalRegistrations.keySet().removeIf(be -> be.getLevel() != mc.level);
        terminalLocations.values().removeIf(be -> be.getLevel() != mc.level);
        loadedProjectors.removeIf(be -> be.getLevel() != mc.level);
    }

    public void clear()
    {
        clickedPos = null;
        terminalLocations.clear();
        activeGlass.clear();
        terminalPlacements.clear();
        terminalPlacementCreationTimeout.clear();
        prewarmTicks.clear();
        loadedProjectors.clear();
        glassRegistrations.clear();
        terminalRegistrations.clear();
        SpyViewRenderer.INSTANCE.releaseAll();
    }

    public boolean isOwnChannel(String channel, String playerName)
    {
        return Objects.equals(channel, "") || channel.startsWith("public:") || channel.startsWith(playerName + ":");
    }
}
