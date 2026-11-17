package ryzlar.glass.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ryzlar.glass.blockentity.GlassBaseBlockEntity;
import ryzlar.glass.blockentity.GlassProjectorBlockEntity;
import ryzlar.glass.blockentity.GlassTerminalBlockEntity;
import ryzlar.glass.client.gui.CameraScreen;
import ryzlar.glass.client.gui.LoginScreen;
import ryzlar.glass.client.gui.NetworkScreen;
import ryzlar.glass.client.gui.ProjectorScreen;
import ryzlar.glass.client.gui.WirelessOrderScreen;
import ryzlar.glass.network.GlassNetworkData;
import ryzlar.glass.network.GlassNetworkManager;

/**
 * Builds GLASS screens and cameras in a flat test world and takes screenshots (in build/run/clientGameTest/screenshots).
 * Run with {@code ./gradlew runClientGameTest}.
 * <p>
 * Covers: accounts and channel passwords (through the server-side API, so the checks are real), linking cameras,
 * the screen turning on and off, the camera view, auto-cycling between cameras, wireless screens, view bobbing, and
 * every GLASS screen (GUI).
 */
public class GlassClientGameTest implements FabricClientGameTest
{
    private static final Logger LOGGER = LoggerFactory.getLogger("glass-test");

    private static final BlockPos PROJECTOR = new BlockPos(0, -58, 0);
    private static final BlockPos GOLD_CAMERA = new BlockPos(40, -59, 0);
    private static final BlockPos DIAMOND_CAMERA = new BlockPos(80, -59, 0);
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

    /** Links the terminal at {@code pos} as a camera on {@code channelId}, like the camera screen does. */
    private static void linkCamera(MinecraftServer server, String channelId, BlockPos pos, String name)
    {
        ServerLevel level = server.overworld();
        GlassTerminalBlockEntity terminal = (GlassTerminalBlockEntity)level.getBlockEntity(pos);
        GlassNetworkManager.Result result = GlassNetworkManager.linkCamera(player(server), channelId, null, null, name, level.dimension(), pos, terminal.facing);
        check(result.ok(), "camera " + name + " linked");
        terminal.channelId = channelId;
        terminal.cameraId = result.arg();
        terminal.cameraName = name;
        terminal.sync();
    }

    private static String cameraId(MinecraftServer server, BlockPos pos)
    {
        return ((GlassTerminalBlockEntity)server.overworld().getBlockEntity(pos)).cameraId;
    }

