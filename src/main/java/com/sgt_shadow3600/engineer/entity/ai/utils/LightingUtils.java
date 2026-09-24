package com.sgt_shadow3600.engineer.entity.ai.utils;

import com.sgt_shadow3600.engineer.entity.EngineerCompanionEntity;
import com.sgt_shadow3600.engineer.entity.ai.shapes.DigShapeLibrary;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;

public class LightingUtils {

    /**
     * Evaluates and places the initial torch at the start of a new job.
     * Returns true if a torch was placed (or had already been placed).
     */
    public static boolean handleStartTorch(EngineerCompanionEntity npc, Level level, DigShapeLibrary.AbstractDigShape currentShape, BlockPos start, Direction dir, boolean placedStartTorch) {
        if (!placedStartTorch && DiggingUtils.hasLightSources(npc)) {
            BlockPos safePos = currentShape.getStartTorchPos(start, dir, npc.jobController.jobLength);
            BlockPos floor = safePos.below();
            if (level.isEmptyBlock(safePos) && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
                level.setBlockAndUpdate(safePos, Blocks.TORCH.defaultBlockState());
                DiggingUtils.consumeTorch(npc);
                return true;
            }
        }
        return placedStartTorch;
    }

    /**
     * Scans the ambient light and places Wall Torches or temporary Work-Lights if it's too dark.
     * Returns the updated cooldown timer for the lighting engine.
     */
    public static int handleDynamicLighting(EngineerCompanionEntity npc, Level level, DigShapeLibrary.AbstractDigShape currentShape, BlockPos start, Direction dir, int currentCooldown) {
        if (currentCooldown > 0) return currentCooldown - 1;

        if (DiggingUtils.hasLightSources(npc)) {
            if (level.getMaxLocalRawBrightness(npc.blockPosition().above()) < 9) {
                boolean placed = false;
                BlockPos pos = npc.blockPosition();

                // 1. Check for a mathematically safe wall to mount a permanent torch
                BlockPos wallTorchPos = currentShape.getValidWallTorchPos(level, start, pos, dir, dir.getClockWise(), npc.jobController.jobWidth, npc.jobController.jobLength);

                if (wallTorchPos != null) {
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        BlockPos wallPos = wallTorchPos.relative(d);
                        if (level.getBlockState(wallPos).isFaceSturdy(level, wallPos, d.getOpposite())) {
                            level.setBlockAndUpdate(wallTorchPos, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, d.getOpposite()));
                            npc.swing(InteractionHand.OFF_HAND, true);
                            DiggingUtils.consumeTorch(npc);
                            placed = true;
                            break;
                        }
                    }
                }
                // 2. The Work-Light Protocol: Drop a temporary torch on the floor if the center is dark
                else if (level.isEmptyBlock(pos) && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
                    level.setBlockAndUpdate(pos, Blocks.TORCH.defaultBlockState());
                    npc.swing(InteractionHand.OFF_HAND, true);
                    DiggingUtils.consumeTorch(npc);
                    placed = true;
                }

                // If placed, pause scanner for 40 ticks (2s) to prevent spam. Otherwise, check again in 10 ticks (0.5s)
                return placed ? 40 : 10;
            }
        }
        return 0; // Keep cooldown at 0 if we didn't do anything, ready to check again next tick
    }
}