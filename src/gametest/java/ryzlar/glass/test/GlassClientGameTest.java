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
import ryzlar.glass.GlassRegistry;
import ryzlar.glass.client.holo.HoloProjectorScreen;
import ryzlar.glass.client.holo.HoloScannerScreen;
import ryzlar.glass.holo.HoloCubeEntity;
import ryzlar.glass.holo.HoloNetworking;
import ryzlar.glass.holo.HoloProjectionData;
import ryzlar.glass.holo.HoloProjectorBlockEntity;
import ryzlar.glass.holo.HoloScannerBlockEntity;
import ryzlar.glass.holo.ProjectionRef;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import ryzlar.glass.blockentity.GlassBaseBlockEntity;
import ryzlar.glass.blockentity.GlassProjectorBlockEntity;
import ryzlar.glass.blockentity.GlassSceneSelectorBlockEntity;
import ryzlar.glass.blockentity.GlassTerminalBlockEntity;
import net.minecraft.client.Minecraft;
import ryzlar.glass.client.gui.CameraScreen;
import ryzlar.glass.client.gui.ProjectorScreen;
import ryzlar.glass.client.gui.SceneSelectorScreen;
import ryzlar.glass.client.gui.WirelessOrderScreen;
import ryzlar.glass.client.gui.WirelessProjectorScreen;
import ryzlar.glass.network.GlassNetworkData;
import ryzlar.glass.network.GlassNetworkManager;
import ryzlar.glass.network.GlassNetworking;
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
    private static final BlockPos LEVER = new BlockPos(0, -58, -1);
    private static final BlockPos CLOAK_PROJECTOR = new BlockPos(-37, -60, -40);
    private static final BlockPos SELECTOR = new BlockPos(5, -60, 3);
    private static final BlockPos HOLO_SCANNER = new BlockPos(58, -60, -26);
    private static final BlockPos HOLO_PROJECTOR = new BlockPos(61, -60, -40);
    private static final BlockPos FIELD_PROJECTOR = new BlockPos(-66, -60, 50);
    private static final BlockPos GOLD_CAMERA = new BlockPos(40, -59, 0);
    private static final BlockPos DIAMOND_CAMERA = new BlockPos(80, -59, 0);
    private static final BlockPos SPARE_TERMINAL = new BlockPos(5, -60, 6);
    private static final BlockPos WIRELESS_PROJECTOR = new BlockPos(-14, -59, 6);
    private static final BlockPos WINDOW_PROJECTOR = new BlockPos(23, -60, 30);
    private static final BlockPos WINDOW_CAMERA = new BlockPos(23, -60, 31);

    /** Right-clicks a lever as the player, like flicking it by hand. */
    private static void flickLever(ClientGameTestContext context, BlockPos pos)
    {
        context.runOnClient(mc -> mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.NORTH, pos, false)));
    }

    /**
     * Waits for the chunks around the player to be drawn. That wait sometimes times out on a busy machine (all the views
     * this test opens keep the chunk builder busy); then just give it a few more seconds instead of failing.
     */
    private static void settle(TestSingleplayerContext singleplayer, ClientGameTestContext context)
    {
        try
        {
            singleplayer.getClientWorld().waitForChunksRender();
        }
        catch(AssertionError e)
        {
            LOGGER.warn("Chunks not drawn in time, waiting a little longer");
            context.waitTicks(80);
        }
    }

    private static void check(boolean condition, String message)
    {
        if(!condition)
        {
            throw new AssertionError(message);
        }
        LOGGER.info("GLASS test OK: {}", message);
    }

    /** A cloak field in a little village, looked at from low, grazing, high and inside angles. */
    private static void cloakOnly(TestSingleplayerContext singleplayer, ClientGameTestContext context, TestServerContext server)
    {
        //A village-like spot: paths, a raised farm, houses, a tree and a lamp post, round and inside the field.
        server.runCommand("fill -100 -61 0 -20 -61 90 minecraft:grass_block");
        server.runCommand("fill -100 -60 0 -20 -50 90 minecraft:air");
        //A long strip of ground out along the field, for looking along a wall far into the distance.
        server.runCommand("fill -19 -61 25 120 -61 60 minecraft:grass_block");
        server.runCommand("fill -75 -61 42 -40 -61 43 minecraft:dirt_path");
        server.runCommand("fill -58 -61 20 -57 -61 70 minecraft:dirt_path");
        server.runCommand("fill -52 -60 50 -47 -60 55 minecraft:oak_log");
        server.runCommand("fill -51 -60 51 -48 -60 54 minecraft:farmland[moisture=7]");
        server.runCommand("fill -70 -60 30 -66 -56 34 minecraft:oak_planks hollow");
        server.runCommand("fill -47 -60 30 -43 -57 34 minecraft:cobblestone hollow");
        server.runCommand("fill -58 -60 42 -58 -56 42 minecraft:oak_log");
        server.runCommand("fill -60 -55 40 -56 -53 44 minecraft:oak_leaves[persistent=true]");
        server.runCommand("fill -45 -60 44 -45 -58 44 minecraft:oak_fence");
        server.runCommand("setblock -45 -57 44 minecraft:white_wool");
        //The field: four wireless extensions in the corners, the projector beside them.
        server.runCommand("setblock -62 -60 38 generallaymansaestheticspyingscreen:glass_wireless_extension");
        server.runCommand("setblock -54 -60 38 generallaymansaestheticspyingscreen:glass_wireless_extension");
        server.runCommand("setblock -62 -60 46 generallaymansaestheticspyingscreen:glass_wireless_extension");
        server.runCommand("setblock -54 -60 46 generallaymansaestheticspyingscreen:glass_wireless_extension");
        server.runCommand("setblock -66 -60 50 generallaymansaestheticspyingscreen:glass_projector{placingFace:\"south\",wirelessPos:[[I;-62,-60,38],[I;-54,-60,38],[I;-62,-60,46],[I;-54,-60,46]]}");
        context.waitTicks(2);
        server.runOnServer(s -> {
            GlassProjectorBlockEntity field = (GlassProjectorBlockEntity)s.overworld().getBlockEntity(FIELD_PROJECTOR);
            field.cloak = true;
            field.cloakField = true;
            field.fieldHeight = 10;
            field.sync();
        });
        server.runCommand("tp @p -58 -60 70 180 5");
        settle(singleplayer, context);
        server.runCommand("setblock -66 -61 50 minecraft:redstone_block");
        context.waitTicks(80);
        //Spectator, so the high views do not fall.
        server.runCommand("gamemode spectator @p");
        //Far enough to see where the cloak's ground ends (the void showed as pale triangles near the horizon).
        context.runOnClient(client -> client.options.renderDistance().set(12));
        //Each view: where the feet are, and how far to the side of the field's centre it looks (degrees), so the field
        //is seen square on, off to one side, or at the edge of the screen. Low ones, high ones from afar, and one
        //looking down at a wall's foot.
        double[] centre = { -57.5D, -56D, 42.5D };
        Object[][] views = {
                { "front", -57.5D, -60D, 62.5D, 0D },
                { "grazing_left", -75D, -60D, 52D, 0D },
                { "grazing_right", -40D, -60D, 52D, 0D },
                { "along_south", -80D, -60D, 48D, 0D },
                { "high_sw", -85D, -40D, 70D, 0D },
                { "high_sw_side", -85D, -40D, 70D, 25D },
                { "high_se_side", -30D, -40D, 70D, -25D },
                { "high_nw_side", -85D, -40D, 15D, -25D },
                { "high_far", -57.5D, -35D, 90D, 20D },
                { "high_far_other", -57.5D, -35D, 90D, -20D },
                { "high_close", -70D, -44D, 56D, 15D },
                { "slant_wall_height", -110D, -51.5D, 52D, 0D },
                { "slant_wall_height_side", -110D, -51.5D, 52D, 12D },
                { "slant_far", -120D, -48D, 50D, -8D },
                { "slant_other", -5D, -51.5D, 33D, 0D },
                { "along_wall_far", -100D, -51.5D, 48D, 0D },
                { "along_wall_far_side", -100D, -51.5D, 48D, 5D },
        };
        for(Object[] view : views)
        {
            double x = (double)view[1], y = (double)view[2], z = (double)view[3];
            double dx = centre[0] - x, dy = centre[1] - (y + 1.62D), dz = centre[2] - z;
            double yaw = Math.toDegrees(Math.atan2(-dx, dz)) + (double)view[4];
            double pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            server.runCommand(String.format(java.util.Locale.ROOT, "tp @p %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
            context.waitTicks(25);
            context.takeScreenshot("cloakonly_" + view[0]);
        }
        server.runCommand("tp @p -57.5 -59.4 50.5 180 45");
        context.waitTicks(25);
        context.takeScreenshot("cloakonly_ground_seam");
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
            settle(singleplayer, context);
            TestServerContext server = singleplayer.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("gamerule doWeatherCycle false");
            server.runCommand("weather clear");
            //GLASS_TEST_ONLY=cloak: only the cloak field, looked at from many sides (quick, to chase a cloak bug).
            if("cloak".equals(System.getenv("GLASS_TEST_ONLY")))
            {
                cloakOnly(singleplayer, context, server);
                return;
            }

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
            settle(singleplayer, context);
            context.waitTicks(10);
            server.runCommand("setblock 0 -58 -1 minecraft:redstone_block");
            int onWaited = 0;
            for(int tick : new int[] { 3, 6, 9, 12, 16, 22 })
            {
                context.waitTicks(tick - onWaited);
                onWaited = tick;
                context.takeScreenshot("turn_on_tick_" + (tick < 10 ? "0" : "") + tick);
            }
            context.waitTicks(60);
            check(server.computeOnServer(GlassClientGameTest::countActive) == 35, "all 35 GLASS blocks turned on");
            context.takeScreenshot("screen_gold");

            //Punching the glass makes it ripple (the client starts the ripple when you hit it).
            context.runOnClient(mc -> {
                if(mc.level.getBlockEntity(new BlockPos(0, -57, 0)) instanceof GlassBaseBlockEntity hit)
                {
                    hit.fadeoutTime = GlassBaseBlockEntity.FADEOUT_TIME;
                    hit.fadePropagate = GlassBaseBlockEntity.PROPAGATE_TIME;
                    hit.fadeDistance = 2;
                    hit.fadePropagate();
                }
            });
            context.waitTicks(3);
            context.takeScreenshot("punch_ripple");
            context.waitTicks(20);

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
            settle(singleplayer, context);
            context.waitTicks(20);
            context.takeScreenshot("screen_wireless");
            //From behind: its edges glow; close up a nano grid shows; walking through it sends a ripple across it.
            server.runCommand("fill -13 -59 0 -12 -59 9 minecraft:barrier");
            server.runCommand("tp @p -12.5 -58 -1.5 0 -10");
            context.waitTicks(20);
            context.takeScreenshot("wireless_back_far");
            server.runCommand("tp @p -12.5 -58 4.5 0 -10");
            context.waitTicks(10);
            context.takeScreenshot("wireless_back_close");
            server.runCommand("tp @p -12.5 -58 7.6 0 -10");
            context.waitTicks(5);
            context.getInput().holdKey(options -> options.keyDown);
            context.waitTicks(14);
            context.getInput().releaseKey(options -> options.keyDown);
            context.waitTicks(3);
            context.takeScreenshot("wireless_back_walked_through");
            server.runCommand("fill -13 -59 0 -12 -59 9 minecraft:air");

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
            settle(singleplayer, context);
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
            //The extension's own screen: what it is connected to, and the projectors nearby.
            context.setScreen(() -> new WirelessProjectorScreen((ryzlar.glass.blockentity.GlassWirelessBlockEntity)Minecraft.getInstance().level
                    .getBlockEntity(new BlockPos(-16, -55, 6))));
            context.waitTicks(10);
            context.takeScreenshot("gui_wireless_extension");
            context.setScreen(() -> null);

            //Switching off plays the turn-on animation in reverse: the image fades to white, furthest blocks first.
            server.runCommand("tp @p 0.5 -60 8.5 180 0");
            settle(singleplayer, context);
            context.waitTicks(20);
            server.runCommand("setblock 0 -58 -1 minecraft:air");
            int waited = 0;
            for(int tick : new int[] { 1, 4, 8, 12, 16, 20, 30 })
            {
                context.waitTicks(tick - waited);
                waited = tick;
                context.takeScreenshot("switch_off_tick_" + (tick < 10 ? "0" : "") + tick);
            }
            context.waitTicks(60);
            check(server.computeOnServer(GlassClientGameTest::countActive) == 0, "screen switched off");
            server.runCommand("setblock 0 -58 -1 minecraft:redstone_block");
            context.waitTicks(60);
            check(server.computeOnServer(GlassClientGameTest::countActive) == 35, "screen switched on again");
            context.takeScreenshot("reon_after_off");

            //A block right in front of the camera: the screen shows that block, steadily (its face lies on the
            //terminal's front face, where the view is clipped; it used to flicker like z-fighting).
            server.runCommand("setblock 40 -59 -1 minecraft:emerald_block");
            settle(singleplayer, context);
            context.waitTicks(10);
            for(int i = 0; i < 3; i++)
            {
                context.takeScreenshot("blocked_camera_" + i);
                context.waitTicks(1);
            }
            server.runCommand("setblock 40 -59 -1 minecraft:air");
            settle(singleplayer, context);
            context.waitTicks(10);

            //A lever, flicked like a player would: off, on, off quickly, then on and off again with time to watch.
            String leverOn = "setblock 0 -58 -1 minecraft:lever[face=wall,facing=north,powered=true]";
            String leverOff = "setblock 0 -58 -1 minecraft:lever[face=wall,facing=north,powered=false]";
            server.runCommand(leverOn);
            context.waitTicks(40);
            server.runCommand(leverOff);
            context.waitTicks(6);
            server.runCommand(leverOn);
            context.waitTicks(6);
            server.runCommand(leverOff);
            context.waitTicks(6);
            server.runCommand(leverOn);
            context.waitTicks(60);
            check(server.computeOnServer(GlassClientGameTest::countActive) == 35, "screen on after flicking the lever");
            context.takeScreenshot("lever_on_after_flicking");
            server.runCommand(leverOff);
            int leverWaited = 0;
            for(int tick : new int[] { 3, 8, 14 })
            {
                context.waitTicks(tick - leverWaited);
                leverWaited = tick;
                context.takeScreenshot("lever_off_tick_" + (tick < 10 ? "0" : "") + tick);
            }
            context.waitTicks(60);
            context.takeScreenshot("lever_off_done");
            server.runCommand(leverOn);
            context.waitTicks(60);
            context.takeScreenshot("lever_on_again");

            //The player flicks the lever by hand: off a second after switching on (while the view is still being
            //pre-drawn), and off again after a while. Both times the wave must shrink back, not vanish.
            server.runCommand("attribute @p minecraft:block_interaction_range base set 24");
            server.runCommand(leverOff);
            context.waitTicks(60);
            for(int round = 0; round < 2; round++)
            {
                flickLever(context, LEVER);
                context.waitTicks(round == 0 ? 25 : 120);
                check(server.computeOnServer(GlassClientGameTest::countActive) == 35, "hand lever round " + round + ": screen on");
                flickLever(context, LEVER);
                int handWaited = 0;
                for(int tick : new int[] { 2, 5, 8, 11 })
                {
                    context.waitTicks(tick - handWaited);
                    handWaited = tick;
                    context.takeScreenshot("hand_lever_" + round + "_off_tick_" + (tick < 10 ? "0" : "") + tick);
                }
                context.waitTicks(60);
            }
            server.runCommand("setblock 0 -58 -1 minecraft:redstone_block");
            context.waitTicks(20);

            //Switching scene while the screen is on: the new scene wipes over the old one in a wave from the projector.
            server.runCommand("tp @p 0.5 -60 8.5 180 0");
            context.waitTicks(40);
            context.takeScreenshot("scene_before_switch");
            server.runOnServer(s -> GlassNetworking.showChannel((GlassProjectorBlockEntity)s.overworld().getBlockEntity(PROJECTOR),
                    GlassNetworkManager.data(s), diamondId, "Diamond"));
            int sceneWaited = 0;
            for(int tick : new int[] { 3, 6, 9, 13, 20 })
            {
                context.waitTicks(tick - sceneWaited);
                sceneWaited = tick;
                context.takeScreenshot("scene_wipe_tick_" + (tick < 10 ? "0" : "") + tick);
            }
            check(server.computeOnServer(GlassClientGameTest::countActive) == 35, "screen stays on while switching scene");
            server.runOnServer(s -> GlassNetworking.showChannel((GlassProjectorBlockEntity)s.overworld().getBlockEntity(PROJECTOR),
                    GlassNetworkManager.data(s), goldId, "Gold"));
            context.waitTicks(40);
            context.takeScreenshot("scene_back_to_gold");

            //Cloak: a glass wall in front of a brick base shows what is behind the base, as if it were not there.
            server.runCommand("fill -48 -61 -60 -24 -61 -25 minecraft:grass_block");
            //Under the base the grass has died, as it does under blocks; the cloak must not show that footprint.
            server.runCommand("fill -40 -61 -45 -34 -61 -41 minecraft:dirt");
            //Higher ground to the west of the base, and grass dotted all around: the made-up ground under the cloak has
            //to carry both on.
            server.runCommand("fill -48 -60 -52 -41 -60 -28 minecraft:grass_block");
            server.runOnServer(s -> {
                for(int x = -47; x <= -26; x++)
                {
                    for(int z = -55; z <= -26; z++)
                    {
                        boolean base = x >= -41 && x <= -33 && z >= -46 && z <= -39;
                        boolean player = Math.abs(x + 36) <= 1 && z >= -38;
                        if(!base && !player && Math.floorMod(x * 7 + z * 3, 5) == 0)
                        {
                            BlockPos ground = new BlockPos(x, x <= -41 ? -60 : -61, z);
                            if(s.overworld().getBlockState(ground).is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)
                                    && s.overworld().getBlockState(ground.above()).isAir())
                            {
                                s.overworld().setBlockAndUpdate(ground.above(), net.minecraft.world.level.block.Blocks.SHORT_GRASS.defaultBlockState());
                            }
                        }
                    }
                }
            });
            server.runCommand("fill -40 -60 -45 -34 -56 -41 minecraft:bricks");
            server.runCommand("fill -39 -60 -50 -39 -55 -50 minecraft:red_wool");
            server.runCommand("fill -37 -60 -50 -37 -55 -50 minecraft:blue_wool");
            server.runCommand("fill -35 -60 -50 -35 -55 -50 minecraft:yellow_wool");
            server.runCommand("fill -46 -60 -56 -28 -51 -56 minecraft:gold_block");
            server.runCommand("fill -40 -60 -40 -34 -56 -40 generallaymansaestheticspyingscreen:glass_extension");
            server.runCommand("setblock -37 -60 -40 generallaymansaestheticspyingscreen:glass_projector{placingFace:\"south\"}");
            context.waitTicks(2);
            server.runOnServer(s -> {
                GlassProjectorBlockEntity cloak = (GlassProjectorBlockEntity)s.overworld().getBlockEntity(CLOAK_PROJECTOR);
                cloak.cloak = true;
                cloak.cloakDepth = 6;
                cloak.sync();
            });
            server.runCommand("tp @p -36.5 -60 -29.5 180 0");
            settle(singleplayer, context);
            context.waitTicks(20);
            context.takeScreenshot("cloak_off");
            server.runCommand("setblock -37 -61 -40 minecraft:redstone_block");
            context.waitTicks(60);
            check(server.computeOnServer(s -> s.overworld().getBlockEntity(CLOAK_PROJECTOR) instanceof GlassProjectorBlockEntity c && c.active), "cloak on");
            context.takeScreenshot("cloak_on");
            server.runCommand("tp @p -30.5 -60 -32.5 150 0");
            context.waitTicks(20);
            context.takeScreenshot("cloak_on_angle");
            //Close up, looking at the glass: no selection outline gives the cloak away.
            server.runCommand("tp @p -36.5 -60 -37.5 160 25");
            context.waitTicks(20);
            context.takeScreenshot("cloak_close");
            check(context.computeOnClient(mc -> mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
                    && ryzlar.glass.client.GlassClientState.INSTANCE.isHiddenByCloak(hit.getBlockPos(), mc.gameRenderer.getMainCamera().getPosition())), "cloak hides the outline of what you look at");
            server.runCommand("setblock -37 -61 -40 minecraft:grass_block");
            context.waitTicks(40);
            context.takeScreenshot("cloak_switched_off");
            //Back to the first screen, so the cameras' chunks are loaded again for the checks below.
            server.runCommand("tp @p 0.5 -60 8.5 180 0");
            settle(singleplayer, context);
            context.waitTicks(40);

            //The scene selector: linked to the nearest projector, a list of scenes, a redstone pulse shows the next one.
            server.runCommand("setblock 5 -60 3 generallaymansaestheticspyingscreen:glass_scene_selector");
            context.waitTicks(2);
            boolean linked = server.computeOnServer(s -> {
                GlassSceneSelectorBlockEntity selector = (GlassSceneSelectorBlockEntity)s.overworld().getBlockEntity(SELECTOR);
                return selector.linkNearest() && PROJECTOR.equals(selector.projectorPos)
                        && selector.addScene(goldId, "Gold") && selector.addScene(diamondId, "Diamond")
                        && !selector.addScene(goldId, "Gold") && selector.showScene(0);
            });
            check(linked, "selector linked to the nearest projector, with two scenes");
            context.waitTicks(30);
            check(server.computeOnServer(s -> ((GlassProjectorBlockEntity)s.overworld().getBlockEntity(PROJECTOR)).channelId.equals(goldId)), "selector shows scene 1");
            server.runCommand("setblock 5 -60 2 minecraft:redstone_block");
            context.waitTicks(2);
            server.runCommand("setblock 5 -60 2 minecraft:air");
            context.waitTicks(8);
            context.takeScreenshot("selector_pulse_wipe");
            context.waitTicks(30);
            check(server.computeOnServer(s -> ((GlassProjectorBlockEntity)s.overworld().getBlockEntity(PROJECTOR)).channelId.equals(diamondId)), "redstone pulse shows the next scene");
            context.takeScreenshot("selector_scene_2");
            GlassSceneSelectorBlockEntity clientSelector = context.computeOnClient(mc -> (GlassSceneSelectorBlockEntity)mc.level.getBlockEntity(SELECTOR));
            GlassProjectorBlockEntity clientProjector = context.computeOnClient(mc -> (GlassProjectorBlockEntity)mc.level.getBlockEntity(PROJECTOR));
            context.setScreen(() -> new SceneSelectorScreen(clientSelector));
            context.waitTicks(15);
            context.takeScreenshot("gui_scene_selector");
            context.setScreen(() -> new ProjectorScreen(clientProjector));
            context.waitTicks(15);
            context.takeScreenshot("gui_projector_cloak");
            context.setScreen(() -> null);
            server.runOnServer(s -> ((GlassSceneSelectorBlockEntity)s.overworld().getBlockEntity(SELECTOR)).showScene(0));
            context.waitTicks(30);

            //Holograms: scan a little tower into a crystal, then project it.
            server.runCommand("fill 52 -61 -50 70 -61 -24 minecraft:white_concrete");
            server.runCommand("fill 60 -60 -28 64 -60 -24 minecraft:stone_bricks");
            server.runCommand("fill 61 -59 -27 63 -57 -25 minecraft:gold_block");
            server.runCommand("setblock 62 -56 -26 minecraft:glowstone");
            server.runCommand("fill 60 -59 -28 60 -57 -28 minecraft:red_wool");
            server.runCommand("fill 64 -59 -24 64 -57 -24 minecraft:blue_wool");
            server.runCommand("setblock 58 -60 -26 generallaymansaestheticspyingscreen:holo_scanner");
            server.runCommand("tp @p 61.5 -60 -15.5 180 10");
            settle(singleplayer, context);
            context.waitTicks(5);
            server.runOnServer(s -> ((HoloScannerBlockEntity)s.overworld().getBlockEntity(HOLO_SCANNER)).configure(2, 0, -2, 5, 5, 5, "Tower"));
            context.waitTicks(5);
            server.runCommand("give @p minecraft:amethyst_shard 4");
            context.waitTicks(2);
            HoloScannerBlockEntity clientScanner = context.computeOnClient(mc -> (HoloScannerBlockEntity)mc.level.getBlockEntity(HOLO_SCANNER));
            context.setScreen(() -> new HoloScannerScreen(clientScanner));
            context.waitTicks(15);
            context.takeScreenshot("gui_holo_scanner");
            context.setScreen(() -> null);
            context.waitTicks(5);
            context.takeScreenshot("holo_scan_grid");
            context.runOnClient(mc -> ClientPlayNetworking.send(new HoloNetworking.ScannerScan(HOLO_SCANNER)));
            context.waitTicks(10);
            boolean gotCrystal = server.computeOnServer(s -> {
                ServerPlayer p = player(s);
                for(int i = 0; i < p.getInventory().getContainerSize(); i++)
                {
                    ProjectionRef ref = p.getInventory().getItem(i).get(GlassRegistry.PROJECTION);
                    if(ref != null && ref.name().equals("Tower") && ref.sizeX() == 5 && HoloProjectionData.find(s, ref.id()) != null)
                    {
                        return true;
                    }
                }
                return false;
            });
            check(gotCrystal, "scanning gives a crystal holding the projection");

            //The holo projector, with the crystal in it, powered.
            server.runCommand("setblock 61 -60 -40 generallaymansaestheticspyingscreen:holo_projector");
            context.waitTicks(2);
            server.runOnServer(s -> {
                ServerPlayer p = player(s);
                for(int i = 0; i < p.getInventory().getContainerSize(); i++)
                {
                    ItemStack stack = p.getInventory().getItem(i);
                    if(stack.get(GlassRegistry.PROJECTION) != null)
                    {
                        ((HoloProjectorBlockEntity)s.overworld().getBlockEntity(HOLO_PROJECTOR)).putCrystal(stack);
                        stack.shrink(1);
                        break;
                    }
                }
            });
            server.runCommand("setblock 61 -61 -40 minecraft:redstone_block");
            //Look from the other side: the hologram in front, the real tower behind it.
            server.runCommand("tp @p 61.5 -59 -53.5 0 12");
            settle(singleplayer, context);
            context.waitTicks(40);
            check(server.computeOnServer(s -> ((HoloProjectorBlockEntity)s.overworld().getBlockEntity(HOLO_PROJECTOR)).active), "holo projector on with a crystal");
            context.takeScreenshot("holo_no_emitters");
            //Emitters in four different corners: now every side is lit.
            server.runCommand("setblock 57 -60 -44 generallaymansaestheticspyingscreen:holo_emitter[facing=up]");
            server.runCommand("setblock 65 -60 -36 generallaymansaestheticspyingscreen:holo_emitter[facing=up]");
            server.runCommand("setblock 57 -53 -36 generallaymansaestheticspyingscreen:holo_emitter[facing=down]");
            server.runCommand("setblock 65 -53 -44 generallaymansaestheticspyingscreen:holo_emitter[facing=down]");
            context.waitTicks(2);
            check(server.computeOnServer(s -> ((HoloProjectorBlockEntity)s.overworld().getBlockEntity(HOLO_PROJECTOR)).linkEmitters()) == 4, "four emitters linked");
            context.waitTicks(30);
            context.takeScreenshot("holo_four_emitters");
            //Switching off and on: the nano wave shrinks back into the projector, then grows out again.
            server.runCommand("setblock 61 -61 -40 minecraft:white_concrete");
            int holoWaited = 0;
            for(int tick : new int[] { 4, 10, 16 })
            {
                context.waitTicks(tick - holoWaited);
                holoWaited = tick;
                context.takeScreenshot("holo_off_tick_" + (tick < 10 ? "0" : "") + tick);
            }
            context.waitTicks(30);
            server.runCommand("setblock 61 -61 -40 minecraft:redstone_block");
            holoWaited = 0;
            for(int tick : new int[] { 4, 9, 15, 24 })
            {
                context.waitTicks(tick - holoWaited);
                holoWaited = tick;
                context.takeScreenshot("holo_on_tick_" + (tick < 10 ? "0" : "") + tick);
            }
            context.waitTicks(20);
            server.runCommand("setblock 57 -53 -36 minecraft:air");
            server.runCommand("setblock 65 -53 -44 minecraft:air");
            context.waitTicks(30);
            context.takeScreenshot("holo_two_bottom_emitters");
            server.runCommand("setblock 57 -53 -36 generallaymansaestheticspyingscreen:holo_emitter[facing=down]");
            server.runCommand("setblock 65 -53 -44 generallaymansaestheticspyingscreen:holo_emitter[facing=down]");
            context.waitTicks(2);
            server.runOnServer(s -> ((HoloProjectorBlockEntity)s.overworld().getBlockEntity(HOLO_PROJECTOR)).linkEmitters());
            server.runOnServer(s -> ((HoloProjectorBlockEntity)s.overworld().getBlockEntity(HOLO_PROJECTOR)).configure(1, 0, 1));
            context.waitTicks(30);
            context.takeScreenshot("holo_blue_turned");
            server.runOnServer(s -> ((HoloProjectorBlockEntity)s.overworld().getBlockEntity(HOLO_PROJECTOR)).configure(2, 0, 0));
            context.waitTicks(30);
            context.takeScreenshot("holo_solid");
            server.runOnServer(s -> ((HoloProjectorBlockEntity)s.overworld().getBlockEntity(HOLO_PROJECTOR)).configure(0, 3, 0));
            context.waitTicks(30);
            context.takeScreenshot("holo_quarter_scale");
            HoloProjectorBlockEntity clientHolo = context.computeOnClient(mc -> (HoloProjectorBlockEntity)mc.level.getBlockEntity(HOLO_PROJECTOR));
            context.setScreen(() -> new HoloProjectorScreen(clientHolo));
            context.waitTicks(15);
            context.takeScreenshot("gui_holo_projector");
            context.setScreen(() -> null);
            server.runCommand("tp @p 0.5 -60 8.5 180 0");
            settle(singleplayer, context);
            context.waitTicks(20);

            //Holo cube: set it down in a room and let it scan the ball round it, then set it down elsewhere to project it 1:1.
            server.runCommand("fill 100 -61 -10 106 -56 -4 minecraft:stone_bricks hollow");
            server.runCommand("fill 103 -59 -10 104 -58 -10 minecraft:glass");
            server.runCommand("setblock 101 -60 -9 minecraft:bookshelf");
            server.runCommand("setblock 101 -59 -9 minecraft:bookshelf");
            server.runCommand("setblock 105 -60 -9 minecraft:crafting_table");
            server.runCommand("setblock 105 -60 -5 minecraft:red_wool");
            server.runCommand("setblock 101 -60 -5 minecraft:gold_block");
            //A chest with something in it, a sign, and a sheep: all go into the scan.
            server.runCommand("setblock 101 -60 -7 minecraft:chest[facing=east]{Items:[{Slot:0b,id:\"minecraft:diamond\",count:3}]}");
            server.runCommand("setblock 105 -59 -7 minecraft:oak_wall_sign[facing=west]{front_text:{messages:[\"GLASS\",\"\",\"\",\"\"]}}");
            server.runCommand("summon minecraft:sheep 104.5 -60 -6.5 {NoAI:1b,Color:3}");
            server.runCommand("fill 112 -61 -24 140 -61 12 minecraft:white_concrete");
            server.runCommand("tp @p 103.5 -60 -5.5 180 20");
            settle(singleplayer, context);
            server.runCommand("item replace entity @p weapon.mainhand with generallaymansaestheticspyingscreen:holo_cube");
            context.waitTicks(5);
            BlockPos roomFloor = new BlockPos(103, -61, -8);
            context.runOnClient(mc -> mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(roomFloor).add(0, 0.5, 0), net.minecraft.core.Direction.UP, roomFloor, false)));
            context.waitTicks(5);
            net.minecraft.world.phys.AABB room = new net.minecraft.world.phys.AABB(100, -62, -11, 107, -55, -3);
            check(server.computeOnServer(s -> !s.overworld().getEntitiesOfClass(HoloCubeEntity.class, room).isEmpty()), "the empty cube is set down in the room");
            //Scanned at midnight in the rain, the sky goes into the scan too (projected at noon, below, it is night inside).
            server.runCommand("time set midnight");
            server.runCommand("weather rain");
            server.runOnServer(s -> s.overworld().getEntitiesOfClass(HoloCubeEntity.class, room).forEach(d -> d.scanFor(player(s))));
            int scanWaited = 0;
            for(int tick : new int[] { 8, 20, 35 })
            {
                context.waitTicks(tick - scanWaited);
                scanWaited = tick;
                context.takeScreenshot("cube_scan_tick_" + tick);
            }
            boolean scanned = false;
            for(int i = 0; i < 40 && !scanned; i++)
            {
                context.waitTicks(5);
                scanned = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(HoloCubeEntity.class, room).stream().anyMatch(d -> d.scan() != null));
            }
            check(scanned, "the cube scans the ball around it");
            float scanRain = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(HoloCubeEntity.class, room).stream()
                    .map(HoloCubeEntity::scan).filter(java.util.Objects::nonNull).findFirst().map(ryzlar.glass.holo.CubeScan::rain).orElse(0F));
            check(server.computeOnServer(s -> s.overworld().getEntitiesOfClass(HoloCubeEntity.class, room).stream()
                    .map(HoloCubeEntity::scan).anyMatch(scan -> scan != null && scan.skyTime() == 18000L)) && scanRain > 0F, "the scan keeps the sky: midnight, raining");
            check(server.computeOnServer(s -> s.overworld().getEntitiesOfClass(HoloCubeEntity.class, room).stream()
                    .map(HoloCubeEntity::scan).filter(java.util.Objects::nonNull)
                    .map(scan -> ryzlar.glass.holo.HoloProjectionData.find(s, scan.id()))
                    .anyMatch(p -> p != null && p.blockEntities().stream().anyMatch(t -> t.toString().contains("diamond"))
                            && p.entities().stream().anyMatch(t -> t.toString().contains("sheep")))),
                    "the scan keeps the chest's contents and the sheep");
            server.runCommand("time set noon");
            server.runCommand("weather clear");
            context.waitTicks(4);
            context.takeScreenshot("cube_scan_done");
            //Pick it up (with its scan) and take it in hand again.
            server.runOnServer(s -> s.overworld().getEntitiesOfClass(HoloCubeEntity.class, room).forEach(d -> d.startPickUp(player(s))));
            context.waitTicks(3);
            server.runOnServer(s -> {
                for(int i = 0; i < player(s).getInventory().getContainerSize(); i++)
                {
                    ItemStack stack = player(s).getInventory().getItem(i);
                    if(stack.is(GlassRegistry.HOLO_CUBE) && stack.get(GlassRegistry.CUBE_SCAN) != null)
                    {
                        player(s).getInventory().setItem(i, ItemStack.EMPTY);
                        player(s).setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
                        return;
                    }
                }
            });
            check(server.computeOnServer(s -> player(s).getMainHandItem().get(GlassRegistry.CUBE_SCAN) != null), "the cube is picked up with its scan");

            //Set it down in the open and switch it on: the bubble grows out of it, the scanned ball inside, 1:1.
            server.runCommand("tp @p 126.5 -60 7.5 180 0");
            settle(singleplayer, context);
            context.waitTicks(5);
            BlockPos ground = new BlockPos(126, -61, 4);
            context.runOnClient(mc -> mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), net.minecraft.core.Direction.UP, ground, false)));
            context.waitTicks(10);
            check(server.computeOnServer(s -> !s.overworld().getEntitiesOfClass(HoloCubeEntity.class, new net.minecraft.world.phys.AABB(120, -62, -2, 132, -55, 10)).isEmpty()),
                    "the cube is set down on the ground");
            context.takeScreenshot("cube_on_ground");
            server.runOnServer(s -> s.overworld().getEntitiesOfClass(HoloCubeEntity.class, new net.minecraft.world.phys.AABB(120, -62, -2, 132, -55, 10)).forEach(HoloCubeEntity::toggle));
            int cubeWaited = 0;
            for(int tick : new int[] { 20, 45, 100 })
            {
                context.waitTicks(tick - cubeWaited);
                cubeWaited = tick;
                context.takeScreenshot("cube_on_tick_" + tick);
            }
            server.runCommand("tp @p 126.5 -60 4.5 135 10");
            context.waitTicks(20);
            context.takeScreenshot("cube_room_inside");
            //Inside, it is lit and rains as when it was scanned (midnight, rain), while the real world has a clear noon.
            check(context.computeOnClient(mc -> mc.level.getSkyDarken(1F) < 0.3F && Math.abs(mc.level.getRainLevel(1F) - scanRain) < 0.05F),
                    "inside the simulation the light and weather are the scanned ones");
            //Inside, the scanned floor can be stood on (the real world is untouched), and the scanned chest can be looked into.
            check(context.computeOnClient(mc -> {
                        var scannedGround = ryzlar.glass.holo.HoloBubbles.groundAt(mc.level, new BlockPos(126, -61, 4));
                        return scannedGround != null && !scannedGround.isEmpty() &&mc.level.getBlockState(new BlockPos(126, -61, 4)).is(net.minecraft.world.level.block.Blocks.WHITE_CONCRETE);
                    }), "inside the bubble the scanned floor is there, over the untouched real one");
            boolean chestOpened = context.computeOnClient(mc -> mc.level.getEntitiesOfClass(HoloCubeEntity.class, new net.minecraft.world.phys.AABB(120, -62, -2, 132, -55, 10)).stream()
                    .anyMatch(ryzlar.glass.client.holo.SimulationView::openFirstContainer) && mc.screen instanceof ryzlar.glass.client.holo.HoloContainerScreen);
            check(chestOpened, "the scanned chest opens read-only");
            context.waitTicks(10);
            context.takeScreenshot("cube_chest_view");
            context.setScreen(() -> null);
            for(int yaw : new int[] { 0, 90, 180, 270 })
            {
                server.runCommand("tp @p 126.5 -60 4.5 " + yaw + " -5");
                context.waitTicks(4);
                context.takeScreenshot("cube_inside_" + yaw);
            }
            //From outside, the glowing bubble with the scan in blue inside it shows a simulation is running there.
            server.runCommand("tp @p 133.5 -59 12.5 135 15");
            context.waitTicks(10);
            context.takeScreenshot("cube_barrier_outside");
            server.runCommand("tp @p 126.5 -60 20.5 180 5");
            context.waitTicks(10);
            context.takeScreenshot("cube_barrier_far");
            check(context.computeOnClient(mc -> mc.level.getSkyDarken(1F) > 0.9F && mc.level.getRainLevel(1F) < 0.05F),
                    "outside the simulation the light and weather are the real ones");
            server.runCommand("tp @p 126.5 -60 10.5 180 10");
            context.waitTicks(10);
            //Switch it off: the room shrinks back into the cube.
            server.runOnServer(s -> s.overworld().getEntitiesOfClass(HoloCubeEntity.class, new net.minecraft.world.phys.AABB(120, -62, -2, 132, -55, 10)).forEach(HoloCubeEntity::toggle));
            context.waitTicks(30);
            context.takeScreenshot("cube_off_tick_30");
            context.waitTicks(80);
            //Pick it up again: it keeps its scan.
            server.runOnServer(s -> s.overworld().getEntitiesOfClass(HoloCubeEntity.class, new net.minecraft.world.phys.AABB(120, -62, -2, 132, -55, 10)).forEach(d -> d.startPickUp(player(s))));
            context.waitTicks(5);
            check(server.computeOnServer(s -> {
                for(int i = 0; i < player(s).getInventory().getContainerSize(); i++)
                {
                    ItemStack stack = player(s).getInventory().getItem(i);
                    if(stack.is(GlassRegistry.HOLO_CUBE) && stack.get(GlassRegistry.CUBE_SCAN) != null)
                    {
                        return true;
                    }
                }
                return false;
            }), "the cube is picked up with its scan");
            server.runCommand("tp @p 0.5 -60 8.5 180 0");
            settle(singleplayer, context);
            context.waitTicks(20);

            //Cloak field: four wireless extensions in the corners; walls rise from them and hide what is in between.
            server.runCommand("fill -70 -61 30 -40 -61 60 minecraft:grass_block");
            server.runCommand("fill -58 -60 42 -58 -56 42 minecraft:oak_log");
            server.runCommand("fill -60 -55 40 -56 -53 44 minecraft:oak_leaves[persistent=true]");
            server.runCommand("setblock -56 -60 44 minecraft:red_wool");
            //Like a village around it: a path through the field, a raised farm with water beside it, a house wall.
            server.runCommand("fill -70 -61 42 -44 -61 42 minecraft:dirt_path");
            server.runCommand("fill -52 -60 38 -47 -60 46 minecraft:oak_log");
            server.runCommand("fill -51 -60 39 -48 -60 45 minecraft:farmland[moisture=7]");
            server.runCommand("fill -50 -60 39 -50 -60 45 minecraft:water");
            server.runCommand("fill -51 -59 39 -51 -59 45 minecraft:wheat[age=7]");
            server.runCommand("fill -49 -59 39 -48 -59 45 minecraft:carrots[age=7]");
            server.runCommand("fill -67 -60 38 -65 -56 40 minecraft:oak_planks");
            server.runCommand("setblock -62 -60 38 generallaymansaestheticspyingscreen:glass_wireless_extension");
            server.runCommand("setblock -54 -60 38 generallaymansaestheticspyingscreen:glass_wireless_extension");
            server.runCommand("setblock -62 -60 46 generallaymansaestheticspyingscreen:glass_wireless_extension");
            server.runCommand("setblock -54 -60 46 generallaymansaestheticspyingscreen:glass_wireless_extension");
            server.runCommand("setblock -66 -60 50 generallaymansaestheticspyingscreen:glass_projector{placingFace:\"south\",wirelessPos:[[I;-62,-60,38],[I;-54,-60,38],[I;-62,-60,46],[I;-54,-60,46]]}");
            context.waitTicks(2);
            server.runOnServer(s -> {
                GlassProjectorBlockEntity field = (GlassProjectorBlockEntity)s.overworld().getBlockEntity(FIELD_PROJECTOR);
                field.cloak = true;
                field.cloakField = true;
                field.fieldHeight = 8;
                field.sync();
            });
            check(server.computeOnServer(s -> ((GlassProjectorBlockEntity)s.overworld().getBlockEntity(FIELD_PROJECTOR)).cloakFieldBox() != null), "four corner extensions make a cloak field");
            server.runCommand("tp @p -57.5 -60 58.5 180 5");
            settle(singleplayer, context);
            context.waitTicks(20);
            context.takeScreenshot("field_off");
            server.runCommand("setblock -66 -61 50 minecraft:redstone_block");
            int fieldWaited = 0;
            for(int tick : new int[] { 12, 24, 60 })
            {
                context.waitTicks(tick - fieldWaited);
                fieldWaited = tick;
                context.takeScreenshot("field_rise_tick_" + tick);
            }
            server.runCommand("tp @p -47.5 -60 52.5 135 5");
            context.waitTicks(20);
            context.takeScreenshot("field_angle");
            server.runCommand("setblock -58 -45 52 minecraft:barrier");
            server.runCommand("tp @p -57.5 -44 52.5 180 50");
            context.waitTicks(20);
            context.takeScreenshot("field_from_above");
            server.runCommand("setblock -58 -43 42 minecraft:barrier");
            server.runCommand("tp @p -57.5 -42 42.5 180 85");
            context.waitTicks(20);
            context.takeScreenshot("field_straight_above");
            server.runCommand("setblock -58 -45 52 minecraft:air");
            server.runCommand("setblock -58 -43 42 minecraft:air");
            server.runCommand("tp @p -59.5 -60 44.5 135 0");
            context.waitTicks(20);
            context.takeScreenshot("field_inside");
            server.runCommand("tp @p -55.5 -60 44.5 0 10");
            context.waitTicks(5);
            context.getInput().holdKey(options -> options.keyUp);
            context.waitTicks(12);
            context.getInput().releaseKey(options -> options.keyUp);
            context.waitTicks(3);
            context.takeScreenshot("field_inside_touch");
            server.runCommand("tp @p -57.5 -60 58.5 180 5");
            context.waitTicks(10);
            server.runCommand("setblock -66 -61 50 minecraft:grass_block");
            context.waitTicks(20);
            context.takeScreenshot("field_sink_tick_20");
            context.waitTicks(40);
            server.runCommand("tp @p 0.5 -60 8.5 180 0");
            settle(singleplayer, context);
            context.waitTicks(20);

            //The gadgets, while they are switched on (see GadgetFeature).
            if(ryzlar.glass.gadget.GadgetFeature.ENABLED)
            {
                //Gadgets. A motion sensor next to the diamond camera, and a zombie in its view: the sensor fires, and the
                //tablet tuned to that channel shows the camera's picture with the alert.
                server.runCommand("tp @p 76.5 -60 10.5 200 10");
                settle(singleplayer, context);
                server.runOnServer(s -> {
                    ItemStack tablet = new ItemStack(ryzlar.glass.gadget.Gadgets.GLASS_TABLET);
                    tablet.set(ryzlar.glass.gadget.Gadgets.TABLET_LINK, new ryzlar.glass.gadget.TabletLink(diamondId, "Diamond Net"));
                    player(s).setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, tablet);
                });
                server.runCommand("setblock 81 -59 0 generallaymansaestheticspyingscreen:motion_sensor");
                server.runCommand("summon minecraft:zombie 80.5 -60 6.5 {NoAI:1b,PersistenceRequired:1b,Silent:1b}");
                context.waitTicks(15);
                check(server.computeOnServer(s -> s.overworld().getBlockState(new BlockPos(81, -59, 0)).getValue(ryzlar.glass.gadget.MotionSensorBlock.POWERED)),
                        "the motion sensor sees the zombie through the terminal");
                context.setScreen(() -> new ryzlar.glass.client.gadget.TabletScreen(net.minecraft.world.InteractionHand.MAIN_HAND));
                context.waitTicks(50);
                context.takeScreenshot("gadget_tablet");
                context.setScreen(() -> null);
                server.runCommand("kill @e[type=minecraft:zombie]");
                server.runCommand("setblock 81 -59 0 minecraft:air");

                //A doorway filled with hard light.
                server.runCommand("fill 89 -61 14 99 -61 26 minecraft:stone_bricks");
                server.runCommand("fill 90 -60 20 90 -57 20 minecraft:stone_bricks");
                server.runCommand("fill 94 -60 20 94 -57 20 minecraft:stone_bricks");
                server.runCommand("fill 90 -57 20 94 -57 20 minecraft:stone_bricks");
                server.runCommand("setblock 90 -60 20 generallaymansaestheticspyingscreen:hard_light_emitter[facing=east]");
                server.runCommand("setblock 89 -60 20 minecraft:redstone_block");
                context.waitTicks(12);
                check(server.computeOnServer(s -> s.overworld().getBlockState(new BlockPos(92, -59, 20)).is(ryzlar.glass.gadget.Gadgets.HARD_LIGHT)),
                        "a powered hard-light emitter fills the doorway");
                server.runCommand("tp @p 92.5 -60 25.5 180 5");
                context.waitTicks(10);
                context.takeScreenshot("gadget_hard_light");

                //A holo map table, a decoy of the player, and optical camo.
                server.runCommand("setblock 96 -60 16 generallaymansaestheticspyingscreen:holo_map_table");
                server.runOnServer(s -> s.overworld().addFreshEntity(ryzlar.glass.gadget.HoloDecoyEntity.of(player(s), 92.5, -60, 16.5)));
                server.runCommand("fill 97 -60 13 99 -59 14 minecraft:oak_planks");
                server.runCommand("fill 93 -60 18 94 -58 18 minecraft:oak_log");
                server.runCommand("tp @p 98.5 -58.5 19.5 145 35");
                context.waitTicks(40);
                context.takeScreenshot("gadget_map_table");
                server.runCommand("tp @p 92.5 -60 21.5 180 5");
                context.waitTicks(10);
                context.takeScreenshot("gadget_decoy");
                server.runOnServer(s -> ryzlar.glass.gadget.OpticalCamo.engage(player(s)));
                context.waitTicks(10);
                check(server.computeOnServer(s -> ryzlar.glass.gadget.OpticalCamo.isCamouflaged(player(s))), "optical camo is on");
                context.takeScreenshot("gadget_camo");
                server.runOnServer(s -> ryzlar.glass.gadget.OpticalCamo.breakCamo(player(s)));
                server.runCommand("setblock 89 -60 20 minecraft:air");
                context.waitTicks(15);
                check(server.computeOnServer(s -> s.overworld().getBlockState(new BlockPos(92, -59, 20)).isAir()), "unpowered, the hard light goes");
            }
            //An outdoor simulation, scanned at dusk in the rain and run at noon in the dry: from inside, the sky at the
            //scanned time and weather over the scanned land and the real world beyond the bubble's wall.
            server.runCommand("weather rain");
            server.runCommand("time set 12600");
            server.runCommand("tp @p 95.5 -60 19.5 0 30");
            context.waitTicks(5);
            server.runCommand("item replace entity @p weapon.mainhand with generallaymansaestheticspyingscreen:holo_cube");
            context.waitTicks(3);
            BlockPos outdoorFloor = new BlockPos(95, -61, 23);
            context.runOnClient(mc -> mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(outdoorFloor).add(0, 0.5, 0), net.minecraft.core.Direction.UP, outdoorFloor, false)));
            context.waitTicks(5);
            net.minecraft.world.phys.AABB outdoor = new net.minecraft.world.phys.AABB(93, -62, 21, 98, -55, 26);
            server.runOnServer(s -> s.overworld().getEntitiesOfClass(HoloCubeEntity.class, outdoor).forEach(c -> c.scanFor(player(s))));
            boolean outdoorScanned = false;
            for(int i = 0; i < 60 && !outdoorScanned; i++)
            {
                context.waitTicks(5);
                outdoorScanned = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(HoloCubeEntity.class, outdoor).stream().anyMatch(c -> c.scan() != null));
            }
            check(outdoorScanned, "the cube scans the open air around it");
            context.waitTicks(40);
            server.runCommand("weather clear");
            server.runCommand("time set noon");
            server.runOnServer(s -> s.overworld().getEntitiesOfClass(HoloCubeEntity.class, outdoor).forEach(HoloCubeEntity::toggle));
            context.waitTicks(160);
            server.runCommand("tp @p 95.5 -60 21.5 0 -8");
            context.waitTicks(20);
            context.takeScreenshot("cube_outdoor_horizon");
            server.runCommand("tp @p 95.5 -60 21.5 0 -55");
            context.waitTicks(5);
            context.takeScreenshot("cube_outdoor_sky");
            server.runCommand("tp @p 95.5 -60 21.5 0 12");
            context.waitTicks(5);
            context.takeScreenshot("cube_outdoor_edge");
            server.runOnServer(s -> s.overworld().getEntitiesOfClass(HoloCubeEntity.class, outdoor).forEach(c -> c.startPickUp(player(s))));
            context.waitTicks(80);
            server.runCommand("tp @p 0.5 -60 8.5 180 0");
            settle(singleplayer, context);
            context.waitTicks(10);

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
