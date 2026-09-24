package com.sgt_shadow3600.engineer.entity.ai;

import com.sgt_shadow3600.engineer.entity.EngineerCompanionEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.windcharge.BreezeWindCharge;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class EngineerRangedDefenseGoal extends Goal {
    private final EngineerCompanionEntity engineer;
    private LivingEntity target;

    public EngineerRangedDefenseGoal(EngineerCompanionEntity engineer) {
        this.engineer = engineer;
        // THE PASSENGER SEAT PATTERN:
        // By registering no flags, this goal runs concurrently. It will never block
        // the MeleeAttackGoal or AvoidEntityGoal from handling movement and rotation.
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        this.target = this.engineer.getTarget();
        if (this.target == null || !this.target.isAlive()) return false;

        // ARCHITECT FIX: At least Level 10 required to access the lowest tier ranged attack (Windcharge)
        if (this.engineer.experienceManager.getLevel() < 10) return false;

        // Engagement range: 24 blocks (576 = 24^2)
        return this.engineer.distanceToSqr(this.target) < 576.0D;
    }

    @Override
    public void tick() {
        if (this.target == null || !this.target.isAlive()) return;

        // Only fire if we actually have Line of Sight to avoid shooting into walls.
        if (this.engineer.getSensing().hasLineOfSight(this.target)) {
            int currentLevel = this.engineer.experienceManager.getLevel();

            // TIER 2: Sonic Blast / Seismic Pulse (Level 25+)
            // Cooldown: 600 - 900 ticks (30 to 45 seconds)
            if (currentLevel >= 25 && this.engineer.seismicCooldown <= 0) {
                fireSeismicPulse();
                this.engineer.seismicCooldown = 600 + this.engineer.getRandom().nextInt(300);
            }

            // TIER 1: Pneumatic Blast / Windcharge (Level 10+)
            // Cooldown: 60 - 100 ticks (3 to 5 seconds)
            if (currentLevel >= 10 && this.engineer.pneumaticCooldown <= 0) {
                firePneumaticBlast();
                this.engineer.pneumaticCooldown = 60 + this.engineer.getRandom().nextInt(40);
            }
        }
    }

    private void firePneumaticBlast() {
        Level level = this.engineer.level();
        Vec3 shootPos = this.engineer.position().add(0, this.engineer.getEyeHeight() - 0.2, 0);
        Vec3 targetPos = this.target.getEyePosition();
        Vec3 dir = targetPos.subtract(shootPos).normalize();

        // ARCHITECT FIX: Using BreezeWindCharge instead of standard WindCharge to prevent
        // Player-casting crashes in the vanilla projectile ticker.
        BreezeWindCharge windCharge = new BreezeWindCharge(EntityType.BREEZE_WIND_CHARGE, level);
        windCharge.setOwner(this.engineer);
        windCharge.setPos(shootPos.x, shootPos.y, shootPos.z);
        windCharge.shoot(dir.x, dir.y, dir.z, 1.5F, 1.0F);

        level.addFreshEntity(windCharge);

        float pitch = (this.engineer.getRandom().nextFloat() - this.engineer.getRandom().nextFloat()) * 0.2F + 1.0F;
        this.engineer.playSound(SoundEvents.WIND_CHARGE_THROW, 1.0F, pitch);
    }

    private void fireSeismicPulse() {
        Level level = this.engineer.level();
        Vec3 start = this.engineer.getEyePosition();
        Vec3 end = this.target.getEyePosition();
        Vec3 dir = end.subtract(start).normalize();
        double distance = start.distanceTo(end);

        // Render sonic boom particles purely on the server side to stream to clients
        if (level instanceof ServerLevel serverLevel) {
            for (int i = 1; i < Math.floor(distance); i++) {
                Vec3 particlePos = start.add(dir.scale(i));
                serverLevel.sendParticles(ParticleTypes.SONIC_BOOM, particlePos.x, particlePos.y, particlePos.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }

        this.target.hurt(this.engineer.damageSources().mobAttack(this.engineer), 10.0F);
        this.engineer.playSound(SoundEvents.WARDEN_SONIC_BOOM, 3.0F, 1.0F);
    }
}