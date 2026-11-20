package ryzlar.glass.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import ryzlar.glass.Glass;
import ryzlar.glass.GlassRegistry;
import ryzlar.glass.blockentity.GlassProjectorBlockEntity;
import ryzlar.glass.holo.HoloCubeEntity;

import java.util.List;

/**
 * Tests on a dedicated server (no client code at all): the mod loads there, a holo cube scans its ball and stands in it, worlds
 * from before the holo cube was renamed still load it, and four corner extensions make a cloak field.
 * Run with {@code ./gradlew runGameTest}.
 */
public class GlassServerGameTest
{
    /** The old drone ids lead to the cube. */
    @GameTest
    public void renamedCubeStillLoads(GameTestHelper helper)
    {
        helper.assertTrue(BuiltInRegistries.ITEM.getValue(Glass.id("holo_drone")) == GlassRegistry.HOLO_CUBE, Component.literal("holo_drone item alias"));
        helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.getValue(Glass.id("holo_drone")) == GlassRegistry.HOLO_CUBE_ENTITY, Component.literal("holo_drone entity alias"));
        helper.assertTrue(BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Glass.id("drone_scan")) == GlassRegistry.CUBE_SCAN, Component.literal("drone_scan component alias"));
        helper.succeed();
    }

    /**
     * A cube scans the ball round it, chest contents and all; switched on, its ground can be stood on where the world is
     * empty, the rest is walked through, and the real world is not touched.
     */
    @GameTest(maxTicks = 260)
    public void cubeScansAndStandsInItsBall(GameTestHelper helper)
    {
        //A stone floor with a chest holding a diamond on it.
        for(int x = 0; x <= 8; x++)
        {
            for(int z = 0; z <= 8; z++)
            {
                helper.setBlock(x, 1, z, Blocks.STONE);
            }
        }
        helper.setBlock(5, 2, 4, Blocks.CHEST);
        if(helper.getBlockEntity(new BlockPos(5, 2, 4), net.minecraft.world.level.block.entity.ChestBlockEntity.class) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest)
        {
            chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.DIAMOND));
        }
        HoloCubeEntity cube = helper.spawn(GlassRegistry.HOLO_CUBE_ENTITY, new BlockPos(4, 2, 4));
        cube.setItem(new ItemStack(GlassRegistry.HOLO_CUBE));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.runAfterDelay(2, () -> cube.scanFor(player));
        helper.succeedWhen(() -> {
            helper.assertTrue(cube.scan() != null, Component.literal("the cube has no scan yet"));
            helper.assertTrue(cube.scan().isCurrent() && cube.scan().sizeX() == ryzlar.glass.holo.HoloSphere.SIZE, Component.literal("the scan is not the ball: " + cube.scan()));
            var projection = ryzlar.glass.holo.HoloProjectionData.find(helper.getLevel().getServer(), cube.scan().id());
            helper.assertTrue(projection != null && projection.blockEntities().stream().anyMatch(tag -> tag.toString().contains("diamond")),
                    Component.literal("the chest's diamond is not in the scan"));
            //Switched on: the scanned (shrunk) floor can be stood on in the floor's block, the cube's own block has no
            //ground in it (the chest beside it is walked through), and the real world is left as it was.
            if(!cube.projecting())
            {
                cube.toggle();
            }
            cube.tick();
            BlockPos floor = helper.absolutePos(new BlockPos(4, 1, 4));
            var ground = ryzlar.glass.holo.HoloBubbles.groundAt(helper.getLevel(), floor);
            helper.assertTrue(ground != null && !ground.isEmpty(), Component.literal("the simulation's ground cannot be stood on"));
            helper.assertTrue(ryzlar.glass.holo.HoloBubbles.groundAt(helper.getLevel(), helper.absolutePos(new BlockPos(4, 2, 4))) == null,
                    Component.literal("the simulation has ground where it should be walked through"));
            helper.assertTrue(helper.getLevel().getBlockState(floor).is(Blocks.STONE) && helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(5, 2, 4))).is(Blocks.CHEST),
                    Component.literal("the real world was changed"));
        });
    }

    /** A powered hard-light emitter fills the opening in front of it, and empties it again unpowered. */
    @GameTest(maxTicks = 80)
    public void hardLightFillsAnOpening(GameTestHelper helper)
    {
        if(!ryzlar.glass.gadget.GadgetFeature.ENABLED)
        {
            helper.succeed();
            return;
        }
        //A doorway: posts at x 1 and 5, a lintel at y 4; the emitter at the bottom of the left post, facing across.
        for(int y = 1; y <= 4; y++)
        {
            helper.setBlock(5, y, 3, Blocks.STONE);
            helper.setBlock(1, y, 3, Blocks.STONE);
        }
        for(int x = 1; x <= 5; x++)
        {
            helper.setBlock(x, 4, 3, Blocks.STONE);
        }
        helper.setBlock(1, 1, 3, ryzlar.glass.gadget.Gadgets.HARD_LIGHT_EMITTER.defaultBlockState()
                .setValue(ryzlar.glass.gadget.HardLightEmitterBlock.FACING, net.minecraft.core.Direction.EAST));
        helper.setBlock(0, 1, 3, Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(15, () -> {
            for(int x = 2; x <= 4; x++)
            {
                for(int y = 1; y <= 3; y++)
                {
                    helper.assertBlockPresent(ryzlar.glass.gadget.Gadgets.HARD_LIGHT, new BlockPos(x, y, 3));
                }
            }
            helper.assertBlockNotPresent(ryzlar.glass.gadget.Gadgets.HARD_LIGHT, new BlockPos(3, 4, 3));
            helper.setBlock(0, 1, 3, Blocks.AIR);
        });
        helper.runAfterDelay(35, () -> {
            for(int x = 2; x <= 4; x++)
            {
                for(int y = 1; y <= 3; y++)
                {
                    helper.assertBlockPresent(Blocks.AIR, new BlockPos(x, y, 3));
                }
            }
            helper.succeed();
        });
    }

    /** A motion sensor next to a terminal gives a signal when a monster is in the terminal's view. */
    @GameTest(maxTicks = 60)
    public void motionSensorSeesThroughItsTerminal(GameTestHelper helper)
    {
        if(!ryzlar.glass.gadget.GadgetFeature.ENABLED)
        {
            helper.succeed();
            return;
        }
        helper.setBlock(3, 2, 1, GlassRegistry.GLASS_TERMINAL);
        ((ryzlar.glass.blockentity.GlassTerminalBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(3, 2, 1)))).facing = net.minecraft.core.Direction.SOUTH;
        helper.setBlock(4, 2, 1, ryzlar.glass.gadget.Gadgets.MOTION_SENSOR);
        helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(3, 1, 6));
        helper.succeedWhen(() -> helper.assertTrue(helper.getBlockState(new BlockPos(4, 2, 1)).getValue(ryzlar.glass.gadget.MotionSensorBlock.POWERED),
                Component.literal("the sensor does not see the zombie")));
    }

    /** A holo decoy draws the monsters around it. */
    @GameTest(maxTicks = 60)
    public void decoyLuresMonsters(GameTestHelper helper)
    {
        if(!ryzlar.glass.gadget.GadgetFeature.ENABLED)
        {
            helper.succeed();
            return;
        }
        ryzlar.glass.gadget.HoloDecoyEntity decoy = helper.spawn(ryzlar.glass.gadget.Gadgets.HOLO_DECOY_ENTITY, new BlockPos(1, 1, 1));
        net.minecraft.world.entity.monster.Zombie zombie = helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(6, 1, 6));
        helper.succeedWhen(() -> helper.assertTrue(zombie.getTarget() == decoy, Component.literal("the zombie is not after the decoy")));
    }

    /** Four wireless extensions in a rectangle are the corners of a cloak field. */
    @GameTest
    public void fourCornersMakeAField(GameTestHelper helper)
    {
        BlockPos[] corners = { new BlockPos(1, 1, 1), new BlockPos(6, 1, 1), new BlockPos(1, 1, 6), new BlockPos(6, 1, 6) };
        for(BlockPos corner : corners)
        {
            helper.setBlock(corner, GlassRegistry.GLASS_WIRELESS_EXTENSION);
        }
        helper.setBlock(new BlockPos(3, 1, 7), GlassRegistry.GLASS_PROJECTOR);
        GlassProjectorBlockEntity projector = (GlassProjectorBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(3, 1, 7)));
        helper.assertTrue(projector != null, Component.literal("no projector block entity"));
        projector.wirelessPos = new java.util.ArrayList<>(List.of(helper.absolutePos(corners[0]), helper.absolutePos(corners[1]),
                helper.absolutePos(corners[2]), helper.absolutePos(corners[3])));
        projector.cloak = true;
        projector.cloakField = true;
        projector.fieldHeight = 4;
        helper.assertTrue(projector.hasFieldCorners(), Component.literal("the corners are not a rectangle"));
        helper.assertTrue(projector.cloakFieldBox() != null, Component.literal("no cloak field box"));
        helper.succeed();
    }
}
