package com.sgt_shadow3600.engineer.network;

import com.sgt_shadow3600.engineer.EngineerCompanion;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public record SchematicResponsePayload(boolean success, String schematicName, Map<String, Integer> materials, CompoundTag structureData) implements CustomPacketPayload {

    public static final Type<SchematicResponsePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "schematic_response"));

    public static final StreamCodec<FriendlyByteBuf, SchematicResponsePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.success());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.schematicName());
                buf.writeInt(payload.materials().size());
                for (Map.Entry<String, Integer> entry : payload.materials().entrySet()) {
                    ByteBufCodecs.STRING_UTF8.encode(buf, entry.getKey());
                    buf.writeInt(entry.getValue());
                }
                buf.writeNbt(payload.structureData());
            },
            buf -> {
                boolean success = buf.readBoolean();
                String name = ByteBufCodecs.STRING_UTF8.decode(buf);
                int size = buf.readInt();
                Map<String, Integer> materials = new HashMap<>();
                for (int i = 0; i < size; i++) {
                    materials.put(ByteBufCodecs.STRING_UTF8.decode(buf), buf.readInt());
                }
                CompoundTag tag = buf.readNbt();
                return new SchematicResponsePayload(success, name, materials, tag);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}