package ryzlar.glass.test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import ryzlar.glass.GlassRegistry;
import ryzlar.glass.blockentity.GlassBaseBlockEntity;
import ryzlar.glass.blockentity.GlassProjectorBlockEntity;
import ryzlar.glass.blockentity.GlassTerminalBlockEntity;
import ryzlar.glass.holo.CubeScan;
import ryzlar.glass.holo.HoloCubeEntity;
import ryzlar.glass.holo.HoloNetworking;
import ryzlar.glass.holo.HoloProjectionData;
import ryzlar.glass.network.GlassNetworkData;
import ryzlar.glass.network.GlassNetworkManager;
import ryzlar.glass.network.GlassNetworking;
import ryzlar.glass.network.PasswordHasher;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Release checks on a dedicated server: what a player could abuse (passwords, other players' channels, huge scans,
 * duplicating a cube), and that breaking or blowing up a running screen leaves the world in order.
 * Run with {@code ./gradlew runGameTest}.
 */
public class GlassStressGameTest
{
    private static Component say(String text)
    {
        return Component.literal(text);
    }

    /** A channel owned by {@code owner} with password {@code password}, put straight into the world's channels. */
    private static GlassNetworkData.Channel channel(GameTestHelper helper, UUID owner, String password)
    {
        GlassNetworkData data = GlassNetworkManager.data(helper.getLevel().getServer());
        PasswordHasher.Hash hash = PasswordHasher.hash(password);
        String id = UUID.randomUUID().toString().substring(0, 8);
        GlassNetworkData.Channel channel = new GlassNetworkData.Channel(id, "stress " + id, owner, "someone", hash.salt(), hash.hash(), hash.iterations(),
                List.of(), Optional.empty());
        data.channels.put(id, channel);
        return channel;
    }

    /** Logging out and in again does not give a player fresh password guesses. */
    @GameTest
    public void passwordLimitSurvivesRelog(GameTestHelper helper)
    {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        GlassNetworkData.Channel channel = channel(helper, UUID.randomUUID(), "rightpass");
        for(int i = 0; i < 5; i++)
        {
            helper.assertTrue(!GlassNetworkManager.unlock(player, channel.id, "wrong" + i).ok(), say("a wrong password was taken"));
        }
        GlassNetworkManager.onLeave(player);
        GlassNetworkManager.Result after = GlassNetworkManager.unlock(player, channel.id, "rightpass");
        helper.assertTrue(!after.ok() && after.message().equals("glass.msg.tooManyAttempts"),
                say("after logging out and in the guesses started over: " + after));
        helper.succeed();
    }

    /** Setting passwords (slow to hash on purpose) is limited, so spamming it cannot stall the server. */
    @GameTest
    public void newPasswordsAreLimited(GameTestHelper helper)
    {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        GlassNetworkData.Channel channel = channel(helper, player.getUUID(), "firstpass");
        int changed = 0;
        GlassNetworkManager.Result last = null;
        for(int i = 0; i < 20; i++)
        {
            last = GlassNetworkManager.changePassword(player, channel.id, "newpass" + i);
            if(last.ok())
            {
                changed++;
            }
        }
        helper.assertTrue(changed == 5, say("expected 5 password changes in a minute, got " + changed));
        helper.assertTrue(last != null && last.message().equals("glass.msg.tooManyPasswords"), say("the limit has no message: " + last));
        helper.succeed();
    }

    /**
     * A wireless extension only links to a projector near it, and not to one showing a channel the player has no
     * access to (or anyone could show someone else's protected channel on their own glass).
     */
    @GameTest
    public void wirelessLinksStayNearAndPrivate(GameTestHelper helper)
    {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos projectorPos = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos wirelessPos = helper.absolutePos(new BlockPos(5, 1, 1));
        helper.setBlock(new BlockPos(1, 1, 1), GlassRegistry.GLASS_PROJECTOR);
        helper.setBlock(new BlockPos(5, 1, 1), GlassRegistry.GLASS_WIRELESS_EXTENSION);
        GlassProjectorBlockEntity projector = (GlassProjectorBlockEntity)helper.getLevel().getBlockEntity(projectorPos);

        GlassNetworkData.Channel foreign = channel(helper, UUID.randomUUID(), "theirpass");
        projector.channelId = foreign.id;
        GlassNetworkManager.Result result = GlassNetworking.linkWireless(player, wirelessPos, projectorPos);
        helper.assertTrue(result != null && !result.ok() && projector.wirelessPos.isEmpty(),
                say("linked to a projector showing someone else's protected channel: " + result));

        GlassNetworkData.Channel own = channel(helper, player.getUUID(), "mypass");
        projector.channelId = own.id;
        result = GlassNetworking.linkWireless(player, wirelessPos, projectorPos);
        helper.assertTrue(result != null && result.ok() && projector.wirelessPos.contains(wirelessPos), say("could not link to own projector: " + result));

        helper.assertTrue(GlassProjectorBlockEntity.inWirelessRange(projectorPos, projectorPos.offset(GlassProjectorBlockEntity.WIRELESS_RANGE, 0, 0)),
                say("the edge of the range is out of range"));
        helper.assertTrue(!GlassProjectorBlockEntity.inWirelessRange(projectorPos, projectorPos.offset(0, 0, GlassProjectorBlockEntity.WIRELESS_RANGE + 1)),
                say("a projector past the range counts as in range"));
        helper.succeed();
    }

