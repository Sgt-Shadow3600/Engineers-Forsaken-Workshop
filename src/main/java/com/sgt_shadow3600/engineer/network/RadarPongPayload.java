package com.sgt_shadow3600.engineer.network;

import com.sgt_shadow3600.engineer.EngineerCompanion;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RadarPongPayload(
        int task,
        int level,
        int xp, // ARCHITECT FIX: Injected XP data for telemetry
        int health,
        int maxHealth,
        int armor,
        boolean isLoaded,
        String missingSupplies,
        CompoundTag tools,
        boolean defensive,
        CompoundTag queueData
) implements CustomPacketPayload {

    public static final Type<RadarPongPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "radar_pong"));

    public static final StreamCodec<FriendlyByteBuf, RadarPongPayload> STREAM_CODEC = StreamCodec.ofMember(
            RadarPongPayload::write,
            RadarPongPayload::new
    );

    public RadarPongPayload(FriendlyByteBuf buf) {
        this(
                buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readBoolean(),
                buf.readUtf(), buf.readNbt(), buf.readBoolean(), buf.readNbt()
        );
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(this.task); buf.writeInt(this.level); buf.writeInt(this.xp); buf.writeInt(this.health); buf.writeInt(this.maxHealth);
        buf.writeInt(this.armor); buf.writeBoolean(this.isLoaded); buf.writeUtf(this.missingSupplies);
        buf.writeNbt(this.tools); buf.writeBoolean(this.defensive); buf.writeNbt(this.queueData);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}