package com.sgt_shadow3600.engineer.network;

import com.sgt_shadow3600.engineer.EngineerCompanion;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SchematicRequestPayload(String schematicName) implements CustomPacketPayload {

    public static final Type<SchematicRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "schematic_request"));

    public static final StreamCodec<FriendlyByteBuf, SchematicRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> ByteBufCodecs.STRING_UTF8.encode(buf, payload.schematicName()),
            buf -> new SchematicRequestPayload(ByteBufCodecs.STRING_UTF8.decode(buf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}