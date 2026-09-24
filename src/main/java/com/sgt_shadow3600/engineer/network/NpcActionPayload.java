package com.sgt_shadow3600.engineer.network;

import com.sgt_shadow3600.engineer.EngineerCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record NpcActionPayload(BlockPos terminalPos, String action) implements CustomPacketPayload {

    public static final Type<NpcActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "npc_action"));

    public static final StreamCodec<FriendlyByteBuf, NpcActionPayload> STREAM_CODEC = StreamCodec.ofMember(
            NpcActionPayload::write,
            NpcActionPayload::new
    );

    public NpcActionPayload(FriendlyByteBuf buf) {
        this(buf.readBlockPos(), ByteBufCodecs.STRING_UTF8.decode(buf));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.terminalPos);
        ByteBufCodecs.STRING_UTF8.encode(buf, this.action);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}