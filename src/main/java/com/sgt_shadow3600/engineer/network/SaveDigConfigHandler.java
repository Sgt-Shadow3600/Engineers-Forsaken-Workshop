package com.sgt_shadow3600.engineer.network;

import com.sgt_shadow3600.engineer.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public class SaveDigConfigHandler {

    public static void handleData(final SaveDigConfigPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ItemStack slate = player.getMainHandItem();

            if (slate.getItem() instanceof com.sgt_shadow3600.engineer.item.CommandSlateItem) {

                if (payload.action().equals("SAVE_ONLY")) {
                    List<Integer> digConfig = List.of(
                            payload.depth(), payload.width(), payload.shape(), payload.length(), payload.height(), payload.veinmine() ? 1 : 0
                    );
                    slate.set(ModDataComponents.PENDING_DIG_CONFIG.get(), digConfig);
                    slate.remove(ModDataComponents.PENDING_BUILD_CONFIG.get());
                    player.displayClientMessage(Component.literal("§aConfig Saved. Shift-Right-Click block to Anchor.§r"), true);
                } else {
                    BlockPos anchorPos = BlockPos.ZERO;

                    // ARCHITECT FIX: The default payload direction is the root anchor's direction.
                    // We now preserve this for Chained Jobs instead of overriding it with the player's current look angle.
                    int dirValue = payload.direction();

                    if (payload.action().equals("QUEUE_CHAINED")) {
                        anchorPos = new BlockPos(0, -500, 0); // Magic Anchor
                    } else if (payload.action().equals("QUEUE_ANCHORED")) {
                        CustomData data = slate.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
                        net.minecraft.nbt.CompoundTag tag = data.copyTag();

                        if (tag.getBoolean("IsAnchored")) {
                            anchorPos = new BlockPos(tag.getInt("AnchorX"), tag.getInt("AnchorY"), tag.getInt("AnchorZ"));
                            dirValue = tag.getInt("AnchorDir");
                            tag.putBoolean("IsAnchored", false); // Clear anchor safely
                            slate.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
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
                                anchorPos, dirValue, payload.depth(), payload.width(), payload.shape(), payload.length(), payload.height(), payload.veinmine()
                        );

                        if (terminal.isNpcActive && terminal.activeNpcId != null) {
                            net.minecraft.world.entity.Entity e = targetLevel.getEntity(terminal.activeNpcId);
                            if (e instanceof com.sgt_shadow3600.engineer.entity.EngineerCompanionEntity npc) {
                                npc.jobController.queueJob(job);
                                player.displayClientMessage(Component.literal("§aDig Operation Queued! Run 'Execute Queue' to begin.§r"), true);
                                slate.remove(ModDataComponents.PENDING_DIG_CONFIG.get());
                                slate.remove(ModDataComponents.PENDING_BUILD_CONFIG.get());
                            } else {
                                player.displayClientMessage(Component.literal("§cNPC Entity not found in world! Is it unloaded?§r"), true);
                            }
                        } else {
                            // ARCHITECT FIX: Bypass the storedNpcData requirement so fresh terminals can boot up smoothly.
                            terminal.addSleepingJob(job);
                            player.displayClientMessage(Component.literal("§aDig Operation Queued to Standby Unit!§r"), true);
                            slate.remove(ModDataComponents.PENDING_DIG_CONFIG.get());
                            slate.remove(ModDataComponents.PENDING_BUILD_CONFIG.get());
                        }
                    } else {
                        player.displayClientMessage(Component.literal("§cTerminal not found or chunk is unloaded!§r"), true);
                    }
                }
            }
        });
    }
}