package com.sgt_shadow3600.engineer.entity.ai.utils;

import com.sgt_shadow3600.engineer.entity.EngineerCompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

public class AIUtils {

    // Overload for backwards compatibility with other AI goals
    public static BlockPos getSafeDropZone(Level level, BlockPos target) {
        return getSafeDropZone(level, target, target);
    }

    // 1. Vertical Safety Scanner (Architect Refactor)
    public static BlockPos getSafeDropZone(Level level, BlockPos target, BlockPos fallback) {
        BlockPos.MutableBlockPos cursor = target.mutable();

        // Step A: If we warped inside a wall, try to ascend to the surface (Max 16 blocks)
        int upTries = 0;
        while (!level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty() && cursor.getY() < level.getMaxBuildHeight()) {
            cursor.move(Direction.UP);
            if (++upTries > 16) return fallback; // Trapped too deep, abort to fallback
        }

        // Step B: If we warped in the air, descend to find the floor (Max 32 blocks)
        int downTries = 0;
        while (level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty() && cursor.getY() > level.getMinBuildHeight()) {
            cursor.move(Direction.DOWN);
            if (++downTries > 32) return fallback; // Bottomless pit or void, abort to fallback
        }

        // Step C: Final safety verification. Cursor must be solid ground, block above must be air.
        if (!level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty() &&
                level.getBlockState(cursor.above()).getCollisionShape(level, cursor.above()).isEmpty()) {
            return cursor.above();
        }

        return fallback;
    }

    // 2. Standardized Teleport Sequence
    public static void executeEnderWarp(EngineerCompanionEntity npc, BlockPos target) {
        if (npc.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.PORTAL, npc.getX(), npc.getY() + 1.0D, npc.getZ(), 20, 0.5D, 1.0D, 0.5D, 0.1D);
            sl.playSound(null, npc.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1.0F, 1.0F);

            npc.setPos(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);

            // ARCHITECT FIX: Purge kinetic momentum and fall data. DO NOT REMOVE.
            npc.setDeltaMovement(0, 0, 0);
            npc.fallDistance = 0.0F;
            npc.getNavigation().stop();

            sl.sendParticles(ParticleTypes.PORTAL, npc.getX(), npc.getY() + 1.0D, npc.getZ(), 20, 0.5D, 1.0D, 0.5D, 0.1D);
            sl.playSound(null, npc.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1.0F, 1.0F);
        } else {
            npc.setPos(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        }
    }

    // 3. Stationary Visual Pop (For changing dimensions, sleeping, etc.)
    public static void playEnderPopVFX(EngineerCompanionEntity npc) {
        if (npc.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.PORTAL, npc.getX(), npc.getY() + 1.0D, npc.getZ(), 30, 0.5D, 1.0D, 0.5D, 0.1D);
            sl.playSound(null, npc.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1.0F, 1.0F);
        }
    }

    // 4. Centralized Owner Messaging
    public static void sendOwnerAlert(EngineerCompanionEntity npc, String prefix, String colorCode, String message) {
        if (npc.getOwnerUUID() != null && npc.level().getServer() != null) {
            ServerPlayer owner = npc.level().getServer().getPlayerList().getPlayer(npc.getOwnerUUID());
            if (owner != null) {
                String name = npc.hasCustomName() ? npc.getCustomName().getString() : "Engineer";
                owner.sendSystemMessage(Component.literal(colorCode + prefix + "§r " + name + " " + message));
            }
        }
    }
}