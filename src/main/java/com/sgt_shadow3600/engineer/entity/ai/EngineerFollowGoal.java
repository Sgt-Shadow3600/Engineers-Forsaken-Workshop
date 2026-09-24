package com.sgt_shadow3600.engineer.entity.ai;

import com.sgt_shadow3600.engineer.entity.EngineerCompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

public class EngineerFollowGoal extends Goal {
    private final EngineerCompanionEntity npc;
    private Player owner;
    private final double speedModifier;
    private int timeToRecalcPath;

    public EngineerFollowGoal(EngineerCompanionEntity npc, double speedModifier) {
        this.npc = npc;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (npc.getCurrentTask() != EngineerCompanionEntity.CompanionTask.FOLLOW) return false;
        if (npc.getOwnerUUID() == null) return false;
        Player player = npc.level().getPlayerByUUID(npc.getOwnerUUID());
        if (player == null) return false;
        if (npc.distanceTo(player) <= 6.0D) return false; // Only start pathing if > 6 blocks away
        this.owner = player;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (npc.getCurrentTask() != EngineerCompanionEntity.CompanionTask.FOLLOW) return false;
        if (this.owner == null || !this.owner.isAlive()) return false;
        return npc.distanceTo(this.owner) > 3.0D;
    }

    @Override
    public void start() {
        this.timeToRecalcPath = 0;
    }

    @Override
    public void stop() {
        this.owner = null;
        this.npc.getNavigation().stop();
    }

    @Override
    public void tick() {
        this.npc.getLookControl().setLookAt(this.owner, 10.0F, (float)this.npc.getMaxHeadXRot());
        if (--this.timeToRecalcPath <= 0) {
            this.timeToRecalcPath = 10;
            double dist = this.npc.distanceTo(this.owner);
            boolean isAttacking = this.npc.getTarget() != null && this.npc.getTarget().isAlive();

            if (dist > 20.0D) {
                this.npc.setTarget(null); // Abandon attack
                this.teleportToOwner();
            } else if (dist > 16.0D && !isAttacking) {
                this.teleportToOwner();
            } else if (dist > 6.0D) {
                this.npc.getNavigation().moveTo(this.owner, this.speedModifier);
            } else {
                this.npc.getNavigation().stop();
            }
        }
    }

    private void teleportToOwner() {
        BlockPos pos = this.owner.blockPosition();
        for (int i = 0; i < 10; i++) {
            int dx = this.npc.getRandom().nextInt(5) - 2;
            int dz = this.npc.getRandom().nextInt(5) - 2;
            int dy = this.npc.getRandom().nextInt(3) - 1;
            BlockPos targetPos = pos.offset(dx, dy, dz);

            // Gravitational Raytrace: If player is flying, scan downwards up to 64 blocks to find solid ground
            int drop = 0;
            while (!this.npc.level().getBlockState(targetPos.below()).blocksMotion() && drop < 64 && targetPos.getY() > this.npc.level().getMinBuildHeight()) {
                targetPos = targetPos.below();
                drop++;
            }

            if (this.npc.level().getBlockState(targetPos.below()).blocksMotion() && this.npc.level().isEmptyBlock(targetPos) && this.npc.level().isEmptyBlock(targetPos.above())) {
                this.npc.moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, this.npc.getYRot(), this.npc.getXRot());
                this.npc.getNavigation().stop();
                return;
            }
        }
    }
}