package com.sgt_shadow3600.engineer.entity;

import com.sgt_shadow3600.engineer.EngineerCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.DifficultyInstance;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

public class EchoSentinelEntity extends Shulker implements VibrationSystem {

    private final VibrationSystem.Data vibrationData;
    private final VibrationSystem.User vibrationUser;
    private final DynamicGameEventListener<VibrationSystem.Listener> dynamicListener;

    private int cooldown = 0;
    private boolean attacksEverything = false;

    public EchoSentinelEntity(EntityType<? extends Shulker> type, Level level) {
        super(type, level);
        this.vibrationData = new VibrationSystem.Data();

        this.vibrationUser = new VibrationSystem.User() {
            @Override
            public int getListenerRadius() {
                return 8;
            }

            @Override
            public net.minecraft.world.level.gameevent.PositionSource getPositionSource() {
                return new net.minecraft.world.level.gameevent.EntityPositionSource(EchoSentinelEntity.this, EchoSentinelEntity.this.getEyeHeight());
            }

            @Override
            public boolean canReceiveVibration(ServerLevel level, BlockPos pos, Holder<GameEvent> gameEvent, GameEvent.Context context) {
                return cooldown <= 0;
            }

            @Override
            public void onReceiveVibration(ServerLevel level, BlockPos pos, Holder<GameEvent> gameEvent, @Nullable Entity entity, @Nullable Entity player, float distance) {
                Entity target = player != null ? player : entity;

                if (target instanceof LivingEntity livingTarget) {

                    if (livingTarget == EchoSentinelEntity.this || livingTarget instanceof EchoSentinelEntity) {
                        return;
                    }

                    boolean isValidTarget = attacksEverything || (livingTarget instanceof Enemy);

                    if (isValidTarget && cooldown <= 0) {
                        EchoSentinelEntity.this.setTarget(livingTarget);
                        EchoSentinelEntity.this.fireAtTarget(level, livingTarget);
                        cooldown = 40;
                    }
                }
            }
        };

        this.dynamicListener = new DynamicGameEventListener<>(new VibrationSystem.Listener(this));
    }

    @Override
    public void updateDynamicGameEventListener(BiConsumer<DynamicGameEventListener<?>, ServerLevel> consumer) {
        super.updateDynamicGameEventListener(consumer);
        if (this.level() instanceof ServerLevel serverLevel) {
            consumer.accept(this.dynamicListener, serverLevel);
        }
    }

    private void fireAtTarget(ServerLevel level, LivingEntity target) {
        ShulkerBullet bullet = new ShulkerBullet(level, this, target, Direction.Axis.Y);
        bullet.setPos(this.getX(), this.getY() + 0.8, this.getZ());
        level.addFreshEntity(bullet);

        this.playSound(SoundEvents.SHULKER_SHOOT, 2.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
        this.playSound(SoundEvents.SCULK_CLICKING, 1.0F, 1.5F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 50.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData spawnData) {
        if (reason == MobSpawnType.SPAWN_EGG || reason == MobSpawnType.NATURAL || reason == MobSpawnType.SPAWNER) {
            this.attacksEverything = true;
        }
        return super.finalizeSpawn(level, difficulty, reason, spawnData);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel serverLevel) {
            if (this.cooldown > 0) {
                this.cooldown--;
            } else if (this.getTarget() != null) {
                this.setTarget(null);
            }
            VibrationSystem.Ticker.tick(serverLevel, this.vibrationData, this.vibrationUser);
        }
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.targetSelector.removeAllGoals(goal -> true);
    }

    @Override
    public boolean isPushable() { return false; }

    @Override
    protected void doPush(Entity entity) {}

    @Override
    public boolean randomTeleport(double pX, double pY, double pZ, boolean pBroadcast) {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) return false;

        // Anti-Mitosis: Prevent Shulker Bullets from causing Shulker Duplication
        if (source.getDirectEntity() instanceof ShulkerBullet) {
            return false;
        }

        // Vanilla Shulker super.hurt() automatically handles the 50% damage reduction when closed!
        return super.hurt(source, amount);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);

        if (this.attacksEverything) {
            if (this.random.nextBoolean()) {
                this.spawnAtLocation(Items.SHULKER_SHELL);
            } else {
                this.spawnAtLocation(Items.SCULK_SENSOR);
            }
        } else {
            this.spawnAtLocation(EngineerCompanion.ECHO_SENTINEL_ITEM.get());
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (stack.is(Items.SCULK_VEIN) || stack.is(Items.COPPER_INGOT)) {
            if (this.getHealth() < this.getMaxHealth()) {
                this.heal(10.0F);
                if (!player.isCreative()) stack.shrink(1);
                this.playSound(SoundEvents.SCULK_BLOCK_HIT, 1.0F, 1.5F);
                if (this.level() instanceof ServerLevel sl) {
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY() + 0.5, this.getZ(), 5, 0.2, 0.2, 0.2, 0);
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
        }

        if (stack.isEmpty() && player.isShiftKeyDown()) {
            if (this.attacksEverything) {
                return InteractionResult.PASS;
            }

            if (!this.level().isClientSide) {
                this.spawnAtLocation(EngineerCompanion.ECHO_SENTINEL_ITEM.get());
                this.discard();
            }
            this.playSound(SoundEvents.ITEM_PICKUP, 1.0F, 1.0F);
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        return super.mobInteract(player, hand);
    }

    @Override
    public VibrationSystem.Data getVibrationData() { return this.vibrationData; }

    @Override
    public VibrationSystem.User getVibrationUser() { return this.vibrationUser; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("AttacksEverything", this.attacksEverything);
        VibrationSystem.Data.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, this.vibrationData)
                .ifSuccess(vibeData -> tag.put("VibrationData", vibeData));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.attacksEverything = tag.getBoolean("AttacksEverything");
        if (tag.contains("VibrationData")) {
            VibrationSystem.Data.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("VibrationData"))
                    .ifSuccess(data -> {});
        }
    }
}