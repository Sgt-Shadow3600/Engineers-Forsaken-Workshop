package com.sgt_shadow3600.engineer.entity.ai;

import com.sgt_shadow3600.engineer.entity.EngineerCompanionEntity;
import com.sgt_shadow3600.engineer.entity.ai.shapes.DigShapeLibrary;
import com.sgt_shadow3600.engineer.entity.ai.utils.AIUtils;
import com.sgt_shadow3600.engineer.entity.ai.utils.ArrivalUtils;
import com.sgt_shadow3600.engineer.entity.ai.utils.DiggingUtils;
import com.sgt_shadow3600.engineer.entity.ai.utils.LightingUtils;
import com.sgt_shadow3600.engineer.entity.ai.utils.BridgingUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.EnumSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.HashSet;
import java.util.Set;

public class DiggingGoal extends Goal {
    private final EngineerCompanionEntity npc;
    private DigShapeLibrary.AbstractDigShape currentShape = null;
    private JobState cachedJobState = null;

    private int diggingTicks = 0;
    private int lightCooldown = 0;
    private int pauseTicks = 0;
    private int arrivalState = 0;
    private int arrivalTicks = 0;

    // ARCHITECT ADDITION: Throttle the heavy geometry scanner
    private int scanCooldown = 0;

    private int stuckTicks = 0;
    private int horizontalStuckTicks = 0;
    private int rimStuckTicks = 0;
    private double lastFailsafeX = 0;
    private double lastFailsafeY = 0;
    private double lastFailsafeZ = 0;

    private boolean placedStartTorch = false;
    private int activeToolSlot = -1;

    private final Queue<BlockPos> veinQueue = new LinkedList<>();
    private final Queue<BlockPos> fillQueue = new LinkedList<>();
    private final Set<BlockPos> veinScanned = new HashSet<>();

    private record JobState(BlockPos center, BlockPos target, BlockPos floor, BlockPos stand, BlockPos leak, BlockPos rail, boolean plug, boolean railNeeded, boolean bridge, boolean done, boolean isObstruction) {}

    public DiggingGoal(EngineerCompanionEntity npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return npc.getCurrentTask() == EngineerCompanionEntity.CompanionTask.DIGGING &&
                npc.jobController.jobSite != null &&
                npc.getTarget() == null;
    }

    @Override
    public void start() {
        if (currentShape == null || npc.isNewJob) {
            currentShape = DigShapeLibrary.getShape(npc.jobController.jobShape);
            if (currentShape == null) {
                currentShape = new DigShapeLibrary.TunnelShape();
            }
        }

        double maxRadius = 0;
        if (currentShape instanceof DigShapeLibrary.QuarryShape) {
            maxRadius = npc.jobController.jobLength;
        } else if (currentShape instanceof DigShapeLibrary.RoomShape) {
            maxRadius = Math.max(npc.jobController.jobWidth, npc.jobController.jobLength) / 2.0;
        }

        this.arrivalState = ArrivalUtils.determineInitialState(npc, npc.jobController.jobSite, maxRadius);
        this.arrivalTicks = 0;

        if (npc.isNewJob) {
            currentShape.reset(true);
            this.placedStartTorch = false;
            this.veinQueue.clear();
            this.fillQueue.clear();
            this.veinScanned.clear();
            npc.isNewJob = false;
        }

        this.pauseTicks = 0;
        this.diggingTicks = 0;
        this.lightCooldown = 0;
        this.scanCooldown = 0;
        this.stuckTicks = 0;
        this.horizontalStuckTicks = 0;
        this.rimStuckTicks = 0;
        this.lastFailsafeX = npc.getX();
        this.lastFailsafeY = npc.getY();
        this.lastFailsafeZ = npc.getZ();
        this.activeToolSlot = -1;
        this.cachedJobState = null;
    }

    @Override
    public void stop() {
        if (cachedJobState != null && cachedJobState.target() != null) {
            npc.level().destroyBlockProgress(npc.getId(), cachedJobState.target(), -1);
        }
    }

