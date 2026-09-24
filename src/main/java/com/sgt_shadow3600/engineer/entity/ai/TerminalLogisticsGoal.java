package com.sgt_shadow3600.engineer.entity.ai;

import com.sgt_shadow3600.engineer.block.TerminalBlock;
import com.sgt_shadow3600.engineer.entity.EngineerCompanionEntity;
import com.sgt_shadow3600.engineer.entity.ai.utils.AIUtils;
import com.sgt_shadow3600.engineer.entity.ai.utils.LogisticsUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import java.util.EnumSet;

public class TerminalLogisticsGoal extends Goal {
    private final EngineerCompanionEntity npc;
    private boolean hasNotified = false;
    private BlockPos returnPos = null;

    private int logisticsState = 0;
    private int cooldown = 0;
    private int pathingCooldown = 0;
    private int timeoutTicks = 0;
    private int waitWanderTimer = 0;
    private boolean cachedUpgradeCheck = false;

    public TerminalLogisticsGoal(EngineerCompanionEntity npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (npc.getLinkedTerminal() == null) return false;
        if (npc.getCurrentTask() == EngineerCompanionEntity.CompanionTask.PREPARE_WARP) return true;
        if (npc.getCurrentTask() == EngineerCompanionEntity.CompanionTask.SYSTEM_HALT) return false;

        // If actively fighting, don't drop combat to get logistics UNLESS health is critical
        if (npc.getTarget() != null && !npc.inventory.getStackInSlot(3).isEmpty() && !LogisticsUtils.needsHealing(npc)) return false;

        boolean isWorking = (npc.getCurrentTask() == EngineerCompanionEntity.CompanionTask.DIGGING || npc.getCurrentTask() == EngineerCompanionEntity.CompanionTask.BUILD_HUT);

        // 1. Normal priority triggers (Only trigger dumps/resupplies if actively working)
        if (isWorking) {
            if (LogisticsUtils.needsToDump(npc) || !npc.getMissingSupplies().isEmpty() || npc.isNeedsBuildingBlocks()) return true;
        }

        // Always flee to heal regardless of task
        if (LogisticsUtils.needsHealing(npc)) return true;

        // 2. Proximity-based dynamic equipment upgrades. Throttled based on task state.
        int checkInterval = npc.isNewJob ? 1 : (isWorking ? 60 : 10);

        if (npc.tickCount % checkInterval == 0) {
            if (npc.level() instanceof ServerLevel sl && sl.dimension() == npc.getLinkedTerminal().dimension()) {
                this.cachedUpgradeCheck = LogisticsUtils.hasEquipmentUpgradeAvailable(npc, sl, npc.getLinkedTerminal().pos());
            } else {
                this.cachedUpgradeCheck = false;
            }
        }

        return this.cachedUpgradeCheck;
    }

    @Override
    public boolean canContinueToUse() {
        return logisticsState > 0 && logisticsState != 5;
    }

    @Override
    public void start() {
        this.hasNotified = false; this.returnPos = null; this.logisticsState = 0;
        this.cooldown = 0; this.pathingCooldown = 0; this.timeoutTicks = 0;
        this.waitWanderTimer = 0;
        this.cachedUpgradeCheck = false;
        npc.getNavigation().stop();
    }

    @Override
    public void stop() {
        this.logisticsState = 0;
        this.cachedUpgradeCheck = false;
        this.returnPos = null;
    }

