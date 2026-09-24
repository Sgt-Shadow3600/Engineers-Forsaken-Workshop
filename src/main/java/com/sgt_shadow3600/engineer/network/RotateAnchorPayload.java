package com.sgt_shadow3600.engineer.network;

import com.sgt_shadow3600.engineer.EngineerCompanion;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RotateAnchorPayload(int scrollDelta) implements CustomPacketPayload {
    public static final Type<RotateAnchorPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "rotate_anchor"));
    public static final StreamCodec<FriendlyByteBuf, RotateAnchorPayload> STREAM_CODEC = StreamCodec.ofMember(RotateAnchorPayload::write, RotateAnchorPayload::new);

    public RotateAnchorPayload(FriendlyByteBuf buf) {
        this(buf.readInt());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(this.scrollDelta);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}