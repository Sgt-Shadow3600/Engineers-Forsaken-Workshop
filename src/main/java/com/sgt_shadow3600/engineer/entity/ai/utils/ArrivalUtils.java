package com.sgt_shadow3600.engineer.entity.ai.utils;

import com.sgt_shadow3600.engineer.entity.EngineerCompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

public class ArrivalUtils {

    public static int determineInitialState(EngineerCompanionEntity npc, BlockPos target, double maxRadius) {
        if (target == null) return 0;
        double distSq = npc.distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        double warpThreshold = Math.pow(maxRadius + 16.0, 2);
        double walkThreshold = Math.pow(maxRadius + 4.0, 2);

        if (distSq > warpThreshold) return 1;
        if (distSq > walkThreshold) return 2;
        return 0;
    }

    public static int processArrival(EngineerCompanionEntity npc, BlockPos targetPos, Direction facing, double maxRadius, int currentState, int ticksInState) {
        Level level = npc.level();
        if (targetPos == null) return 0;

        if (currentState == 1) { // WARP
            int baseOffset = 3 + npc.getRandom().nextInt(3);
            int lateralOffset = (npc.getRandom().nextBoolean() ? 1 : -1) * npc.getRandom().nextInt(2);

            BlockPos rawTpPos = targetPos.relative(facing.getOpposite(), baseOffset + (int)maxRadius);
            rawTpPos = (facing.getAxis() == Direction.Axis.Z) ? rawTpPos.offset(lateralOffset, 0, 0) : rawTpPos.offset(0, 0, lateralOffset);

            BlockPos safePos = AIUtils.getSafeDropZone(level, rawTpPos);
            AIUtils.executeEnderWarp(npc, safePos);
            return 2; // Transition to walk
        }

        if (currentState == 2) { // WALK
            double distSq = npc.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
            double walkThreshold = Math.pow(maxRadius + 4.0, 2);

            if (distSq < walkThreshold) {
                npc.getNavigation().stop();
                return 0; // Arrived
            } else {
                BlockPos pathTarget = targetPos.relative(facing.getOpposite(), (int)maxRadius);
                boolean canPath = npc.getNavigation().moveTo(pathTarget.getX() + 0.5, pathTarget.getY(), pathTarget.getZ() + 0.5, 1.0D);
                if (!canPath || ticksInState > 40) {
                    npc.getNavigation().stop();
                    return 0; // Give up and arrive to prevent hard lock
                }
                return 2; // Keep walking
            }
        }

        return 0;
    }
}