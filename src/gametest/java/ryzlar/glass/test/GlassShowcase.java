package ryzlar.glass.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Builds the showcase map for filming the trailer and store pages and saves it as "GLASS Showcase" in run/saves (run
 * with {@code GLASS_TEST_ONLY=showcase}; skipped otherwise). Only the map: no GLASS blocks, those are set up by hand.
 * <ul>
 *     <li>A tropical island at sunset: palms, beach, rocks, a rounded hill, plants; seagrass and a coral reef offshore.</li>
 *     <li>Under the island an empty square chamber with a podium of real blocks in the middle.</li>
 *     <li>A small bunker at the foot of the hill (room in front of it for a wall of glass), a shaft down to the chamber.</li>
 *     <li>A sailing ship north of the island with four mooring posts round it, and a jetty out to it.</li>
 *     <li>A research station on stilts east of the island: a bridge, a corridor and four empty rooms (one with a lighthouse
 *     model, one with a cosy corner).</li>
 * </ul>
 * Review screenshots land in build/run/clientGameTest/screenshots (showcase_*).
 */
public class GlassShowcase implements FabricClientGameTest
{
    private static final Logger LOGGER = LoggerFactory.getLogger("glass-showcase");
    static final int SEA = -51;            //top water block
    static final int FLOOR = -62;          //sea floor
    static final int LAB_Y = -49;          //the lab's floor
    static final int LAB_X = 41;           //the first lab room's west inside column
    static final int CHAMBER_FLOOR = -61;  //the chamber's floor
    static final int SHIP_Z = -45;         //the ship's keel line

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    @Override
    public void runTest(ClientGameTestContext context)
    {
        if(!"showcase".equals(System.getenv("GLASS_TEST_ONLY")))
        {
            return;
        }
        context.getInput().resizeWindow(1920, 1080);
        Path save;
        try(TestSingleplayerContext singleplayer = context.worldBuilder().create())
        {
            save = singleplayer.getWorldSave().getSaveDirectory();
            TestServerContext server = singleplayer.getServer();
            for(String command : new String[] { "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false",
                    "gamerule randomTickSpeed 0", "gamerule doFireTick false", "gamerule mobGriefing false", "weather clear", "time set 12350",
                    "gamemode spectator @a" })
            {
                server.runCommand(command);
            }
            context.runOnClient(mc -> {
                mc.options.renderDistance().set(12);
                mc.options.hideGui = true;
                mc.options.fov().set(75);
            });
            for(String area : new String[] { "-176 -176 -1 175", "0 -176 175 175" })
            {
                server.runCommand("forceload add " + area);
            }
            context.waitTicks(40);
            server.runOnServer(s -> terrain(s.overworld()));
            context.waitTicks(10);
            server.runOnServer(s -> {
                ServerLevel level = s.overworld();
                rocks(level);
                plants(level);
                palms(level);
                reef(level);
                chamber(level);
                bunker(level);
                ship(level);
                lab(level);
            });
            context.waitTicks(40);

            shot(context, server, "island", -55, -36, 48, -140, 15);
            shot(context, server, "island_low", -36, -48.5, 6, -95, 2);
            shot(context, server, "beach", cameraX() + 1.5, -48.4, 10.5, -160, 8);
            shot(context, server, "hill", -14, -44, -2, -110, 10);
            shot(context, server, "bunker", -11.5, padHeight() + 2.6, -9.5, -90, 5);
            shot(context, server, "chamber", 5.5, CHAMBER_FLOOR + 2.6, 5.5, 135, 12);
            shot(context, server, "ship", 10, -43, -22, 150, 12);
            shot(context, server, "ship_side", -28, -46, -48, -80, 5);
            shot(context, server, "reef", -38, -57, 31.5, -90, 8);
            shot(context, server, "lab", 20, -40, 26, -135, 15);
            shot(context, server, "lab_bridge", 24, -46.4, 1.5, -90, 3);
            shot(context, server, "lab_corridor", LAB_X + 1.5, LAB_Y + 2.6, 1.5, -90, 5);
            for(int i = 0; i < 4; i++)
            {
                shot(context, server, "lab_room" + (i + 1), labRoom(i).getX() + 5.5, LAB_Y + 3.2, -3.2, 180, 18);
            }

            //Leave it as a map to play in: creative, on the beach, the sun going down.
            server.runCommand("forceload remove all");
            int cx = cameraX();
            server.runCommand(String.format(Locale.ROOT, "setworldspawn %d %d 6", cx + 3, top(cx + 3, 6) + 1));
            server.runCommand("defaultgamemode creative");
            server.runCommand("gamemode creative @a");
            server.runCommand(String.format(Locale.ROOT, "tp @a %d %d 6 -90 0", cx + 3, top(cx + 3, 6) + 1));
            context.runOnClient(mc -> mc.options.hideGui = false);
            context.waitTicks(40);
        }
        copySave(save);
    }

    private static void shot(ClientGameTestContext context, TestServerContext server, String name, double x, double y, double z, float yaw, float pitch)
    {
        server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
        context.waitTicks(40);
        LOGGER.info("GLASS showcase: {}", context.takeScreenshot(TestScreenshotOptions.of("showcase_" + name)));
    }

