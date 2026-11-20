package ryzlar.glass.test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestClientWorldContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ryzlar.glass.GlassRegistry;
import ryzlar.glass.blockentity.GlassBaseBlockEntity;
import ryzlar.glass.blockentity.GlassProjectorBlockEntity;
import ryzlar.glass.blockentity.GlassTerminalBlockEntity;
import ryzlar.glass.client.holo.HoloClient;
import ryzlar.glass.client.render.SpyViewRenderer;
import ryzlar.glass.holo.CubeScan;
import ryzlar.glass.holo.HoloCubeEntity;
import ryzlar.glass.holo.HoloNetworking;
import ryzlar.glass.holo.HoloProjectionData;
import ryzlar.glass.network.GlassNetworkManager;
import ryzlar.glass.network.GlassNetworking;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Release stress test, run with {@code GLASS_TEST_ONLY=stress ./gradlew runClientGameTest} (skipped otherwise):
 * <ul>
 *     <li>a world full of GLASS: eight cloak fields, four glass cloaks and a 40x20 screen, looked at from near, far,
 *     inside and away (how many views are kept), and the server's tick time with it all on;</li>
 *     <li>the window resized, third-person view, tiny and big render distances, a trip to the nether and back, and
 *     leaving the world, checking no views (images on the graphics card) are left behind;</li>
 *     <li>a real dedicated server: a cloak field and a holo cube whose scan is far bigger than one packet, joined, left
 *     and joined again.</li>
 * </ul>
 * Screenshots in build/run/clientGameTest/screenshots (stress_*).
 */
public class GlassClientStressTest implements FabricClientGameTest
{
    private static final Logger LOGGER = LoggerFactory.getLogger("glass-test");
    private static final int FIELDS = 8;
    private static final int GLASS_CLOAKS = 4;

    private static void check(boolean condition, String message)
    {
        if(!condition)
        {
            throw new AssertionError(message);
        }
        LOGGER.info("GLASS stress OK: {}", message);
    }

    private static void settle(TestClientWorldContext world, ClientGameTestContext context)
    {
        try
        {
            world.waitForChunksRender();
        }
        catch(AssertionError e)
        {
            LOGGER.warn("Chunks not drawn in time, waiting a little longer");
            context.waitTicks(80);
        }
    }

    private static int views(ClientGameTestContext context)
    {
        return context.computeOnClient(mc -> SpyViewRenderer.INSTANCE.viewCount());
    }

    private static void measure(ClientGameTestContext context, TestServerContext server, String name, String tp)
    {
        //(Frame rates cannot be measured here: the client gametest draws one frame per tick, 20 a second.)
        server.runCommand("tp @a " + tp);
        context.waitTicks(80);
        LOGGER.info("GLASS stress {}: {} views kept", name, views(context));
        context.takeScreenshot("stress_" + name);
    }

    @Override
    public void runTest(ClientGameTestContext context)
    {
        if(!"stress".equals(System.getenv("GLASS_TEST_ONLY")))
        {
            return;
        }
        singleplayer(context);
        context.waitTicks(5);
        check(views(context) == 0, "leaving the world leaves no views behind");
        dedicatedServer(context);
        context.waitTicks(5);
        check(views(context) == 0, "leaving the server leaves no views behind");
    }

    // ---- A world full of GLASS ----

