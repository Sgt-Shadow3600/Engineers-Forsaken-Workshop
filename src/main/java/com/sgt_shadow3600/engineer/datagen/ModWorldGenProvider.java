package com.sgt_shadow3600.engineer.datagen;

import com.sgt_shadow3600.engineer.worldgen.ModWorldGen;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ModWorldGenProvider extends DatapackBuiltinEntriesProvider {
    public static final RegistrySetBuilder BUILDER = new RegistrySetBuilder()
            .add(Registries.TEMPLATE_POOL, ModWorldGen::bootstrapPools)
            .add(Registries.STRUCTURE, ModWorldGen::bootstrapStructures)
            .add(Registries.STRUCTURE_SET, ModWorldGen::bootstrapSets);

    public ModWorldGenProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries, BUILDER, Set.of("engineer"));
    }
}