    @Override
    public void tick() {
        if (!(npc.level() instanceof ServerLevel level)) return;
        BlockPos terminalPos = npc.getLinkedTerminal().pos();

        if (level.dimension() != npc.getLinkedTerminal().dimension()) {
            handleCrossDimensionLogistics(level, terminalPos);
            return;
        }

        BlockState bs = level.getBlockState(terminalPos);
        Direction facing = bs.hasProperty(TerminalBlock.FACING) ? bs.getValue(TerminalBlock.FACING) : Direction.NORTH;

        if (logisticsState == 0) {
            this.returnPos = npc.blockPosition();

            if (npc.distanceToSqr(terminalPos.getX() + 0.5, terminalPos.getY(), terminalPos.getZ() + 0.5) > 64.0) {
                BlockPos safeWaypoint = AIUtils.getSafeDropZone(level, terminalPos.relative(facing, 3));
                AIUtils.executeEnderWarp(npc, safeWaypoint);
            }
            logisticsState = 1; timeoutTicks = 0;
        }
        else if (logisticsState == 1) {
            BlockPos finalPos = terminalPos.relative(facing, 1);
            if (pathingCooldown > 0) pathingCooldown--;
            if (npc.distanceToSqr(finalPos.getX() + 0.5, finalPos.getY(), finalPos.getZ() + 0.5) < 2.0 || ++timeoutTicks > 100) {
                logisticsState = 2; timeoutTicks = 0;
                npc.getLookControl().setLookAt(terminalPos.getX() + 0.5, terminalPos.getY() + 1.0, terminalPos.getZ() + 0.5);
            } else if (npc.getNavigation().isDone() && pathingCooldown <= 0) {
                npc.getNavigation().moveTo(finalPos.getX() + 0.5, finalPos.getY(), finalPos.getZ() + 0.5, 1.0D); pathingCooldown = 10;
            }
        }
        else if (logisticsState == 2) {
            if (cooldown == 0) {
                LogisticsUtils.executeLogistics(npc, level, terminalPos);
                npc.experienceManager.addExperience(5); // INJECTED: Logistics XP
            }

            if (++cooldown > 22) {
                boolean missingBlocks = npc.isNeedsBuildingBlocks();
                boolean missingPickaxe = npc.inventory.getStackInSlot(0).isEmpty();
                boolean missingTorches = npc.inventory.getStackInSlot(8).isEmpty();

                if (missingBlocks || missingPickaxe || missingTorches) {
                    if (!hasNotified) {
                        String mStr = "";
                        if (missingBlocks) mStr += "Blocks, ";
                        if (missingPickaxe) mStr += "Pickaxe, ";
                        if (missingTorches) mStr += "Torches, ";
                        if (!mStr.isEmpty()) mStr = mStr.substring(0, mStr.length() - 2);

                        AIUtils.sendOwnerAlert(npc, "[Logistics Alert]", "§e", "is waiting at the Terminal! Needs: " + mStr);
                        hasNotified = true;
                    }
                    logisticsState = 10;
                    cooldown = 0;
                    waitWanderTimer = 0;
                    return;
                }
                logisticsState = 3; timeoutTicks = 0;
            }
        }
        else if (logisticsState == 10) {
            if (++cooldown > 100) {
                LogisticsUtils.executeLogistics(npc, level, terminalPos);
                npc.experienceManager.addExperience(5); // INJECTED: Logistics XP

                boolean missingBlocks = npc.isNeedsBuildingBlocks();
                boolean missingPickaxe = npc.inventory.getStackInSlot(0).isEmpty();
                boolean missingTorches = npc.inventory.getStackInSlot(8).isEmpty();

                if (!missingBlocks && !missingPickaxe && !missingTorches) {
                    logisticsState = 3;
                    timeoutTicks = 0;
                    hasNotified = false;
                    AIUtils.sendOwnerAlert(npc, "[Logistics]", "§a", "Supplies acquired! Resuming operation.");
                }
                cooldown = 0;
            }

            if (--waitWanderTimer <= 0) {
                waitWanderTimer = 40 + npc.getRandom().nextInt(60);
                net.minecraft.world.phys.Vec3 pos = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPos(npc, 6, 4);
                if (pos != null) {
                    BlockPos wanderPos = net.minecraft.core.BlockPos.containing(pos);
                    if (wanderPos.distSqr(terminalPos) < 144) {
                        npc.getNavigation().moveTo(pos.x, pos.y, pos.z, 0.7D);
                    }
                }
            }
        }
        else if (logisticsState == 3) {
            BlockPos waypoint = terminalPos.relative(facing, 3);
            if (pathingCooldown > 0) pathingCooldown--;
            if (npc.distanceToSqr(waypoint.getX() + 0.5, waypoint.getY(), waypoint.getZ() + 0.5) < 2.0 || ++timeoutTicks > 100) {
                logisticsState = 4;
            } else if (npc.getNavigation().isDone() && pathingCooldown <= 0) {
                npc.getNavigation().moveTo(waypoint.getX() + 0.5, waypoint.getY(), waypoint.getZ() + 0.5, 1.0D); pathingCooldown = 10;
            }
        }
        else if (logisticsState == 4) {
            if (npc.getCurrentTask() == EngineerCompanionEntity.CompanionTask.PREPARE_WARP && npc.getWarpTarget() != null) {
                net.minecraft.server.level.ServerPlayer targetPlayer = level.getServer().getPlayerList().getPlayer(npc.getWarpTarget());
                if (targetPlayer != null) {
                    ServerLevel targetLevel = targetPlayer.serverLevel();
                    if (targetLevel != level) {
                        AIUtils.playEnderPopVFX(npc);
                        npc.setCurrentTask(EngineerCompanionEntity.CompanionTask.FOLLOW);
                        npc.changeDimension(new net.minecraft.world.level.portal.DimensionTransition(
                                targetLevel, targetPlayer.position(), net.minecraft.world.phys.Vec3.ZERO, npc.getYRot(), npc.getXRot(), net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING
                        ));
                    } else {
                        npc.setCurrentTask(EngineerCompanionEntity.CompanionTask.FOLLOW);
                        AIUtils.executeEnderWarp(npc, targetPlayer.blockPosition());
                    }
                } else {
                    npc.setCurrentTask(EngineerCompanionEntity.CompanionTask.IDLE);
                }
                npc.setWarpTarget(null);
            } else if (returnPos != null) {
                if (npc.distanceToSqr(returnPos.getX() + 0.5, returnPos.getY(), returnPos.getZ() + 0.5) > 64.0) {
                    AIUtils.executeEnderWarp(npc, returnPos);
                }
            }
            logisticsState = 5;
        }
    }

