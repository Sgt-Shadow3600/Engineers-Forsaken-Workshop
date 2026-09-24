package com.sgt_shadow3600.engineer.datagen;

import com.sgt_shadow3600.engineer.EngineerCompanion;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.tags.ItemTags;

import java.util.concurrent.CompletableFuture;

public class ModRecipeProvider extends RecipeProvider {

    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput output) {

        // 1. Command Slate
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, EngineerCompanion.COMMAND_SLATE_ITEM.get())
                .pattern("G G")
                .pattern(" R ")
                .pattern("SSS")
                .define('G', Items.GLASS_PANE)
                .define('R', Items.REDSTONE)
                .define('S', Items.SMOOTH_STONE)
                .unlockedBy("has_terminal", has(EngineerCompanion.TERMINAL_ITEM.get()))
                .save(output);

        // 2. Logic Core
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, EngineerCompanion.LOGIC_CORE_ITEM.get())
                .pattern("GNG")
                .pattern("RDR")
                .pattern("GNG")
                .define('G', Items.GOLD_INGOT)
                .define('N', Items.NETHER_BRICK)
                .define('D', Items.DIAMOND)
                .define('R', Items.REDSTONE_BLOCK)
                .unlockedBy("defeated_rogue", has(EngineerCompanion.LOGIC_CORE_ITEM.get()))
                .save(output);

        // 3. Resonance Disruptor
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, EngineerCompanion.RESONANCE_DISRUPTOR_ITEM.get())
                .pattern("NCN")
                .pattern("PXP")
                .pattern("ERE")
                .define('N', Items.NETHERITE_INGOT)
                .define('C', Items.CHISELED_COPPER)
                .define('P', Items.PRISMARINE_SHARD)
                .define('X', Items.SCULK_SHRIEKER)
                .define('E', Items.ECHO_SHARD)
                .define('R', Items.REDSTONE_BLOCK)
                .unlockedBy("has_terminal", has(EngineerCompanion.TERMINAL_ITEM.get()))
                .save(output);

        // 4. Soundproof Glass (Standard) - Glass surrounded by Wool
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, EngineerCompanion.SOUNDPROOF_GLASS_ITEM.get())
                .pattern("WWW")
                .pattern("WGW")
                .pattern("WWW")
                .define('W', ItemTags.WOOL) // Accepts any color wool
                .define('G', Items.GLASS)
                .unlockedBy("has_glass", has(Items.GLASS))
                .save(output);

        // 5. Soundproof Glass (Tinted) - Tinted Glass surrounded by Wool
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, EngineerCompanion.SOUNDPROOF_TINTED_GLASS_ITEM.get())
                .pattern("WWW")
                .pattern("WTW")
                .pattern("WWW")
                .define('W', ItemTags.WOOL)
                .define('T', Items.TINTED_GLASS)
                .unlockedBy("has_tinted_glass", has(Items.TINTED_GLASS))
                .save(output);

        // 6. Spatial Anchor (Teleport Inhibitor)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, EngineerCompanion.SPATIAL_ANCHOR_ITEM.get())
                .pattern("VLV")
                .pattern(" E ")
                .pattern("LCL")
                .define('V', Items.SCULK_VEIN)
                .define('L', Items.LIGHTNING_ROD)
                .define('E', Items.ENDER_PEARL)
                .define('C', Items.CUT_COPPER_SLAB)
                .unlockedBy("has_ender_pearl", has(Items.ENDER_PEARL))
                .save(output);

        // 7. Echo Sentinel (Turret Block)
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, EngineerCompanion.ECHO_SENTINEL_ITEM.get())
                .pattern("NHN")
                .pattern("CSC")
                .pattern("NCN")
                .define('N', Items.NETHERITE_INGOT)
                .define('H', Items.SHULKER_SHELL)
                .define('S', Items.SCULK_SENSOR)
                .define('C', Items.COPPER_BLOCK)
                .unlockedBy("has_sculk_sensor", has(Items.SCULK_SENSOR))
                .save(output);
    }
}