    private static void singleplayer(ClientGameTestContext context)
    {
        try(TestSingleplayerContext singleplayer = context.worldBuilder().create())
        {
            TestServerContext server = singleplayer.getServer();
            settle(singleplayer.getClientWorld(), context);
            server.runCommand("time set noon");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("gamerule doWeatherCycle false");
            server.runCommand("gamerule doMobSpawning false");
            server.runCommand("weather clear");
            server.runCommand("gamemode spectator @a");
            context.runOnClient(mc -> mc.options.renderDistance().set(12));

            build(server, context);
            server.runCommand("tp @a 280 -50 40 180 10");
            settle(singleplayer.getClientWorld(), context);
            power(server, context);

            double tickMs = server.computeOnServer(s -> s.getAverageTickTimeNanos() / 1.0E6D);
            LOGGER.info("GLASS stress server tick time with everything on: {} ms", String.format(Locale.ROOT, "%.2f", tickMs));
            check(tickMs < 50D, "the server keeps up with every GLASS thing on (" + String.format(Locale.ROOT, "%.1f", tickMs) + " ms per tick)");

            measure(context, server, "baseline_away", "150 -60 -100 90 0");
            measure(context, server, "one_field_close", "204 -60 20 180 0");
            measure(context, server, "inside_field", "204 -60 4 180 0");
            measure(context, server, "all_fields", "280 -40 75 180 15");
            measure(context, server, "glass_cloaks", "260 -58 60 180 0");
            measure(context, server, "big_screen", "100 -50 0 180 0");
            check(views(context) < 64, "views stay bounded (" + views(context) + ")");

            //The window resized while the cloaks are drawn: the views follow the new size.
            server.runCommand("tp @a 280 -40 75 180 15");
            context.getInput().resizeWindow(1280, 720);
            context.waitTicks(30);
            context.takeScreenshot("stress_resized_big");
            context.getInput().resizeWindow(640, 360);
            context.waitTicks(30);
            context.takeScreenshot("stress_resized_small");
            context.getInput().resizeWindow(854, 480);
            context.waitTicks(20);

            //Third person, inside a field and outside it.
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            server.runCommand("tp @a 204 -60 4 0 10");
            context.waitTicks(30);
            context.takeScreenshot("stress_third_person_inside");
            server.runCommand("tp @a 204 -60 16 180 10");
            context.waitTicks(30);
            context.takeScreenshot("stress_third_person_outside");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

            //Tiny and big render distances.
            server.runCommand("tp @a 204 -60 20 180 0");
            context.runOnClient(mc -> mc.options.renderDistance().set(2));
            context.waitTicks(40);
            context.takeScreenshot("stress_render_distance_2");
            context.runOnClient(mc -> mc.options.renderDistance().set(24));
            settle(singleplayer.getClientWorld(), context);
            context.waitTicks(20);
            context.takeScreenshot("stress_render_distance_24");
            context.runOnClient(mc -> mc.options.renderDistance().set(12));

            //To the nether and back: the overworld's views go, and come back after.
            server.runCommand("execute in minecraft:the_nether run tp @a 0 100 0");
            context.waitTicks(100);
            check(views(context) == 0, "in the nether no overworld views are kept (" + views(context) + ")");
            server.runCommand("execute in minecraft:overworld run tp @a 204 -60 20 180 0");
            settle(singleplayer.getClientWorld(), context);
            context.waitTicks(60);
            check(views(context) > 0, "back in the overworld the cloaks are drawn again");
            context.takeScreenshot("stress_back_from_nether");

            check(server.computeOnServer(s -> {
                int on = 0;
                for(int i = 0; i < FIELDS; i++)
                {
                    if(s.overworld().getBlockEntity(new BlockPos(fieldX(i) + 4, -60, 12)) instanceof GlassProjectorBlockEntity p && p.active)
                    {
                        on++;
                    }
                }
                return on;
            }) == FIELDS, "every cloak field is still on");
        }
    }

    private static int fieldX(int i)
    {
        return 200 + 22 * i;
    }

    private static int glassCloakX(int i)
    {
        return 200 + 40 * i;
    }

