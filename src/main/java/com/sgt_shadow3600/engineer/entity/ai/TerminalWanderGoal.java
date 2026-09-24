package com.sgt_shadow3600.engineer.entity.ai;

import com.sgt_shadow3600.engineer.entity.EngineerCompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class TerminalWanderGoal extends Goal {
    private final EngineerCompanionEntity npc;
    private final double speedModifier;
    private double targetX, targetY, targetZ;

    public TerminalWanderGoal(EngineerCompanionEntity npc, double speedModifier) {
        this.npc = npc;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        // ARCHITECT FIX: Allow wandering if IDLE (short term) or GUARD (permanent patrol)
        if (npc.getCurrentTask() != EngineerCompanionEntity.CompanionTask.IDLE && npc.getCurrentTask() != EngineerCompanionEntity.CompanionTask.GUARD) return false;

        if (npc.getRandom().nextInt(40) != 0) return false;

        BlockPos home = npc.getHomeTerminal();
        if (home == null) return false;

        // If outside 32 blocks (1024 sqr), force path back towards the terminal
        if (npc.distanceToSqr(Vec3.atCenterOf(home)) > 1024.0D) {
            this.targetX = home.getX() + 0.5;
            this.targetY = home.getY();
            this.targetZ = home.getZ() + 0.5;
            return true;
        }

        Vec3 pos = DefaultRandomPos.getPos(this.npc, 10, 7);
        if (pos == null) return false;

        if (pos.distanceToSqr(Vec3.atCenterOf(home)) > 1024.0D) {
            return false; // Reject random positions outside the 32-block safe zone
        }

        this.targetX = pos.x;
        this.targetY = pos.y;
        this.targetZ = pos.z;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return !this.npc.getNavigation().isDone();
    }

    @Override
    public void start() {
        this.npc.getNavigation().moveTo(this.targetX, this.targetY, this.targetZ, this.speedModifier);
    }
}