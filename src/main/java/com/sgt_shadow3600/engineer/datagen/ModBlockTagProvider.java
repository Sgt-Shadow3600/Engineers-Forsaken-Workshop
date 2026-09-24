package com.sgt_shadow3600.engineer.datagen;

import com.sgt_shadow3600.engineer.EngineerCompanion;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class ModBlockTagProvider extends BlockTagsProvider {

    public ModBlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, EngineerCompanion.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        // --- SOUNDPROOFING ---
        this.tag(BlockTags.OCCLUDES_VIBRATION_SIGNALS)
                .add(EngineerCompanion.SOUNDPROOF_GLASS.get())
                .add(EngineerCompanion.SOUNDPROOF_TINTED_GLASS.get());

        // --- MINING TOOLS ---
        // The Anchor still needs a pickaxe. The Sentinel was removed because it is now an Entity.
        this.tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(EngineerCompanion.SPATIAL_ANCHOR.get());
    }
}