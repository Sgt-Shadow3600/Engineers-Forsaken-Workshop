package com.sgt_shadow3600.engineer.worldgen;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.ConstantHeight;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ModWorldGen {
    public static final ResourceKey<StructureTemplatePool> UPPER_POOL = ResourceKey.create(Registries.TEMPLATE_POOL, ResourceLocation.fromNamespaceAndPath("engineer", "engineers_workshop_upper"));
    public static final ResourceKey<StructureTemplatePool> MID_1_POOL = ResourceKey.create(Registries.TEMPLATE_POOL, ResourceLocation.fromNamespaceAndPath("engineer", "engineers_workshop_middle_1"));
    public static final ResourceKey<StructureTemplatePool> MID_2_POOL = ResourceKey.create(Registries.TEMPLATE_POOL, ResourceLocation.fromNamespaceAndPath("engineer", "engineers_workshop_middle_2"));
    public static final ResourceKey<StructureTemplatePool> LOWER_POOL = ResourceKey.create(Registries.TEMPLATE_POOL, ResourceLocation.fromNamespaceAndPath("engineer", "engineers_workshop_lower"));

    public static final ResourceKey<Structure> WORKSHOP_STRUCTURE = ResourceKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath("engineer", "engineers_workshop"));
    public static final ResourceKey<StructureSet> WORKSHOP_SET = ResourceKey.create(Registries.STRUCTURE_SET, ResourceLocation.fromNamespaceAndPath("engineer", "engineers_workshop"));
    public static final TagKey<Biome> HAS_WORKSHOP_TAG = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("engineer", "has_engineers_workshop"));
    public static final ResourceKey<StructureProcessorList> EMPTY_PROCESSOR_LIST_KEY = ResourceKey.create(Registries.PROCESSOR_LIST, ResourceLocation.fromNamespaceAndPath("minecraft", "empty"));

    public static void bootstrapPools(BootstrapContext<StructureTemplatePool> context) {
        HolderGetter<StructureTemplatePool> poolGetter = context.lookup(Registries.TEMPLATE_POOL);
        Holder<StructureTemplatePool> emptyPool = poolGetter.getOrThrow(Pools.EMPTY);
        HolderGetter<StructureProcessorList> processorGetter = context.lookup(Registries.PROCESSOR_LIST);
        Holder<StructureProcessorList> emptyProcessor = processorGetter.getOrThrow(EMPTY_PROCESSOR_LIST_KEY);

        context.register(LOWER_POOL, new StructureTemplatePool(emptyPool, List.of(Pair.of(StructurePoolElement.single("engineer:engineers_workshop_lower", emptyProcessor), 1)), StructureTemplatePool.Projection.RIGID));
        context.register(MID_2_POOL, new StructureTemplatePool(emptyPool, List.of(Pair.of(StructurePoolElement.single("engineer:engineers_workshop_middle_2", emptyProcessor), 1)), StructureTemplatePool.Projection.RIGID));
        context.register(MID_1_POOL, new StructureTemplatePool(emptyPool, List.of(Pair.of(StructurePoolElement.single("engineer:engineers_workshop_middle_1", emptyProcessor), 1)), StructureTemplatePool.Projection.RIGID));
        context.register(UPPER_POOL, new StructureTemplatePool(emptyPool, List.of(Pair.of(StructurePoolElement.single("engineer:engineers_workshop_upper", emptyProcessor), 1)), StructureTemplatePool.Projection.RIGID));
    }

    public static void bootstrapStructures(BootstrapContext<Structure> context) {
        HolderGetter<Biome> biomeGetter = context.lookup(Registries.BIOME);
        HolderGetter<StructureTemplatePool> poolGetter = context.lookup(Registries.TEMPLATE_POOL);

        Structure.StructureSettings settings = new Structure.StructureSettings(
                biomeGetter.getOrThrow(HAS_WORKSHOP_TAG),
                Map.of(),
                GenerationStep.Decoration.SURFACE_STRUCTURES,
                TerrainAdjustment.BEARD_BOX
        );

        context.register(WORKSHOP_STRUCTURE, new JigsawStructure(
                settings,
                poolGetter.getOrThrow(UPPER_POOL),
                Optional.empty(),
                7,
                ConstantHeight.of(VerticalAnchor.absolute(0)),
                false,
                Optional.of(Heightmap.Types.WORLD_SURFACE_WG),
                116,
                List.of(),
                JigsawStructure.DEFAULT_DIMENSION_PADDING,
                LiquidSettings.IGNORE_WATERLOGGING
        ));
    }

    public static void bootstrapSets(BootstrapContext<StructureSet> context) {
        HolderGetter<Structure> structureGetter = context.lookup(Registries.STRUCTURE);
        // UPDATED: Spacing 64, Separation 24 for massive distance between spawns
        context.register(WORKSHOP_SET, new StructureSet(structureGetter.getOrThrow(WORKSHOP_STRUCTURE), new RandomSpreadStructurePlacement(64, 24, RandomSpreadType.LINEAR, 18273645)));
    }
}