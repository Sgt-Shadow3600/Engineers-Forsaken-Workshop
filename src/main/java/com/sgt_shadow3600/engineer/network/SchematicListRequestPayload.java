package com.sgt_shadow3600.engineer.network;

import com.sgt_shadow3600.engineer.EngineerCompanion;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SchematicListRequestPayload() implements CustomPacketPayload {
    public static final Type<SchematicListRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "schematic_list_req"));

    public static final StreamCodec<FriendlyByteBuf, SchematicListRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {},
            buf -> new SchematicListRequestPayload()
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}