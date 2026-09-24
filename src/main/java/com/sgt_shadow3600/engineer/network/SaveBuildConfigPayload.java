package com.sgt_shadow3600.engineer.network;

import com.sgt_shadow3600.engineer.EngineerCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public record SaveBuildConfigPayload(
        int direction,
        int shape,
        int width,
        int length,
        int height,
        int fillMode,
        int offsetX,
        int offsetY,
        int offsetZ,
        String blockName,
        boolean isAdvanced,
        String schematicName,
        boolean clearSite,
        Map<String, String> substitutions,
        String action,
        BlockPos terminalPos
) implements CustomPacketPayload {

    public static final Type<SaveBuildConfigPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "save_build_config"));

    public static final StreamCodec<FriendlyByteBuf, SaveBuildConfigPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.direction());
                buf.writeInt(payload.shape());
                buf.writeInt(payload.width());
                buf.writeInt(payload.length());
                buf.writeInt(payload.height());
                buf.writeInt(payload.fillMode());
                buf.writeInt(payload.offsetX());
                buf.writeInt(payload.offsetY());
                buf.writeInt(payload.offsetZ());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.blockName());
                buf.writeBoolean(payload.isAdvanced());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.schematicName());
                buf.writeBoolean(payload.clearSite());

                buf.writeInt(payload.substitutions().size());
                for (Map.Entry<String, String> entry : payload.substitutions().entrySet()) {
                    ByteBufCodecs.STRING_UTF8.encode(buf, entry.getKey());
                    ByteBufCodecs.STRING_UTF8.encode(buf, entry.getValue());
                }

                ByteBufCodecs.STRING_UTF8.encode(buf, payload.action());
                buf.writeBlockPos(payload.terminalPos());
            },
            buf -> {
                int direction = buf.readInt();
                int shape = buf.readInt();
                int width = buf.readInt();
                int length = buf.readInt();
                int height = buf.readInt();
                int fillMode = buf.readInt();
                int offsetX = buf.readInt();
                int offsetY = buf.readInt();
                int offsetZ = buf.readInt();
                String blockName = ByteBufCodecs.STRING_UTF8.decode(buf);
                boolean isAdvanced = buf.readBoolean();
                String schematicName = ByteBufCodecs.STRING_UTF8.decode(buf);
                boolean clearSite = buf.readBoolean();

                int subSize = buf.readInt();
                Map<String, String> subs = new HashMap<>();
                for (int i = 0; i < subSize; i++) {
                    subs.put(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf));
                }

                String action = ByteBufCodecs.STRING_UTF8.decode(buf);
                BlockPos terminalPos = buf.readBlockPos();

                return new SaveBuildConfigPayload(direction, shape, width, length, height, fillMode, offsetX, offsetY, offsetZ, blockName, isAdvanced, schematicName, clearSite, subs, action, terminalPos);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}