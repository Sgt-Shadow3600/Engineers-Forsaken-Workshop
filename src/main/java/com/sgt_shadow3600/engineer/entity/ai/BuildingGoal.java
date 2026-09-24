package com.sgt_shadow3600.engineer.entity.ai;

import com.sgt_shadow3600.engineer.entity.EngineerCompanionEntity;
import com.sgt_shadow3600.engineer.entity.ai.utils.AIUtils;
import com.sgt_shadow3600.engineer.entity.ai.utils.ArrivalUtils;
import com.sgt_shadow3600.engineer.entity.ai.utils.BlueprintSession;
import com.sgt_shadow3600.engineer.entity.ai.utils.DiggingUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.EnumSet;
import java.util.List;

public class BuildingGoal extends Goal {
    private final EngineerCompanionEntity npc;
    private int buildCooldown = 0;
    private BlueprintSession.PendingBlock currentTarget = null;

    private int arrivalState = 0;
    private int arrivalTicks = 0;

    private int activeInventorySlot = -1;
    private int activeToolSlot = -1;
    private int stuckTicks = 0;
    private boolean isClearingObstruction = false;

    public BuildingGoal(EngineerCompanionEntity npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return npc.experienceManager.getLevel() >= 4 && npc.getCurrentTask() == EngineerCompanionEntity.CompanionTask.BUILD_HUT && !npc.isNeedsBuildingBlocks();
    }

    @Override
    public void start() {
        if (npc.level() instanceof ServerLevel sl) {
            com.sgt_shadow3600.engineer.entity.ai.utils.JobDefinition job = new com.sgt_shadow3600.engineer.entity.ai.utils.JobDefinition(
                    npc.jobController.jobSite, npc.jobController.jobDirection, npc.jobController.jobDepth, npc.jobController.jobWidth,
                    npc.jobController.jobShape, npc.jobController.jobLength, npc.jobController.jobHeight, npc.jobController.veinmine,
                    npc.jobController.clearSite, npc.jobController.isAdvancedJob, npc.jobController.schematicName, npc.jobController.blockSubstitutions
            );

            if (npc.isNewJob || npc.jobController.blueprintSession == null) {
                npc.jobController.blueprintSession = (new BlueprintSession(sl, job, npc.getJobMined()));
                npc.setJobTotal(Math.max(1, npc.jobController.blueprintSession.getTotalBlocks()));

                if (npc.jobController.clearSite && npc.isNewJob) {
                    npc.jobController.clearSite = false;

                    int bw = npc.jobController.blueprintSession.getBoundWidth();
                    int bh = npc.jobController.blueprintSession.getBoundHeight();
                    int bl = npc.jobController.blueprintSession.getBoundLength();

                    Direction forwardDir = Direction.from3DDataValue(npc.jobController.jobDirection);
                    if (forwardDir.getAxis() == Direction.Axis.Y) forwardDir = Direction.NORTH;

                    BlockPos digStart = npc.jobController.jobSite.relative(forwardDir, 1);

                    com.sgt_shadow3600.engineer.entity.ai.utils.JobDefinition digPrep = new com.sgt_shadow3600.engineer.entity.ai.utils.JobDefinition(
                            digStart, npc.jobController.jobDirection, bl, bw,
                            1, bl, bh, false,
                            false, false, "", new java.util.HashMap<>()
                    );

                    npc.jobController.jobQueue.addFirst(job);
                    npc.jobController.startJob(digPrep);
                    return;
                }
                npc.isNewJob = false;
            }
        }

        double maxRadius = Math.max(npc.jobController.blueprintSession.getBoundWidth(), npc.jobController.blueprintSession.getBoundLength()) / 2.0;
        this.arrivalState = ArrivalUtils.determineInitialState(npc, npc.jobController.jobSite, maxRadius);
        this.arrivalTicks = 0;

        buildCooldown = 0;
        stuckTicks = 0;
        activeToolSlot = -1;
        isClearingObstruction = false;
        currentTarget = null;
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
        isClearingObstruction = false;
    }

