package com.sgt_shadow3600.engineer.network;

import com.sgt_shadow3600.engineer.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public class SaveBuildConfigHandler {

    public static void handleData(final SaveBuildConfigPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ItemStack slate = player.getMainHandItem();

            if (slate.getItem() instanceof com.sgt_shadow3600.engineer.item.CommandSlateItem) {

                if (payload.action().equals("SAVE_ONLY")) {
                    CompoundTag tag = new CompoundTag();
                    tag.putInt("shape", payload.shape());
                    tag.putInt("width", payload.width());
                    tag.putInt("length", payload.length());
                    tag.putInt("height", payload.height());
                    tag.putInt("fillMode", payload.fillMode());
                    tag.putInt("offsetX", payload.offsetX());
                    tag.putInt("offsetY", payload.offsetY());
                    tag.putInt("offsetZ", payload.offsetZ());
                    tag.putString("blockName", payload.blockName());
                    tag.putBoolean("isAdvanced", payload.isAdvanced());
                    tag.putString("schematicName", payload.schematicName());
                    tag.putBoolean("clearSite", payload.clearSite());

                    CompoundTag subs = new CompoundTag();
                    payload.substitutions().forEach(subs::putString);
                    tag.put("substitutions", subs);

                    slate.set(ModDataComponents.PENDING_BUILD_CONFIG.get(), tag);
                    slate.remove(ModDataComponents.PENDING_DIG_CONFIG.get());

                    player.displayClientMessage(Component.literal("§aBuild Config Saved. Shift-Right-Click to Anchor.§r"), true);
                } else {
                    BlockPos anchorPos = BlockPos.ZERO;

                    // ARCHITECT FIX: Always respect the payload direction (which holds the player's UI rotation)
                    int dirValue = payload.direction();

                    if (payload.action().equals("QUEUE_CHAINED")) {
                        anchorPos = new BlockPos(0, -500, 0);
                    } else if (payload.action().equals("QUEUE_ANCHORED")) {
                        CustomData data = slate.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
                        CompoundTag sTag = data.copyTag();

                        if (sTag.getBoolean("IsAnchored")) {
                            int ax = sTag.getInt("AnchorX") + payload.offsetX();
                            int ay = sTag.getInt("AnchorY") + payload.offsetY();
                            int az = sTag.getInt("AnchorZ") + payload.offsetZ();

                            anchorPos = new BlockPos(ax, ay, az);

                            // We explicitly DO NOT overwrite dirValue with AnchorDir here anymore!
                            // This ensures the player's rotation choice is preserved.

                            sTag.putBoolean("IsAnchored", false);
                            slate.set(DataComponents.CUSTOM_DATA, CustomData.of(sTag));
                        } else {
                            player.displayClientMessage(Component.literal("§cNo anchor set! Save config and Shift-Right-Click a block first.§r"), true);
                            return;
                        }
                    }

                    int selectedIndex = slate.getOrDefault(ModDataComponents.ACTIVE_TERMINAL.get(), 0);
                    List<GlobalPos> terminals = slate.getOrDefault(ModDataComponents.BOUND_TERMINALS.get(), List.of());

                    if (terminals.isEmpty()) {
                        player.displayClientMessage(Component.literal("§cNo terminal bound to Slate!§r"), true);
                        return;
                    }

                    GlobalPos activeTerm = terminals.get(selectedIndex);
                    ServerLevel targetLevel = player.server.getLevel(activeTerm.dimension());

                    if (targetLevel != null && targetLevel.getBlockEntity(activeTerm.pos()) instanceof com.sgt_shadow3600.engineer.block.TerminalBlockEntity terminal) {

                        com.sgt_shadow3600.engineer.entity.ai.utils.JobDefinition job = new com.sgt_shadow3600.engineer.entity.ai.utils.JobDefinition(
                                anchorPos, dirValue, payload.fillMode(), payload.width(), payload.shape(), payload.length(), payload.height(), false, payload.clearSite(), payload.isAdvanced(), payload.schematicName(), payload.substitutions()
                        );

                        if (terminal.isNpcActive && terminal.activeNpcId != null) {
                            net.minecraft.world.entity.Entity e = targetLevel.getEntity(terminal.activeNpcId);
                            if (e instanceof com.sgt_shadow3600.engineer.entity.EngineerCompanionEntity npc) {
                                npc.jobController.queueJob(job);
                                player.displayClientMessage(Component.literal("§aBuild Operation Queued! Run 'Execute Queue' to begin.§r"), true);
                                slate.remove(ModDataComponents.PENDING_BUILD_CONFIG.get());
                                slate.remove(ModDataComponents.PENDING_DIG_CONFIG.get());
                            } else {
                                player.displayClientMessage(Component.literal("§cNPC Entity not found in world! Is it unloaded?§r"), true);
                            }
                        } else {
                            terminal.addSleepingJob(job);
                            player.displayClientMessage(Component.literal("§aBuild Operation Queued to Standby Unit!§r"), true);
                            slate.remove(ModDataComponents.PENDING_BUILD_CONFIG.get());
                            slate.remove(ModDataComponents.PENDING_DIG_CONFIG.get());
                        }
                    } else {
                        player.displayClientMessage(Component.literal("§cTerminal not found or chunk is unloaded!§r"), true);
                    }
                }
            }
        });
    }
}