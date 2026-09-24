package com.sgt_shadow3600.engineer.datagen;

import com.sgt_shadow3600.engineer.EngineerCompanion;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = EngineerCompanion.MODID, bus = EventBusSubscriber.Bus.MOD)
public class DataGenerators {

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput packOutput = generator.getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        // 1. Recipe Provider
        generator.addProvider(
                event.includeServer(),
                new ModRecipeProvider(packOutput, lookupProvider)
        );

        // 2. WorldGen Provider (Structures, Template Pools, Structure Sets)
        generator.addProvider(
                event.includeServer(),
                new ModWorldGenProvider(packOutput, lookupProvider)
        );

        // 3. Biome Tags Provider (Tying the structure to the Deep Dark)
        generator.addProvider(
                event.includeServer(),
                new ModBiomeTagProvider(packOutput, lookupProvider, event.getExistingFileHelper())
        );

        // 4. Block Tags Provider (Handles Mineable tags and Soundproofing)
        generator.addProvider(
                event.includeServer(),
                new ModBlockTagProvider(packOutput, lookupProvider, event.getExistingFileHelper())
        );
    }
}