    @Override
    public void tick() {
        Level level = npc.level();

        if (arrivalState > 0) {
            Direction dir = Direction.from3DDataValue(npc.jobController.jobDirection);
            if (dir.getAxis() == Direction.Axis.Y) dir = npc.getDirection();

            double maxRadius = 0;
            if (currentShape instanceof DigShapeLibrary.QuarryShape) maxRadius = npc.jobController.jobLength;
            else if (currentShape instanceof DigShapeLibrary.RoomShape) maxRadius = Math.max(npc.jobController.jobWidth, npc.jobController.jobLength) / 2.0;

            arrivalState = ArrivalUtils.processArrival(npc, npc.jobController.jobSite, dir, maxRadius, arrivalState, arrivalTicks++);
            return;
        }

        if (pauseTicks > 0) { pauseTicks--; return; }
        if (scanCooldown > 0) { scanCooldown--; return; } // TPS Mitigation

        if (npc.getHealth() <= npc.getMaxHealth() * 0.4f || DiggingUtils.isInventoryFull(npc)) {
            npc.setCurrentTask(EngineerCompanionEntity.CompanionTask.GO_HOME); return;
        }

        BlockPos start = npc.jobController.jobSite;
        Direction dir = Direction.from3DDataValue(npc.jobController.jobDirection);
        if (dir.getAxis() == Direction.Axis.Y) dir = npc.getDirection();
        Direction rightDir = dir.getClockWise();

        if (currentShape.currentY == 1 && !currentShape.rimTorchesPopulated && (currentShape instanceof DigShapeLibrary.QuarryShape || currentShape instanceof DigShapeLibrary.RoomShape)) {
            currentShape.populateRimTorches(start, dir, rightDir, npc.jobController.jobWidth, npc.jobController.jobLength);
            currentShape.rimTorchesPopulated = true;
        }

        if (currentShape.hasPendingRimTorches()) {
            if (!DiggingUtils.hasLightSources(npc)) {
                currentShape.rimTorchQueue.clear();
            } else {
                handleRimTorchPlacement(level);
                return;
            }
        }

        if (cachedJobState == null || isStateInvalid(level, cachedJobState)) {
            cachedJobState = scanAndCalculateTargets(level, start, dir, rightDir);
            if (cachedJobState == null) {
                // ARCHITECT FIX: Back-off if we fail to find a block. Don't spam the scanner next tick.
                scanCooldown = 2;
                return;
            }
        }

        JobState state = cachedJobState;

        if (state.done()) {
            npc.setJobProgress(1.0f);
            AIUtils.sendOwnerAlert(npc, "[JOB COMPLETE]", "§a", "has finished digging at X:" + npc.getBlockX() + " Y:" + npc.getBlockY() + " Z:" + npc.getBlockZ());

            npc.jobController.archiveCurrentJob();
            if (npc.jobController.startNextJob()) {
                AIUtils.sendOwnerAlert(npc, "[QUEUE ACTIVE]", "§e", "Moving to next queued job! (" + npc.jobController.jobQueue.size() + " remaining)");
                this.start();
            } else {
                npc.setCurrentTask(EngineerCompanionEntity.CompanionTask.GO_HOME);
            }
            return;
        }

        if (handleFallFailsafe(level, state.stand())) return;
        handleHorizontalFailsafe(level, state.stand());

        if (handleMovement(state.stand(), state.target())) return;

        this.placedStartTorch = LightingUtils.handleStartTorch(npc, level, currentShape, start, dir, placedStartTorch);
        this.lightCooldown = LightingUtils.handleDynamicLighting(npc, level, currentShape, start, dir, lightCooldown);

        executeWork(level, state);
    }