    private void handleCrossDimensionLogistics(ServerLevel currentLevel, BlockPos terminalPos) {
        ServerLevel targetLevel = currentLevel.getServer().getLevel(npc.getLinkedTerminal().dimension());
        if (targetLevel != null && targetLevel.isLoaded(terminalPos)) {
            if (++cooldown == 1) AIUtils.playEnderPopVFX(npc);

            if (cooldown == 40) {
                LogisticsUtils.executeLogistics(npc, targetLevel, terminalPos);
                npc.experienceManager.addExperience(5); // INJECTED: Logistics XP
            }

            if (cooldown > 62) {
                boolean missingBlocks = npc.isNeedsBuildingBlocks();
                boolean missingPickaxe = npc.inventory.getStackInSlot(0).isEmpty();
                boolean missingTorches = npc.inventory.getStackInSlot(8).isEmpty();

                if (missingBlocks || missingPickaxe || missingTorches) {
                    if (!hasNotified) {
                        String mStr = "";
                        if (missingBlocks) mStr += "Blocks, ";
                        if (missingPickaxe) mStr += "Pickaxe, ";
                        if (missingTorches) mStr += "Torches, ";
                        if (!mStr.isEmpty()) mStr = mStr.substring(0, mStr.length() - 2);

                        AIUtils.sendOwnerAlert(npc, "[Quantum Uplink Alert]", "§e", "requires supplies in base terminal! Needs: " + mStr);
                        hasNotified = true;
                    }
                    cooldown = -40;
                    return;
                }

                if (hasNotified) {
                    AIUtils.sendOwnerAlert(npc, "[Quantum Uplink]", "§a", "Supplies acquired remotely! Resuming operation.");
                    hasNotified = false;
                }

                AIUtils.playEnderPopVFX(npc);

                if (npc.getCurrentTask() == EngineerCompanionEntity.CompanionTask.PREPARE_WARP && npc.getWarpTarget() != null) {
                    net.minecraft.server.level.ServerPlayer tp = currentLevel.getServer().getPlayerList().getPlayer(npc.getWarpTarget());
                    if (tp != null) {
                        if (tp.serverLevel() != currentLevel) {
                            npc.setCurrentTask(EngineerCompanionEntity.CompanionTask.FOLLOW);
                            npc.changeDimension(new net.minecraft.world.level.portal.DimensionTransition(tp.serverLevel(), tp.position(), net.minecraft.world.phys.Vec3.ZERO, npc.getYRot(), npc.getXRot(), net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING));
                        } else {
                            npc.setPos(tp.getX(), tp.getY(), tp.getZ());
                            npc.setCurrentTask(EngineerCompanionEntity.CompanionTask.FOLLOW);
                        }
                    } else npc.setCurrentTask(EngineerCompanionEntity.CompanionTask.IDLE);
                    npc.setWarpTarget(null);
                }
                logisticsState = 5;
            }
        } else {
            if (!hasNotified) {
                AIUtils.sendOwnerAlert(npc, "[Uplink Failed]", "§c", "Base chunk in " + npc.getLinkedTerminal().dimension().location().getPath() + " is unloaded! Entering IDLE state.");
                hasNotified = true;
            }
            npc.setCurrentTask(EngineerCompanionEntity.CompanionTask.IDLE);
            logisticsState = 5;
        }
    }
}