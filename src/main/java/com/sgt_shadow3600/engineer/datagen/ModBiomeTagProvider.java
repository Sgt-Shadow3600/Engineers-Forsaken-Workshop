package com.sgt_shadow3600.engineer.datagen;

import com.sgt_shadow3600.engineer.worldgen.ModWorldGen;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.BiomeTagsProvider;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

public class ModBiomeTagProvider extends BiomeTagsProvider {
    public ModBiomeTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> provider, ExistingFileHelper existingFileHelper) {
        super(output, provider, "engineer", existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        // Targeted "Flat-ish" High-Altitude Biomes to prevent severe cliff overhangs
        tag(ModWorldGen.HAS_WORKSHOP_TAG).add(
                Biomes.MEADOW,
                Biomes.CHERRY_GROVE,
                Biomes.GROVE
        );
    }
}