package com.sgt_shadow3600.engineer;

import com.mojang.serialization.Codec;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class ModDataComponents {

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, EngineerCompanion.MODID);

    // Binds companion UUIDs to specific items/terminals
    public static final Supplier<DataComponentType<List<UUID>>> BOUND_COMPANIONS = COMPONENTS.register("bound_companions",
            () -> DataComponentType.<List<UUID>>builder()
                    .persistent(Codec.list(UUIDUtil.CODEC))
                    .networkSynchronized(ByteBufCodecs.collection(ArrayList::new, UUIDUtil.STREAM_CODEC))
                    .build()
    );

    // Binds terminal global positions (Includes Dimension data securely)
    public static final Supplier<DataComponentType<List<GlobalPos>>> BOUND_TERMINALS = COMPONENTS.register("bound_terminals",
            () -> DataComponentType.<List<GlobalPos>>builder()
                    .persistent(Codec.list(GlobalPos.CODEC))
                    .networkSynchronized(ByteBufCodecs.collection(ArrayList::new, GlobalPos.STREAM_CODEC))
                    .build()
    );

    public static final Supplier<DataComponentType<Integer>> ACTIVE_TERMINAL = COMPONENTS.register("active_terminal",
            () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.INT)
                    .build()
    );

    public static final Supplier<DataComponentType<GlobalPos>> LINKED_TERMINAL = COMPONENTS.register("linked_terminal",
            () -> DataComponentType.<GlobalPos>builder()
                    .persistent(GlobalPos.CODEC)
                    .networkSynchronized(GlobalPos.STREAM_CODEC)
                    .build()
    );

    public static final Supplier<DataComponentType<GlobalPos>> JOB_SITE = COMPONENTS.register("job_site",
            () -> DataComponentType.<GlobalPos>builder()
                    .persistent(GlobalPos.CODEC)
                    .networkSynchronized(GlobalPos.STREAM_CODEC)
                    .build()
    );

    public static final Supplier<DataComponentType<Integer>> JOB_DIRECTION = COMPONENTS.register("job_direction",
            () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.INT)
                    .build()
    );

    public static final Supplier<DataComponentType<List<Integer>>> PENDING_DIG_CONFIG = COMPONENTS.register("pending_dig_config",
            () -> DataComponentType.<List<Integer>>builder()
                    .persistent(Codec.list(Codec.INT))
                    .networkSynchronized(ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.INT))
                    .build()
    );

    public static final Supplier<DataComponentType<CompoundTag>> PENDING_BUILD_CONFIG = COMPONENTS.register("pending_build_config",
            () -> DataComponentType.<CompoundTag>builder()
                    .persistent(CompoundTag.CODEC)
                    .networkSynchronized(ByteBufCodecs.COMPOUND_TAG)
                    .build()
    );

    // ARCHITECT ADDITION: Stores the entire NPC's NBT on the physical Logic Core item drop
    // Keeps data safe when transitioning entities back to items.
    public static final Supplier<DataComponentType<CompoundTag>> STORED_NPC_DATA = COMPONENTS.register("stored_npc_data",
            () -> DataComponentType.<CompoundTag>builder()
                    .persistent(CompoundTag.CODEC)
                    .networkSynchronized(ByteBufCodecs.COMPOUND_TAG)
                    .build()
    );
}