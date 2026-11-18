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
import net.minecraft.world.item.Item;
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
            BlockBehaviour.Properties.ofFullCopy(Blocks.LODESTONE).strength(2.5F).lightLevel(state -> 6));

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

    private static <T extends BlockEntityType<?>> T registerBlockEntity(String name, T type)
    {
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Glass.id(name), type);
    }

    public static void init()
    {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(entries -> {
            entries.accept(GLASS_PROJECTOR);
            entries.accept(GLASS_WIRELESS_EXTENSION);
            entries.accept(GLASS_EXTENSION);
            entries.accept(GLASS_TERMINAL);
            entries.accept(GLASS_SCENE_SELECTOR);
        });
    }
}
