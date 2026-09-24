package com.sgt_shadow3600.engineer.entity.managers;

import com.sgt_shadow3600.engineer.block.TerminalBlockEntity;
import com.sgt_shadow3600.engineer.entity.EngineerCompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class EngineerDeathHandler {

    /**
     * Calculates a safe Y-level, spawns a Logic Core block, serializes the dead NPC's data
     * into it, and notifies the linked terminal across dimensions.
     */
    public static void handleTerminalNotification(EngineerCompanionEntity companion) {
        if (companion.level().isClientSide) return;

        BlockPos pos = companion.blockPosition();
        if (pos.getY() < companion.level().getMinBuildHeight()) pos = pos.atY(companion.level().getMinBuildHeight() + 5);
        while (pos.getY() < companion.level().getMaxBuildHeight() && !companion.level().getBlockState(pos).getCollisionShape(companion.level(), pos).isEmpty()) {
            pos = pos.above();
        }

        CompoundTag npcData = new CompoundTag();
        companion.saveWithoutId(npcData);
        npcData.putFloat("Health", companion.getMaxHealth());
        npcData.putShort("DeathTime", (short) 0);
        npcData.putShort("HurtTime", (short) 0);
        npcData.remove("UUID");
        npcData.putFloat("SavedMaxHealth", companion.getMaxHealth());
        npcData.putInt("SavedArmor", companion.getArmorValue());

        Block blockToPlace = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.sgt_shadow3600.engineer.EngineerCompanion.MODID, "logic_core"));
        if (blockToPlace != null && blockToPlace != Blocks.AIR) {
            companion.level().setBlockAndUpdate(pos, blockToPlace.defaultBlockState());
            if (companion.level().getBlockEntity(pos) instanceof com.sgt_shadow3600.engineer.block.LogicCoreBlockEntity coreEntity) {
                coreEntity.setNpcData(npcData);
            }
        }

        if (companion.getLinkedTerminal() != null && companion.level().getServer() != null) {
            ServerLevel terminalLevel = companion.level().getServer().getLevel(companion.getLinkedTerminal().dimension());
            if (terminalLevel != null && terminalLevel.getBlockEntity(companion.getLinkedTerminal().pos()) instanceof TerminalBlockEntity terminal) {
                terminal.recordNpcDeath(companion, pos);
            }
        }
    }
}