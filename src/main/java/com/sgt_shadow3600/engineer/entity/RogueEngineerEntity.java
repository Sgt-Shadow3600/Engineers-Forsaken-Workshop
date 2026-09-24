package com.sgt_shadow3600.engineer.entity;

import com.sgt_shadow3600.engineer.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

public class RogueEngineerEntity extends EngineerCompanionEntity {

    public RogueEngineerEntity(EntityType<? extends EngineerCompanionEntity> type, Level level) {
        super(type, level);
    }

    // --- MONSTER PROPERTIES ---
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) { return true; }

    @Override
    public boolean isPersistenceRequired() { return false; }

    @Override
    public boolean shouldDropExperience() { return true; }

    @Override
    public boolean isRogue() { return true; }

    public static boolean checkRogueSpawnRules(EntityType<RogueEngineerEntity> type, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (spawnType == MobSpawnType.SPAWNER) {
            if (!Config.ALLOW_ROGUE_SPAWNERS.get()) return false;
        }
        return Monster.isDarkEnoughToSpawn(level, pos, random);
    }

    // --- SETUP & STATS ---
    public static AttributeSupplier.Builder createRogueAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D) // Config applied dynamically below
                .add(Attributes.MOVEMENT_SPEED, 0.35D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D) // Config applied dynamically below
                .add(Attributes.FOLLOW_RANGE, 64.0D);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);

        // Configs are safe to pull here, as the world is actively running
        double healthMult = Config.ROGUE_HEALTH_MULTIPLIER.get();
        double damageMult = Config.ROGUE_DAMAGE_MULTIPLIER.get();

        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(30.0D * healthMult);
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(4.0D * damageMult);

        this.setCurrentTask(CompanionTask.GUARD);
        this.setHealth(this.getMaxHealth());
        this.inventory.equipRogueGear(level);

        return data;
    }

    // --- AGGRESSIVE AI GOALS ---
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));

        this.goalSelector.addGoal(1, new com.sgt_shadow3600.engineer.entity.ai.EngineerRangedDefenseGoal(this) {
            @Override public boolean canUse() { return Config.ROGUE_RANGED_ATTACK.get() && super.canUse(); }
        });

        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, true));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 1.0D));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // --- TICK & COMBAT ---
    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.isAlive()) {
            if (this.tickCount % 40 == 0 && this.getHealth() < this.getMaxHealth()) {
                this.heal(1.0F); // Passive Regen
            }
        }
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        return super.doHurtTarget(target);
    }

    // --- DEATH & DROPS ---
    @Override
    protected ResourceKey<net.minecraft.world.level.storage.loot.LootTable> getDefaultLootTable() {
        return ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(com.sgt_shadow3600.engineer.EngineerCompanion.MODID, "entities/rogue_engineer"));
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource cause) {
        if (!this.level().isClientSide && this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + 1.0, this.getZ(), 80, 0.5, 1.0, 0.5, 0.1);
            sl.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + 1.0, this.getZ(), 30, 0.5, 1.0, 0.5, 0.05);
        }
        super.die(cause);
    }

    // --- INTERACTION ---
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS; // Cannot be interacted with via slates or bare hands
    }
}