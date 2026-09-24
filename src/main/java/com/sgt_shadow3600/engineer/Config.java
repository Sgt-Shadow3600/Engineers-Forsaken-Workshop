package com.sgt_shadow3600.engineer;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // -- ENGINEER SCHEMATIC VARS --
    public static final ModConfigSpec.BooleanValue ALLOW_CUSTOM_SCHEMATICS = BUILDER
            .comment("Allow players to build custom schematics from the game's engineer_schematics folder.")
            .define("allowCustomSchematics", true);

    public static final ModConfigSpec.BooleanValue ALLOW_ALL_NATIVE_STRUCTURES = BUILDER
            .comment("If true, dynamically lists and allows ALL structures from the game and mods (Excludes entities/animals).")
            .define("unlockAllNativeStructures", false);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> ALLOWED_NATIVE_PREFIXES = BUILDER
            .comment("A list of namespace prefixes to allow whole folders of structures. Default is all village structures.")
            .defineListAllowEmpty("allowedNativePrefixes", List.of(
                    "minecraft:village/"
            ), () -> "", o -> o instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> ALLOWED_SPECIFIC_STRUCTURES = BUILDER
            .comment("A whitelist of specific native/modded structures if they don't fall under the allowed prefixes.")
            .defineListAllowEmpty("allowedSpecificStructures", List.of(
                    "minecraft:igloo/top"
            ), () -> "", o -> o instanceof String);

    // -- ROGUE ENGINEER VARS --
    public static final ModConfigSpec.BooleanValue ALLOW_ROGUE_SPAWNERS = BUILDER
            .comment("Allow Rogue Engineers to be spawned from Monster Spawners. If false, spawners will fail to spawn them.")
            .define("allowRogueSpawners", true);

    public static final ModConfigSpec.DoubleValue ROGUE_HEALTH_MULTIPLIER = BUILDER
            .comment("Multiplier for Rogue Engineer health (Base is 30.0).")
            .defineInRange("rogueHealthMultiplier", 2.0, 0.1, 100.0);

    public static final ModConfigSpec.DoubleValue ROGUE_DAMAGE_MULTIPLIER = BUILDER
            .comment("Multiplier for Rogue Engineer melee damage (Base is 4.0).")
            .defineInRange("rogueDamageMultiplier", 2.0, 0.1, 100.0);

    public static final ModConfigSpec.DoubleValue ROGUE_LOGIC_CORE_DROP_CHANCE = BUILDER
            .comment("Chance (0.0 to 1.0) for a Rogue to drop a Blank Logic Core on death.")
            .defineInRange("rogueLogicCoreDropChance", 0.25, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue ROGUE_LOOT_DROP_CHANCE = BUILDER
            .comment("Chance (0.0 to 1.0) for a Rogue to drop its equipped gear.")
            .defineInRange("rogueLootDropChance", 0.15, 0.0, 1.0);

    public static final ModConfigSpec.BooleanValue ROGUE_RANGED_ATTACK = BUILDER
            .comment("Allow Rogue Engineers to use Pneumatic/Seismic ranged attacks.")
            .define("rogueRangedAttack", true);

    public static final ModConfigSpec SPEC = BUILDER.build();
}