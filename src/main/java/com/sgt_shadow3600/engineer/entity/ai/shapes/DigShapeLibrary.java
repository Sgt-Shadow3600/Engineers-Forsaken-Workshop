package com.sgt_shadow3600.engineer.entity.ai.shapes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class DigShapeLibrary {

    public static AbstractDigShape getShape(int shapeId) {
        return switch (shapeId) {
            case 0 -> new StairsShape();
            case 1 -> new TunnelShape();
            case 2 -> new RoomShape();
            case 3 -> new QuarryShape();
            default -> null;
        };
    }

    // --- THE INTERFACE / ABSTRACT BLUEPRINT ---
    public static abstract class AbstractDigShape {
        public int currentX = 0;
        public int currentY = 0;
        public int currentZ = 0;

        // ARCHITECT FEATURE: Rim Lighting Queue
        public boolean rimTorchesPopulated = false;
        public Queue<BlockPos> rimTorchQueue = new LinkedList<>();

        public boolean hasPendingRimTorches() { return !rimTorchQueue.isEmpty(); }
        public BlockPos getNextRimTorchPos() { return rimTorchQueue.peek(); }
        public void consumeRimTorch() { rimTorchQueue.poll(); }
        public void populateRimTorches(BlockPos start, Direction dir, Direction rightDir, int width, int length) {}

        public void reset(boolean newJob) {
            if (newJob) {
                currentX = 0;
                currentZ = 0;
                rimTorchesPopulated = false;
                rimTorchQueue.clear();
                initY();
            }
        }

        protected abstract void initY();

        public abstract boolean isComplete(Level level, BlockPos start, Direction dir, int maxDepth);
        public abstract BlockPos getCenterPos(BlockPos start, Direction dir, int width, int length, int height);
        public abstract BlockPos getTargetBlock(BlockPos start, Direction dir, Direction rightDir, int width, int length, int height);
        public abstract BlockPos getFloorBlock(BlockPos start, Direction dir, Direction rightDir, int width, int length, int height);
        public abstract BlockPos getStandPos(Level level, BlockPos start, Direction dir, Direction rightDir, BlockPos targetBlock, BlockPos centerPos, int width, int length, int height);
        public abstract void advance(int width, int length, int height);
        public abstract BlockPos getStartTorchPos(BlockPos start, Direction dir, int length);

        public abstract BlockPos getValidWallTorchPos(Level level, BlockPos start, BlockPos npcPos, Direction dir, Direction rightDir, int width, int length);

        public abstract float calculateProgress(int maxDepth, int width, int length, int height);
        public abstract BlockPos getChainedAnchor(BlockPos start, Direction dir, int depth, int width, int length, int height);

        public boolean usesRailsAndBridges() { return false; }
        public BlockPos getLeftEdgeFloor(BlockPos start, Direction dir, Direction rightDir, int width, int length, int height) { return null; }
        public BlockPos getRightEdgeFloor(BlockPos start, Direction dir, Direction rightDir, int width, int length, int height) { return null; }

        public boolean hasOverride() { return false; }
        public BlockPos getOverrideTarget() { return null; }
        public BlockPos getOverrideStand() { return null; }
    }

    // --- 0: STAIRS SHAPE ---
    public static class StairsShape extends AbstractDigShape {
        @Override protected void initY() { currentY = 0; }

        @Override public boolean isComplete(Level level, BlockPos start, Direction dir, int maxDepth) {
            return (maxDepth == 999 && start.getY() - currentY <= level.getMinBuildHeight()) || (maxDepth != 999 && currentY >= maxDepth);
        }
        @Override public BlockPos getCenterPos(BlockPos start, Direction dir, int width, int length, int height) {
            return start.relative(dir, currentY).below(currentY);
        }
        @Override public BlockPos getTargetBlock(BlockPos start, Direction dir, Direction rightDir, int width, int length, int height) {
            return getCenterPos(start, dir, width, length, height).relative(rightDir, -(width - 1) / 2 + currentX).above(height - 1 - currentZ);
        }
        @Override public BlockPos getFloorBlock(BlockPos start, Direction dir, Direction rightDir, int width, int length, int height) {
            return getCenterPos(start, dir, width, length, height).relative(rightDir, -(width - 1) / 2 + currentX).below();
        }
        @Override public BlockPos getStandPos(Level level, BlockPos start, Direction dir, Direction rightDir, BlockPos targetBlock, BlockPos centerPos, int width, int length, int height) {
            return centerPos.relative(dir.getOpposite()).above();
        }
        @Override public void advance(int width, int length, int height) {
            currentZ++; if (currentZ >= height) { currentZ = 0; currentX++; if (currentX >= width) { currentX = 0; currentY++; } }
        }
        @Override public BlockPos getStartTorchPos(BlockPos start, Direction dir, int length) {
            return start.relative(dir.getOpposite()).above();
        }
        @Override public BlockPos getValidWallTorchPos(Level level, BlockPos start, BlockPos npcPos, Direction dir, Direction rightDir, int width, int length) {
            return null;
        }
        @Override public float calculateProgress(int maxDepth, int width, int length, int height) {
            if (maxDepth <= 0) return 0.0f;
            float layerProgress = ((float)(currentX * height) + currentZ) / (width * height);
            return (currentY + layerProgress) / maxDepth;
        }
        @Override public BlockPos getChainedAnchor(BlockPos start, Direction dir, int depth, int width, int length, int height) {
            int offset = Math.max(0, depth);
            return start.relative(dir, offset).below(offset);
        }
        @Override public boolean usesRailsAndBridges() { return true; }
        @Override public BlockPos getLeftEdgeFloor(BlockPos start, Direction dir, Direction rightDir, int width, int length, int height) {
            return getCenterPos(start, dir, width, length, height).relative(rightDir, -(width - 1) / 2).below();
        }
        @Override public BlockPos getRightEdgeFloor(BlockPos start, Direction dir, Direction rightDir, int width, int length, int height) {
            return getCenterPos(start, dir, width, length, height).relative(rightDir, -(width - 1) / 2 + (width - 1)).below();
        }
    }

    // --- 1: TUNNEL SHAPE ---
    public static class TunnelShape extends AbstractDigShape {
        @Override protected void initY() { currentY = 0; }

        @Override public boolean isComplete(Level level, BlockPos start, Direction dir, int maxDepth) {
            return (maxDepth != 999 && currentY >= maxDepth);
        }
        @Override public BlockPos getCenterPos(BlockPos start, Direction dir, int width, int length, int height) {
            return start.relative(dir, currentY);
        }
        @Override public BlockPos getTargetBlock(BlockPos start, Direction dir, Direction rightDir, int width, int length, int height) {
            return getCenterPos(start, dir, width, length, height).relative(rightDir, -(width - 1) / 2 + currentX).above(height - 1 - currentZ);
        }
        @Override public BlockPos getFloorBlock(BlockPos start, Direction dir, Direction rightDir, int width, int length, int height) {
            return getCenterPos(start, dir, width, length, height).relative(rightDir, -(width - 1) / 2 + currentX).below();
        }
        @Override public BlockPos getStandPos(Level level, BlockPos start, Direction dir, Direction rightDir, BlockPos targetBlock, BlockPos centerPos, int width, int length, int height) {
            return centerPos.relative(dir.getOpposite());
        }
        @Override public void advance(int width, int length, int height) {
            currentZ++; if (currentZ >= height) { currentZ = 0; currentX++; if (currentX >= width) { currentX = 0; currentY++; } }
        }
        @Override public BlockPos getStartTorchPos(BlockPos start, Direction dir, int length) {
            return start.relative(dir.getOpposite()).above();
        }
        @Override public BlockPos getValidWallTorchPos(Level level, BlockPos start, BlockPos npcPos, Direction dir, Direction rightDir, int width, int length) {
            return null;
        }
        @Override public float calculateProgress(int maxDepth, int width, int length, int height) {
            if (maxDepth <= 0) return 0.0f;
            float layerProgress = ((float)(currentX * height) + currentZ) / (width * height);
            return (currentY + layerProgress) / maxDepth;
        }
        @Override public BlockPos getChainedAnchor(BlockPos start, Direction dir, int depth, int width, int length, int height) {
            int offset = Math.max(0, depth);
            return start.relative(dir, offset);
        }
        @Override public boolean usesRailsAndBridges() { return true; }
        @Override public BlockPos getLeftEdgeFloor(BlockPos start, Direction dir, Direction rightDir, int width, int length, int height) {
            return getCenterPos(start, dir, width, length, height).relative(rightDir, -(width - 1) / 2).below();
        }
        @Override public BlockPos getRightEdgeFloor(BlockPos start, Direction dir, Direction rightDir, int width, int length, int height) {
            return getCenterPos(start, dir, width, length, height).relative(rightDir, -(width - 1) / 2 + (width - 1)).below();
        }
    }

    // --- 2: ROOM SHAPE ---
    public static class RoomShape extends AbstractDigShape {
        private record GridPos(int x, int z) {}
        private List<GridPos> cachedRoomGrid = null;
        private int cachedWidth = -1;
        private int cachedLength = -1;

        private List<GridPos> getGrid(int width, int length) {
            if (cachedRoomGrid != null && cachedWidth == width && cachedLength == length) return cachedRoomGrid;
            List<GridPos> list = new ArrayList<>();
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < length; z++) {
                    list.add(new GridPos(x, z));
                }
            }
            double cx = (width - 1) / 2.0;
            double cz = (length - 1) / 2.0;
            list.sort(java.util.Comparator.<GridPos>comparingDouble(p -> Math.pow(p.x - cx, 2) + Math.pow(p.z - cz, 2))
                    .thenComparingDouble(p -> Math.atan2(p.z - cz, p.x - cx)));
            cachedRoomGrid = list; cachedWidth = width; cachedLength = length;
            return list;
        }

        @Override protected void initY() { currentY = -2; }

        @Override
        public void populateRimTorches(BlockPos start, Direction dir, Direction rightDir, int width, int length) {
            int wMax = width / 2;
            int wMin = (width - 1) / 2;
            int lMax = length / 2;
            int lMin = (length - 1) / 2;

            // Targets the unmined wall blocks directly at head level of the top room
            rimTorchQueue.add(start.relative(dir, lMax).above());
            rimTorchQueue.add(start.relative(dir.getOpposite(), lMin).above());
            rimTorchQueue.add(start.relative(rightDir, wMax).above());
            rimTorchQueue.add(start.relative(rightDir.getOpposite(), wMin).above());
        }

        @Override public boolean isComplete(Level level, BlockPos start, Direction dir, int maxDepth) {
            return (maxDepth == 999 && start.getY() - currentY <= level.getMinBuildHeight()) || (maxDepth != 999 && currentY >= maxDepth);
        }
        @Override public BlockPos getCenterPos(BlockPos start, Direction dir, int width, int length, int height) {
            return start.below(currentY);
        }
        @Override public BlockPos getTargetBlock(BlockPos start, Direction dir, Direction rightDir, int width, int length, int height) {
            List<GridPos> grid = getGrid(width, length);
            if (currentX >= grid.size()) return start;
            GridPos gp = grid.get(currentX);
            int offsetW = -(width - 1) / 2 + gp.x;
            int offsetL = -(length - 1) / 2 + gp.z;
            return start.relative(rightDir, offsetW).relative(dir, offsetL).below(currentY);
        }
        @Override public BlockPos getFloorBlock(BlockPos start, Direction dir, Direction rightDir, int width, int length, int height) {
            return getTargetBlock(start, dir, rightDir, width, length, height).below();
        }
        @Override public BlockPos getStandPos(Level level, BlockPos start, Direction dir, Direction rightDir, BlockPos targetBlock, BlockPos centerPos, int width, int length, int height) {
            if (currentY < 0) return new BlockPos(targetBlock.getX(), start.getY(), targetBlock.getZ());

            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos candidate = targetBlock.above().relative(d);
                BlockPos floor = candidate.below();
                if (level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty() &&
                        level.getFluidState(candidate).isEmpty() &&
                        level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
                    return candidate;
                }
            }
            return targetBlock.above();
        }
        @Override public void advance(int width, int length, int height) {
            currentX++;
            if (currentX >= getGrid(width, length).size()) {
                currentX = 0; currentY++;
            }
        }
        @Override public BlockPos getStartTorchPos(BlockPos start, Direction dir, int length) {
            return start.relative(dir.getOpposite()).above();
        }

        @Override public BlockPos getValidWallTorchPos(Level level, BlockPos start, BlockPos npcPos, Direction dir, Direction rightDir, int width, int length) {
            BlockPos headPos = npcPos.above();
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos wallPos = headPos.relative(d);
                if (level.isEmptyBlock(headPos) && level.getBlockState(wallPos).isFaceSturdy(level, wallPos, d.getOpposite())) {
                    int dx = wallPos.getX() - start.getX();
                    int dz = wallPos.getZ() - start.getZ();

                    int localW = dx * rightDir.getStepX() + dz * rightDir.getStepZ();
                    int localL = dx * dir.getStepX() + dz * dir.getStepZ();

                    if (localW < -(width - 1) / 2 || localW > width / 2 || localL < -(length - 1) / 2 || localL > length / 2) {
                        return headPos;
                    }
                }
            }
            return null;
        }

        @Override public float calculateProgress(int maxDepth, int width, int length, int height) {
            if (maxDepth <= 0) return 0.0f;
            int adjustedY = Math.max(0, currentY + 2);
            float layerProgress = (float)currentX / getGrid(width, length).size();
            return (adjustedY + layerProgress) / (maxDepth + 2);
        }
        @Override public BlockPos getChainedAnchor(BlockPos start, Direction dir, int depth, int width, int length, int height) {
            int offset = Math.max(0, depth);
            return start.below(offset);
        }
    }

    // --- 3: QUARRY SHAPE ---
    public static class QuarryShape extends AbstractDigShape {
        private record GridPos(int x, int z) {}
        private List<GridPos> cachedQuarryGrid = null;
        private int cachedRadius = -1;

        private List<GridPos> getGrid(int radius) {
            if (cachedQuarryGrid != null && cachedRadius == radius) return cachedQuarryGrid;
            List<GridPos> list = new ArrayList<>();
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    double dist = Math.sqrt(x*x + z*z);
                    if (Math.round(dist) <= radius) {
                        list.add(new GridPos(x, z));
                    }
                }
            }
            // Sort to spiral outward from center
            list.sort(java.util.Comparator.<GridPos>comparingDouble(p -> p.x * p.x + p.z * p.z).thenComparingDouble(p -> Math.atan2(p.z, p.x)));
            cachedQuarryGrid = list;
            cachedRadius = radius;
            return list;
        }

        @Override protected void initY() { currentY = -2; }

        @Override
        public void populateRimTorches(BlockPos start, Direction dir, Direction rightDir, int width, int length) {
            // Targets the 4 cardinal outer rim blocks of the circular radius
            rimTorchQueue.add(start.relative(Direction.NORTH, length).above());
            rimTorchQueue.add(start.relative(Direction.SOUTH, length).above());
            rimTorchQueue.add(start.relative(Direction.EAST, length).above());
            rimTorchQueue.add(start.relative(Direction.WEST, length).above());
        }

        @Override public boolean isComplete(Level level, BlockPos start, Direction dir, int maxDepth) {
            return (maxDepth == 999 && start.getY() - currentY <= level.getMinBuildHeight()) || (maxDepth != 999 && currentY >= maxDepth);
        }
        @Override public BlockPos getCenterPos(BlockPos start, Direction dir, int width, int length, int height) {
            return start.below(currentY);
        }

        @Override public BlockPos getTargetBlock(BlockPos start, Direction dir, Direction rightDir, int width, int length, int height) {
            List<GridPos> grid = getGrid(length);
            if (currentX >= grid.size()) return start;
            GridPos gp = grid.get(currentX);

            // Directly yield the target, no staircase override logic required
            return start.offset(gp.x, -currentY, gp.z);
        }

        @Override public BlockPos getFloorBlock(BlockPos start, Direction dir, Direction rightDir, int width, int length, int height) {
            return getTargetBlock(start, dir, rightDir, width, length, height).below();
        }
        @Override public BlockPos getStandPos(Level level, BlockPos start, Direction dir, Direction rightDir, BlockPos targetBlock, BlockPos centerPos, int width, int length, int height) {
            if (currentY < 0) return new BlockPos(targetBlock.getX(), start.getY(), targetBlock.getZ());

            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos candidate = targetBlock.above().relative(d);
                BlockPos floor = candidate.below();
                if (level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty() &&
                        level.getFluidState(candidate).isEmpty() &&
                        level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
                    return candidate;
                }
            }
            return targetBlock.above();
        }
        @Override public void advance(int width, int length, int height) {
            currentX++;
            if (currentX >= getGrid(length).size()) {
                currentX = 0; currentY++;
            }
        }

        @Override public BlockPos getStartTorchPos(BlockPos start, Direction dir, int length) {
            return start.relative(dir.getOpposite(), length).above();
        }

        @Override public BlockPos getValidWallTorchPos(Level level, BlockPos start, BlockPos npcPos, Direction dir, Direction rightDir, int width, int length) {
            BlockPos headPos = npcPos.above();
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos wallPos = headPos.relative(d);
                if (level.isEmptyBlock(headPos) && level.getBlockState(wallPos).isFaceSturdy(level, wallPos, d.getOpposite())) {
                    double dist = Math.sqrt(Math.pow(wallPos.getX() - start.getX(), 2) + Math.pow(wallPos.getZ() - start.getZ(), 2));
                    if (Math.round(dist) > length) {
                        return headPos;
                    }
                }
            }
            return null;
        }

        @Override public float calculateProgress(int maxDepth, int width, int length, int height) {
            if (maxDepth <= 0) return 0.0f;
            int adjustedY = Math.max(0, currentY + 2);
            float layerProgress = (float)currentX / getGrid(length).size();
            return (adjustedY + layerProgress) / (maxDepth + 2);
        }
        @Override public BlockPos getChainedAnchor(BlockPos start, Direction dir, int depth, int width, int length, int height) {
            int offset = Math.max(0, depth);
            return start.below(offset);
        }
    }
}