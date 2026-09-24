package com.sgt_shadow3600.engineer.block;

import com.sgt_shadow3600.engineer.entity.EngineerCompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.UUID;

public class TerminalBlockEntity extends BlockEntity {

    public final ItemStackHandler inventory = new ItemStackHandler(54) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    public CompoundTag storedNpcData = new CompoundTag();

    public boolean isNpcActive = false;
    public UUID activeNpcId = null;
    public UUID ownerId = null;
    public int respawnTimer = 0;
    public int healTimer = 0;
    public boolean allowWarp = true;
    public int missingTicks = 0;

    public BlockPos deathPos = null;
    public boolean coreInTransit = false;

    // Replaces the heavy liveStats NBT tag for the chunk-load failsafe
    private BlockPos lastKnownNpcPos = null;

    public TerminalBlockEntity(BlockPos pos, BlockState state) {
        super(com.sgt_shadow3600.engineer.EngineerCompanion.TERMINAL_BE.get(), pos, state);
    }

    public void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    // ARCHITECT FIX: Decoupled client sync packets from disk serialization.
    // This prevents sending 81 slots of inventory NBT to all players in render distance every second!
    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        // ONLY send visual data required by the client renderer.
        tag.putBoolean("IsNpcActive", this.isNpcActive);
        tag.putBoolean("CoreInTransit", this.coreInTransit);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void addSleepingJob(com.sgt_shadow3600.engineer.entity.ai.utils.JobDefinition job) {
        if (this.deathPos != null) return;

        float hp = storedNpcData.contains("Health") ? storedNpcData.getFloat("Health") : 30.0f;
        float maxHp = storedNpcData.contains("SavedMaxHealth") ? storedNpcData.getFloat("SavedMaxHealth") : 30.0f;
        boolean isReconstructing = hp < maxHp;

        if (!this.isNpcActive && this.respawnTimer <= 0 && !isReconstructing && this.level instanceof ServerLevel sl) {
            EngineerCompanionEntity npc = this.spawnNpc(sl, this.worldPosition);
            if (npc != null) {
                npc.jobController.queueJob(job);
            }
            return;
        }

        net.minecraft.nbt.ListTag queueTag = this.storedNpcData.getList("JobQueue", 10);

        if (queueTag.size() < 30) {
            queueTag.add(job.toNBT());
            this.storedNpcData.put("JobQueue", queueTag);
            this.sync();
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, TerminalBlockEntity entity) {
        if (level.isClientSide) return;

        if (entity.isNpcActive && entity.activeNpcId != null) {
            if (level.getGameTime() % 20 == 0) {
                Entity e = ((ServerLevel)level).getEntity(entity.activeNpcId);

                if (e instanceof EngineerCompanionEntity npc) {
                    entity.lastKnownNpcPos = npc.blockPosition();
                    entity.missingTicks = 0;
                } else {
                    if (entity.lastKnownNpcPos != null) {
                        if (((ServerLevel) level).isPositionEntityTicking(entity.lastKnownNpcPos)) {
                            entity.missingTicks++;
                            if (entity.missingTicks >= 3) {
                                entity.recoverLostNpc();
                            }
                        }
                    }
                }
            }
        }

        if (entity.respawnTimer > 0) {
            entity.respawnTimer--;
            if (entity.respawnTimer % 20 == 0) entity.sync();
            if (entity.respawnTimer <= 0 && entity.deathPos == null) {
                entity.spawnNpc((ServerLevel) level, pos);
            }
            return;
        }

        if (!entity.isNpcActive && !entity.storedNpcData.isEmpty() && entity.respawnTimer <= 0 && entity.deathPos == null) {
            float currentHealth = entity.storedNpcData.getFloat("Health");
            float maxHealth = entity.storedNpcData.contains("SavedMaxHealth") ? entity.storedNpcData.getFloat("SavedMaxHealth") : 30.0f;

            if (currentHealth < maxHealth) {
                entity.healTimer++;
                if (entity.healTimer >= 20) {
                    currentHealth += 1.0f;
                    entity.storedNpcData.putFloat("Health", Math.min(currentHealth, maxHealth));
                    entity.healTimer = 0;
                    entity.sync();

                    if (currentHealth >= maxHealth) {
                        boolean hasActiveTask = entity.storedNpcData.contains("CurrentTask") &&
                                (entity.storedNpcData.getInt("CurrentTask") == EngineerCompanionEntity.CompanionTask.DIGGING.ordinal() ||
                                        entity.storedNpcData.getInt("CurrentTask") == EngineerCompanionEntity.CompanionTask.BUILD_HUT.ordinal());
                        boolean hasQueuedJobs = entity.storedNpcData.contains("JobQueue") && !entity.storedNpcData.getList("JobQueue", 10).isEmpty();

                        if (hasActiveTask || hasQueuedJobs) {
                            entity.spawnNpc((ServerLevel) level, pos);
                        }
                    }
                }
            }
        }
    }

    public void recoverLostNpc() {
        if (this.deathPos != null) return;
        this.isNpcActive = false;
        this.activeNpcId = null;
        this.missingTicks = 0;
        this.respawnTimer = 60;
        this.sync();
    }

    public void storeNpc(EngineerCompanionEntity npc) {
        this.storedNpcData = new CompoundTag();
        npc.saveWithoutId(this.storedNpcData);
        this.storedNpcData.putFloat("SavedMaxHealth", npc.getMaxHealth());
        this.storedNpcData.putInt("SavedArmor", npc.getArmorValue());

        this.isNpcActive = false;
        this.activeNpcId = null;
        this.deathPos = null;
        this.lastKnownNpcPos = null;
        npc.discard();
        this.sync();
    }

    public void recordNpcDeath(EngineerCompanionEntity npc, BlockPos exactDeathPos) {
        this.storedNpcData = new CompoundTag();
        this.isNpcActive = false;
        this.activeNpcId = null;
        this.deathPos = exactDeathPos;
        this.coreInTransit = true;
        this.lastKnownNpcPos = null;
        this.sync();
    }

    public void restoreFromCore(CompoundTag coreData) {
        if (level == null || level.isClientSide) return;

        this.storedNpcData = coreData;
        this.isNpcActive = false;
        this.activeNpcId = null;
        this.deathPos = null;
        this.coreInTransit = false;

        this.storedNpcData.putFloat("Health", 1.0f);
        if (!this.storedNpcData.contains("SavedMaxHealth")) {
            this.storedNpcData.putFloat("SavedMaxHealth", 30.0f);
        }

        this.sync();
    }

    public EngineerCompanionEntity spawnNpc(ServerLevel level, BlockPos pos) {
        EngineerCompanionEntity npc = new EngineerCompanionEntity(com.sgt_shadow3600.engineer.EngineerCompanion.ENGINEER_NPC.get(), level);

        if (!this.storedNpcData.isEmpty()) {
            npc.load(this.storedNpcData);
        }

        if (npc.getHealth() <= 0.0f) {
            npc.setHealth(npc.getMaxHealth());
        }

        npc.setDeltaMovement(0, 0, 0);
        npc.fallDistance = 0.0F;
        npc.deathTime = 0;
        npc.hurtTime = 0;
        npc.clearFire();

        if (this.ownerId != null) {
            npc.setOwnerUUID(this.ownerId);
        }

        BlockState state = level.getBlockState(pos);
        Direction facing = Direction.NORTH;
        if (state.hasProperty(TerminalBlock.FACING)) {
            facing = state.getValue(TerminalBlock.FACING);
        }

        BlockPos frontPos = pos.relative(facing);

        npc.setPos(frontPos.getX() + 0.5, frontPos.getY(), frontPos.getZ() + 0.5);
        npc.setHomeTerminal(pos);
        level.addFreshEntity(npc);
        this.isNpcActive = true;
        this.activeNpcId = npc.getUUID();
        this.lastKnownNpcPos = npc.blockPosition();
        this.storedNpcData = new CompoundTag();
        this.sync();

        return npc;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.put("StoredNPC", storedNpcData);
        tag.putBoolean("IsNpcActive", isNpcActive);
        tag.putBoolean("AllowWarp", allowWarp);
        if (activeNpcId != null) tag.putUUID("ActiveNpcId", activeNpcId);
        if (ownerId != null) tag.putUUID("OwnerId", ownerId);
        tag.putInt("RespawnTimer", respawnTimer);
        tag.putInt("HealTimer", healTimer);

        if (deathPos != null) tag.putLong("DeathPos", deathPos.asLong());
        tag.putBoolean("CoreInTransit", coreInTransit);

        if (lastKnownNpcPos != null) tag.putLong("LastKnownNpcPos", lastKnownNpcPos.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        storedNpcData = tag.getCompound("StoredNPC");
        isNpcActive = tag.getBoolean("IsNpcActive");
        if (tag.contains("AllowWarp")) allowWarp = tag.getBoolean("AllowWarp");
        if (tag.hasUUID("ActiveNpcId")) activeNpcId = tag.getUUID("ActiveNpcId");
        if (tag.hasUUID("OwnerId")) ownerId = tag.getUUID("OwnerId");
        respawnTimer = tag.getInt("RespawnTimer");
        healTimer = tag.getInt("HealTimer");

        if (tag.contains("DeathPos")) deathPos = BlockPos.of(tag.getLong("DeathPos"));
        coreInTransit = tag.getBoolean("CoreInTransit");

        if (tag.contains("LastKnownNpcPos")) lastKnownNpcPos = BlockPos.of(tag.getLong("LastKnownNpcPos"));
    }
}