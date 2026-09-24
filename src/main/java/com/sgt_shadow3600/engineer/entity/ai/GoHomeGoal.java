package com.sgt_shadow3600.engineer.entity.ai;

import com.sgt_shadow3600.engineer.entity.EngineerCompanionEntity;
import com.sgt_shadow3600.engineer.block.TerminalBlock;
import com.sgt_shadow3600.engineer.block.TerminalBlockEntity;
import com.sgt_shadow3600.engineer.entity.ai.utils.AIUtils;
import com.sgt_shadow3600.engineer.entity.ai.utils.LogisticsUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import java.util.EnumSet;

public class GoHomeGoal extends Goal {
    private final EngineerCompanionEntity npc;
    private BlockPos finalStandPos = null;
    private boolean hasTeleported = false;
    private int pathingCooldown = 0;
    private int forceSleepTimer = 0;

    public GoHomeGoal(EngineerCompanionEntity npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return npc.getCurrentTask() == EngineerCompanionEntity.CompanionTask.GO_HOME && npc.getLinkedTerminal() != null;
    }

    @Override
    public void start() {
        this.forceSleepTimer = 0;
        if (npc.level().dimension() != npc.getLinkedTerminal().dimension()) {
            executeEmergencyRecall();
            return;
        }

        BlockPos termPos = npc.getHomeTerminal();
        BlockState state = npc.level().getBlockState(termPos);
        Direction facing = state.hasProperty(TerminalBlock.FACING) ? state.getValue(TerminalBlock.FACING) : Direction.NORTH;

        this.finalStandPos = termPos.relative(facing);
        this.hasTeleported = (npc.distanceToSqr(termPos.getX() + 0.5, termPos.getY(), termPos.getZ() + 0.5) < 100.0);
        this.pathingCooldown = 0;
        npc.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.finalStandPos == null || !(npc.level() instanceof ServerLevel level)) return;

        if (++this.forceSleepTimer >= 400) {
            executeEmergencyRecall();
            return;
        }

        if (!hasTeleported) {
            BlockPos termPos = npc.getHomeTerminal();
            Direction facing = level.getBlockState(termPos).hasProperty(TerminalBlock.FACING) ? level.getBlockState(termPos).getValue(TerminalBlock.FACING) : Direction.NORTH;

            int dist = 3 + npc.getRandom().nextInt(3);
            int lateralOffset = (npc.getRandom().nextBoolean() ? 1 : -1) * npc.getRandom().nextInt(2);
            BlockPos rawTpPos = termPos.relative(facing, dist);
            rawTpPos = (facing.getAxis() == Direction.Axis.Z) ? rawTpPos.offset(lateralOffset, 0, 0) : rawTpPos.offset(0, 0, lateralOffset);

            BlockPos safePos = AIUtils.getSafeDropZone(level, rawTpPos, finalStandPos);
            AIUtils.executeEnderWarp(npc, safePos);

            this.hasTeleported = true;
            npc.getNavigation().moveTo(finalStandPos.getX() + 0.5, finalStandPos.getY(), finalStandPos.getZ() + 0.5, 1.0D);
        } else {
            if (pathingCooldown > 0) pathingCooldown--;
            if (npc.distanceToSqr(finalStandPos.getX() + 0.5, finalStandPos.getY(), finalStandPos.getZ() + 0.5) < 2.0) {
                if (npc.level().getBlockEntity(npc.getHomeTerminal()) instanceof TerminalBlockEntity terminal) {
                    AIUtils.executeEnderWarp(npc, npc.blockPosition()); // Visual pop

                    LogisticsUtils.executeLogistics(npc, level, npc.getHomeTerminal());

                    // ARCHITECT FIX: Safely retain the active task instead of erasing it!
                    EngineerCompanionEntity.CompanionTask nextTask = EngineerCompanionEntity.CompanionTask.IDLE;
                    if (npc.jobController.jobSite != null) {
                        nextTask = (npc.jobController.jobShape >= 10 || npc.jobController.jobShape == 99) ? EngineerCompanionEntity.CompanionTask.BUILD_HUT : EngineerCompanionEntity.CompanionTask.DIGGING;
                    }
                    npc.setCurrentTask(nextTask);

                    terminal.storeNpc(npc);
                }
            } else if (npc.getNavigation().isDone() && pathingCooldown <= 0) {
                npc.getNavigation().moveTo(finalStandPos.getX() + 0.5, finalStandPos.getY(), finalStandPos.getZ() + 0.5, 1.0D);
                pathingCooldown = 10;
            }
        }
    }

    private void executeEmergencyRecall() {
        if (npc.level().getServer() != null) {
            ServerLevel targetLevel = npc.level().getServer().getLevel(npc.getLinkedTerminal().dimension());
            if (targetLevel != null && targetLevel.isLoaded(npc.getHomeTerminal())) {
                if (targetLevel.getBlockEntity(npc.getHomeTerminal()) instanceof TerminalBlockEntity terminal) {
                    AIUtils.executeEnderWarp(npc, npc.blockPosition());

                    LogisticsUtils.executeLogistics(npc, targetLevel, npc.getHomeTerminal());

                    EngineerCompanionEntity.CompanionTask nextTask = EngineerCompanionEntity.CompanionTask.IDLE;
                    if (npc.jobController.jobSite != null) {
                        nextTask = (npc.jobController.jobShape >= 10 || npc.jobController.jobShape == 99) ? EngineerCompanionEntity.CompanionTask.BUILD_HUT : EngineerCompanionEntity.CompanionTask.DIGGING;
                    }
                    npc.setCurrentTask(nextTask);

                    terminal.storeNpc(npc);
                }
            } else {
                AIUtils.sendOwnerAlert(npc, "[SYSTEM ALERT]", "§c", "Base chunk in " + npc.getLinkedTerminal().dimension().location().getPath() + " is unloaded. Recall failed.");
                npc.setCurrentTask(EngineerCompanionEntity.CompanionTask.IDLE);
            }
        }
    }
}