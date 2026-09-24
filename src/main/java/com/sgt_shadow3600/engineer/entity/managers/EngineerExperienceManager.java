package com.sgt_shadow3600.engineer.entity.managers;

import com.sgt_shadow3600.engineer.entity.EngineerCompanionEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.List;

public class EngineerExperienceManager {

    private final EngineerCompanionEntity companion;
    private int experience = 0;
    private int level = 1;

    public EngineerExperienceManager(EngineerCompanionEntity companion) {
        this.companion = companion;
    }

    /**
     * Handles XP gain, Mending calculations, and Level-up thresholds.
     */
    public void addExperience(int xp) {
        int remainingXp = xp;
        if (!companion.level().isClientSide && remainingXp > 0) {
            try {
                // Fetch the Mending enchantment reference dynamically (1.21 standard)
                var registry = companion.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
                var mendingOpt = registry.getHolder(net.minecraft.world.item.enchantment.Enchantments.MENDING);

                if (mendingOpt.isPresent()) {
                    var mendingHolder = mendingOpt.get();
                    List<Integer> mendingSlots = new ArrayList<>();

                    // Scan equipped inventory for damaged items with Mending
                    for (int i = 0; i <= 8; i++) {
                        ItemStack stack = companion.inventory.getStackInSlot(i);
                        if (!stack.isEmpty() && stack.isDamaged() && EnchantmentHelper.getItemEnchantmentLevel(mendingHolder, stack) > 0) {
                            mendingSlots.add(i);
                        }
                    }

                    // Apply repair logic
                    if (!mendingSlots.isEmpty()) {
                        int slotIndex = mendingSlots.get(companion.getRandom().nextInt(mendingSlots.size()));
                        ItemStack stackToRepair = companion.inventory.getStackInSlot(slotIndex);
                        int durabilityToRepair = remainingXp * 2;
                        int actualRepair = Math.min(durabilityToRepair, stackToRepair.getDamageValue());

                        stackToRepair.setDamageValue(stackToRepair.getDamageValue() - actualRepair);
                        remainingXp -= (int) Math.ceil(actualRepair / 2.0f);
                        companion.inventory.setStackInSlot(slotIndex, stackToRepair);
                    }
                }
            } catch (Exception ignored) {
                // Failsafe catch to prevent ticking crashes if the registry lookup fails during boot
            }
        }

        // Apply remaining XP to leveling
        if (remainingXp > 0) {
            this.experience += remainingXp;
            // Standard scaling threshold: 50 * (Level ^ 1.5)
            if (this.experience >= (int) (50 * Math.pow(this.level, 1.5))) {
                this.level++;
                this.experience = 0;
                if (!companion.level().isClientSide) {
                    ((ServerLevel) companion.level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, companion.getX(), companion.getY() + 1.0D, companion.getZ(), 10, 0.5D, 0.5D, 0.5D, 0.0D);
                }
            }
        }
    }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public int getExperience() { return experience; }
    public void setExperience(int experience) { this.experience = experience; }

    public void saveNBT(CompoundTag tag) {
        tag.putInt("CompanionLevel", this.level);
        tag.putInt("CompanionXP", this.experience);
    }

    public void loadNBT(CompoundTag tag) {
        if (tag.contains("CompanionLevel")) this.level = tag.getInt("CompanionLevel");
        if (tag.contains("CompanionXP")) this.experience = tag.getInt("CompanionXP");
    }
}