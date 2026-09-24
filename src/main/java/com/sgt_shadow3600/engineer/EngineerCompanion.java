package com.sgt_shadow3600.engineer;

import com.sgt_shadow3600.engineer.entity.EchoSentinelEntity;
import com.sgt_shadow3600.engineer.entity.EngineerCompanionEntity;
import com.sgt_shadow3600.engineer.entity.RogueEngineerEntity;
import com.sgt_shadow3600.engineer.inventory.ModMenus;
import com.sgt_shadow3600.engineer.block.TerminalBlock;
import com.sgt_shadow3600.engineer.block.TerminalBlockEntity;
import com.sgt_shadow3600.engineer.block.ResonanceDisruptorBlock;
import com.sgt_shadow3600.engineer.block.ResonanceDisruptorBlockEntity;
import com.sgt_shadow3600.engineer.item.CommandSlateItem;
import com.sgt_shadow3600.engineer.item.EchoSentinelItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Mod(EngineerCompanion.MODID)
public class EngineerCompanion {
    public static final String MODID = "engineer";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB, MODID);

    // --- BLOCKS & ITEMS ---
    public static final DeferredHolder<Block, Block> TERMINAL_BLOCK = BLOCKS.register("operations_terminal",
            () -> new TerminalBlock(BlockBehaviour.Properties.of().strength(2.0f)));

    public static final DeferredHolder<Item, Item> TERMINAL_ITEM = ITEMS.register("operations_terminal",
            () -> new BlockItem(TERMINAL_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> COMMAND_SLATE_ITEM = ITEMS.register("command_slate",
            () -> new CommandSlateItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Block, Block> SOUNDPROOF_GLASS = BLOCKS.register("soundproof_glass",
            () -> new com.sgt_shadow3600.engineer.block.SoundproofGlassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)));

    public static final DeferredHolder<Item, Item> SOUNDPROOF_GLASS_ITEM = ITEMS.register("soundproof_glass",
            () -> new BlockItem(SOUNDPROOF_GLASS.get(), new Item.Properties()));

    public static final DeferredHolder<Block, Block> SOUNDPROOF_TINTED_GLASS = BLOCKS.register("soundproof_tinted_glass",
            () -> new com.sgt_shadow3600.engineer.block.SoundproofTintedGlassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.TINTED_GLASS)));

    public static final DeferredHolder<Item, Item> SOUNDPROOF_TINTED_GLASS_ITEM = ITEMS.register("soundproof_tinted_glass",
            () -> new BlockItem(SOUNDPROOF_TINTED_GLASS.get(), new Item.Properties()));

    public static final DeferredHolder<Block, Block> SPATIAL_ANCHOR = BLOCKS.register("spatial_anchor",
            () -> new com.sgt_shadow3600.engineer.block.SpatialAnchorBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.LODESTONE)));

    public static final DeferredHolder<Item, Item> SPATIAL_ANCHOR_ITEM = ITEMS.register("spatial_anchor",
            () -> new BlockItem(SPATIAL_ANCHOR.get(), new Item.Properties()));

    public static final DeferredHolder<Block, Block> RESONANCE_DISRUPTOR_BLOCK = BLOCKS.register("resonance_disruptor",
            () -> new ResonanceDisruptorBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SCULK_SHRIEKER)));

    public static final DeferredHolder<Item, Item> RESONANCE_DISRUPTOR_ITEM = ITEMS.register("resonance_disruptor",
            () -> new BlockItem(RESONANCE_DISRUPTOR_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ResonanceDisruptorBlockEntity>> RESONANCE_DISRUPTOR_BE =
            BLOCK_ENTITIES.register("resonance_disruptor",
                    () -> BlockEntityType.Builder.of(ResonanceDisruptorBlockEntity::new, RESONANCE_DISRUPTOR_BLOCK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TerminalBlockEntity>> TERMINAL_BE =
            BLOCK_ENTITIES.register("operations_terminal",
                    () -> BlockEntityType.Builder.of(TerminalBlockEntity::new, TERMINAL_BLOCK.get()).build(null));

    // --- ENTITIES ---
    public static final DeferredHolder<EntityType<?>, EntityType<EngineerCompanionEntity>> ENGINEER_NPC = ENTITIES.register("engineer",
            () -> EntityType.Builder.of(EngineerCompanionEntity::new, MobCategory.CREATURE)
                    .sized(0.6f, 1.8f)
                    .build("engineer"));

    public static final DeferredHolder<EntityType<?>, EntityType<RogueEngineerEntity>> ROGUE_ENGINEER_NPC = ENTITIES.register("rogue_engineer",
            () -> EntityType.Builder.of(RogueEngineerEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.8f)
                    .build("rogue_engineer"));

    public static final DeferredHolder<Item, DeferredSpawnEggItem> ROGUE_ENGINEER_SPAWN_EGG = ITEMS.register("rogue_engineer_spawn_egg",
            () -> new DeferredSpawnEggItem(ROGUE_ENGINEER_NPC, 0x4B5358, 0xAA0000, new Item.Properties()));

    // NEW TURRET ENTITY
    public static final DeferredHolder<EntityType<?>, EntityType<EchoSentinelEntity>> ECHO_SENTINEL_ENTITY = ENTITIES.register("echo_sentinel",
            () -> EntityType.Builder.of(EchoSentinelEntity::new, MobCategory.MISC)
                    .sized(1.0f, 1.0f)
                    .build("echo_sentinel"));

    // NEW TURRET SPAWNER ITEM
    public static final DeferredHolder<Item, Item> ECHO_SENTINEL_ITEM = ITEMS.register("echo_sentinel",
            () -> new EchoSentinelItem(new Item.Properties()));

    // NEW TURRET SPAWN EGG (For the "Attack Everything" Variant)
    public static final DeferredHolder<Item, DeferredSpawnEggItem> ECHO_SENTINEL_SPAWN_EGG = ITEMS.register("echo_sentinel_spawn_egg",
            () -> new DeferredSpawnEggItem(ECHO_SENTINEL_ENTITY, 0x054854, 0x935A88, new Item.Properties()));

    // --- LOGIC CORE ---
    public static final DeferredHolder<Block, Block> LOGIC_CORE_BLOCK = BLOCKS.register("logic_core",
            () -> new com.sgt_shadow3600.engineer.block.LogicCoreBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SCULK)));

    public static final DeferredHolder<Item, Item> LOGIC_CORE_ITEM = ITEMS.register("logic_core",
            () -> new BlockItem(LOGIC_CORE_BLOCK.get(), new Item.Properties().fireResistant().stacksTo(1)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sgt_shadow3600.engineer.block.LogicCoreBlockEntity>> LOGIC_CORE_BE =
            BLOCK_ENTITIES.register("logic_core",
                    () -> BlockEntityType.Builder.of(com.sgt_shadow3600.engineer.block.LogicCoreBlockEntity::new, LOGIC_CORE_BLOCK.get()).build(null));

    // --- CREATIVE TAB ---
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ENGINEER_TAB = CREATIVE_MODE_TABS.register("engineer_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.literal("Engineers Workshop"))
                    .icon(() -> LOGIC_CORE_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(TERMINAL_ITEM.get());
                        output.accept(COMMAND_SLATE_ITEM.get());
                        output.accept(ROGUE_ENGINEER_SPAWN_EGG.get());
                        output.accept(LOGIC_CORE_ITEM.get());
                        output.accept(RESONANCE_DISRUPTOR_ITEM.get());
                        output.accept(SOUNDPROOF_GLASS_ITEM.get());
                        output.accept(SOUNDPROOF_TINTED_GLASS_ITEM.get());
                        output.accept(SPATIAL_ANCHOR_ITEM.get());
                        output.accept(ECHO_SENTINEL_ITEM.get());
                        output.accept(ECHO_SENTINEL_SPAWN_EGG.get()); // Added Egg to Tab
                    })
                    .build());

    public EngineerCompanion(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        ENTITIES.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModDataComponents.COMPONENTS.register(modEventBus);

        modEventBus.addListener(this::registerAttributes);
        modEventBus.addListener(this::registerSpawnPlacements);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ENGINEER_NPC.get(), EngineerCompanionEntity.createAttributes().build());
        event.put(ROGUE_ENGINEER_NPC.get(), RogueEngineerEntity.createRogueAttributes().build());
        event.put(ECHO_SENTINEL_ENTITY.get(), EchoSentinelEntity.createAttributes().build());
    }

    private void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(
                ROGUE_ENGINEER_NPC.get(),
                net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                RogueEngineerEntity::checkRogueSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE
        );
    }

    private void onServerStarting(ServerStartingEvent event) {
        Path schematicDir = event.getServer().getServerDirectory().resolve("engineer_schematics");
        if (!Files.exists(schematicDir)) {
            try {
                Files.createDirectories(schematicDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}