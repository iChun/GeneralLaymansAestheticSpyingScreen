package ryzlar.glass.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ryzlar.glass.blockentity.GlassBaseBlockEntity;
import ryzlar.glass.blockentity.GlassProjectorBlockEntity;
import ryzlar.glass.blockentity.GlassTerminalBlockEntity;
import ryzlar.glass.client.gui.CameraScreen;
import ryzlar.glass.client.gui.ProjectorScreen;
import ryzlar.glass.client.gui.WirelessOrderScreen;
import ryzlar.glass.network.GlassNetworkData;
import ryzlar.glass.network.GlassNetworkManager;
import ryzlar.glass.network.PasswordHasher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds GLASS screens and cameras in a flat test world and takes screenshots (in build/run/clientGameTest/screenshots).
 * Run with {@code ./gradlew runClientGameTest}.
 * <p>
 * Covers: channel names and passwords (through the server-side API, so the checks are real), one camera per channel,
 * the screen turning on and off, the camera view, wireless screens, view bobbing, and the GLASS screens (GUI).
 */
public class GlassClientGameTest implements FabricClientGameTest
{
    private static final Logger LOGGER = LoggerFactory.getLogger("glass-test");

    private static final BlockPos PROJECTOR = new BlockPos(0, -58, 0);
    private static final BlockPos GOLD_CAMERA = new BlockPos(40, -59, 0);
    private static final BlockPos DIAMOND_CAMERA = new BlockPos(80, -59, 0);
    private static final BlockPos SPARE_TERMINAL = new BlockPos(5, -60, 6);
    private static final BlockPos WIRELESS_PROJECTOR = new BlockPos(-14, -59, 6);
    private static final BlockPos WINDOW_PROJECTOR = new BlockPos(23, -60, 30);
    private static final BlockPos WINDOW_CAMERA = new BlockPos(23, -60, 31);

    private static void check(boolean condition, String message)
    {
        if(!condition)
        {
            throw new AssertionError(message);
        }
        LOGGER.info("GLASS test OK: {}", message);
    }

