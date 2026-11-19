package ryzlar.glass;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import ryzlar.glass.holo.CubeScan;
import ryzlar.glass.holo.HoloBlocks;
import ryzlar.glass.holo.HoloCubeEntity;
import ryzlar.glass.holo.HoloCubeItem;
import ryzlar.glass.holo.HoloProjectorBlockEntity;
import ryzlar.glass.holo.HoloScannerBlockEntity;
import ryzlar.glass.holo.ProjectionRef;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import ryzlar.glass.block.GlassBlock;
import ryzlar.glass.block.GlassSceneSelectorBlock;
import ryzlar.glass.block.GlassTerminalBlock;
import ryzlar.glass.blockentity.GlassBaseBlockEntity;
import ryzlar.glass.blockentity.GlassProjectorBlockEntity;
import ryzlar.glass.blockentity.GlassSceneSelectorBlockEntity;
import ryzlar.glass.blockentity.GlassTerminalBlockEntity;
import ryzlar.glass.blockentity.GlassWirelessBlockEntity;

import java.util.function.Function;

/**
 * The 1.12 mod had one block with three metadata variants (extension, projector, wireless).
 * Metadata no longer exists, so each variant is its own block here.
 */
public final class GlassRegistry
{
    public static final GlassBlock GLASS_EXTENSION = registerBlock("glass_extension", p -> new GlassBlock(p, GlassBlock.Kind.EXTENSION), glassProperties());
    public static final GlassBlock GLASS_PROJECTOR = registerBlock("glass_projector", p -> new GlassBlock(p, GlassBlock.Kind.PROJECTOR), glassProperties());
    public static final GlassBlock GLASS_WIRELESS_EXTENSION = registerBlock("glass_wireless_extension", p -> new GlassBlock(p, GlassBlock.Kind.WIRELESS), glassProperties());
    public static final GlassTerminalBlock GLASS_TERMINAL = registerBlock("glass_terminal", GlassTerminalBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN).strength(50.0F, 2000.0F).sound(SoundType.STONE).noOcclusion());
    public static final GlassSceneSelectorBlock GLASS_SCENE_SELECTOR = registerBlock("glass_scene_selector", GlassSceneSelectorBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.LODESTONE).strength(2.5F).lightLevel(state -> 6).noOcclusion());

    public static final HoloBlocks.Scanner HOLO_SCANNER = registerBlock("holo_scanner", HoloBlocks.Scanner::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(3.0F).lightLevel(state -> 5).noOcclusion());
    public static final HoloBlocks.Projector HOLO_PROJECTOR = registerBlock("holo_projector", HoloBlocks.Projector::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(3.0F).lightLevel(state -> 8).noOcclusion());
    public static final HoloBlocks.Emitter HOLO_EMITTER = registerBlock("holo_emitter", HoloBlocks.Emitter::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.END_ROD).lightLevel(state -> 10));

    /** What a projection crystal holds. */
    public static final DataComponentType<ProjectionRef> PROJECTION = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Glass.id("projection"),
            DataComponentType.<ProjectionRef>builder().persistent(ProjectionRef.CODEC).networkSynchronized(ProjectionRef.STREAM_CODEC).build());
    /** A holo cube's room scan. */
    public static final DataComponentType<CubeScan> CUBE_SCAN = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Glass.id("cube_scan"),
            DataComponentType.<CubeScan>builder().persistent(CubeScan.CODEC).networkSynchronized(CubeScan.STREAM_CODEC).build());
    public static final Item HOLO_CUBE = registerItem("holo_cube", properties -> new HoloCubeItem(properties.stacksTo(1).rarity(Rarity.RARE)));
    public static final EntityType<HoloCubeEntity> HOLO_CUBE_ENTITY = registerEntity("holo_cube",
            EntityType.Builder.<HoloCubeEntity>of(HoloCubeEntity::new, MobCategory.MISC).sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(2));
    public static final Item PROJECTION_CRYSTAL = registerItem("projection_crystal", properties -> new Item(properties.stacksTo(1).rarity(Rarity.RARE)));

    public static final BlockEntityType<GlassBaseBlockEntity> GLASS_EXTENSION_BE = registerBlockEntity("glass_extension",
            FabricBlockEntityTypeBuilder.create(GlassBaseBlockEntity::new, GLASS_EXTENSION).build());
    public static final BlockEntityType<GlassProjectorBlockEntity> GLASS_PROJECTOR_BE = registerBlockEntity("glass_projector",
            FabricBlockEntityTypeBuilder.create(GlassProjectorBlockEntity::new, GLASS_PROJECTOR).build());
    public static final BlockEntityType<GlassWirelessBlockEntity> GLASS_WIRELESS_BE = registerBlockEntity("glass_wireless_extension",
            FabricBlockEntityTypeBuilder.create(GlassWirelessBlockEntity::new, GLASS_WIRELESS_EXTENSION).build());
    public static final BlockEntityType<GlassTerminalBlockEntity> GLASS_TERMINAL_BE = registerBlockEntity("glass_terminal",
            FabricBlockEntityTypeBuilder.create(GlassTerminalBlockEntity::new, GLASS_TERMINAL).build());
    public static final BlockEntityType<GlassSceneSelectorBlockEntity> GLASS_SCENE_SELECTOR_BE = registerBlockEntity("glass_scene_selector",
            FabricBlockEntityTypeBuilder.create(GlassSceneSelectorBlockEntity::new, GLASS_SCENE_SELECTOR).build());
    public static final BlockEntityType<HoloScannerBlockEntity> HOLO_SCANNER_BE = registerBlockEntity("holo_scanner",
            FabricBlockEntityTypeBuilder.create(HoloScannerBlockEntity::new, HOLO_SCANNER).build());
    public static final BlockEntityType<HoloProjectorBlockEntity> HOLO_PROJECTOR_BE = registerBlockEntity("holo_projector",
            FabricBlockEntityTypeBuilder.create(HoloProjectorBlockEntity::new, HOLO_PROJECTOR).build());

    public static final SoundEvent SOUND_AMBIENCE = Registry.register(BuiltInRegistries.SOUND_EVENT, Glass.id("amb"), SoundEvent.createVariableRangeEvent(Glass.id("amb")));

    private GlassRegistry() {}

    private static BlockBehaviour.Properties glassProperties()
    {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS).strength(0.8F);
    }

    private static <B extends Block> B registerBlock(String name, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties properties)
    {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, Glass.id(name));
        B block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(properties.setId(blockKey)));

        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Glass.id(name));
        Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        return block;
    }

    private static <T extends net.minecraft.world.entity.Entity> EntityType<T> registerEntity(String name, EntityType.Builder<T> builder)
    {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Glass.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    private static Item registerItem(String name, Function<Item.Properties, Item> factory)
    {
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Glass.id(name));
        return Registry.register(BuiltInRegistries.ITEM, itemKey, factory.apply(new Item.Properties().setId(itemKey)));
    }

    private static <T extends BlockEntityType<?>> T registerBlockEntity(String name, T type)
    {
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Glass.id(name), type);
    }

    public static void init()
    {
        //The holo cube was the holo drone: worlds from before keep their cubes, set down or carried, and their scans.
        BuiltInRegistries.ITEM.addAlias(Glass.id("holo_drone"), Glass.id("holo_cube"));
        BuiltInRegistries.ENTITY_TYPE.addAlias(Glass.id("holo_drone"), Glass.id("holo_cube"));
        BuiltInRegistries.DATA_COMPONENT_TYPE.addAlias(Glass.id("drone_scan"), Glass.id("cube_scan"));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(entries -> {
            entries.accept(GLASS_PROJECTOR);
            entries.accept(GLASS_WIRELESS_EXTENSION);
            entries.accept(GLASS_EXTENSION);
            entries.accept(GLASS_TERMINAL);
            entries.accept(GLASS_SCENE_SELECTOR);
            entries.accept(HOLO_SCANNER);
            entries.accept(HOLO_PROJECTOR);
            entries.accept(HOLO_EMITTER);
            entries.accept(HOLO_CUBE);
        });
    }
}