    @Override
    public void tick() {
        if (npc.jobController.blueprintSession == null) return;
        ServerLevel level = (ServerLevel) npc.level();

        // 1. ARRIVAL PHASE
        if (arrivalState > 0) {
            double maxRadius = Math.max(npc.jobController.blueprintSession.getBoundWidth(), npc.jobController.blueprintSession.getBoundLength()) / 2.0;
            arrivalState = ArrivalUtils.processArrival(npc, npc.jobController.jobSite, Direction.from3DDataValue(npc.jobController.jobDirection), maxRadius, arrivalState, arrivalTicks++);
            return;
        }

        // 2. BLUEPRINT POLLING
        if (currentTarget == null) {
            if (npc.jobController.blueprintSession.getQueue().isEmpty()) {
                npc.setJobProgress(1.0f);
                AIUtils.sendOwnerAlert(npc, "[BUILD COMPLETE]", "§a", "has finished construction at X:" + npc.getBlockX() + " Y:" + npc.getBlockY() + " Z:" + npc.getBlockZ());

                npc.jobController.archiveCurrentJob();
                if (npc.jobController.startNextJob()) {
                    AIUtils.sendOwnerAlert(npc, "[QUEUE ACTIVE]", "§e", "Moving to next queued blueprint! (" + npc.jobController.jobQueue.size() + " remaining)");
                    this.start();
                } else {
                    npc.setCurrentTask(EngineerCompanionEntity.CompanionTask.GO_HOME);
                }
                return;
            }
            currentTarget = npc.jobController.blueprintSession.getQueue().peek();
        }

        BlockPos pos = currentTarget.pos();
        BlockState desiredState = currentTarget.state();

        BlockState existingState = level.getBlockState(pos);
        if (existingState.is(desiredState.getBlock())) {
            npc.jobController.blueprintSession.getQueue().poll();
            currentTarget = null;
            incrementProgress();
            isClearingObstruction = false;
            return;
        }

        Item requiredItem = desiredState.getBlock().asItem();
        boolean hasItem = requiredItem == net.minecraft.world.item.Items.AIR || consumeItemFromInventory(requiredItem, true);

        if (!hasItem) {
            npc.setNeedsBuildingBlocks(true);
            return;
        } else {
            npc.setNeedsBuildingBlocks(false);
        }

        // 3. PLACEMENT PATHING
        double distSq = npc.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

        if (distSq > 20.0) {
            if (buildCooldown <= 0) {
                // ARCHITECT FIX: Dynamic pathfinding back-off to prevent CPU lag spikes.
                boolean canPath = npc.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0D);
                buildCooldown = canPath ? 10 : 40;
            }

            if (npc.getNavigation().isDone() || npc.getNavigation().isStuck()) {
                stuckTicks++;

                if (stuckTicks > 20) {
                    npc.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 30.0F, 30.0F);
                    npc.swing(InteractionHand.MAIN_HAND);
                    level.setBlock(pos, desiredState, 3);
                    SoundType soundType = desiredState.getSoundType(level, pos, npc);
                    level.playSound(null, pos, soundType.getPlaceSound(), SoundSource.BLOCKS, (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);

                    consumeItemFromInventory(requiredItem, false);
                    npc.jobController.blueprintSession.getQueue().poll();
                    currentTarget = null;
                    incrementProgress();

                    buildCooldown = 4;
                    stuckTicks = 0;
                }
            }
            buildCooldown--;
            return;
        }

        buildCooldown--;

        if (distSq <= 20.0 && buildCooldown <= 0) {
            npc.getNavigation().stop();
            npc.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 30.0F, 30.0F);