    private static void copySave(Path save)
    {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while(root != null && !Files.exists(root.resolve("build.gradle")))
        {
            root = root.getParent();
        }
        if(root == null)
        {
            throw new AssertionError("no project folder above " + System.getProperty("user.dir"));
        }
        Path target = root.resolve("run").resolve("saves").resolve("GLASS Showcase");
        try
        {
            if(Files.exists(target))
            {
                try(Stream<Path> old = Files.walk(target))
                {
                    old.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                }
            }
            try(Stream<Path> files = Files.walk(save))
            {
                for(Path file : (Iterable<Path>)files::iterator)
                {
                    Path to = target.resolve(save.relativize(file).toString());
                    if(Files.isDirectory(file))
                    {
                        Files.createDirectories(to);
                    }
                    else if(!file.getFileName().toString().equals("session.lock"))
                    {
                        Files.copy(file, to, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            Path levelDat = target.resolve("level.dat");
            net.minecraft.nbt.CompoundTag tag = net.minecraft.nbt.NbtIo.readCompressed(levelDat, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            tag.getCompoundOrEmpty("Data").putString("LevelName", "GLASS Showcase");
            net.minecraft.nbt.NbtIo.writeCompressed(tag, levelDat);
            LOGGER.info("GLASS showcase: world saved to {}", target);
        }
        catch(IOException e)
        {
            throw new AssertionError("could not copy the showcase world to " + target, e);
        }
    }

    // ---- Helpers ----

    private static void set(ServerLevel level, int x, int y, int z, BlockState state)
    {
        level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state)
    {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    private static void fill(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1, BlockState state)
    {
        for(int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
        {
            for(int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
            {
                for(int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++)
                {
                    set(level, x, y, z, state);
                }
            }
        }
    }

    private static boolean isAir(ServerLevel level, int x, int y, int z)
    {
        return level.getBlockState(new BlockPos(x, y, z)).isAir();
    }

    /** A steady pseudo-random number in [0, 1) for a spot. */
    static double rand(int x, int z, int salt)
    {
        long h = x * 73856093L ^ z * 19349663L ^ salt * 83492791L;
        h = (h ^ (h >>> 13)) * 0x5bd1e995L;
        h ^= h >>> 15;
        return (h & 0xFFFFFF) / (double)0x1000000;
    }

    /** Smooth value noise in [-1, 1]. */
    static double noise(double x, double z, int salt)
    {
        int xi = Mth.floor(x), zi = Mth.floor(z);
        double fx = x - xi, fz = z - zi;
        double sx = fx * fx * (3D - 2D * fx), sz = fz * fz * (3D - 2D * fz);
        double a = rand(xi, zi, salt), b = rand(xi + 1, zi, salt), c = rand(xi, zi + 1, salt), d = rand(xi + 1, zi + 1, salt);
        return Mth.lerp(sz, Mth.lerp(sx, a, b), Mth.lerp(sx, c, d)) * 2D - 1D;
    }

    private static BlockState leaves(Block block)
    {
        return block.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    }

    private static BlockState stairs(Block block, Direction facing, Half half)
    {
        return block.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, half);
    }

    private static BlockState slab(Block block, SlabType type)
    {
        return block.defaultBlockState().setValue(SlabBlock.TYPE, type);
    }

    private static void tallPlant(ServerLevel level, int x, int y, int z, Block block)
    {
        set(level, x, y, z, block.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
        set(level, x, y + 1, z, block.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
    }

    // ---- Terrain ----

    /** How far out the island's shore is at an angle (a lumpy circle). */
    static double shore(double angle)
    {
        return 27D + 4D * Math.sin(angle * 3D + 0.6D) + 2.5D * Math.cos(angle * 5D) + 1.3D * Math.sin(angle * 9D + 1.7D);
    }

    static double reach(int x, int z)
    {
        return Math.sqrt(x * x + z * z) / shore(Math.atan2(z, x));
    }

    /** The island's land top at a column, before the bunker's pad is flattened (SEA or below: water). */
    static int naturalTop(int x, int z)
    {
        double d = reach(x, z) + 0.05D * noise(x * 0.25D, z * 0.25D, 3);
        double hx = x - 9D, hz = z + 8D;
        double hill = Math.max(0D, 1D - Math.sqrt(hx * hx + hz * hz) / (12D + 2D * noise(x * 0.15D, z * 0.15D, 5)));
        hill = hill * hill * (3D - 2D * hill);
        if(reef(x, z) && d >= 1D)
        {
            return FLOOR + 1;
        }
        if(d >= 1.45D)
        {
            return FLOOR;
        }
        if(d >= 1D)
        {
            return (int)Math.round(Mth.lerp(Math.pow((d - 1D) / 0.45D, 0.7D), SEA - 1D, FLOOR));
        }
        double land = 4D * Math.pow(Math.min(1D, (1D - d) / 0.7D), 1.3D) + 0.8D * noise(x * 0.2D, z * 0.2D, 7);
        return SEA + 1 + (int)Math.round(Math.max(0D, land) + 10D * hill);
    }

    /** The flat pad at the foot of the hill where the bunker stands, with room in front of it. */
    static boolean pad(int x, int z)
    {
        return x >= -6 && x <= 3 && z >= -14 && z <= -4;
    }

    static int padHeight()
    {
        return naturalTop(-5, -9);
    }

    static int top(int x, int z)
    {
        return pad(x, z) ? padHeight() : naturalTop(x, z);
    }

    static boolean beach(int x, int z)
    {
        return reach(x, z) > 0.78D + 0.05D * noise(x * 0.3D, z * 0.3D, 11);
    }

    /** The coral reef, south-west of the island, on a raised sand bank. */
    static boolean reef(int x, int z)
    {
        return x >= -40 && x <= -20 && z >= 28 && z <= 44;
    }

    /** The westmost land on the island's centre line (the beach spawn is just inland of it). */
    static int cameraX()
    {
        int x = -45;
        while(top(x, 2) <= SEA)
        {
            x++;
        }
        return x + 3;
    }

    static void terrain(ServerLevel level)
    {
        BlockState water = Blocks.WATER.defaultBlockState();
        for(int x = -176; x < 176; x++)
        {
            for(int z = -176; z < 176; z++)
            {
                int top = Math.abs(x) < 50 && Math.abs(z) < 50 ? top(x, z) : FLOOR;
                set(level, x, FLOOR - 1, z, Blocks.SANDSTONE.defaultBlockState());
                boolean sandy = top <= SEA + 1 || beach(x, z);
                //How steep it is here: steep slopes show their rock.
                int steep = 0;
                if(!sandy)
                {
                    for(int[] n : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } })
                    {
                        steep = Math.max(steep, top - top(x + n[0], z + n[1]));
                    }
                }
                for(int y = FLOOR; y <= Math.max(top, SEA); y++)
                {
                    BlockState state;
                    if(y > top)
                    {
                        state = water;
                    }
                    else if(sandy)
                    {
                        state = y > top - 3 ? (top < SEA - 3 && rand(x, z, 13) < 0.15D ? Blocks.GRAVEL.defaultBlockState() : Blocks.SAND.defaultBlockState())
                                : Blocks.SANDSTONE.defaultBlockState();
                    }
                    else if(y == top)
                    {
                        state = steep >= 2 ? (rand(x, z, 17) < 0.5D ? Blocks.ANDESITE.defaultBlockState() : Blocks.STONE.defaultBlockState())
                                : noise(x * 0.3D, z * 0.3D, 19) > 0.55D ? Blocks.COARSE_DIRT.defaultBlockState()
                                : noise(x * 0.25D, z * 0.25D, 23) > 0.6D ? Blocks.PODZOL.defaultBlockState()
                                : Blocks.GRASS_BLOCK.defaultBlockState();
                    }
                    else
                    {
                        state = y > top - 3 && steep < 2 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState();
                    }
                    set(level, x, y, z, state);
                }
                for(int y = Math.max(top, SEA) + 1; y <= SEA + 30; y++)
                {
                    if(!isAir(level, x, y, z))
                    {
                        set(level, x, y, z, AIR);
                    }
                }
            }
        }
    }

    /** Boulders on the beach and in the shallows. */
    static void rocks(ServerLevel level)
    {
        int[][] boulders = { { -22, -14, 2 }, { 24, 12, 2 }, { -6, 27, 1 }, { 18, -22, 2 }, { -27, 8, 1 }, { 14, 22, 1 }, { 22, -4, 1 } };
        Block[] stones = { Blocks.STONE, Blocks.ANDESITE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE };
        for(int[] b : boulders)
        {
            int r = b[2];
            int base = Math.max(top(b[0], b[1]), SEA - 1);
            for(int dx = -r - 1; dx <= r + 1; dx++)
            {
                for(int dz = -r - 1; dz <= r + 1; dz++)
                {
                    for(int dy = 0; dy <= r; dy++)
                    {
                        double dist = Math.sqrt(dx * dx + dz * dz + dy * dy * 1.6D);
                        if(dist <= r + 0.6D + 0.4D * rand(b[0] + dx, b[1] + dz, dy))
                        {
                            set(level, b[0] + dx, base + dy, b[1] + dz, stones[(int)(rand(b[0] + dx, b[1] + dz, 41 + dy) * stones.length)].defaultBlockState());
                        }
                    }
                }
            }
        }
    }

    /** Grass, ferns, flowers, bushes, sugar cane, bamboo and lily pads; seagrass under the water. */
    static void plants(ServerLevel level)
    {
        Block[] flowers = { Blocks.POPPY, Blocks.DANDELION, Blocks.BLUE_ORCHID, Blocks.ALLIUM, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER };
        for(int x = -40; x <= 40; x++)
        {
            for(int z = -40; z <= 40; z++)
            {
                int top = top(x, z);
                BlockState ground = level.getBlockState(new BlockPos(x, top, z));
                double r = rand(x, z, 31);
                if(top > SEA && !pad(x, z) && isAir(level, x, top + 1, z))
                {
                    if(ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.PODZOL) || ground.is(Blocks.COARSE_DIRT))
                    {
                        if(r < 0.22D)
                        {
                            set(level, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                        }
                        else if(r < 0.30D)
                        {
                            set(level, x, top + 1, z, Blocks.FERN.defaultBlockState());
                        }
                        else if(r < 0.34D && isAir(level, x, top + 2, z))
                        {
                            tallPlant(level, x, top + 1, z, r < 0.32D ? Blocks.TALL_GRASS : Blocks.LARGE_FERN);
                        }
                        else if(r < 0.37D && noise(x * 0.2D, z * 0.2D, 37) > 0.2D)
                        {
                            set(level, x, top + 1, z, flowers[(int)(rand(x, z, 43) * flowers.length)].defaultBlockState());
                        }
                    }
                    else if(ground.is(Blocks.SAND) && top == SEA + 1 && r < 0.08D)
                    {
                        //Sugar cane where the sand meets the water.
                        boolean water = false;
                        for(int[] n : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } })
                        {
                            water |= level.getBlockState(new BlockPos(x + n[0], top, z + n[1])).is(Blocks.WATER);
                        }
                        if(water && Math.abs(z) > 5)
                        {
                            for(int h = 1; h <= 2 + (int)(r * 30D) % 2; h++)
                            {
                                set(level, x, top + h, z, Blocks.SUGAR_CANE.defaultBlockState());
                            }
                        }
                    }
                }
                else if(top < SEA - 1 && !reef(x, z))
                {
                    if(r < 0.12D)
                    {
                        set(level, x, top + 1, z, Blocks.SEAGRASS.defaultBlockState());
                    }
                    else if(r < 0.15D && top < SEA - 2)
                    {
                        set(level, x, top + 1, z, Blocks.TALL_SEAGRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
                        set(level, x, top + 2, z, Blocks.TALL_SEAGRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
                    }
                }
                if(top <= SEA - 1 && top >= SEA - 3 && rand(x, z, 47) < 0.025D && isAir(level, x, SEA + 1, z))
                {
                    set(level, x, SEA + 1, z, Blocks.LILY_PAD.defaultBlockState());
                }
            }
        }
        //Bushes: a stub of log in a clump of leaves.
        int[][] bushes = { { -15, 9 }, { 3, -20 }, { 16, 9 }, { -12, -17 }, { 9, 4 }, { -2, 9 }, { 12, -18 }, { -18, -4 }, { 4, 16 } };
        for(int[] b : bushes)
        {
            int y = top(b[0], b[1]) + 1;
            set(level, b[0], y, b[1], Blocks.JUNGLE_LOG.defaultBlockState());
            for(int dx = -2; dx <= 2; dx++)
            {
                for(int dz = -2; dz <= 2; dz++)
                {
                    for(int dy = 0; dy <= 1; dy++)
                    {
                        if(Math.abs(dx) + Math.abs(dz) + dy * 2 <= 3 && rand(b[0] + dx, b[1] + dz, dy) < 0.85D && isAir(level, b[0] + dx, y + dy, b[1] + dz))
                        {
                            set(level, b[0] + dx, y + dy, b[1] + dz, leaves(Blocks.JUNGLE_LEAVES));
                        }
                    }
                }
            }
        }
        //A clump of bamboo.
        for(int i = 0; i < 9; i++)
        {
            int x = 1 + (int)(rand(i, 0, 51) * 4D), z = 2 + (int)(rand(i, 1, 51) * 4D);
            int y = top(x, z);
            if(isAir(level, x, y + 1, z))
            {
                set(level, x, y, z, Blocks.PODZOL.defaultBlockState());
                int height = 5 + (int)(rand(i, 2, 51) * 5D);
                for(int h = 1; h <= height; h++)
                {
                    set(level, x, y + h, z, Blocks.BAMBOO.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.BambooStalkBlock.LEAVES,
                                    h >= height - 1 ? net.minecraft.world.level.block.state.properties.BambooLeaves.LARGE
                                            : h >= height - 3 ? net.minecraft.world.level.block.state.properties.BambooLeaves.SMALL
                                            : net.minecraft.world.level.block.state.properties.BambooLeaves.NONE));
                }
            }
        }
    }

    /** Palm trees: curved trunks leaning out, drooping fronds, coconuts under the crown. */
    static void palms(ServerLevel level)
    {
        //x, z, height, lean x, lean z
        int[][] palms = { { -19, 4, 8, -1, 0 }, { -17, 13, 7, -1, 1 }, { -8, 20, 9, 0, 1 }, { 6, 21, 8, 1, 1 }, { 18, 14, 7, 1, 0 },
                { 21, 0, 9, 1, 0 }, { -21, -8, 8, -1, -1 }, { -9, -22, 7, 0, -1 }, { 19, -12, 6, 1, -1 }, { -3, 14, 10, 0, 1 }, { 11, 15, 6, 1, 1 } };
        for(int[] p : palms)
        {
            palm(level, p[0], top(p[0], p[1]) + 1, p[1], p[2], p[3], p[4]);
        }
    }

    static void palm(ServerLevel level, int x, int y, int z, int height, int lx, int lz)
    {
        BlockState log = Blocks.JUNGLE_LOG.defaultBlockState();
        int tx = x, tz = z;
        for(int i = 0; i < height; i++)
        {
            double bend = Math.pow(i / (double)height, 2D) * 3D;
            tx = x + (int)Math.round(lx * bend);
            tz = z + (int)Math.round(lz * bend);
            set(level, tx, y + i, tz, log);
        }
        int cy = y + height;
        set(level, tx, cy, tz, leaves(Blocks.JUNGLE_LEAVES));
        set(level, tx, cy + 1, tz, leaves(Blocks.JUNGLE_LEAVES));
        //Eight fronds, out and down.
        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { -1, -1 }, { 1, -1 }, { -1, 1 } };
        for(int[] d : dirs)
        {
            boolean diagonal = d[0] != 0 && d[1] != 0;
            int length = diagonal ? 3 : 4;
            for(int k = 1; k <= length; k++)
            {
                int dy = k <= 1 ? 1 : k == 2 ? 0 : -1;
                if(k == length && !diagonal)
                {
                    dy = -2;
                }
                set(level, tx + d[0] * k, cy + dy, tz + d[1] * k, leaves(Blocks.JUNGLE_LEAVES));
                if(k == length - 1 && !diagonal)
                {
                    set(level, tx + d[0] * k, cy + dy - 1, tz + d[1] * k, leaves(Blocks.JUNGLE_LEAVES));
                }
            }
        }
        //Coconuts on the trunk under the crown.
        for(Direction side : new Direction[] { Direction.NORTH, Direction.EAST })
        {
            BlockPos at = new BlockPos(tx, cy - 1, tz).relative(side);
            if(level.getBlockState(at).isAir())
            {
                set(level, at, Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING, side.getOpposite()).setValue(CocoaBlock.AGE, 2));
            }
        }
    }

    /** The coral reef on its sand bank. */
    static void reef(ServerLevel level)
    {
        Block[] corals = { Blocks.BRAIN_CORAL_BLOCK, Blocks.TUBE_CORAL_BLOCK, Blocks.FIRE_CORAL_BLOCK, Blocks.BUBBLE_CORAL_BLOCK, Blocks.HORN_CORAL_BLOCK };
        Block[] plants = { Blocks.BRAIN_CORAL, Blocks.TUBE_CORAL, Blocks.FIRE_CORAL, Blocks.BUBBLE_CORAL, Blocks.HORN_CORAL,
                Blocks.BRAIN_CORAL_FAN, Blocks.TUBE_CORAL_FAN, Blocks.FIRE_CORAL_FAN, Blocks.BUBBLE_CORAL_FAN, Blocks.HORN_CORAL_FAN, Blocks.SEAGRASS };
        for(int x = -39; x <= -21; x++)
        {
            for(int z = 29; z <= 43; z++)
            {
                double n = noise(x * 0.45D, z * 0.45D, 61) + 0.6D * noise(x * 1.1D, z * 1.1D, 67);
                int y = FLOOR + 2;
                if(n > 0.55D)
                {
                    int height = n > 1.05D ? 3 : n > 0.8D ? 2 : 1;
                    for(int i = 0; i < height; i++)
                    {
                        set(level, x, y + i, z, corals[(int)(rand(x, z, 71 + i) * corals.length)].defaultBlockState());
                    }
                    set(level, x, y + height, z, plants[(int)(rand(x, z, 73) * plants.length)].defaultBlockState());
                }
                else if(n > -0.3D)
                {
                    set(level, x, y, z, plants[(int)(rand(x, z, 79) * plants.length)].defaultBlockState());
                }
                else if(rand(x, z, 83) < 0.3D)
                {
                    set(level, x, y, z, Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 1 + (int)(rand(x, z, 89) * 4D)));
                }
            }
        }
        for(int[] kelp : new int[][] { { -38, 30 }, { -22, 42 }, { -27, 44 }, { -39, 39 }, { -33, 28 } })
        {
            int height = SEA - 2 - (int)(rand(kelp[0], kelp[1], 97) * 3D);
            for(int y = FLOOR + 2; y < height; y++)
            {
                set(level, kelp[0], y, kelp[1], Blocks.KELP_PLANT.defaultBlockState());
            }
            set(level, kelp[0], height, kelp[1], Blocks.KELP.defaultBlockState());
        }
    }

    // ---- Under the island ----

    /**
     * The chamber under the island: an empty, clean room thirteen wide inside and seven high, with a podium in the
     * middle; a doorway on its north side to the passage from the bunker.
     */
    static void chamber(ServerLevel level)
    {
        int r = 6, y0 = CHAMBER_FLOOR, y1 = y0 + 8;
        fill(level, -r - 1, y0, -r - 1, r + 1, y1, r + 1, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        fill(level, -r, y0 + 1, -r, r, y1 - 1, r, AIR);
        fill(level, -r, y0, -r, r, y0, r, Blocks.DEEPSLATE_TILES.defaultBlockState());
        //Trim: a band of polished blackstone along the floor and the ceiling.
        for(int x = -r; x <= r; x++)
        {
            for(int z : new int[] { -r - 1, r + 1 })
            {
                set(level, x, y0 + 1, z, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                set(level, x, y1 - 1, z, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                set(level, z, y0 + 1, x, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                set(level, z, y1 - 1, x, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
            }
        }
        //The podium: a step of slabs round a block of chiseled blackstone, a polished block on top.
        for(int dx = -1; dx <= 1; dx++)
        {
            for(int dz = -1; dz <= 1; dz++)
            {
                set(level, dx, y0 + 1, dz, dx == 0 && dz == 0 ? Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState()
                        : slab(Blocks.POLISHED_BLACKSTONE_SLAB, SlabType.BOTTOM));
            }
        }
        set(level, 0, y0 + 2, 0, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        //Light without lamps in view.
        for(int[] l : new int[][] { { 3, 3 }, { -3, 3 }, { 3, -3 }, { -3, -3 }, { 0, 0 } })
        {
            set(level, l[0], y1 - 2, l[1], Blocks.LIGHT.defaultBlockState());
        }
        //The doorway north, to the passage.
        fill(level, 0, y0 + 1, -r - 1, 0, y0 + 2, -r - 1, AIR);
    }

    /**
     * The bunker at the foot of the hill: deepslate walls, an iron door facing the beach, a hatch and a ladder down to
     * the chamber. In front of it the pad stays clear (for a wall of glass).
     */
    static void bunker(ServerLevel level)
    {
        int h = padHeight();
        int x0 = -1, x1 = 2, z0 = -11, z1 = -7;
        fill(level, x0, h + 1, z0, x1, h + 3, z1, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
        fill(level, x0 + 1, h + 1, z0 + 1, x1 - 1, h + 3, z1 - 1, AIR);
        fill(level, x0 - 1, h + 4, z0 - 1, x1 + 1, h + 4, z1 + 1, slab(Blocks.DEEPSLATE_TILE_SLAB, SlabType.BOTTOM));
        fill(level, x0, h + 4, z0, x1, h + 4, z1, Blocks.DEEPSLATE_TILES.defaultBlockState());
        for(int[] c : new int[][] { { x0, z0 }, { x1, z0 }, { x0, z1 }, { x1, z1 } })
        {
            fill(level, c[0], h + 1, c[1], c[0], h + 3, c[1], Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        }
        //The door, facing west to the beach, a lamp beside it.
        int dz = -9;
        set(level, x0, h + 1, dz, Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.WEST).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(level, x0, h + 2, dz, Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.WEST).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        set(level, x0 - 1, h + 1, dz - 1, Blocks.STONE_BUTTON.defaultBlockState()
                .setValue(net.minecraft.world.level.block.ButtonBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL)
                .setValue(net.minecraft.world.level.block.ButtonBlock.FACING, Direction.WEST));
        set(level, x0 - 1, h + 3, dz + 1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        set(level, x1 - 1, h + 3, dz, Blocks.SEA_LANTERN.defaultBlockState());
        //The hatch and the shaft down.
        int sx = 0, sz = -10;
        set(level, sx, h + 1, sz, Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.BOTTOM).setValue(TrapDoorBlock.FACING, Direction.SOUTH));
        for(int y = CHAMBER_FLOOR + 1; y <= h; y++)
        {
            set(level, sx, y, sz - 1, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            set(level, sx - 1, y, sz, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            set(level, sx + 1, y, sz, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            set(level, sx, y, sz, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        }
        //The passage from the foot of the shaft to the chamber's doorway.
        fill(level, -1, CHAMBER_FLOOR, sz + 1, 1, CHAMBER_FLOOR + 3, -8, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        fill(level, 0, CHAMBER_FLOOR + 1, sz + 1, 0, CHAMBER_FLOOR + 2, -8, AIR);
        set(level, 0, CHAMBER_FLOOR + 3, sz + 1, Blocks.SEA_LANTERN.defaultBlockState());
        set(level, sx, CHAMBER_FLOOR, sz, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
    }

    // ---- The ship ----

    /** The four mooring posts round the ship (tops at the water's level). */
    static final int[][] POSTS = { { -18, SHIP_Z - 6 }, { 18, SHIP_Z - 6 }, { -18, SHIP_Z + 6 }, { 18, SHIP_Z + 6 } };

    /** Half the ship's beam at a spot along it (bow at +x). */
    static int beam(int x)
    {
        if(x > 8)
        {
            return Math.max(0, 3 - (x - 8) * 3 / 6);
        }
        return x < -11 ? 2 : 3;
    }

    static void ship(ServerLevel level)
    {
        int zc = SHIP_Z;
        BlockState hull = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState deck = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState trim = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        //The hull lies in the water's top layer (nothing under it), the deck above.
        for(int x = -13; x <= 14; x++)
        {
            int w = beam(x);
            for(int dz = -w; dz <= w; dz++)
            {
                int z = zc + dz;
                boolean side = Math.abs(dz) == w || x == -13 || x == 14;
                set(level, x, SEA, z, hull);
                set(level, x, SEA + 1, z, side ? trim : deck);
                if(side)
                {
                    set(level, x, SEA + 2, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
                }
            }
        }
        //The bowsprit.
        for(int x = 15; x <= 18; x++)
        {
            set(level, x, SEA + 2 + (x - 15) / 2, zc, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        }
        //Masts with yards and sails, a crow's nest on the main one.
        int[][] masts = { { 1, 14, 5 }, { 8, 10, 4 }, { -6, 11, 4 } };
        for(int[] mast : masts)
        {
            int mx = mast[0], height = mast[1], half = mast[2] / 2 + 1;
            for(int y = SEA + 2; y <= SEA + 1 + height; y++)
            {
                set(level, mx, y, zc, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
            }
            int yard = SEA + height - 1;
            for(int dz = -half; dz <= half; dz++)
            {
                set(level, mx, yard, zc + dz, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
            }
            //A sail hanging from it, bellied out towards the bow.
            for(int y = yard - 1; y >= yard - mast[2] - 2; y--)
            {
                for(int dz = -half + 1; dz <= half - 1; dz++)
                {
                    int belly = Math.abs(dz) < half - 1 && y < yard - 1 && y > yard - mast[2] - 2 ? 1 : 0;
                    set(level, mx + 1 + belly, y, zc + dz, Blocks.WHITE_WOOL.defaultBlockState());
                }
            }
            set(level, mx, SEA + 2 + height, zc, Blocks.RED_WOOL.defaultBlockState());
        }
        int nest = SEA + 1 + 14 - 3;
        for(int dx = -1; dx <= 1; dx++)
        {
            for(int dz = -1; dz <= 1; dz++)
            {
                if(dx != 0 || dz != 0)
                {
                    set(level, 1 + dx, nest, zc + dz, slab(Blocks.SPRUCE_SLAB, SlabType.TOP));
                    set(level, 1 + dx, nest + 1, zc + dz, Blocks.SPRUCE_FENCE.defaultBlockState());
                }
            }
        }
        //The stern castle: a cabin with windows and a door, a railed deck on top.
        int s0 = -13, s1 = -9;
        fill(level, s0, SEA + 2, zc - 2, s1, SEA + 4, zc + 2, Blocks.SPRUCE_PLANKS.defaultBlockState());
        fill(level, s0 + 1, SEA + 2, zc - 1, s1 - 1, SEA + 3, zc + 1, AIR);
        for(int x = s0; x <= s1; x++)
        {
            for(int side : new int[] { -2, 2 })
            {
                set(level, x, SEA + 2, zc + side, Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
            }
        }
        set(level, s1, SEA + 3, zc - 1, Blocks.GLASS_PANE.defaultBlockState());
        set(level, s1, SEA + 3, zc + 1, Blocks.GLASS_PANE.defaultBlockState());
        set(level, s1, SEA + 2, zc, Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(level, s1, SEA + 3, zc, Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        set(level, s0 + 1, SEA + 3, zc, Blocks.LANTERN.defaultBlockState());
        set(level, s0 + 2, SEA + 2, zc + 1, Blocks.BARREL.defaultBlockState());
        fill(level, s0, SEA + 5, zc - 2, s1, SEA + 5, zc - 2, Blocks.DARK_OAK_FENCE.defaultBlockState());
        fill(level, s0, SEA + 5, zc + 2, s1, SEA + 5, zc + 2, Blocks.DARK_OAK_FENCE.defaultBlockState());
        fill(level, s0, SEA + 5, zc - 2, s0, SEA + 5, zc + 2, Blocks.DARK_OAK_FENCE.defaultBlockState());
        set(level, s1 + 1, SEA + 2, zc + 2, stairs(Blocks.SPRUCE_STAIRS, Direction.WEST, Half.BOTTOM));
        //Cargo, lamps and the anchor chain.
        for(int[] cargo : new int[][] { { 4, 1 }, { 4, -1 }, { 5, 1 }, { -3, 2 }, { 11, 0 } })
        {
            set(level, cargo[0], SEA + 2, zc + cargo[1], cargo[0] == 11 ? Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST)
                    : Blocks.BARREL.defaultBlockState());
        }
        set(level, -3, SEA + 3, zc + 2, Blocks.LANTERN.defaultBlockState());
        set(level, 12, SEA + 3, zc, Blocks.LANTERN.defaultBlockState());
        for(int y = FLOOR + 1; y <= SEA + 1; y++)
        {
            set(level, 13, y, zc + 3, Blocks.CHAIN.defaultBlockState());
        }
        set(level, 13, FLOOR + 1, zc + 3, Blocks.ANVIL.defaultBlockState());
        //Mooring posts round the ship, tops at the water's level.
        for(int[] c : POSTS)
        {
            for(int y = FLOOR; y <= SEA; y++)
            {
                set(level, c[0], y, c[1], Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState());
            }
        }
        //The jetty from the north beach out towards the ship.
        int endZ = SHIP_Z + 11;
        int shoreZ = -50;
        while(top(0, shoreZ) <= SEA)
        {
            shoreZ++;
        }
        for(int z = endZ; z <= shoreZ; z++)
        {
            for(int x = -1; x <= 1; x++)
            {
                set(level, x, SEA + 1, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
            }
            set(level, -2, SEA + 1, z, slab(Blocks.SPRUCE_SLAB, SlabType.TOP));
            set(level, 2, SEA + 1, z, slab(Blocks.SPRUCE_SLAB, SlabType.TOP));
            if((z - endZ) % 4 == 0)
            {
                for(int x : new int[] { -2, 2 })
                {
                    for(int y = FLOOR; y <= SEA + 2; y++)
                    {
                        set(level, x, y, z, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
                    }
                    set(level, x, SEA + 3, z, Blocks.LANTERN.defaultBlockState());
                }
            }
        }
    }

    // ---- The research station ----

    /** The north-west inside corner of lab room {@code i} (rooms 11x11 inside, west to east). */
    static BlockPos labRoom(int i)
    {
        return new BlockPos(LAB_X + i * 12, LAB_Y, -13);
    }

    /** A research station on stilts east of the island: a corridor along the south, four rooms north of it, a bridge. */
    static void lab(ServerLevel level)
    {
        BlockState wall = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState pilaster = Blocks.POLISHED_BASALT.defaultBlockState();
        BlockState light = Blocks.SEA_LANTERN.defaultBlockState();
        int x0 = LAB_X - 1, x1 = LAB_X + 4 * 12 - 1, z0 = -14, z1 = 3, top = LAB_Y + 7;
        //Stilts on a footing under the water, a beam under the floor.
        for(int x = x0; x <= x1; x += 6)
        {
            for(int z : new int[] { z0, -5, z1 })
            {
                fill(level, x, FLOOR, z, x, LAB_Y - 1, z, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
                set(level, x, LAB_Y - 2, z, Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
            }
        }
        fill(level, x0, LAB_Y - 1, z0, x1, LAB_Y - 1, z1, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        //Shell: floor, walls with pilasters, roof with an overhang.
        fill(level, x0, LAB_Y, z0, x1, LAB_Y, z1, Blocks.DEEPSLATE_TILES.defaultBlockState());
        fill(level, x0, LAB_Y + 1, z0, x1, top - 1, z1, wall);
        fill(level, x0 - 1, top, z0 - 1, x1 + 1, top, z1 + 1, slab(Blocks.POLISHED_BLACKSTONE_SLAB, SlabType.BOTTOM));
        fill(level, x0, top, z0, x1, top, z1, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        fill(level, x0 + 1, LAB_Y + 1, z0 + 1, x1 - 1, top - 1, z1 - 1, AIR);
        for(int x = x0; x <= x1; x += 6)
        {
            fill(level, x, LAB_Y + 1, z0, x, top - 1, z0, pilaster);
            fill(level, x, LAB_Y + 1, z1, x, top - 1, z1, pilaster);
        }
        for(int z = z0; z <= z1; z += 6)
        {
            fill(level, x1, LAB_Y + 1, z, x1, top - 1, z, pilaster);
            fill(level, x0, LAB_Y + 1, z, x0, top - 1, z, pilaster);
        }
        //A band of windows round the outside, a rim of light under the roof.
        for(int x = x0 + 1; x < x1; x++)
        {
            if((x - x0) % 6 != 0)
            {
                for(int z : new int[] { z0, z1 })
                {
                    set(level, x, LAB_Y + 3, z, Blocks.CYAN_STAINED_GLASS.defaultBlockState());
                    set(level, x, LAB_Y + 4, z, Blocks.CYAN_STAINED_GLASS.defaultBlockState());
                }
            }
            if((x - x0) % 3 == 0)
            {
                set(level, x, top - 1, z0, light);
                set(level, x, top - 1, z1, light);
            }
        }
        for(int z = z0 + 1; z < z1; z++)
        {
            if((z - z0) % 6 != 0)
            {
                set(level, x1, LAB_Y + 3, z, Blocks.CYAN_STAINED_GLASS.defaultBlockState());
                set(level, x1, LAB_Y + 4, z, Blocks.CYAN_STAINED_GLASS.defaultBlockState());
            }
        }
        //Inside: the wall between the corridor and the rooms, and between the rooms.
        fill(level, x0 + 1, LAB_Y + 1, -2, x1 - 1, top - 1, -2, wall);
        for(int i = 1; i < 4; i++)
        {
            int x = LAB_X + i * 12 - 1;
            fill(level, x, LAB_Y + 1, z0 + 1, x, top - 1, -3, wall);
        }
        //The corridor: a border round the floor, a strip of light down its middle, lamps above.
        for(int x = x0 + 1; x < x1; x++)
        {
            set(level, x, LAB_Y, -1, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            set(level, x, LAB_Y, 2, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            set(level, x, LAB_Y, 0, (x - x0) % 4 == 2 ? light : Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            set(level, x, LAB_Y, 1, (x - x0) % 4 == 2 ? light : Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            if((x - x0) % 4 == 0)
            {
                set(level, x, top, 0, light);
                set(level, x, top, 1, light);
            }
        }
        for(int i = 0; i < 4; i++)
        {
            BlockPos room = labRoom(i);
            int rx = room.getX(), rz = room.getZ();
            //A floor of tiles with a border, light panels in the roof (and light that does not show).
            for(int x = rx; x <= rx + 10; x++)
            {
                for(int z = rz; z <= rz + 10; z++)
                {
                    boolean border = x == rx || x == rx + 10 || z == rz || z == rz + 10;
                    set(level, x, LAB_Y, z, border ? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState());
                }
            }
            for(int[] l : new int[][] { { 2, 2 }, { 8, 2 }, { 2, 8 }, { 8, 8 }, { 5, 5 } })
            {
                set(level, rx + l[0], top, rz + l[1], light);
                set(level, rx + l[0], LAB_Y + 5, rz + l[1], Blocks.LIGHT.defaultBlockState());
            }
            //A framed doorway from the corridor.
            fill(level, rx + 4, LAB_Y + 1, -2, rx + 6, LAB_Y + 3, -2, AIR);
            fill(level, rx + 3, LAB_Y + 1, -2, rx + 3, LAB_Y + 4, -2, pilaster);
            fill(level, rx + 7, LAB_Y + 1, -2, rx + 7, LAB_Y + 4, -2, pilaster);
            fill(level, rx + 4, LAB_Y + 4, -2, rx + 6, LAB_Y + 4, -2, Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
            set(level, rx + 5, LAB_Y + 5, -1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }
        //Light in the corridor too.
        for(int x = x0 + 4; x < x1; x += 8)
        {
            set(level, x, LAB_Y + 5, 0, Blocks.LIGHT.defaultBlockState());
        }
        //The way in at the west end, and the bridge from the island's east beach.
        fill(level, x0, LAB_Y + 1, 0, x0, LAB_Y + 3, 1, AIR);
        fill(level, x0, LAB_Y + 4, -1, x0, LAB_Y + 4, 2, Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
        int shoreX = 50;
        while(top(shoreX, 1) <= SEA && shoreX > 0)
        {
            shoreX--;
        }
        for(int x = shoreX; x < x0; x++)
        {
            fill(level, x, LAB_Y, 0, x, LAB_Y, 1, Blocks.DARK_OAK_PLANKS.defaultBlockState());
            set(level, x, LAB_Y, -1, slab(Blocks.DARK_OAK_SLAB, SlabType.TOP));
            set(level, x, LAB_Y, 2, slab(Blocks.DARK_OAK_SLAB, SlabType.TOP));
            set(level, x, LAB_Y + 1, -1, Blocks.DARK_OAK_FENCE.defaultBlockState());
            set(level, x, LAB_Y + 1, 2, Blocks.DARK_OAK_FENCE.defaultBlockState());
            if((x - shoreX) % 5 == 0)
            {
                for(int z : new int[] { -1, 2 })
                {
                    fill(level, x, FLOOR, z, x, LAB_Y + 1, z, Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState());
                    set(level, x, LAB_Y + 2, z, Blocks.LANTERN.defaultBlockState());
                }
            }
        }
        //Steps from the beach up onto the bridge.
        for(int x = shoreX - 3; x < shoreX; x++)
        {
            int y = Math.max(top(x, 0), top(x, 1)) + 1;
            if(y < LAB_Y)
            {
                fill(level, x, y, 0, x, LAB_Y - 1, 1, Blocks.DARK_OAK_PLANKS.defaultBlockState());
            }
            if(y <= LAB_Y)
            {
                fill(level, x, LAB_Y, 0, x, LAB_Y, 1, stairs(Blocks.DARK_OAK_STAIRS, Direction.EAST, Half.BOTTOM));
            }
        }

        lighthouseModel(level, labRoom(2));
        cosyCorner(level, labRoom(3));
    }

    /** Room 3: a little lighthouse model on a plinth (to scan into a hologram). */
    private static void lighthouseModel(ServerLevel level, BlockPos room)
    {
        int lx = room.getX() + 8, lz = room.getZ() + 2, y0 = LAB_Y + 1;
        fill(level, lx - 2, y0, lz - 2, lx + 2, y0, lz + 2, slab(Blocks.SMOOTH_STONE_SLAB, SlabType.BOTTOM));
        for(int y = 1; y <= 6; y++)
        {
            BlockState ring = y % 2 == 0 ? Blocks.RED_CONCRETE.defaultBlockState() : Blocks.WHITE_CONCRETE.defaultBlockState();
            int r = y <= 2 ? 1 : 0;
            for(int dx = -r; dx <= r; dx++)
            {
                for(int dz = -r; dz <= r; dz++)
                {
                    set(level, lx + dx, y0 + y, lz + dz, ring);
                }
            }
        }
        set(level, lx, y0 + 7, lz, Blocks.GLOWSTONE.defaultBlockState());
        set(level, lx, y0 + 8, lz, slab(Blocks.BLACKSTONE_SLAB, SlabType.BOTTOM));
    }

    /** Room 4: a cosy corner for the holo cube to scan: books, a chest, plants, a bed, a cat. */
    private static void cosyCorner(ServerLevel level, BlockPos room)
    {
        int x = room.getX(), z = room.getZ(), y = LAB_Y + 1;
        fill(level, x, y, z, x + 10, y + 2, z, Blocks.BOOKSHELF.defaultBlockState());
        for(int bx = x; bx <= x + 10; bx += 5)
        {
            fill(level, bx, y, z, bx, y + 2, z, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
        }
        fill(level, x, y + 3, z, x + 10, y + 3, z, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH, Half.TOP));
        fill(level, x + 1, LAB_Y, z + 1, x + 9, LAB_Y, z + 5, Blocks.SPRUCE_PLANKS.defaultBlockState());
        fill(level, x + 3, y, z + 2, x + 7, y, z + 4, Blocks.RED_CARPET.defaultBlockState());
        set(level, x + 1, y, z + 1, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
        if(level.getBlockEntity(new BlockPos(x + 1, y, z + 1)) instanceof ChestBlockEntity chest)
        {
            chest.setItem(0, new ItemStack(Items.DIAMOND, 12));
            chest.setItem(1, new ItemStack(Items.GOLDEN_APPLE, 3));
            chest.setItem(2, new ItemStack(Items.ENCHANTED_BOOK));
            chest.setItem(13, new ItemStack(Items.MAP));
        }
        set(level, x + 9, y, z + 1, Blocks.CRAFTING_TABLE.defaultBlockState());
        set(level, x + 8, y, z + 1, Blocks.LANTERN.defaultBlockState());
        set(level, x + 3, y, z + 1, Blocks.POTTED_FERN.defaultBlockState());
        set(level, x + 6, y, z + 1, Blocks.POTTED_RED_TULIP.defaultBlockState());
        set(level, x + 1, y, z + 4, Blocks.LECTERN.defaultBlockState());
        set(level, x + 1, y, z + 5, Blocks.POTTED_BAMBOO.defaultBlockState());
        set(level, x + 9, y, z + 3, Blocks.BLUE_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD).setValue(BedBlock.FACING, Direction.NORTH));
        set(level, x + 9, y, z + 4, Blocks.BLUE_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.FOOT).setValue(BedBlock.FACING, Direction.NORTH));
        set(level, x + 2, y, z + 4, stairs(Blocks.SPRUCE_STAIRS, Direction.EAST, Half.BOTTOM));
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput(), String.format(Locale.ROOT,
                "summon minecraft:cat %d %d %d {NoAI:1b,Silent:1b,PersistenceRequired:1b,variant:\"minecraft:black\",Rotation:[200f,0f]}", x + 5, y, z + 3));
    }
}
