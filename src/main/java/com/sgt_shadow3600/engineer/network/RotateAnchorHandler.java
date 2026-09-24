package com.sgt_shadow3600.engineer.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;

public class RotateAnchorHandler {
    public static void handleData(final RotateAnchorPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ItemStack slate = player.getMainHandItem();
                if (slate.getItem() instanceof com.sgt_shadow3600.engineer.item.CommandSlateItem) {
                    CustomData data = slate.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
                    CompoundTag tag = data.copyTag();

                    if (tag.getBoolean("IsAnchored")) {
                        int rot = tag.getInt("StructureRotation");
                        rot += payload.scrollDelta() > 0 ? 1 : -1;
                        if (rot > 3) rot = 0;
                        if (rot < 0) rot = 3;
                        tag.putInt("StructureRotation", rot);
                        slate.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                    }
                }
            }
        });
    }
}