    private static ServerPlayer player(MinecraftServer server)
    {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static GlassTerminalBlockEntity terminal(MinecraftServer server, BlockPos pos)
    {
        return (GlassTerminalBlockEntity)server.overworld().getBlockEntity(pos);
    }

    /** Makes a channel at the terminal at {@code pos}, like the camera screen does. Returns its id. */
    private static String create(MinecraftServer server, BlockPos pos, String name, String password)
    {
        GlassNetworkManager.Result result = GlassNetworkManager.createChannel(player(server), terminal(server, pos), name, password);
        check(result.ok(), "channel " + name + " created");
        return terminal(server, pos).channelId;
    }

    /** Tunes the projector at {@code pos}, like the projector screen does. */
    private static void tune(MinecraftServer server, BlockPos pos, String channelId)
    {
        GlassNetworkData data = GlassNetworkManager.data(server);
        GlassProjectorBlockEntity projector = (GlassProjectorBlockEntity)server.overworld().getBlockEntity(pos);
        projector.channelId = channelId;
        projector.channelName = data.channels.get(channelId).name;
        projector.resolveCamera(data, true);
    }

    private static int countActive(MinecraftServer server)
    {
        int count = 0;
        for(BlockPos pos : BlockPos.betweenClosed(-3, -60, 0, 3, -56, 0))
        {
            if(server.overworld().getBlockEntity(pos) instanceof GlassBaseBlockEntity glass && glass.active)
            {
                count++;
            }
        }
        return count;
    }

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try(TestSingleplayerContext singleplayer = context.worldBuilder().create())
        {
            singleplayer.getClientWorld().waitForChunksRender();
            TestServerContext server = singleplayer.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("gamerule doWeatherCycle false");
            server.runCommand("weather clear");

            //Two cameras: one looking at pillars and a gold wall, one at a diamond wall. A spare terminal for the GUI.
            server.runCommand("fill 38 -60 -6 38 -55 -6 minecraft:red_wool");
            server.runCommand("fill 40 -60 -6 40 -55 -6 minecraft:blue_wool");
            server.runCommand("fill 42 -60 -6 42 -55 -6 minecraft:yellow_wool");
            server.runCommand("fill 33 -60 -12 47 -52 -12 minecraft:gold_block");
            server.runCommand("fill 30 -61 -15 50 -61 10 minecraft:white_concrete");
            server.runCommand("fill 75 -61 -5 85 -61 12 minecraft:light_blue_concrete");
            server.runCommand("fill 75 -60 9 85 -52 9 minecraft:diamond_block");
            server.runCommand("setblock 40 -59 0 generallaymansaestheticspyingscreen:glass_terminal{facing:\"north\"}");
            server.runCommand("setblock 80 -59 0 generallaymansaestheticspyingscreen:glass_terminal{facing:\"south\"}");
            server.runCommand("setblock 5 -60 6 generallaymansaestheticspyingscreen:glass_terminal{facing:\"south\"}");
            context.waitTicks(2);

            //Channels: unique names, passwords, one camera each.
            String[] ids = server.computeOnServer(s -> {
                ServerPlayer p = player(s);
                String gold = create(s, GOLD_CAMERA, "Gold Net", "goldpass");
                check(!GlassNetworkManager.createChannel(p, terminal(s, SPARE_TERMINAL), "gold net", "other1").ok(), "existing channel name refused (any case)");
                String diamond = create(s, DIAMOND_CAMERA, "Diamond Net", "diamond1");
                GlassNetworkData data = GlassNetworkManager.data(s);
                check(!data.channels.get(gold).hash.contains("goldpass"), "password stored hashed");
                check(data.channels.get(gold).owner.equals(p.getUUID()) && data.channels.get(gold).ownerName.equals(p.getGameProfile().getName()), "owner stored by UUID and name");

                //One camera per channel: moving it to another terminal unlinks the old one.
                check(GlassNetworkManager.moveCamera(p, terminal(s, SPARE_TERMINAL), diamond).ok(), "camera moved to another terminal");
                check(!terminal(s, DIAMOND_CAMERA).isLinked() && terminal(s, SPARE_TERMINAL).channelId.equals(diamond), "old terminal unlinked, new one linked");
                check(GlassNetworkManager.moveCamera(p, terminal(s, DIAMOND_CAMERA), diamond).ok(), "camera moved back");
                check(!terminal(s, SPARE_TERMINAL).isLinked(), "spare terminal unlinked again");

                //Another player's channel: needs the password, which is checked and rate-limited.
                UUID stranger = UUID.randomUUID();
                PasswordHasher.Hash hash = PasswordHasher.hash("letmein");
                GlassNetworkData.Channel secret = new GlassNetworkData.Channel("5ec12e70", "Secret Net", stranger, "Someone", hash.salt(), hash.hash(), hash.iterations(), List.of(), Optional.empty());
                data.channels.put(secret.id, secret);
                check(!secret.hasAccess(p.getUUID()), "no access without the password");
                check(!GlassNetworkManager.unlock(p, secret.id, "nope").ok(), "wrong password refused");
                check(!GlassNetworkManager.deleteChannel(p, secret.id).ok(), "only the owner can delete");
                check(!GlassNetworkManager.changePassword(p, secret.id, "mine1234").ok(), "only the owner can change the password");
                check(GlassNetworkManager.unlock(p, secret.id, "letmein").ok() && secret.hasAccess(p.getUUID()), "right password gives access");
                GlassNetworkData.Channel other = new GlassNetworkData.Channel("0717e200", "Other Net", stranger, "Someone", hash.salt(), hash.hash(), hash.iterations(), List.of(), Optional.empty());
                data.channels.put(other.id, other);
                for(int i = 0; i < 6; i++)
                {
                    GlassNetworkManager.unlock(p, other.id, "wrong");
                }
                check(!GlassNetworkManager.unlock(p, other.id, "letmein").ok(), "too many wrong passwords: locked out for a minute");
                check(GlassNetworkManager.changePassword(p, gold, "goldpass2").ok(), "owner changed the password");
                GlassNetworkManager.syncAll(s);
                return new String[] { gold, diamond };
            });
            String goldId = ids[0];
            String diamondId = ids[1];

            //The screen: a 7x5 wall of GLASS facing south, with the projector in it, showing the gold channel.
            server.runCommand("fill -3 -60 0 3 -56 0 generallaymansaestheticspyingscreen:glass_extension");
            server.runCommand("setblock 0 -58 0 generallaymansaestheticspyingscreen:glass_projector{placingFace:\"south\"}");
            context.waitTicks(2);
            server.runOnServer(s -> tune(s, PROJECTOR, goldId));
            context.waitTicks(5);

            server.runCommand("tp @p 0.5 -60 8.5 180 0");
            singleplayer.getClientWorld().waitForChunksRender();
            context.waitTicks(10);
            server.runCommand("setblock 0 -58 -1 minecraft:redstone_block");
            context.waitTicks(3);
            context.takeScreenshot("turn_on");
            context.waitTicks(60);
            check(server.computeOnServer(GlassClientGameTest::countActive) == 35, "all 35 GLASS blocks turned on");
            context.takeScreenshot("screen_gold");

            //A wireless screen showing the diamond channel.
            server.runCommand("setblock -16 -55 6 generallaymansaestheticspyingscreen:glass_wireless_extension");
            server.runCommand("setblock -12 -55 6 generallaymansaestheticspyingscreen:glass_wireless_extension");
            server.runCommand("setblock -16 -58 6 generallaymansaestheticspyingscreen:glass_wireless_extension");
            server.runCommand("setblock -12 -58 6 generallaymansaestheticspyingscreen:glass_wireless_extension");
            server.runCommand("setblock -14 -59 6 generallaymansaestheticspyingscreen:glass_projector{placingFace:\"south\",wirelessPos:[[I;-16,-55,6],[I;-12,-55,6],[I;-16,-58,6],[I;-12,-58,6]]}");
            context.waitTicks(2);
            server.runOnServer(s -> tune(s, WIRELESS_PROJECTOR, diamondId));
            server.runCommand("setblock -14 -59 5 minecraft:redstone_block");
            context.waitTicks(40);
            server.runCommand("tp @p -13.5 -60 14.5 180 -10");
            singleplayer.getClientWorld().waitForChunksRender();
            context.waitTicks(20);
            context.takeScreenshot("screen_wireless");

            //A "window": the camera sits right in front of its own projector, so the glass should line up with the world
            //behind it, even while walking (view bobbing).
            server.runCommand("fill 10 -60 24 36 -50 24 minecraft:white_wool");
            for(int x = 10; x <= 36; x += 2)
            {
                server.runCommand("fill " + x + " -60 24 " + x + " -50 24 minecraft:black_wool");
            }
            for(int y = -59; y <= -50; y += 3)
            {
                server.runCommand("fill 10 " + y + " 24 36 " + y + " 24 minecraft:red_wool");
            }
            server.runCommand("fill 20 -60 30 26 -56 30 generallaymansaestheticspyingscreen:glass_extension");
            server.runCommand("setblock 23 -60 30 generallaymansaestheticspyingscreen:glass_projector{placingFace:\"south\"}");
            server.runCommand("setblock 23 -60 31 generallaymansaestheticspyingscreen:glass_terminal{facing:\"north\"}");
            context.waitTicks(2);
            server.runOnServer(s -> tune(s, WINDOW_PROJECTOR, create(s, WINDOW_CAMERA, "Window", "window1")));
            server.runCommand("setblock 23 -60 29 minecraft:redstone_block");
            context.runOnClient(mc -> mc.options.bobView().set(true));
            server.runCommand("tp @p 23.5 -60 44.5 180 0");
            singleplayer.getClientWorld().waitForChunksRender();
            context.waitTicks(60);
            context.getInput().holdKey(options -> options.keyUp);
            context.waitTicks(6);
            context.takeScreenshot("window_walking");
            context.getInput().releaseKey(options -> options.keyUp);
            context.waitTicks(10);

            //The screens.
            GlassTerminalBlockEntity spare = context.computeOnClient(mc -> (GlassTerminalBlockEntity)mc.level.getBlockEntity(SPARE_TERMINAL));
            GlassTerminalBlockEntity gold = context.computeOnClient(mc -> (GlassTerminalBlockEntity)mc.level.getBlockEntity(GOLD_CAMERA));
            GlassProjectorBlockEntity projector = context.computeOnClient(mc -> (GlassProjectorBlockEntity)mc.level.getBlockEntity(PROJECTOR));
            GlassProjectorBlockEntity wirelessProjector = context.computeOnClient(mc -> (GlassProjectorBlockEntity)mc.level.getBlockEntity(WIRELESS_PROJECTOR));
            context.setScreen(() -> new CameraScreen(spare));
            context.waitTicks(10);
            context.takeScreenshot("gui_camera_new");
            context.setScreen(() -> new CameraScreen(gold));
            context.waitTicks(10);
            context.takeScreenshot("gui_camera_owner");
            context.setScreen(() -> new ProjectorScreen(projector));
            context.waitTicks(10);
            context.takeScreenshot("gui_projector");
            context.setScreen(() -> new WirelessOrderScreen(wirelessProjector, null));
            context.waitTicks(10);
            context.takeScreenshot("gui_wireless_order");
            context.setScreen(() -> null);

            //Removing a camera terminal takes the channel's camera offline: at once when broken, and within a second even
            //when the removal skips block entity side effects (as /setblock does).
            server.runCommand("setblock 80 -59 0 minecraft:air");
            context.waitTicks(30);
            check(server.computeOnServer(s -> GlassNetworkManager.data(s).channels.get(diamondId).camera == null), "removed terminal: channel camera offline");
            server.runCommand("setblock 40 -59 0 minecraft:air destroy");
            context.waitTicks(2);
            check(server.computeOnServer(s -> GlassNetworkManager.data(s).channels.get(goldId).camera == null), "broken terminal: channel camera offline at once");
            check(server.computeOnServer(s -> GlassNetworkManager.data(s).channels.containsKey(goldId)), "the channel itself stays");

            //Turning the projector off turns the whole screen off again.
            server.runCommand("setblock 0 -58 -1 minecraft:air");
            context.waitTicks(80);
            check(server.computeOnServer(GlassClientGameTest::countActive) == 0, "screen turned off");
        }
    }
}
