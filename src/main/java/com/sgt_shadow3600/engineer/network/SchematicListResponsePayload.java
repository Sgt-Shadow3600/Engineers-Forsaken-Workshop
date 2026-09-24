package com.sgt_shadow3600.engineer.network;

import com.sgt_shadow3600.engineer.EngineerCompanion;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record SchematicListResponsePayload(List<String> schematics) implements CustomPacketPayload {
    public static final Type<SchematicListResponsePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "schematic_list_res"));

    public static final StreamCodec<FriendlyByteBuf, SchematicListResponsePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.schematics().size());
                for (String s : payload.schematics()) {
                    ByteBufCodecs.STRING_UTF8.encode(buf, s);
                }
            },
            buf -> {
                int size = buf.readInt();
                List<String> list = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    list.add(ByteBufCodecs.STRING_UTF8.decode(buf));
                }
                return new SchematicListResponsePayload(list);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}