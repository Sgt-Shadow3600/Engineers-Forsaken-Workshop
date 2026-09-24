package com.sgt_shadow3600.engineer.network;

import com.sgt_shadow3600.engineer.EngineerCompanion;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;

public record RadarPingPayload(BlockPos pos) implements CustomPacketPayload {

    public static final Type<RadarPingPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "radar_ping"));

    public static final StreamCodec<FriendlyByteBuf, RadarPingPayload> STREAM_CODEC = StreamCodec.ofMember(
            RadarPingPayload::write,
            RadarPingPayload::new
    );

    public RadarPingPayload(FriendlyByteBuf buf) {
        this(buf.readBlockPos());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}