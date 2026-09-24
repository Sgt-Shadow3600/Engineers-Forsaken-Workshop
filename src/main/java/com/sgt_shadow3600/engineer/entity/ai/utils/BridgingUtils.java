package com.sgt_shadow3600.engineer.entity.ai.utils;

import com.sgt_shadow3600.engineer.entity.EngineerCompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class BridgingUtils {

    /**
     * Safely attempts to place a physical bridge or fluid-sealing block.
     * Automatically manages the 'needsBuildingBlocks' logistics flag.
     * Returns true if successful, false if the unit is out of blocks and needs to resupply.
     */
    public static boolean executeSeal(EngineerCompanionEntity npc, Level level, BlockPos pos) {
        if (!DiggingUtils.placeBridgeBlock(npc, level, pos)) {
            npc.setNeedsBuildingBlocks(true);
            return false;
        }
        npc.setNeedsBuildingBlocks(false);
        return true;
    }

    /**
     * Defines what the AI considers a dangerous fluid that must be preemptively plugged.
     * Currently triggers on any block space that contains a fluid state (Water, Lava, Source, or Flowing).
     */
    public static boolean isFluidThreat(Level level, BlockPos pos) {
        return !level.getFluidState(pos).isEmpty();
    }

    /**
     * Defines what the AI considers an unsafe floor space that must be bridged.
     * Triggers if the block has no collision box (air/tall grass) or is a fluid.
     */
    public static boolean isUnsafeGap(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() || isFluidThreat(level, pos);
    }
}