    private void handleRimTorchPlacement(Level level) {
        BlockPos torchPos = currentShape.getNextRimTorchPos();

        if (!level.getBlockState(torchPos).canBeReplaced()) {
            currentShape.consumeRimTorch();
            return;
        }

        double distSq = npc.distanceToSqr(torchPos.getX() + 0.5, npc.getY(), torchPos.getZ() + 0.5);
        if (distSq > 6.0) {
            npc.getLookControl().setLookAt(torchPos.getX() + 0.5, torchPos.getY(), torchPos.getZ() + 0.5);
            boolean canPath = npc.getNavigation().moveTo(torchPos.getX() + 0.5, torchPos.getY() - 1, torchPos.getZ() + 0.5, 1.0D);
            if (!canPath || ++rimStuckTicks > 100) {
                currentShape.consumeRimTorch();
                rimStuckTicks = 0;
            }
            return;
        }

        npc.getNavigation().stop();
        npc.getLookControl().setLookAt(torchPos.getX() + 0.5, torchPos.getY(), torchPos.getZ() + 0.5);
        rimStuckTicks = 0;

        if (++diggingTicks > 10) {
            boolean placed = false;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos wallPos = torchPos.relative(d);
                if (level.getBlockState(wallPos).isFaceSturdy(level, wallPos, d.getOpposite())) {
                    level.setBlockAndUpdate(torchPos, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, d.getOpposite()));
                    placed = true;
                    break;
                }
            }
            if (!placed && level.getBlockState(torchPos.below()).isFaceSturdy(level, torchPos.below(), Direction.UP)) {
                level.setBlockAndUpdate(torchPos, Blocks.TORCH.defaultBlockState());
                placed = true;
            }

            if (placed) {
                npc.swing(InteractionHand.OFF_HAND, true);
                DiggingUtils.consumeTorch(npc);
            }

