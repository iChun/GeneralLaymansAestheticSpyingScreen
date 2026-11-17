package ryzlar.glass.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ryzlar.glass.blockentity.GlassBaseBlockEntity;
import ryzlar.glass.blockentity.GlassProjectorBlockEntity;
import ryzlar.glass.blockentity.GlassTerminalBlockEntity;
import ryzlar.glass.client.gui.ChannelSetterScreen;
import ryzlar.glass.client.gui.ProjectorChannelScreen;
import ryzlar.glass.client.gui.WirelessOrderScreen;

/**
 * Builds a GLASS screen and a terminal in a flat test world, powers the projector and takes screenshots
 * (in build/run/clientGameTest/screenshots). Run with {@code ./gradlew runClientGameTest}.
 */
public class GlassClientGameTest implements FabricClientGameTest
{
    private static final Logger LOGGER = LoggerFactory.getLogger("glass-test");

    private static final BlockPos PROJECTOR = new BlockPos(0, -58, 0);
    private static final BlockPos TERMINAL = new BlockPos(40, -59, 0);
    private static final BlockPos WIRELESS_PROJECTOR = new BlockPos(-14, -59, 6);

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

            //What the terminal looks at: three coloured pillars and a gold wall to the north of it.
            server.runCommand("fill 38 -60 -6 38 -55 -6 minecraft:red_wool");
            server.runCommand("fill 40 -60 -6 40 -55 -6 minecraft:blue_wool");
            server.runCommand("fill 42 -60 -6 42 -55 -6 minecraft:yellow_wool");
            server.runCommand("fill 33 -60 -12 47 -52 -12 minecraft:gold_block");
            //A white floor around the terminal, so the screen visibly shows another place.
            server.runCommand("fill 30 -61 -15 50 -61 10 minecraft:white_concrete");
            //A second terminal (channel public:test2) looking south at a diamond wall, for the wireless screen.
            server.runCommand("fill 75 -61 -5 85 -61 12 minecraft:light_blue_concrete");
            server.runCommand("fill 75 -60 9 85 -52 9 minecraft:diamond_block");
            server.runCommand("setblock 80 -59 0 generallaymansaestheticspyingscreen:glass_terminal{channelName:\"public:test2\",facing:\"south\"}");
            server.runCommand("setblock 40 -59 0 generallaymansaestheticspyingscreen:glass_terminal{channelName:\"public:test\",facing:\"north\"}");

            //The screen: a 7x5 wall of GLASS facing south, with the projector in it.
            server.runCommand("fill -3 -60 0 3 -56 0 generallaymansaestheticspyingscreen:glass_extension");
            server.runCommand("setblock 0 -58 0 generallaymansaestheticspyingscreen:glass_projector{setChannel:\"public:test\",placingFace:\"south\"}");
            context.waitTicks(5);

            //Watch the screen turn on, twice (the second time the terminal view is created from scratch again).
            server.runCommand("tp @p 0.5 -60 8.5 180 0");
            singleplayer.getClientWorld().waitForChunksRender();
            context.waitTicks(10);
            for(int round = 1; round <= 2; round++)
            {
                server.runCommand("setblock 0 -58 -1 minecraft:redstone_block");
                int waited = 0;
                for(int tick : new int[] { 1, 2, 3, 5, 8, 12, 20 })
                {
                    context.waitTicks(tick - waited);
                    waited = tick;
                    context.takeScreenshot("turn_on_" + round + "_tick_" + (tick < 10 ? "0" : "") + tick);
                }
                if(round == 1)
                {
                    server.runCommand("setblock 0 -58 -1 minecraft:air");
                    context.waitTicks(80);
                }
            }
            context.waitTicks(40);

            int activeCount = server.computeOnServer(s -> {
                ServerLevel level = s.overworld();
                int count = 0;
                for(BlockPos pos : BlockPos.betweenClosed(-3, -60, 0, 3, -56, 0))
                {
                    if(level.getBlockEntity(pos) instanceof GlassBaseBlockEntity glass && glass.active && glass.channel.equals("public:test"))
                    {
                        count++;
                    }
                }
                return count;
            });
            LOGGER.info("GLASS test: {} of 35 glass blocks are active on public:test", activeCount);
            if(activeCount != 35)
            {
                throw new AssertionError("Expected all 35 GLASS blocks to turn on, but " + activeCount + " did");
            }

            //Look at the screen from the front, then at an angle.
            server.runCommand("tp @p 0.5 -60 8.5 180 0");
            singleplayer.getClientWorld().waitForChunksRender();
            context.waitTicks(20);
            context.takeScreenshot("glass_screen_front");