    /** Eight cloak fields round little houses, four glass cloaks in front of brick boxes, and a 40x20 screen. */
    private static void build(TestServerContext server, ClientGameTestContext context)
    {
        //Far from spawn: keep the chunks loaded while building (and running).
        //(At most 256 chunks per forceload, and 32768 blocks per fill.)
        server.runCommand("forceload add 80 -110 230 120");
        server.runCommand("forceload add 231 -110 380 120");
        context.waitTicks(40);
        for(int x = 140; x <= 380; x += 60)
        {
            server.runCommand(String.format(Locale.ROOT, "fill %d -61 -110 %d -61 120 minecraft:grass_block", x, Math.min(380, x + 59)));
        }
        for(int i = 0; i < FIELDS; i++)
        {
            int x = fieldX(i);
            server.runCommand(String.format(Locale.ROOT, "fill %d -60 2 %d -56 6 minecraft:oak_planks hollow", x + 2, x + 6));
            for(int[] corner : new int[][] { { 0, 0 }, { 8, 0 }, { 0, 8 }, { 8, 8 } })
            {
                server.runCommand(String.format(Locale.ROOT, "setblock %d -60 %d generallaymansaestheticspyingscreen:glass_wireless_extension", x + corner[0], corner[1]));
            }
            server.runCommand(String.format(Locale.ROOT,
                    "setblock %d -60 12 generallaymansaestheticspyingscreen:glass_projector{placingFace:\"south\",wirelessPos:[[I;%d,-60,0],[I;%d,-60,0],[I;%d,-60,8],[I;%d,-60,8]]}",
                    x + 4, x, x + 8, x, x + 8));
        }
        for(int i = 0; i < GLASS_CLOAKS; i++)
        {
            int x = glassCloakX(i);
            server.runCommand(String.format(Locale.ROOT, "fill %d -60 35 %d -56 39 minecraft:bricks", x, x + 6));
            server.runCommand(String.format(Locale.ROOT, "fill %d -60 30 %d -52 30 minecraft:gold_block", x - 4, x + 10));
            server.runCommand(String.format(Locale.ROOT, "fill %d -60 40 %d -56 40 generallaymansaestheticspyingscreen:glass_extension", x, x + 6));
            server.runCommand(String.format(Locale.ROOT, "setblock %d -60 40 generallaymansaestheticspyingscreen:glass_projector{placingFace:\"south\"}", x + 3));
        }
        //The big screen and its camera, looking at a wall of wool far off.
        server.runCommand("fill 90 -60 -60 110 -50 -60 minecraft:lime_wool");
        server.runCommand("setblock 100 -59 -40 generallaymansaestheticspyingscreen:glass_terminal{facing:\"north\"}");
        server.runCommand("fill 80 -60 -20 119 -41 -20 generallaymansaestheticspyingscreen:glass_extension");
        server.runCommand("setblock 100 -60 -20 generallaymansaestheticspyingscreen:glass_projector{placingFace:\"south\"}");
        context.waitTicks(5);
        server.runOnServer(s -> {
            for(int i = 0; i < FIELDS; i++)
            {
                GlassProjectorBlockEntity field = (GlassProjectorBlockEntity)s.overworld().getBlockEntity(new BlockPos(fieldX(i) + 4, -60, 12));
                field.cloak = true;
                field.cloakField = true;
                field.fieldHeight = 8;
                field.sync();
            }
            for(int i = 0; i < GLASS_CLOAKS; i++)
            {
                GlassProjectorBlockEntity cloak = (GlassProjectorBlockEntity)s.overworld().getBlockEntity(new BlockPos(glassCloakX(i) + 3, -60, 40));
                cloak.cloak = true;
                cloak.cloakDepth = 6;
                cloak.sync();
            }
            GlassTerminalBlockEntity terminal = (GlassTerminalBlockEntity)s.overworld().getBlockEntity(new BlockPos(100, -59, -40));
            GlassNetworkManager.Result made = GlassNetworkManager.createChannel(s.getPlayerList().getPlayers().getFirst(), terminal, "Stress Net", "stresspass");
            check(made.ok(), "big screen channel made");
            GlassProjectorBlockEntity big = (GlassProjectorBlockEntity)s.overworld().getBlockEntity(new BlockPos(100, -60, -20));
            GlassNetworking.showChannel(big, GlassNetworkManager.data(s), terminal.channelId, "Stress Net");
        });
    }

    /** Powers everything and waits for it all to be on. */
    private static void power(TestServerContext server, ClientGameTestContext context)
    {
        for(int i = 0; i < FIELDS; i++)
        {
            server.runCommand(String.format(Locale.ROOT, "setblock %d -61 12 minecraft:redstone_block", fieldX(i) + 4));
        }
        for(int i = 0; i < GLASS_CLOAKS; i++)
        {
            server.runCommand(String.format(Locale.ROOT, "setblock %d -61 40 minecraft:redstone_block", glassCloakX(i) + 3));
        }
        server.runCommand("setblock 100 -61 -20 minecraft:redstone_block");
        context.waitTicks(200);
        check(server.computeOnServer(s -> {
            int on = 0;
            for(BlockPos pos : BlockPos.betweenClosed(80, -60, -20, 119, -41, -20))
            {
                if(s.overworld().getBlockEntity(pos) instanceof GlassBaseBlockEntity glass && glass.active)
                {
                    on++;
                }
            }
            return on;
        }) == 800, "all 800 blocks of the big screen are on");
        check(server.computeOnServer(s -> {
            int on = 0;
            for(int i = 0; i < GLASS_CLOAKS; i++)
            {
                if(s.overworld().getBlockEntity(new BlockPos(glassCloakX(i) + 3, -60, 40)) instanceof GlassProjectorBlockEntity p && p.active)
                {
                    on++;
                }
            }
            return on;
        }) == GLASS_CLOAKS, "every glass cloak is on");
    }

    // ---- A real dedicated server ----