            if (!existingState.canBeReplaced()) {
                if (npc.jobController.clearSite || existingState.is(Blocks.DIRT) || existingState.is(Blocks.GRASS_BLOCK) || existingState.is(Blocks.TALL_GRASS)) {
                    if (!isClearingObstruction) {
                        isClearingObstruction = true;
                        activeToolSlot = DiggingUtils.equipBestTool(npc, existingState, activeToolSlot);
                        int dynamicDelay = DiggingUtils.calculateDigDelay(npc, existingState, pos);

                        if (dynamicDelay >= 999) {
                            npc.jobController.blueprintSession.getQueue().poll();
                            currentTarget = null;
                            incrementProgress();
                            isClearingObstruction = false;
                            return;
                        }

                        buildCooldown = dynamicDelay;
                        npc.swing(InteractionHand.MAIN_HAND);
                        return;
                    } else {
                        DiggingUtils.damageActiveTool(npc, activeToolSlot);

                        List<ItemStack> drops = Block.getDrops(existingState, level, pos, level.getBlockEntity(pos), npc, npc.getMainHandItem());
                        for (ItemStack drop : drops) {
                            ItemStack remainder = ItemHandlerHelper.insertItemStacked(npc.inventory, drop, false);
                            if (!remainder.isEmpty()) Block.popResource(level, pos, remainder);
                        }

                        level.destroyBlock(pos, false, npc);
                        isClearingObstruction = false;
                        buildCooldown = 5;
                        return;
                    }
                } else {
                    npc.jobController.blueprintSession.getQueue().poll();
                    currentTarget = null;
                    incrementProgress();
                    isClearingObstruction = false;
                    return;
                }
            }

            AABB blockBox = new AABB(pos).deflate(0.35);
            if (npc.getBoundingBox().intersects(blockBox)) {
                int npcY = (int) Math.floor(npc.getY());
                if (pos.getY() <= npcY) {
                    npc.setDeltaMovement(npc.getDeltaMovement().x, 0.42, npc.getDeltaMovement().z);
                    buildCooldown = 6;
                } else {
                    double vecX = npc.getX() - (pos.getX() + 0.5);
                    double vecZ = npc.getZ() - (pos.getZ() + 0.5);
                    double distSqH = vecX * vecX + vecZ * vecZ;

                    if (distSqH < 0.1) {
                        vecX = (npc.getRandom().nextBoolean() ? 1 : -1);
                        vecZ = (npc.getRandom().nextBoolean() ? 1 : -1);
                        distSqH = 2.0;
                    }

                    double dist = Math.sqrt(distSqH);
                    npc.setDeltaMovement(npc.getDeltaMovement().add((vecX / dist) * 0.4, 0.25, (vecZ / dist) * 0.4));
                    buildCooldown = 6;
                }
                return;
            }

            npc.swing(InteractionHand.MAIN_HAND);
            level.setBlock(pos, desiredState, 3);

            SoundType soundType = desiredState.getSoundType(level, pos, npc);
            level.playSound(null, pos, soundType.getPlaceSound(), SoundSource.BLOCKS, (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);

            consumeItemFromInventory(requiredItem, false);

            npc.jobController.blueprintSession.getQueue().poll();
            currentTarget = null;
            incrementProgress();
            buildCooldown = 4;
            isClearingObstruction = false;
        }
    }

    private void incrementProgress() {
        int mined = npc.getJobMined() + 1;
        npc.setJobMined(mined);
        npc.setJobProgress((float) mined / (float) npc.getJobTotal());
    }

    private boolean consumeItemFromInventory(Item item, boolean simulate) {
        if (activeSlotIsValid(item)) {
            if (!simulate) {
                ItemStack stack = npc.inventory.getStackInSlot(activeInventorySlot);
                stack.shrink(1);
                npc.inventory.setStackInSlot(activeInventorySlot, stack);
            }
            return true;
        }

        for (int i = 8; i <= 26; i++) {
            ItemStack stack = npc.inventory.getStackInSlot(i);
            if (stack.getItem() == item) {
                activeInventorySlot = i;
                if (!simulate) {
                    stack.shrink(1);
                    npc.inventory.setStackInSlot(i, stack);
                }
                return true;
            }
        }

        activeInventorySlot = -1;
        return false;
    }

    private boolean activeSlotIsValid(Item requiredItem) {
        if (activeInventorySlot >= 8 && activeInventorySlot <= 26) {
            ItemStack stack = npc.inventory.getStackInSlot(activeInventorySlot);
            return !stack.isEmpty() && stack.getItem() == requiredItem;
        }
        return false;
    }
}