    /** Tunes the projector at {@code pos}, like the projector screen does. */
    private static void tune(MinecraftServer server, BlockPos pos, String channelId, String cameraId, boolean cycle, int seconds)
    {
        GlassProjectorBlockEntity projector = (GlassProjectorBlockEntity)server.overworld().getBlockEntity(pos);
        projector.channelId = channelId;
        projector.cameraId = cameraId;
        projector.cycle = cycle;
        projector.cycleSeconds = seconds;
        projector.resolveCamera(GlassNetworkManager.data(server), true);
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

            //The login screen, before there is an account.
            context.setScreen(() -> new LoginScreen(() -> null));
            context.waitTicks(10);
            context.takeScreenshot("gui_login");
            context.setScreen(() -> null);

            //Accounts and channel passwords.
            String channelId = server.computeOnServer(s -> {
                ServerPlayer p = player(s);
                check(GlassNetworkManager.register(p, "operator", "hunter22", false).ok(), "account registered");
                check(!GlassNetworkManager.register(p, "Operator", "whatever", false).ok(), "duplicate username refused (case-insensitive)");
                check(GlassNetworkManager.createChannel(p, "Gold Net", "goldpass").ok(), "channel created");
                check(!GlassNetworkManager.createChannel(p, "gold net", "other").ok(), "duplicate channel name refused");
                GlassNetworkData.Channel channel = GlassNetworkManager.data(s).channelByName("Gold Net");
                check(channel != null && !channel.hash.contains("goldpass"), "channel password stored hashed");

                check(GlassNetworkManager.logout(p).ok(), "logged out");
                check(GlassNetworkManager.register(p, "visitor", "visitor1", false).ok(), "second account registered");
                check(!GlassNetworkManager.joinChannel(p, "Gold Net", "wrong").ok(), "wrong channel password refused");
                check(GlassNetworkManager.joinChannel(p, "Gold Net", "goldpass").ok(), "right channel password accepted");
                check(!GlassNetworkManager.deleteChannel(p, channel.id).ok(), "members cannot delete");
                GlassNetworkManager.logout(p);
                check(!GlassNetworkManager.login(p, "operator", "nope", false).ok(), "wrong account password refused");
                check(GlassNetworkManager.login(p, "operator", "hunter22", true).ok(), "logged back in");
                check(GlassNetworkManager.kickMember(p, channel.id, "visitor").ok(), "owner kicked a member");
                check(!channel.isMember("visitor"), "kicked member is gone");
                GlassNetworkManager.syncAll(s);
                return channel.id;
            });

            //Two cameras on the channel: one looking at pillars and a gold wall, one at a diamond wall.
            server.runCommand("fill 38 -60 -6 38 -55 -6 minecraft:red_wool");
            server.runCommand("fill 40 -60 -6 40 -55 -6 minecraft:blue_wool");
            server.runCommand("fill 42 -60 -6 42 -55 -6 minecraft:yellow_wool");
            server.runCommand("fill 33 -60 -12 47 -52 -12 minecraft:gold_block");
            server.runCommand("fill 30 -61 -15 50 -61 10 minecraft:white_concrete");
            server.runCommand("fill 75 -61 -5 85 -61 12 minecraft:light_blue_concrete");
            server.runCommand("fill 75 -60 9 85 -52 9 minecraft:diamond_block");
            server.runCommand("setblock 40 -59 0 generallaymansaestheticspyingscreen:glass_terminal{facing:\"north\"}");
            server.runCommand("setblock 80 -59 0 generallaymansaestheticspyingscreen:glass_terminal{facing:\"south\"}");
            context.waitTicks(2);
            server.runOnServer(s -> {
                linkCamera(s, channelId, GOLD_CAMERA, "Gold Wall");
                linkCamera(s, channelId, DIAMOND_CAMERA, "Diamond Wall");
                GlassNetworkManager.syncAll(s);
            });

            //The screen: a 7x5 wall of GLASS facing south, with the projector in it, showing the gold camera.
            server.runCommand("fill -3 -60 0 3 -56 0 generallaymansaestheticspyingscreen:glass_extension");
            server.runCommand("setblock 0 -58 0 generallaymansaestheticspyingscreen:glass_projector{placingFace:\"south\"}");
            context.waitTicks(2);
            server.runOnServer(s -> tune(s, PROJECTOR, channelId, cameraId(s, GOLD_CAMERA), false, 10));
            context.waitTicks(5);

            //Watch the screen turn on (no black frames).
            server.runCommand("tp @p 0.5 -60 8.5 180 0");
            singleplayer.getClientWorld().waitForChunksRender();
            context.waitTicks(10);
            server.runCommand("setblock 0 -58 -1 minecraft:redstone_block");
            int waited = 0;
            for(int tick : new int[] { 2, 5, 12 })
            {
                context.waitTicks(tick - waited);
                waited = tick;
                context.takeScreenshot("turn_on_tick_" + (tick < 10 ? "0" : "") + tick);
            }
            context.waitTicks(60);
            check(server.computeOnServer(GlassClientGameTest::countActive) == 35, "all 35 GLASS blocks turned on");
            context.takeScreenshot("screen_gold_camera");

            //Auto-cycle between both cameras every 3 seconds.
            server.runOnServer(s -> tune(s, PROJECTOR, channelId, "", true, 3));
            context.waitTicks(5);
            context.takeScreenshot("cycle_1");
            context.waitTicks(65);
            context.takeScreenshot("cycle_2");

            //A wireless screen showing the diamond camera.
            server.runCommand("setblock -16 -55 6 generallaymansaestheticspyingscreen:glass_wireless_extension");
            server.runCommand("setblock -12 -55 6 generallaymansaestheticspyingscreen:glass_wireless_extension");
            server.runCommand("setblock -16 -58 6 generallaymansaestheticspyingscreen:glass_wireless_extension");
            server.runCommand("setblock -12 -58 6 generallaymansaestheticspyingscreen:glass_wireless_extension");
            server.runCommand("setblock -14 -59 6 generallaymansaestheticspyingscreen:glass_projector{placingFace:\"south\",wirelessPos:[[I;-16,-55,6],[I;-12,-55,6],[I;-16,-58,6],[I;-12,-58,6]]}");
            context.waitTicks(2);
            server.runOnServer(s -> tune(s, WIRELESS_PROJECTOR, channelId, cameraId(s, DIAMOND_CAMERA), false, 10));
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
            server.runOnServer(s -> {
                linkCamera(s, channelId, WINDOW_CAMERA, "Window");
                tune(s, WINDOW_PROJECTOR, channelId, cameraId(s, WINDOW_CAMERA), false, 10);
            });
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

            //The screens (the client has the network state by now).
            GlassTerminalBlockEntity terminal = context.computeOnClient(mc -> (GlassTerminalBlockEntity)mc.level.getBlockEntity(GOLD_CAMERA));
            GlassProjectorBlockEntity projector = context.computeOnClient(mc -> (GlassProjectorBlockEntity)mc.level.getBlockEntity(PROJECTOR));
            GlassProjectorBlockEntity wirelessProjector = context.computeOnClient(mc -> (GlassProjectorBlockEntity)mc.level.getBlockEntity(WIRELESS_PROJECTOR));
            context.setScreen(() -> new CameraScreen(terminal));
            context.waitTicks(10);
            context.takeScreenshot("gui_camera");
            context.setScreen(() -> new ProjectorScreen(projector));
            context.waitTicks(10);
            context.takeScreenshot("gui_projector");
            context.setScreen(() -> new NetworkScreen(null));
            context.waitTicks(10);
            context.takeScreenshot("gui_network");
            context.setScreen(() -> new WirelessOrderScreen(wirelessProjector, null));
            context.waitTicks(10);
            context.takeScreenshot("gui_wireless_order");
            context.setScreen(() -> null);

            //Breaking a camera takes it off the channel: normally at once, and within a second even when the removal
            //skips block entity side effects (as /setblock does).
            server.runCommand("setblock 80 -59 0 minecraft:air");
            context.waitTicks(30);
            check(server.computeOnServer(s -> GlassNetworkManager.data(s).channels.get(channelId).cameras.size()) == 2, "removed camera taken off the channel");
            server.runCommand("setblock 40 -59 0 minecraft:air destroy");
            context.waitTicks(2);
            check(server.computeOnServer(s -> GlassNetworkManager.data(s).channels.get(channelId).cameras.size()) == 1, "broken camera taken off the channel at once");

            //Turning the projector off turns the whole screen off again.
            server.runCommand("setblock 0 -58 -1 minecraft:air");
            context.waitTicks(80);
            check(server.computeOnServer(GlassClientGameTest::countActive) == 0, "screen turned off");
        }
    }
}