            currentShape.consumeRimTorch();
            diggingTicks = 0;
        }
    }

    private boolean isStateInvalid(Level level, JobState state) {
        if (state.done()) return false;
        if (state.target() != null && level.isEmptyBlock(state.target()) && !state.plug() && !state.railNeeded() && !state.bridge()) return true;
        return false;
    }

    private boolean isOutsideJobBounds(BlockPos pos, BlockPos start, Direction dir, Direction rightDir, int width, int length) {
        if (currentShape instanceof DigShapeLibrary.QuarryShape) {
            double dist = Math.sqrt(Math.pow(pos.getX() - start.getX(), 2) + Math.pow(pos.getZ() - start.getZ(), 2));
            return Math.round(dist) > length;
        } else if (currentShape instanceof DigShapeLibrary.RoomShape) {
            int dx = pos.getX() - start.getX();
            int dz = pos.getZ() - start.getZ();
            int localW = dx * rightDir.getStepX() + dz * rightDir.getStepZ();
            int localL = dx * dir.getStepX() + dz * rightDir.getStepZ();
            return localW < -(width - 1) / 2 || localW > width / 2 || localL < -(length - 1) / 2 || localL > length / 2;
        }
        return false;
    }

    private JobState scanAndCalculateTargets(Level level, BlockPos start, Direction dir, Direction rightDir) {

        if (BridgingUtils.isFluidThreat(level, npc.blockPosition().above())) {
            return new JobState(start, npc.blockPosition().above(), npc.blockPosition(), npc.blockPosition(), npc.blockPosition().above(), null, true, false, false, false, true);
        }
        if (BridgingUtils.isFluidThreat(level, npc.blockPosition())) {
            return new JobState(start, npc.blockPosition(), npc.blockPosition().below(), npc.blockPosition(), npc.blockPosition(), null, true, false, false, false, true);
        }

        if (npc.jobController.veinmine && !veinQueue.isEmpty()) {
            while (!veinQueue.isEmpty()) {
                BlockPos vPos = veinQueue.peek();
                if (npc.distanceToSqr(vPos.getX() + 0.5, vPos.getY() + 0.5, vPos.getZ() + 0.5) > 24.0) { veinQueue.poll(); continue; }
                if (DiggingUtils.isOre(level.getBlockState(vPos))) return new JobState(vPos, vPos, vPos.below(), npc.blockPosition(), null, null, false, false, false, false, false);
                else veinQueue.poll();
            }
        }

        int maxDepth = npc.jobController.jobDepth;
        int jobWidth = npc.jobController.jobWidth;
        int jobLength = npc.jobController.jobLength;
        int jobHeight = npc.jobController.jobHeight;

        float progress = currentShape.calculateProgress(maxDepth, jobWidth, jobLength, jobHeight);
        npc.setJobProgress(progress);

        boolean foundSolid = false;
        boolean needsBridging = false;
        boolean needsPlugging = false;
        boolean needsRailing = false;

        BlockPos targetBlock = null; BlockPos floorPos = null; BlockPos centerPos = null; BlockPos leakPos = null; BlockPos railPos = null;

        int safetyBreak = 0;
        // ARCHITECT FIX: Reduced from 64 to 16. Still scans 320 blocks/sec, but drastically cuts tick load.
        while (!foundSolid && safetyBreak < 16) {
            safetyBreak++;

            if (currentShape.isComplete(level, start, dir, maxDepth)) return new JobState(null, null, null, null, null, null, false, false, false, true, false);

            centerPos = currentShape.getCenterPos(start, dir, jobWidth, jobLength, jobHeight);
            targetBlock = currentShape.getTargetBlock(start, dir, rightDir, jobWidth, jobLength, jobHeight);

            if (currentShape.hasOverride()) {
                BlockPos t = currentShape.getOverrideTarget();
                if (level.getBlockState(t).isAir() || BridgingUtils.isFluidThreat(level, t)) {
                    return new JobState(centerPos, t, t, currentShape.getOverrideStand(), null, t, false, true, false, false, false);
                }
                currentShape.advance(jobWidth, jobLength, jobHeight); continue;
            }

            floorPos = currentShape.getFloorBlock(start, dir, rightDir, jobWidth, jobLength, jobHeight);
            BlockState targetState = level.getBlockState(targetBlock);

            if (npc.jobController.veinmine && DiggingUtils.isOre(targetState) && veinQueue.isEmpty()) {
                Queue<BlockPos> searchQ = new LinkedList<>(); searchQ.add(targetBlock); veinScanned.clear(); veinScanned.add(targetBlock);
                while (!searchQ.isEmpty() && veinQueue.size() < 64) {
                    BlockPos curr = searchQ.poll(); veinQueue.add(curr);
                    for (Direction d : Direction.values()) {
                        BlockPos adj = curr.relative(d);
                        if (!veinScanned.contains(adj) && DiggingUtils.isOre(level.getBlockState(adj))) { veinScanned.add(adj); searchQ.add(adj); }
                    }
                }
                return new JobState(targetBlock, targetBlock, targetBlock.below(), targetBlock.relative(dir.getOpposite()), null, null, false, false, false, false, false);
            }

            if (targetState.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE)) npc.setRequiresAxe(true);

            if (BridgingUtils.isFluidThreat(level, targetBlock)) {
                leakPos = targetBlock; needsPlugging = true; foundSolid = true;
            } else {
                for (Direction d : Direction.values()) {
                    BlockPos adj = targetBlock.relative(d);
                    if (BridgingUtils.isFluidThreat(level, adj)) { leakPos = adj; needsPlugging = true; foundSolid = true; break; }
                }
            }

            if (!foundSolid && currentShape.usesRailsAndBridges()) {
                BlockPos leftFloor = currentShape.getLeftEdgeFloor(start, dir, rightDir, jobWidth, jobLength, jobHeight).relative(rightDir.getOpposite());
                BlockPos leftRail = leftFloor.above();
                if (!level.getBlockState(leftFloor).isFaceSturdy(level, leftFloor, Direction.UP)) { needsRailing = true; railPos = leftFloor; foundSolid = true; }
                else if (level.isEmptyBlock(leftRail) || BridgingUtils.isFluidThreat(level, leftRail)) { needsRailing = true; railPos = leftRail; foundSolid = true; }

                if (!needsRailing) {
                    BlockPos rightFloor = currentShape.getRightEdgeFloor(start, dir, rightDir, jobWidth, jobLength, jobHeight).relative(rightDir);
                    BlockPos rightRail = rightFloor.above();
                    if (!level.getBlockState(rightFloor).isFaceSturdy(level, rightFloor, Direction.UP)) { needsRailing = true; railPos = rightFloor; foundSolid = true; }
                    else if (level.isEmptyBlock(rightRail) || BridgingUtils.isFluidThreat(level, rightRail)) { needsRailing = true; railPos = rightRail; foundSolid = true; }
                }
            }

            if (!foundSolid) {
                if (currentShape.usesRailsAndBridges() && !level.getBlockState(floorPos).isFaceSturdy(level, floorPos, Direction.UP)) {
                    needsBridging = true; foundSolid = true;
                }
                else if (targetState.isAir() || targetState.getDestroySpeed(level, targetBlock) < 0.0f || targetState.is(Blocks.TORCH) || targetState.is(Blocks.WALL_TORCH) || targetState.getBlock() instanceof com.sgt_shadow3600.engineer.block.TerminalBlock) {

                    boolean waitingForSand = false;
                    if (targetState.isAir() || targetState.getDestroySpeed(level, targetBlock) < 0.0f) {
                        if (!level.getEntitiesOfClass(FallingBlockEntity.class, new AABB(targetBlock).expandTowards(0, 10, 0)).isEmpty()) {
                            waitingForSand = true;
                        }
                    }

                    if (waitingForSand) {
                        foundSolid = true;
                    } else {
                        currentShape.advance(jobWidth, jobLength, jobHeight);
                    }
                } else {
                    foundSolid = true;
                }
            }
        }

        if (!foundSolid) return null;

        BlockPos standPos = currentShape.getStandPos(level, start, dir, rightDir, targetBlock, centerPos, jobWidth, jobLength, jobHeight);

        if (!currentShape.usesRailsAndBridges()) {

            BlockPos currentPos = BlockPos.containing(npc.getX(), standPos.getY(), npc.getZ());
            BlockPos iter = currentPos;
            BlockPos prevIter = currentPos;
            int maxSteps = 30;

            while ((iter.getX() != standPos.getX() || iter.getZ() != standPos.getZ()) && maxSteps-- > 0) {
                prevIter = iter;
                int dx = standPos.getX() - iter.getX();
                int dz = standPos.getZ() - iter.getZ();

                if (Math.abs(dx) > Math.abs(dz)) iter = iter.offset(Integer.signum(dx), 0, 0);
                else iter = iter.offset(0, 0, Integer.signum(dz));

                boolean isSafeToTunnel = isOutsideJobBounds(iter, start, dir, rightDir, jobWidth, jobLength) || currentShape.currentY <= 0;

                BlockState hState = level.getBlockState(iter.above());
                BlockState fState = level.getBlockState(iter);

                if (isSafeToTunnel) {
                    if (BridgingUtils.isFluidThreat(level, iter.above())) return new JobState(centerPos, iter.above(), iter, prevIter, iter.above(), null, true, false, false, false, true);
                    if (BridgingUtils.isFluidThreat(level, iter)) return new JobState(centerPos, iter, iter.below(), prevIter, iter, null, true, false, false, false, true);

                    if (!hState.isAir() && hState.getDestroySpeed(level, iter.above()) >= 0.0f && !(hState.getBlock() instanceof com.sgt_shadow3600.engineer.block.TerminalBlock)) {
                        return new JobState(centerPos, iter.above(), iter, prevIter, null, null, false, false, false, false, true);
                    }
                    if (!fState.isAir() && fState.getDestroySpeed(level, iter) >= 0.0f && !(fState.getBlock() instanceof com.sgt_shadow3600.engineer.block.TerminalBlock)) {
                        return new JobState(centerPos, iter, iter.below(), prevIter, null, null, false, false, false, false, true);
                    }
                } else {
                    if ((!hState.isAir() && hState.getDestroySpeed(level, iter.above()) >= 0) || (!fState.isAir() && fState.getDestroySpeed(level, iter) >= 0)) {
                        break;
                    }
                }
            }

            BlockState headState = level.getBlockState(standPos.above());
            if (BridgingUtils.isFluidThreat(level, standPos.above())) {
                return new JobState(centerPos, standPos.above(), standPos, prevIter, standPos.above(), null, true, false, false, false, true);
            }
            if (!headState.isAir() && headState.getDestroySpeed(level, standPos.above()) >= 0.0f && !(headState.getBlock() instanceof com.sgt_shadow3600.engineer.block.TerminalBlock)) {
                return new JobState(centerPos, standPos.above(), standPos, prevIter, null, null, false, false, false, false, true);
            }

            BlockState standState = level.getBlockState(standPos);
            if (BridgingUtils.isFluidThreat(level, standPos)) {
                return new JobState(centerPos, standPos, standPos.below(), prevIter, standPos, null, true, false, false, false, true);
            }
            if (!standState.isAir() && standState.getDestroySpeed(level, standPos) >= 0.0f && !(standState.getBlock() instanceof com.sgt_shadow3600.engineer.block.TerminalBlock)) {
                return new JobState(centerPos, standPos, standPos.below(), prevIter, null, null, false, false, false, false, true);
            }
        }

        return new JobState(centerPos, targetBlock, floorPos, standPos, leakPos, railPos, needsPlugging, needsRailing, needsBridging, false, false);
    }

    private boolean handleFallFailsafe(Level level, BlockPos standPos) {
        if (npc.getY() < standPos.getY() - 1.5) {
            stuckTicks++; npc.setSprinting(false); npc.getNavigation().stop();
            if (stuckTicks > 40) {
                BlockPos floorPos = standPos.below();

                if (BridgingUtils.isUnsafeGap(level, floorPos)) {
                    if (!BridgingUtils.executeSeal(npc, level, floorPos)) {
                        return true;
                    }
                }

                BlockState standState = level.getBlockState(standPos);
                if (!standState.isAir() && !(standState.getBlock() instanceof com.sgt_shadow3600.engineer.block.TerminalBlock)) level.destroyBlock(standPos, true);
                if (!level.getBlockState(standPos.above()).isAir() && !(level.getBlockState(standPos.above()).getBlock() instanceof com.sgt_shadow3600.engineer.block.TerminalBlock)) level.destroyBlock(standPos.above(), true);

                BlockPos safePos = AIUtils.getSafeDropZone(level, standPos, npc.blockPosition());
                AIUtils.executeEnderWarp(npc, safePos);
                stuckTicks = 0;
            }
            return true;
        }
        stuckTicks = 0; return false;
    }

    private void handleHorizontalFailsafe(Level level, BlockPos standPos) {
        double distX = standPos.getX() + 0.5 - npc.getX();
        double distZ = standPos.getZ() + 0.5 - npc.getZ();
        if (distX * distX + distZ * distZ > 1.0) {
            if (++horizontalStuckTicks % 40 == 0) {
                if (npc.distanceToSqr(lastFailsafeX, lastFailsafeY, lastFailsafeZ) < 0.25) {
                    if (horizontalStuckTicks >= 100) {
                        BlockPos safePos = AIUtils.getSafeDropZone(level, standPos, npc.blockPosition());
                        AIUtils.executeEnderWarp(npc, safePos);
                        horizontalStuckTicks = 0;
                    }
                } else horizontalStuckTicks = 0;
                lastFailsafeX = npc.getX(); lastFailsafeY = npc.getY(); lastFailsafeZ = npc.getZ();
            }
        } else horizontalStuckTicks = 0;
    }

    private boolean handleMovement(BlockPos standPos, BlockPos targetBlock) {
        if (!currentShape.usesRailsAndBridges() || !veinQueue.isEmpty()) {
            if (npc.distanceToSqr(targetBlock.getX() + 0.5, targetBlock.getY() + 0.5, targetBlock.getZ() + 0.5) < 16.0 && npc.level().getBlockState(npc.blockPosition().below()).isFaceSturdy(npc.level(), npc.blockPosition().below(), Direction.UP)) {
                npc.setSprinting(false); npc.getNavigation().stop(); npc.setDeltaMovement(0, npc.getDeltaMovement().y, 0); return false;
            }
        }
        double distSq3D = npc.distanceToSqr(standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5);
        if (distSq3D > 2.0) {
            npc.setSprinting(distSq3D > 64.0);

            if (!npc.getNavigation().moveTo(standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5, 1.0D) || npc.getNavigation().isDone()) {
                if (Math.pow(standPos.getX() + 0.5 - npc.getX(), 2) + Math.pow(standPos.getZ() + 0.5 - npc.getZ(), 2) < 0.05) npc.setDeltaMovement(0, npc.getDeltaMovement().y, 0);
                else npc.getMoveControl().setWantedPosition(standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5, 1.0D);
            }
            return true;
        } else {
            npc.setSprinting(false); npc.getNavigation().stop(); npc.setDeltaMovement(0, npc.getDeltaMovement().y, 0);
            if (currentShape.usesRailsAndBridges()) npc.setPos(standPos.getX() + 0.5, npc.getY(), standPos.getZ() + 0.5);
            return false;
        }
    }

    private void executeWork(Level level, JobState state) {

        if (!fillQueue.isEmpty()) {
            BlockPos fillPos = fillQueue.peek();
            if (BridgingUtils.isUnsafeGap(level, fillPos)) {
                if (!BridgingUtils.executeSeal(npc, level, fillPos)) {
                    return;
                }
            }
            fillQueue.poll();
            return;
        }

        if (state.plug() || state.railNeeded() || state.bridge()) {
            BlockPos p = state.plug() ? state.leak() : (state.railNeeded() ? state.rail() : state.floor());
            npc.getLookControl().setLookAt(p.getX(), p.getY(), p.getZ());
            if (++diggingTicks > 5) {
                if (!BridgingUtils.executeSeal(npc, level, p)) {
                    diggingTicks = 0;
                    return;
                }
                diggingTicks = 0;
                cachedJobState = null;
            }
            return;
        }

        npc.getLookControl().setLookAt(state.target().getX(), state.target().getY(), state.target().getZ());

        if (++diggingTicks == 1 || diggingTicks % 5 == 0) npc.swing(InteractionHand.MAIN_HAND, true);

        BlockState targetState = level.getBlockState(state.target());

        if (targetState.isAir() || targetState.getDestroySpeed(level, state.target()) < 0.0f) {
            cachedJobState = null;
            return;
        }

        activeToolSlot = DiggingUtils.equipBestTool(npc, targetState, activeToolSlot);
        int delay = DiggingUtils.calculateDigDelay(npc, targetState, state.target());

        if (delay >= 999) {
            cachedJobState = null;
            currentShape.advance(npc.jobController.jobWidth, npc.jobController.jobLength, npc.jobController.jobHeight);
            diggingTicks = 0;
            return;
        }

        level.destroyBlockProgress(npc.getId(), state.target(), (int) (((float) diggingTicks / delay) * 10.0F));

        if (diggingTicks > delay) {
            DiggingUtils.damageActiveTool(npc, activeToolSlot);

            boolean isFallingBlock = level.getBlockState(state.target().above()).getBlock() instanceof FallingBlock;

            if (!level.isClientSide() && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                java.util.List<ItemStack> drops = Block.getDrops(targetState, serverLevel, state.target(), level.getBlockEntity(state.target()), npc, npc.getMainHandItem());
                for (ItemStack drop : drops) {
                    ItemStack remainder = ItemHandlerHelper.insertItemStacked(npc.inventory, drop, false);
                    if (!remainder.isEmpty()) Block.popResource(level, state.target(), remainder);
                }
            }
            level.destroyBlock(state.target(), false, npc);

            npc.setJobMined(npc.getJobMined() + 1);
            npc.experienceManager.addExperience(1);

            cachedJobState = null;

            if (!veinQueue.isEmpty() && state.target().equals(veinQueue.peek())) {
                veinQueue.poll(); fillQueue.add(state.target());
            } else {
                if (isFallingBlock) {
                    this.pauseTicks = 10;
                } else if (!state.isObstruction()) {
                    currentShape.advance(npc.jobController.jobWidth, npc.jobController.jobLength, npc.jobController.jobHeight);
                }
            }
            diggingTicks = 0;
        }
    }
}