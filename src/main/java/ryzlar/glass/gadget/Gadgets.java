package ryzlar.glass.gadget;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import ryzlar.glass.Glass;
import ryzlar.glass.GlassRegistry;

/**
 * The gadgets: the hard-light wall, the motion sensor, the GLASS tablet, the holo map table, the holo decoy and optical
 * camo.
 */
public final class Gadgets
{
    public static final HardLightEmitterBlock HARD_LIGHT_EMITTER = GlassRegistry.registerBlock("hard_light_emitter", HardLightEmitterBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(3.0F).lightLevel(state -> state.getValue(HardLightEmitterBlock.POWERED) ? 9 : 3).noOcclusion());
    /** Hard light itself has no item: only emitters make it. */
    public static final HardLightBlock HARD_LIGHT = registerBlockOnly("hard_light", HardLightBlock::new,
            BlockBehaviour.Properties.of().strength(-1.0F, 3600000.0F).noLootTable().noOcclusion().lightLevel(state -> 7)
                    .sound(SoundType.AMETHYST).pushReaction(PushReaction.BLOCK).isValidSpawn(Blocks::never).isRedstoneConductor(Blocks::never)
                    .isSuffocating(Blocks::never).isViewBlocking(Blocks::never));
    public static final MotionSensorBlock MOTION_SENSOR = GlassRegistry.registerBlock("motion_sensor", MotionSensorBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(2.0F).lightLevel(state -> state.getValue(MotionSensorBlock.POWERED) ? 7 : 2).noOcclusion());
    public static final HoloMapTableBlock HOLO_MAP_TABLE = GlassRegistry.registerBlock("holo_map_table", HoloMapTableBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(3.0F).lightLevel(state -> state.getValue(HoloMapTableBlock.LIT) ? 8 : 2).noOcclusion());

    public static final BlockEntityType<HardLightEmitterBlockEntity> HARD_LIGHT_EMITTER_BE = GlassRegistry.registerBlockEntity("hard_light_emitter",
            FabricBlockEntityTypeBuilder.create(HardLightEmitterBlockEntity::new, HARD_LIGHT_EMITTER).build());
    public static final BlockEntityType<MotionSensorBlockEntity> MOTION_SENSOR_BE = GlassRegistry.registerBlockEntity("motion_sensor",
            FabricBlockEntityTypeBuilder.create(MotionSensorBlockEntity::new, MOTION_SENSOR).build());
    public static final BlockEntityType<HoloMapTableBlock.HoloMapTableBlockEntity> HOLO_MAP_TABLE_BE = GlassRegistry.registerBlockEntity("holo_map_table",
            FabricBlockEntityTypeBuilder.create(HoloMapTableBlock.HoloMapTableBlockEntity::new, HOLO_MAP_TABLE).build());

    /** The channel a tablet shows. */
    public static final DataComponentType<TabletLink> TABLET_LINK = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Glass.id("tablet_link"),
            DataComponentType.<TabletLink>builder().persistent(TabletLink.CODEC).networkSynchronized(TabletLink.STREAM_CODEC).build());

    public static final Item GLASS_TABLET = GlassRegistry.registerItem("glass_tablet", properties -> new GlassTabletItem(properties.stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final Item HOLO_DECOY = GlassRegistry.registerItem("holo_decoy", properties -> new HoloDecoyItem(properties.stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final Item OPTICAL_CAMO_MODULE = GlassRegistry.registerItem("optical_camo", properties -> new OpticalCamo.ModuleItem(properties.stacksTo(1).rarity(Rarity.RARE)));

    public static final EntityType<HoloDecoyEntity> HOLO_DECOY_ENTITY = GlassRegistry.registerEntity("holo_decoy",
            EntityType.Builder.<HoloDecoyEntity>of(HoloDecoyEntity::new, MobCategory.MISC).sized(0.6F, 1.8F).clientTrackingRange(10));

    public static final Holder<MobEffect> OPTICAL_CAMO = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, Glass.id("optical_camo"), new OpticalCamo.Effect());

    private Gadgets() {}

    private static <B extends Block> B registerBlockOnly(String name, java.util.function.Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties properties)
    {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Glass.id(name));
        return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
    }

    public static void init()
    {
        FabricDefaultAttributeRegistry.register(HOLO_DECOY_ENTITY, HoloDecoyEntity.attributes());
        GadgetNetworking.init();
        OpticalCamo.init();
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(entries -> {
            entries.accept(HARD_LIGHT_EMITTER);
            entries.accept(MOTION_SENSOR);
            entries.accept(HOLO_MAP_TABLE);
        });
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> {
            entries.accept(GLASS_TABLET);
            entries.accept(HOLO_DECOY);
            entries.accept(OPTICAL_CAMO_MODULE);
        });
    }
}
