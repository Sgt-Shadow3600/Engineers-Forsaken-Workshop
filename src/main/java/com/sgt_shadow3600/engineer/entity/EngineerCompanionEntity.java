package com.sgt_shadow3600.engineer.entity;

import com.sgt_shadow3600.engineer.entity.ai.*;
import com.sgt_shadow3600.engineer.entity.managers.EngineerInventoryManager;
import com.sgt_shadow3600.engineer.entity.managers.EngineerJobController;
import com.sgt_shadow3600.engineer.entity.managers.EngineerExperienceManager;
import com.sgt_shadow3600.engineer.entity.managers.EngineerDeathHandler;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class EngineerCompanionEntity extends TamableAnimal {

    // Sync Data
    private static final EntityDataAccessor<Integer> TASK_SYNC = SynchedEntityData.defineId(EngineerCompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DEFENSIVE_MODE = SynchedEntityData.defineId(EngineerCompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> SYNC_LEVEL = SynchedEntityData.defineId(EngineerCompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SYNC_XP = SynchedEntityData.defineId(EngineerCompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SYNC_BLOCKS = SynchedEntityData.defineId(EngineerCompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SYNC_TORCHES = SynchedEntityData.defineId(EngineerCompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> MISSING_SUPPLIES = SynchedEntityData.defineId(EngineerCompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> JOB_MINED = SynchedEntityData.defineId(EngineerCompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> JOB_TOTAL = SynchedEntityData.defineId(EngineerCompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> JOB_PROGRESS = SynchedEntityData.defineId(EngineerCompanionEntity.class, EntityDataSerializers.FLOAT);

    // Sub-Managers
    public final EngineerInventoryManager inventory = new EngineerInventoryManager(this);
    public final EngineerJobController jobController = new EngineerJobController(this);
    public final EngineerExperienceManager experienceManager = new EngineerExperienceManager(this);

    // State Variables
    private CompanionTask currentTask = CompanionTask.IDLE;
    private GlobalPos linkedTerminal = null;
    private BlockPos homeTerminal = null;
    private int idleTicks = 0;
    private UUID warpTargetId = null;

    public boolean isNewJob = false;
    private boolean requiresAxe = false;
    private boolean requiresSword = false;
    private boolean needsBuildingBlocks = false;

    public int pneumaticCooldown = 0;
    public int seismicCooldown = 0;

    public EngineerCompanionEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        this.setCanPickUpLoot(true);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) { return false; }

    @Override
    public boolean isPersistenceRequired() { return true; }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TASK_SYNC, 0);
        builder.define(DEFENSIVE_MODE, true);
        builder.define(SYNC_LEVEL, 1);
        builder.define(SYNC_XP, 0);
        builder.define(SYNC_BLOCKS, 0);
        builder.define(SYNC_TORCHES, 0);
        builder.define(MISSING_SUPPLIES, "");
        builder.define(JOB_MINED, 0);
        builder.define(JOB_TOTAL, 0);
        builder.define(JOB_PROGRESS, 0.0f);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TamableAnimal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 128.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new net.minecraft.world.entity.ai.goal.FloatGoal(this));

        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, true) {
            @Override public boolean canUse() { return !EngineerCompanionEntity.this.inventory.getStackInSlot(3).isEmpty() && super.canUse(); }
        });

        this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Monster.class, 8.0F, 1.2D, 1.5D) {
            @Override public boolean canUse() { return EngineerCompanionEntity.this.inventory.getStackInSlot(3).isEmpty() && super.canUse(); }
        });

        this.goalSelector.addGoal(4, new TerminalLogisticsGoal(this));
        this.goalSelector.addGoal(5, new DiggingGoal(this));
        this.goalSelector.addGoal(5, new BuildingGoal(this));
        this.goalSelector.addGoal(5, new GoHomeGoal(this));
        this.goalSelector.addGoal(6, new EngineerFollowGoal(this, 1.2D));
        this.goalSelector.addGoal(7, new TerminalWanderGoal(this, 1.0D));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false, (target) -> {
            return target.distanceToSqr(this) <= (this.isDefensiveMode() ? 16.0D : 144.0D);
        }));
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.isAlive()) {

            if (this.pneumaticCooldown > 0) this.pneumaticCooldown--;
            if (this.seismicCooldown > 0) this.seismicCooldown--;

            if (this.getHealth() <= this.getMaxHealth() * 0.25f && this.currentTask != CompanionTask.GO_HOME && this.currentTask != CompanionTask.IDLE) {
                this.setCurrentTask(CompanionTask.GO_HOME);
                if (this.getOwnerUUID() != null) {
                    net.minecraft.server.level.ServerPlayer owner = (net.minecraft.server.level.ServerPlayer) this.level().getPlayerByUUID(this.getOwnerUUID());
                    if (owner != null) owner.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e[WARNING]§r " + this.getName().getString() + " sustained critical damage! Retreating..."));
                }
            }

            if (this.currentTask == CompanionTask.IDLE) {
                if (++idleTicks >= 6000) {
                    this.setCurrentTask(CompanionTask.GO_HOME);
                    idleTicks = 0;
                }
            } else {
                idleTicks = 0;
            }

            if (this.tickCount % 20 == 0) {
                this.inventory.syncInventoryDisplayData();
                this.entityData.set(SYNC_LEVEL, experienceManager.getLevel());
                this.entityData.set(SYNC_XP, experienceManager.getExperience());
            }

            if (this.getTarget() != null && this.getTarget().isAlive()) {
                ItemStack sword = this.inventory.getStackInSlot(3);
                if (!sword.isEmpty() && this.getMainHandItem().getItem() != sword.getItem()) {
                    this.setItemSlot(EquipmentSlot.MAINHAND, sword.copy());
                }
            }

            if (this.tickCount % 10 == 0) {
                this.inventory.tickVacuum(this.level());
            }
        }
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        boolean success = super.hurt(source, amount);
        if (success && this.level() instanceof ServerLevel serverLevel && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)) {
            int damageAmount = (int) Math.max(1.0F, amount / 4.0F);
            this.inventory.damageArmorSlot(serverLevel, EquipmentSlot.HEAD, 4, damageAmount);
            this.inventory.damageArmorSlot(serverLevel, EquipmentSlot.CHEST, 5, damageAmount);
            this.inventory.damageArmorSlot(serverLevel, EquipmentSlot.LEGS, 6, damageAmount);
            this.inventory.damageArmorSlot(serverLevel, EquipmentSlot.FEET, 7, damageAmount);
            this.inventory.syncEquipmentDurability(EquipmentSlot.OFFHAND, 8);
        }
        return success;
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        ItemStack sword = this.inventory.getStackInSlot(3);
        if (!sword.isEmpty()) this.setItemSlot(EquipmentSlot.MAINHAND, sword.copy());

        boolean success = super.doHurtTarget(target);
        if (success) {
            ItemStack mainHand = this.getMainHandItem();
            if (!mainHand.isEmpty() && mainHand.isDamageableItem() && this.level() instanceof ServerLevel serverLevel) {
                mainHand.hurtAndBreak(1, serverLevel, null, (item) -> this.onEquippedItemBroken(item, EquipmentSlot.MAINHAND));
                if (!this.inventory.getStackInSlot(3).isEmpty()) {
                    this.inventory.setStackInSlot(3, mainHand.isEmpty() ? ItemStack.EMPTY : mainHand.copy());
                }
            }
            this.experienceManager.addExperience(2);
        }
        return success;
    }

    @Override
    public boolean shouldDropExperience() { return false; }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource cause) {
        if (!this.level().isClientSide) {
            EngineerDeathHandler.handleTerminalNotification(this);
            for (EquipmentSlot slot : EquipmentSlot.values()) this.setItemSlot(slot, ItemStack.EMPTY);
        }
        super.die(cause);
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) { return null; }

    @Override
    public boolean isFood(ItemStack stack) { return false; }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

        if (!(player.getItemInHand(hand).getItem() instanceof com.sgt_shadow3600.engineer.item.CommandSlateItem)) {
            if (!this.level().isClientSide && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider((id, playerInv, p) -> new com.sgt_shadow3600.engineer.inventory.EngineerCompanionMenu(id, playerInv, this), net.minecraft.network.chat.Component.literal("Engineer Loadout")), buf -> buf.writeInt(this.getId()));
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Inventory", inventory.saveNBT(this.registryAccess()));
        tag.putInt("CurrentTask", this.currentTask.ordinal());
        tag.putBoolean("DefensiveMode", this.isDefensiveMode());
        tag.putInt("PneumaticCooldown", this.pneumaticCooldown);
        tag.putInt("SeismicCooldown", this.seismicCooldown);
        tag.putBoolean("RequiresAxe", this.requiresAxe);
        tag.putBoolean("RequiresSword", this.requiresSword);
        tag.putBoolean("NeedsBuildingBlocks", this.needsBuildingBlocks);
        tag.putInt("JobMined", this.getJobMined());
        tag.putInt("JobTotal", this.getJobTotal());
        tag.putFloat("JobProgress", this.getJobProgress());

        if (this.warpTargetId != null) tag.putUUID("WarpTarget", this.warpTargetId);
        if (this.homeTerminal != null) tag.putLong("HomeTerminal", this.homeTerminal.asLong());
        if (this.linkedTerminal != null) GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, this.linkedTerminal).ifSuccess(nbt -> tag.put("LinkedTerminalPos", nbt));

        jobController.saveNBT(tag);
        experienceManager.saveNBT(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Inventory")) inventory.loadNBT(this.registryAccess(), tag.getCompound("Inventory"));
        if (tag.contains("CurrentTask")) this.setCurrentTask(CompanionTask.values()[tag.getInt("CurrentTask")]);
        if (tag.contains("DefensiveMode")) this.setDefensiveMode(tag.getBoolean("DefensiveMode"));
        if (tag.contains("PneumaticCooldown")) this.pneumaticCooldown = tag.getInt("PneumaticCooldown");
        if (tag.contains("SeismicCooldown")) this.seismicCooldown = tag.getInt("SeismicCooldown");
        if (tag.contains("RequiresAxe")) this.requiresAxe = tag.getBoolean("RequiresAxe");
        if (tag.contains("RequiresSword")) this.requiresSword = tag.getBoolean("RequiresSword");
        if (tag.contains("NeedsBuildingBlocks")) this.needsBuildingBlocks = tag.getBoolean("NeedsBuildingBlocks");
        if (tag.contains("JobMined")) this.setJobMined(tag.getInt("JobMined"));
        if (tag.contains("JobTotal")) this.setJobTotal(tag.getInt("JobTotal"));
        if (tag.contains("JobProgress")) this.setJobProgress(tag.getFloat("JobProgress"));

        if (tag.hasUUID("WarpTarget")) this.warpTargetId = tag.getUUID("WarpTarget");
        if (tag.contains("HomeTerminal")) this.homeTerminal = BlockPos.of(tag.getLong("HomeTerminal"));
        if (tag.contains("LinkedTerminalPos")) GlobalPos.CODEC.parse(NbtOps.INSTANCE, tag.get("LinkedTerminalPos")).ifSuccess(pos -> this.linkedTerminal = pos);

        jobController.loadNBT(tag);
        experienceManager.loadNBT(tag);
    }

    public Item getDimensionBlockItem() { return this.level().dimension() == Level.NETHER ? Items.NETHERRACK : this.level().dimension() == Level.END ? Items.END_STONE : Items.COBBLESTONE; }
    public Block getDimensionBlock() { return this.level().dimension() == Level.NETHER ? Blocks.NETHERRACK : this.level().dimension() == Level.END ? Blocks.END_STONE : Blocks.COBBLESTONE; }

    public boolean isRogue() { return false; }

    public CompanionTask getCurrentTask() { return CompanionTask.values()[this.entityData.get(TASK_SYNC)]; }
    public void setCurrentTask(CompanionTask task) {
        if (task == CompanionTask.IDLE) this.requiresAxe = false;
        this.currentTask = task;
        this.entityData.set(TASK_SYNC, task.ordinal());
    }

    public UUID getWarpTarget() { return warpTargetId; }
    public void setWarpTarget(UUID id) { this.warpTargetId = id; }
    public int getJobMined() { return this.entityData.get(JOB_MINED); }
    public void setJobMined(int blocks) {
        if (blocks > this.entityData.get(JOB_MINED)) this.experienceManager.addExperience(1);
        this.entityData.set(JOB_MINED, blocks);
    }
    public int getJobTotal() { return this.entityData.get(JOB_TOTAL); }
    public void setJobTotal(int total) { this.entityData.set(JOB_TOTAL, total); }
    public float getJobProgress() { return this.entityData.get(JOB_PROGRESS); }
    public void setJobProgress(float progress) { this.entityData.set(JOB_PROGRESS, Math.min(1.0f, Math.max(0.0f, progress))); }

    public BlockPos getHomeTerminal() { return homeTerminal; }
    public void setHomeTerminal(BlockPos pos) { this.homeTerminal = pos; this.linkedTerminal = GlobalPos.of(this.level().dimension(), pos); }
    public boolean requiresAxe() { return requiresAxe; }
    public void setRequiresAxe(boolean val) { this.requiresAxe = val; }
    public boolean requiresSword() { return requiresSword; }
    public void setRequiresSword(boolean val) { this.requiresSword = val; }
    public boolean isDefensiveMode() { return this.entityData.get(DEFENSIVE_MODE); }
    public void setDefensiveMode(boolean def) { this.entityData.set(DEFENSIVE_MODE, def); }
    public GlobalPos getLinkedTerminal() { return linkedTerminal; }
    public void setLinkedTerminal(GlobalPos linkedTerminal) { this.linkedTerminal = linkedTerminal; }
    public boolean isNeedsBuildingBlocks() { return needsBuildingBlocks; }
    public void setNeedsBuildingBlocks(boolean val) { this.needsBuildingBlocks = val; }
    public int getSyncedBlocks() { return this.entityData.get(SYNC_BLOCKS); }
    public int getSyncedTorches() { return this.entityData.get(SYNC_TORCHES); }
    public String getMissingSupplies() { return this.entityData.get(MISSING_SUPPLIES); }

    // Setters for Managers
    public void setSyncedBlocks(int blocks) { this.entityData.set(SYNC_BLOCKS, blocks); }
    public void setSyncedTorches(int torches) { this.entityData.set(SYNC_TORCHES, torches); }
    public void setMissingSupplies(String supplies) { this.entityData.set(MISSING_SUPPLIES, supplies); }

    public enum CompanionTask { IDLE, DIGGING, BUILD_HUT, GUARD, FOLLOW, GO_HOME, FETCH_LIGHTS, SYSTEM_HALT, PREPARE_WARP }
}