    /** Wiping a cube's scan forgets the scanned blocks too, so the saved projections do not grow with every scan. */
    @GameTest
    public void wipedScanIsForgotten(GameTestHelper helper)
    {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        HoloProjectionData.Projection projection = HoloProjectionData.storeBall(helper.getLevel(), helper.absolutePos(new BlockPos(4, 2, 4)), "wipe me");
        ItemStack cube = new ItemStack(GlassRegistry.HOLO_CUBE);
        cube.set(GlassRegistry.CUBE_SCAN, new CubeScan(projection.id(), projection.name(), projection.sizeX(), projection.sizeY(), projection.sizeZ(),
                7, 7, 7, 0, CubeScan.NO_SKY, 0F, 0F, CubeScan.VERSION));
        player.setItemInHand(InteractionHand.MAIN_HAND, cube);
        player.setShiftKeyDown(true);
        cube.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getMainHandItem().get(GlassRegistry.CUBE_SCAN) == null, say("the scan was not wiped"));
        helper.assertTrue(HoloProjectionData.find(helper.getLevel().getServer(), projection.id()) == null, say("the wiped scan's blocks are still stored"));
        helper.succeed();
    }

    /** A book of {@code pages} pages of 1000 letters. */
    private static ItemStack book(int pages)
    {
        List<Filterable<String>> text = new ArrayList<>();
        for(int i = 0; i < pages; i++)
        {
            text.add(Filterable.passThrough("x".repeat(1000)));
        }
        ItemStack book = new ItemStack(Items.WRITABLE_BOOK);
        book.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(text));
        return book;
    }

    /**
     * A cube scanning a ball full of barrels stuffed with books stays small enough to send: oversized contents are
     * dropped (the barrels stay), and the packet fits, both ways.
     */
    @GameTest(maxTicks = 40)
    public void hugeScanStaysSendable(GameTestHelper helper)
    {
        int barrels = 0;
        for(int x = 0; x <= 7; x++)
        {
            for(int z = 0; z <= 7; z++)
            {
                for(int y = 1; y <= 2; y++)
                {
                    if(x == 4 && z == 4)
                    {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x, y, z);
                    helper.setBlock(pos, Blocks.BARREL);
                    if(helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof BarrelBlockEntity barrel)
                    {
                        //Every fourth barrel holds far too much on its own, the rest a lot together.
                        barrel.setItem(0, book(barrels % 4 == 0 ? 60 : 25));
                        barrels++;
                    }
                }
            }
        }
        HoloProjectionData.Projection projection = HoloProjectionData.storeBall(helper.getLevel(), helper.absolutePos(new BlockPos(4, 2, 4)), "stuffed");
        long kept = projection.blockEntities().stream().filter(tag -> tag.getStringOr("id", "").equals("minecraft:barrel")).count();
        helper.assertTrue(kept == barrels, say("barrels went missing from the scan: " + kept + " of " + barrels));
        int extras = projection.blockEntities().stream().mapToInt(CompoundTag::sizeInBytes).sum();
        helper.assertTrue(extras <= HoloProjectionData.MAX_EXTRAS_BYTES + 64 * 1024, say("the scan keeps " + extras + " bytes of contents"));

        HoloNetworking.ProjectionBlocks packet = HoloNetworking.blocksOf(projection);
        ByteBuf buffer = Unpooled.buffer();
        HoloNetworking.ProjectionBlocks.CODEC.encode(buffer, packet);
        int bytes = buffer.readableBytes();
        helper.assertTrue(bytes <= HoloNetworking.MAX_PROJECTION_BYTES, say("the projection packet is " + bytes + " bytes"));
        HoloNetworking.ProjectionBlocks back = HoloNetworking.ProjectionBlocks.CODEC.decode(buffer);
        helper.assertTrue(back.blocks().equals(packet.blocks()) && back.extras().blockEntities().size() == packet.extras().blockEntities().size(),
                say("the projection packet does not read back"));
        buffer.release();
        org.slf4j.LoggerFactory.getLogger("glass-test").info("GLASS stress: stuffed scan is {} bytes ({} of contents, {} barrels)", bytes, extras, barrels);
        helper.succeed();
    }

    /** Hitting and using a cube in the same tick hands out one cube, not two. */
    @GameTest
    public void cubeIsNotDuplicated(GameTestHelper helper)
    {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.setBlock(new BlockPos(2, 0, 2), Blocks.STONE);
        HoloCubeEntity cube = helper.spawn(GlassRegistry.HOLO_CUBE_ENTITY, new BlockPos(2, 1, 2));
        cube.setItem(new ItemStack(GlassRegistry.HOLO_CUBE));
        cube.startPickUp(player);
        cube.startPickUp(player);
        cube.startPickUp(null);
        int cubes = player.getInventory().countItem(GlassRegistry.HOLO_CUBE);
        for(ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(32D)))
        {
            if(item.getItem().is(GlassRegistry.HOLO_CUBE))
            {
                cubes += item.getItem().getCount();
            }
        }
        helper.assertTrue(cubes == 1, say("picking the cube up three times at once gave " + cubes + " cubes"));
        helper.succeed();
    }

    private static int activeGlass(GameTestHelper helper, int minX, int maxX, int minY, int maxY, int z)
    {
        int active = 0;
        for(int x = minX; x <= maxX; x++)
        {
            for(int y = minY; y <= maxY; y++)
            {
                if(helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(x, y, z))) instanceof GlassBaseBlockEntity glass && glass.active)
                {
                    active++;
                }
            }
        }
        return active;
    }

    /**
     * A running screen survives being blown up: an explosion in the glass leaves the rest in order, the camera stands a
     * blast but breaking it takes the channel offline, and taking the projector away switches every last bit of glass off.
     */
    @GameTest(maxTicks = 200)
    public void blownUpScreenCleansUp(GameTestHelper helper)
    {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        //A 5x4 wall of glass at z 6 with the projector at its bottom middle, powered from behind; the camera far off.
        for(int x = 1; x <= 5; x++)
        {
            for(int y = 1; y <= 4; y++)
            {
                helper.setBlock(new BlockPos(x, y, 6), x == 3 && y == 1 ? GlassRegistry.GLASS_PROJECTOR : GlassRegistry.GLASS_EXTENSION);
            }
        }
        helper.setBlock(new BlockPos(1, 1, 1), GlassRegistry.GLASS_TERMINAL);
        BlockPos terminalPos = helper.absolutePos(new BlockPos(1, 1, 1));
        GlassTerminalBlockEntity terminal = (GlassTerminalBlockEntity)helper.getLevel().getBlockEntity(terminalPos);
        terminal.facing = Direction.SOUTH;
        GlassNetworkManager.Result made = GlassNetworkManager.createChannel(player, terminal, "boom " + UUID.randomUUID().toString().substring(0, 6), "boompass");
        helper.assertTrue(made.ok(), say("no channel: " + made));
        String channelId = terminal.channelId;
        GlassProjectorBlockEntity projector = (GlassProjectorBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(3, 1, 6)));
        GlassNetworking.showChannel(projector, GlassNetworkManager.data(helper.getLevel().getServer()), channelId, "boom");
        helper.setBlock(new BlockPos(3, 1, 7), Blocks.REDSTONE_BLOCK);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(activeGlass(helper, 1, 5, 1, 4, 6) == 20, say("the screen did not come on: "
                        + activeGlass(helper, 1, 5, 1, 4, 6) + " of 20")))
                .thenExecute(() -> {
                    BlockPos middle = helper.absolutePos(new BlockPos(4, 3, 6));
                    helper.getLevel().explode(null, middle.getX() + 0.5D, middle.getY() + 0.5D, middle.getZ() + 0.5D, 2F, Level.ExplosionInteraction.TNT);
                    helper.getLevel().explode(null, terminalPos.getX() + 0.5D, terminalPos.getY() + 0.5D, terminalPos.getZ() + 0.5D, 2F, Level.ExplosionInteraction.TNT);
                })
                .thenExecute(() -> {
                    //The terminal is as tough as obsidian: the blast leaves it (and the channel's camera) be; breaking it does not.
                    helper.assertTrue(helper.getLevel().getBlockState(terminalPos).is(GlassRegistry.GLASS_TERMINAL), say("the blast broke the terminal"));
                    helper.getLevel().destroyBlock(terminalPos, false);
                })
                .thenWaitUntil(() -> {
                    GlassNetworkData.Channel channel = GlassNetworkManager.data(helper.getLevel().getServer()).channels.get(channelId);
                    helper.assertTrue(channel != null && channel.camera == null, say("the broken camera is still online"));
                })
                .thenExecute(() -> helper.setBlock(new BlockPos(3, 1, 6), Blocks.AIR))
                .thenWaitUntil(() -> helper.assertTrue(activeGlass(helper, 1, 5, 1, 4, 6) == 0,
                        say("glass still on without its projector: " + activeGlass(helper, 1, 5, 1, 4, 6))))
                .thenSucceed();
    }
}