    /** A book of 25 pages of 1000 two-byte letters (about 50 kB sent): many of these make a scan bigger than a packet. */
    private static ItemStack heavyBook()
    {
        List<Filterable<String>> pages = new ArrayList<>();
        for(int i = 0; i < 25; i++)
        {
            pages.add(Filterable.passThrough("é".repeat(1000)));
        }
        ItemStack book = new ItemStack(Items.WRITABLE_BOOK);
        book.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(pages));
        return book;
    }

    /** Barrels of books round a cube, scanned and projected. Returns the scan's id. */
    private static String stuffedCube(MinecraftServer server)
    {
        ServerLevel level = server.overworld();
        BlockPos centre = new BlockPos(-16, -60, 4);
        for(int dx = -4; dx <= 4; dx++)
        {
            for(int dz = -4; dz <= 4; dz++)
            {
                if(dx == 0 && dz == 0)
                {
                    continue;
                }
                for(int dy = 0; dy <= 1; dy++)
                {
                    BlockPos pos = centre.offset(dx, dy, dz);
                    level.setBlockAndUpdate(pos, Blocks.BARREL.defaultBlockState());
                    if(level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel)
                    {
                        barrel.setItem(0, heavyBook());
                    }
                }
            }
        }
        HoloProjectionData.Projection projection = HoloProjectionData.storeBall(level, centre, "stuffed");
        ByteBuf buffer = Unpooled.buffer();
        HoloNetworking.ProjectionBlocks.CODEC.encode(buffer, HoloNetworking.blocksOf(projection));
        LOGGER.info("GLASS stress: the stuffed scan is {} bytes on the wire", buffer.readableBytes());
        check(buffer.readableBytes() > 1024 * 1024, "the stuffed scan is bigger than one vanilla packet (" + buffer.readableBytes() + " bytes)");
        buffer.release();
        ItemStack item = new ItemStack(GlassRegistry.HOLO_CUBE);
        item.set(GlassRegistry.CUBE_SCAN, new CubeScan(projection.id(), projection.name(), projection.sizeX(), projection.sizeY(), projection.sizeZ(),
                7, 7, 7, 0, CubeScan.NO_SKY, 0F, 0F, CubeScan.VERSION));
        HoloCubeEntity cube = new HoloCubeEntity(GlassRegistry.HOLO_CUBE_ENTITY, level);
        cube.setPos(centre.getX() + 0.5D, centre.getY(), centre.getZ() + 0.5D);
        cube.setItem(item);
        level.addFreshEntity(cube);
        cube.toggle();
        return projection.id();
    }

    private static void dedicatedServer(ClientGameTestContext context)
    {
        Properties properties = new Properties();
        properties.setProperty("level-type", "minecraft:flat");
        properties.setProperty("generate-structures", "false");
        properties.setProperty("spawn-protection", "0");
        try(TestDedicatedServerContext server = context.worldBuilder().createServer(properties))
        {
            server.runCommand("time set noon");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("gamerule doMobSpawning false");
            server.runCommand("weather clear");
            //A cloak field round a hut.
            server.runCommand("fill 12 -60 12 16 -56 16 minecraft:oak_planks hollow");
            for(int[] corner : new int[][] { { 10, 10 }, { 18, 10 }, { 10, 18 }, { 18, 18 } })
            {
                server.runCommand(String.format(Locale.ROOT, "setblock %d -60 %d generallaymansaestheticspyingscreen:glass_wireless_extension", corner[0], corner[1]));
            }
            server.runCommand("setblock 14 -60 22 generallaymansaestheticspyingscreen:glass_projector{placingFace:\"south\",wirelessPos:[[I;10,-60,10],[I;18,-60,10],[I;10,-60,18],[I;18,-60,18]]}");
            server.runOnServer(s -> {
                GlassProjectorBlockEntity field = (GlassProjectorBlockEntity)s.overworld().getBlockEntity(new BlockPos(14, -60, 22));
                field.cloak = true;
                field.cloakField = true;
                field.fieldHeight = 8;
                field.sync();
            });
            server.runCommand("setblock 14 -61 22 minecraft:redstone_block");
            String scan = server.computeOnServer(GlassClientStressTest::stuffedCube);

            for(int join = 1; join <= 2; join++)
            {
                try(TestServerConnection connection = server.connect())
                {
                    server.runCommand("gamemode spectator @a");
                    server.runCommand("tp @a 14 -60 30 180 0");
                    settle(connection.getClientWorld(), context);
                    context.waitTicks(60);
                    check(views(context) > 0, "join " + join + ": the cloak field is drawn on a dedicated server");
                    context.takeScreenshot("stress_server_field_join" + join);

                    server.runCommand("tp @a -16 -60 12 180 10");
                    int waited = context.waitFor(mc -> HoloClient.projection(scan) != null, 600);
                    check(context.computeOnClient(mc -> mc.getConnection() != null), "join " + join + ": still connected after the big scan came (" + waited + " ticks)");
                    context.waitTicks(80);
                    context.takeScreenshot("stress_server_cube_join" + join);
                }
                context.waitTicks(20);
            }
        }
    }
}
