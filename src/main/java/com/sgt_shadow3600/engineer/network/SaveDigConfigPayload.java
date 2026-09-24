package com.sgt_shadow3600.engineer.network;

import com.sgt_shadow3600.engineer.EngineerCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SaveDigConfigPayload(
        int direction,
        int depth,
        int width,
        int shape,
        int length,
        int height,
        boolean veinmine,
        String action,
        BlockPos terminalPos
) implements CustomPacketPayload {

    public static final Type<SaveDigConfigPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "save_dig_config"));

    public static final StreamCodec<FriendlyByteBuf, SaveDigConfigPayload> STREAM_CODEC = StreamCodec.ofMember(SaveDigConfigPayload::write, SaveDigConfigPayload::new);

    public SaveDigConfigPayload(FriendlyByteBuf buf) {
        this(
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readBoolean(),
                buf.readUtf(),
                buf.readBlockPos()
        );
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(this.direction);
        buf.writeInt(this.depth);
        buf.writeInt(this.width);
        buf.writeInt(this.shape);
        buf.writeInt(this.length);
        buf.writeInt(this.height);
        buf.writeBoolean(this.veinmine);
        buf.writeUtf(this.action);
        buf.writeBlockPos(this.terminalPos);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}