            server.runCommand("tp @p 4.5 -60 5.5 145 -5");
            context.waitTicks(10);
            context.takeScreenshot("glass_screen_angle");

            //Reference: roughly what the terminal sees, from the spot the front view maps to.
            server.runCommand("tp @p 40.5 -60 7.5 180 0");
            singleplayer.getClientWorld().waitForChunksRender();
            context.waitTicks(10);
            context.takeScreenshot("terminal_reference");

            //Wireless: a projector whose screen floats between four wireless extensions.
            server.runCommand("setblock -16 -55 6 generallaymansaestheticspyingscreen:glass_wireless_extension");
            server.runCommand("setblock -12 -55 6 generallaymansaestheticspyingscreen:glass_wireless_extension");
            server.runCommand("setblock -16 -58 6 generallaymansaestheticspyingscreen:glass_wireless_extension");
            server.runCommand("setblock -12 -58 6 generallaymansaestheticspyingscreen:glass_wireless_extension");
            server.runCommand("setblock -14 -59 6 generallaymansaestheticspyingscreen:glass_projector{setChannel:\"public:test2\",placingFace:\"south\",wirelessPos:[[I;-16,-55,6],[I;-12,-55,6],[I;-16,-58,6],[I;-12,-58,6]]}");
            context.waitTicks(5);
            server.runCommand("setblock -14 -59 5 minecraft:redstone_block");
            context.waitTicks(40);
            server.runCommand("tp @p -13.5 -60 14.5 180 -10");
            singleplayer.getClientWorld().waitForChunksRender();
            context.waitTicks(20);
            context.takeScreenshot("glass_wireless");

            //A "window": the terminal sits right in front of its own projector, facing away from the viewer, so the view
            //through the glass should line up exactly with the world behind it, even while walking (view bobbing).
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
            server.runCommand("setblock 23 -60 30 generallaymansaestheticspyingscreen:glass_projector{setChannel:\"public:window\",placingFace:\"south\"}");
            server.runCommand("setblock 23 -60 31 generallaymansaestheticspyingscreen:glass_terminal{channelName:\"public:window\",facing:\"north\"}");
            context.waitTicks(5);
            server.runCommand("setblock 23 -60 29 minecraft:redstone_block");
            context.runOnClient(mc -> mc.options.bobView().set(true));
            server.runCommand("tp @p 23.5 -60 44.5 180 0");
            singleplayer.getClientWorld().waitForChunksRender();
            context.waitTicks(60);
            context.takeScreenshot("window_standing");
            context.getInput().holdKey(options -> options.keyUp);
            for(int i = 1; i <= 4; i++)
            {
                context.waitTicks(3);
                context.takeScreenshot("window_walking_" + i);
            }
            context.getInput().releaseKey(options -> options.keyUp);
            context.waitTicks(10);

            //The screens.
            GlassTerminalBlockEntity terminal = context.computeOnClient(mc -> (GlassTerminalBlockEntity)mc.level.getBlockEntity(TERMINAL));
            GlassProjectorBlockEntity projector = context.computeOnClient(mc -> (GlassProjectorBlockEntity)mc.level.getBlockEntity(PROJECTOR));
            GlassProjectorBlockEntity wirelessProjector = context.computeOnClient(mc -> (GlassProjectorBlockEntity)mc.level.getBlockEntity(WIRELESS_PROJECTOR));
            context.setScreen(() -> new ChannelSetterScreen(terminal));
            context.waitTicks(2);
            context.takeScreenshot("gui_terminal");
            context.setScreen(() -> new ProjectorChannelScreen(projector));
            context.waitTicks(2);
            context.takeScreenshot("gui_projector");
            context.setScreen(() -> new WirelessOrderScreen(wirelessProjector));
            context.waitTicks(10);
            context.takeScreenshot("gui_wireless_order");
            context.setScreen(() -> null);

            //Turning the projector off turns the whole screen off again.
            server.runCommand("setblock 0 -58 -1 minecraft:air");
            context.waitTicks(80);
            int stillActive = server.computeOnServer(s -> {
                int count = 0;
                for(BlockPos pos : BlockPos.betweenClosed(-3, -60, 0, 3, -56, 0))
                {
                    if(s.overworld().getBlockEntity(pos) instanceof GlassBaseBlockEntity glass && glass.active)
                    {
                        count++;
                    }
                }
                return count;
            });
            LOGGER.info("GLASS test: {} glass blocks still active after removing power", stillActive);
            if(stillActive != 0)
            {
                throw new AssertionError("Expected the screen to turn off, but " + stillActive + " blocks are still active");
            }
        }
    }
}
