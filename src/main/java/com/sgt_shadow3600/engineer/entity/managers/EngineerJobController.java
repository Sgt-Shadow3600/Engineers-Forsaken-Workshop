package com.sgt_shadow3600.engineer.entity.managers;

import com.sgt_shadow3600.engineer.entity.EngineerCompanionEntity;
import com.sgt_shadow3600.engineer.entity.ai.shapes.DigShapeLibrary;
import com.sgt_shadow3600.engineer.entity.ai.utils.BlueprintSession;
import com.sgt_shadow3600.engineer.entity.ai.utils.JobDefinition;
import com.sgt_shadow3600.engineer.entity.ai.utils.LogisticsUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class EngineerJobController {

    private final EngineerCompanionEntity companion;

    public transient BlueprintSession blueprintSession = null;

    public final LinkedList<JobDefinition> jobQueue = new LinkedList<>();
    public final LinkedList<JobDefinition> jobHistory = new LinkedList<>();

    public BlockPos jobSite = null;
    public int jobDirection = 0;
    public int jobShape = 0;
    public int jobDepth = 10;
    public int jobWidth = 1;
    public int jobLength = 5;
    public int jobHeight = 4;

    public boolean veinmine = false;
    public boolean clearSite = false;
    public boolean isAdvancedJob = false;
    public String schematicName = "";
    public Map<String, String> blockSubstitutions = new HashMap<>();

    public EngineerJobController(EngineerCompanionEntity companion) {
        this.companion = companion;
    }

    public void queueJob(JobDefinition job) {
        if (companion.getCurrentTask() == EngineerCompanionEntity.CompanionTask.IDLE || companion.getCurrentTask() == EngineerCompanionEntity.CompanionTask.GUARD) {
            this.startJob(job);
        } else {
            if (this.jobQueue.size() >= 30) {
                if (!companion.level().isClientSide && companion.getOwnerUUID() != null) {
                    ServerPlayer owner = (ServerPlayer) companion.level().getPlayerByUUID(companion.getOwnerUUID());
                    if (owner != null) owner.sendSystemMessage(Component.literal("§c[QUEUE FULL]§r " + companion.getName().getString() + " memory banks full. Cannot accept more than 30 queued jobs."));
                }
                return;
            }

            this.jobQueue.add(job);
            if (!companion.level().isClientSide && companion.getOwnerUUID() != null) {
                ServerPlayer owner = (ServerPlayer) companion.level().getPlayerByUUID(companion.getOwnerUUID());
                if (owner != null) owner.sendSystemMessage(Component.literal("§e[JOB QUEUED]§r " + companion.getName().getString() + " added job to clipboard. (" + jobQueue.size() + " pending)"));
            }
        }
    }

    public void startJob(JobDefinition job) {
        this.blueprintSession = null;

        // Chained Job Logic
        if (job.site.getY() <= -500) {
            if (!this.jobHistory.isEmpty()) {
                JobDefinition last = this.jobHistory.getLast();
                Direction dir = Direction.from3DDataValue(last.direction);
                BlockPos projected = last.site;

                DigShapeLibrary.AbstractDigShape shapeImpl = DigShapeLibrary.getShape(last.shape);

                if (shapeImpl != null) {
                    projected = shapeImpl.getChainedAnchor(last.site, dir, last.depth, last.width, last.length, last.height);
                    boolean lastIsCentered = (last.shape == 2 || last.shape == 3);
                    boolean nextIsCentered = (job.shape == 2 || job.shape == 3);

                    if (lastIsCentered && !nextIsCentered) {
                        int offset = (last.shape == 2) ? (last.length / 2 + 1) : (last.length + 1);
                        projected = projected.relative(dir, offset);
                    } else if (!lastIsCentered && nextIsCentered) {
                        int offset = (job.shape == 2) ? ((job.length - 1) / 2) : job.length;
                        projected = projected.relative(dir, offset);
                    }
                } else if (last.shape >= 10) {
                    projected = projected.relative(dir, last.length);
                }

                this.jobSite = projected;
                job.direction = last.direction;
            } else {
                this.jobSite = companion.blockPosition();
            }
        } else {
            this.jobSite = job.site;
        }

        this.jobDirection = job.direction;
        this.jobDepth = job.depth;
        this.jobWidth = job.width;
        this.jobShape = job.shape;
        this.jobLength = job.length;
        this.jobHeight = job.height;
        this.veinmine = job.veinmine;
        this.clearSite = job.clearSite;
        this.isAdvancedJob = job.isAdvanced;
        this.schematicName = job.schematicName;
        this.blockSubstitutions = job.blockSubstitutions;

        int totalBlocks = 1;
        if (job.shape == 0 || job.shape == 1) totalBlocks = job.depth * job.width * job.height;
        else if (job.shape == 2) totalBlocks = job.depth * job.width * job.length;
        else if (job.shape == 3) {
            float radius = job.length / 2.0f;
            totalBlocks = (int) (Math.PI * radius * radius * job.depth);
        }

        companion.setJobTotal(Math.max(totalBlocks, 1));
        companion.setJobMined(0);
        companion.setJobProgress(0.0f);
        companion.isNewJob = true;

        if (job.shape >= 10 || job.shape == 99) {
            companion.setCurrentTask(EngineerCompanionEntity.CompanionTask.BUILD_HUT);
        } else {
            companion.setCurrentTask(EngineerCompanionEntity.CompanionTask.DIGGING);
        }

        if (!companion.level().isClientSide && companion.getLinkedTerminal() != null) {
            if (companion.level().dimension() == companion.getLinkedTerminal().dimension()) {
                BlockPos tPos = companion.getLinkedTerminal().pos();
                if (companion.distanceToSqr(tPos.getX() + 0.5, tPos.getY(), tPos.getZ() + 0.5) < 64.0) {
                    LogisticsUtils.executeLogistics(companion, (ServerLevel) companion.level(), tPos);
                }
            }
        }
    }

    public boolean startNextJob() {
        if (!this.jobQueue.isEmpty()) {
            startJob(this.jobQueue.poll());
            return true;
        }
        return false;
    }

    public void archiveCurrentJob() {
        if (this.jobSite != null) {
            JobDefinition current = new JobDefinition(
                    this.jobSite, this.jobDirection, this.jobDepth, this.jobWidth,
                    this.jobShape, this.jobLength, this.jobHeight, this.veinmine,
                    this.clearSite, this.isAdvancedJob, this.schematicName, this.blockSubstitutions
            );
            this.jobHistory.add(current);
            if (this.jobHistory.size() > 10) this.jobHistory.removeFirst();
        }
    }

    public void clearJobQueue() {
        this.jobQueue.clear();
    }

    public void saveNBT(CompoundTag tag) {
        tag.putBoolean("Veinmine", this.veinmine);
        tag.putBoolean("IsAdvancedJob", this.isAdvancedJob);
        tag.putString("SchematicName", this.schematicName);
        tag.putBoolean("ClearSite", this.clearSite);

        ListTag subsList = new ListTag();
        for (Map.Entry<String, String> entry : this.blockSubstitutions.entrySet()) {
            CompoundTag subTag = new CompoundTag();
            subTag.putString("original", entry.getKey());
            subTag.putString("replacement", entry.getValue());
            subsList.add(subTag);
        }
        tag.put("Substitutions", subsList);

        if (this.jobSite != null) {
            tag.putLong("JobSite", this.jobSite.asLong());
            tag.putInt("JobDirection", this.jobDirection);
            tag.putInt("JobDepth", this.jobDepth);
            tag.putInt("JobWidth", this.jobWidth);
            tag.putInt("JobShape", this.jobShape);
            tag.putInt("JobLength", this.jobLength);
            tag.putInt("JobHeight", this.jobHeight);
        }

        ListTag queueTag = new ListTag();
        for (JobDefinition job : jobQueue) queueTag.add(job.toNBT());
        tag.put("JobQueue", queueTag);

        ListTag historyTag = new ListTag();
        for (JobDefinition job : jobHistory) historyTag.add(job.toNBT());
        tag.put("JobHistory", historyTag);
    }

    public void loadNBT(CompoundTag tag) {
        if (tag.contains("Veinmine")) this.veinmine = tag.getBoolean("Veinmine");
        if (tag.contains("IsAdvancedJob")) this.isAdvancedJob = tag.getBoolean("IsAdvancedJob");
        if (tag.contains("SchematicName")) this.schematicName = tag.getString("SchematicName");
        if (tag.contains("ClearSite")) this.clearSite = tag.getBoolean("ClearSite");

        this.blockSubstitutions.clear();
        if (tag.contains("Substitutions", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Substitutions", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag subTag = list.getCompound(i);
                this.blockSubstitutions.put(subTag.getString("original"), subTag.getString("replacement"));
            }
        }

        if (tag.contains("JobSite")) {
            this.jobSite = BlockPos.of(tag.getLong("JobSite"));
            this.jobDirection = tag.getInt("JobDirection");
            this.jobDepth = tag.getInt("JobDepth");
            this.jobWidth = tag.getInt("JobWidth");
            this.jobShape = tag.getInt("JobShape");
            this.jobLength = tag.getInt("JobLength");
            this.jobHeight = tag.getInt("JobHeight");
        }

        if (tag.contains("JobQueue")) {
            jobQueue.clear();
            ListTag queueTag = tag.getList("JobQueue", 10);
            for (int i = 0; i < queueTag.size(); i++) jobQueue.add(JobDefinition.fromNBT(queueTag.getCompound(i)));
        }
        if (tag.contains("JobHistory")) {
            jobHistory.clear();
            ListTag historyTag = tag.getList("JobHistory", 10);
            for (int i = 0; i < historyTag.size(); i++) jobHistory.add(JobDefinition.fromNBT(historyTag.getCompound(i)));
        }
    }
}