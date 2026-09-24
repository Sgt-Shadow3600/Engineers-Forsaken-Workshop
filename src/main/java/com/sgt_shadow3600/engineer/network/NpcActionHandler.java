package com.sgt_shadow3600.engineer.network;

import com.sgt_shadow3600.engineer.block.TerminalBlockEntity;
import com.sgt_shadow3600.engineer.entity.EngineerCompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class NpcActionHandler {

    public static void handleData(final NpcActionPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            if (payload.action().equals("PURGE")) {
                ItemStack slate = player.getMainHandItem();
                if (slate.getItem() instanceof com.sgt_shadow3600.engineer.item.CommandSlateItem) {
                    slate.remove(com.sgt_shadow3600.engineer.ModDataComponents.PENDING_DIG_CONFIG.get());
                    slate.remove(com.sgt_shadow3600.engineer.ModDataComponents.PENDING_BUILD_CONFIG.get());
                    player.displayClientMessage(Component.literal("§cSlate Configuration Purged.§r"), true);
                }
                return;
            }

            ServerLevel level = player.serverLevel();
            BlockPos terminalPos = payload.terminalPos();

            if (level.getBlockEntity(terminalPos) instanceof TerminalBlockEntity terminal) {

                if (payload.action().equals("PATROL") || payload.action().equals("FOLLOW") || payload.action().equals("START_QUEUE")) {
                    if (!terminal.isNpcActive) {
                        int checkLevel = 1;
                        if (!terminal.storedNpcData.isEmpty() && terminal.storedNpcData.contains("CompanionLevel")) {
                            checkLevel = terminal.storedNpcData.getInt("CompanionLevel");
                        }

                        if (payload.action().equals("PATROL") && checkLevel < 2) {
                            player.displayClientMessage(Component.literal("§c[ERROR] Patrol Mode requires Level 2.§r"), true);
                            return;
                        }

                        terminal.spawnNpc(level, terminalPos);
                    }
                }

                if (terminal.isNpcActive && terminal.activeNpcId != null) {
                    Entity entity = level.getEntity(terminal.activeNpcId);
                    if (entity instanceof EngineerCompanionEntity npc) {

                        // QUEUE MANAGEMENT UI CLICKS
                        if (payload.action().startsWith("Q_UP_")) {
                            int idx = Integer.parseInt(payload.action().split("_")[2]);
                            if (idx > 0 && idx < npc.jobController.jobQueue.size()) {
                                var job = npc.jobController.jobQueue.remove(idx);
                                npc.jobController.jobQueue.add(idx - 1, job);
                            }
                            return;
                        } else if (payload.action().startsWith("Q_DN_")) {
                            int idx = Integer.parseInt(payload.action().split("_")[2]);
                            if (idx >= 0 && idx < npc.jobController.jobQueue.size() - 1) {
                                var job = npc.jobController.jobQueue.remove(idx);
                                npc.jobController.jobQueue.add(idx + 1, job);
                            }
                            return;
                        } else if (payload.action().startsWith("Q_RM_")) {
                            int idx = Integer.parseInt(payload.action().split("_")[2]);
                            if (idx >= 0 && idx < npc.jobController.jobQueue.size()) {
                                npc.jobController.jobQueue.remove(idx);
                            }
                            return;
                        }

                        switch (payload.action()) {
                            case "RESUME" -> {
                                if (npc.jobController.jobSite != null) {
                                    npc.setCurrentTask(EngineerCompanionEntity.CompanionTask.DIGGING);
                                    player.displayClientMessage(Component.literal("§aResuming logistics job.§r"), true);
                                } else {
                                    player.displayClientMessage(Component.literal("§cNo active job to resume.§r"), true);
                                }
                            }
                            case "PATROL" -> {
                                if (npc.experienceManager.getLevel() < 2) {
                                    player.displayClientMessage(Component.literal("§c[ERROR] Patrol Mode requires Level 2.§r"), true);
                                } else {
                                    npc.setCurrentTask(EngineerCompanionEntity.CompanionTask.GUARD);
                                    player.displayClientMessage(Component.literal("§eExecuting Patrol Pattern.§r"), true);
                                }
                            }
                            case "FOLLOW" -> {
                                npc.setCurrentTask(EngineerCompanionEntity.CompanionTask.FOLLOW);
                                player.displayClientMessage(Component.literal("§eExecuting Follow Pattern.§r"), true);
                            }
                            case "RECALL" -> {
                                npc.setCurrentTask(EngineerCompanionEntity.CompanionTask.IDLE);
                                com.sgt_shadow3600.engineer.entity.ai.utils.LogisticsUtils.executeLogistics(npc, level, terminalPos);
                                terminal.storeNpc(npc);
                                player.displayClientMessage(Component.literal("§eUnit instantly recalled to Terminal.§r"), true);
                            }
                            case "TOGGLE_COMBAT" -> {
                                npc.setDefensiveMode(!npc.isDefensiveMode());
                                String mode = npc.isDefensiveMode() ? "Defensive" : "Offensive";
                                player.displayClientMessage(Component.literal("§eCombat Style: " + mode + "§r"), true);
                            }
                            case "LOCATE" -> {
                                npc.addEffect(new MobEffectInstance(MobEffects.GLOWING, 600, 0, false, false));
                                player.displayClientMessage(Component.literal("§bLocation Ping active for 30 seconds.§r"), true);
                            }
                            case "PAUSE" -> {
                                if (npc.getCurrentTask() == EngineerCompanionEntity.CompanionTask.DIGGING || npc.getCurrentTask() == EngineerCompanionEntity.CompanionTask.BUILD_HUT) {
                                    npc.setCurrentTask(EngineerCompanionEntity.CompanionTask.SYSTEM_HALT);
                                    npc.getNavigation().stop();
                                    player.displayClientMessage(Component.literal("§cLogistics Job Paused.§r"), true);
                                } else {
                                    player.displayClientMessage(Component.literal("§cNo active job to pause.§r"), true);
                                }
                            }
                            case "CANCEL_ACTIVE" -> {
                                npc.jobController.jobSite = (null);
                                if (npc.getCurrentTask() == EngineerCompanionEntity.CompanionTask.DIGGING ||
                                        npc.getCurrentTask() == EngineerCompanionEntity.CompanionTask.BUILD_HUT ||
                                        npc.getCurrentTask() == EngineerCompanionEntity.CompanionTask.SYSTEM_HALT) {
                                    npc.setCurrentTask(EngineerCompanionEntity.CompanionTask.IDLE);
                                }
                                player.displayClientMessage(Component.literal("§cActive Job Aborted!§r"), true);
                            }
                            case "CLEAR_QUEUE" -> {
                                npc.jobController.clearJobQueue();
                                npc.jobController.jobSite = (null);
                                if (npc.getCurrentTask() == EngineerCompanionEntity.CompanionTask.DIGGING ||
                                        npc.getCurrentTask() == EngineerCompanionEntity.CompanionTask.BUILD_HUT ||
                                        npc.getCurrentTask() == EngineerCompanionEntity.CompanionTask.SYSTEM_HALT) {
                                    npc.setCurrentTask(EngineerCompanionEntity.CompanionTask.IDLE);
                                }
                                player.displayClientMessage(Component.literal("§cJob Queue & Active Job Cleared!§r"), true);
                            }
                            case "START_QUEUE" -> {
                                if (npc.jobController.jobSite == null) {
                                    if (npc.jobController.startNextJob()) {
                                        player.displayClientMessage(Component.literal("§aExecuting next job in queue.§r"), true);
                                    } else {
                                        player.displayClientMessage(Component.literal("§cNo Job Configured in Queue.§r"), true);
                                    }
                                } else {
                                    npc.setCurrentTask(EngineerCompanionEntity.CompanionTask.DIGGING);
                                    player.displayClientMessage(Component.literal("§aResuming Job Execution.§r"), true);
                                }
                            }
                        }
                    }
                } else if (!terminal.isNpcActive && !terminal.storedNpcData.isEmpty()) {
                    // SLEEPING NPC - QUEUE MANAGEMENT VIA NBT
                    if (payload.action().startsWith("Q_")) {
                        net.minecraft.nbt.ListTag queueList = terminal.storedNpcData.getList("JobQueue", 10);

                        if (payload.action().startsWith("Q_UP_")) {
                            int idx = Integer.parseInt(payload.action().split("_")[2]);
                            if (idx > 0 && idx < queueList.size()) {
                                var job = queueList.remove(idx);
                                queueList.add(idx - 1, job);
                            }
                        } else if (payload.action().startsWith("Q_DN_")) {
                            int idx = Integer.parseInt(payload.action().split("_")[2]);
                            if (idx >= 0 && idx < queueList.size() - 1) {
                                var job = queueList.remove(idx);
                                queueList.add(idx + 1, job);
                            }
                        } else if (payload.action().startsWith("Q_RM_")) {
                            int idx = Integer.parseInt(payload.action().split("_")[2]);
                            if (idx >= 0 && idx < queueList.size()) {
                                queueList.remove(idx);
                            }
                        }
                        terminal.storedNpcData.put("JobQueue", queueList);
                        terminal.sync();
                        return;
                    }

                    switch (payload.action()) {
                        case "CLEAR_QUEUE" -> {
                            terminal.storedNpcData.remove("JobQueue");
                            terminal.storedNpcData.remove("JobSite");
                            terminal.sync();
                            player.displayClientMessage(Component.literal("§cStandby Unit Job Queue Cleared!§r"), true);
                        }
                        case "CANCEL_ACTIVE" -> {
                            terminal.storedNpcData.remove("JobSite");
                            terminal.sync();
                            player.displayClientMessage(Component.literal("§cStandby Unit Active Job Aborted!§r"), true);
                        }
                    }
                }
            }
        });
    }
}