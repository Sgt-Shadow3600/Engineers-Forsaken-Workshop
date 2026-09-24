package com.sgt_shadow3600.engineer.entity.ai.utils;

import com.sgt_shadow3600.engineer.entity.EngineerCompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.tags.FluidTags;

public class DiggingUtils {

    public static int equipBestTool(EngineerCompanionEntity npc, BlockState targetState, int currentActiveSlot) {
        float bestScore = -1.0F;
        int newBestSlot = -1;

        for (int i = 0; i <= 3; i++) {
            ItemStack stack = npc.inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                float currentSpeed = stack.getDestroySpeed(targetState);

                // 1.21 COMPLIANT: The drop verification is now strictly handled by the item's Tool component.
                boolean isCorrect = stack.isCorrectToolForDrops(targetState);

                // Heavily weight tools that actually drop the block, rather than just breaking it fast.
                float score = currentSpeed * (isCorrect ? 1000.0F : 1.0F);

                if (score > bestScore) {
                    bestScore = score;
                    newBestSlot = i;
                }
            }
        }

        if (newBestSlot != -1) {
            ItemStack bestTool = npc.inventory.getStackInSlot(newBestSlot);
            if (currentActiveSlot != newBestSlot || npc.getMainHandItem().getItem() != bestTool.getItem()) {
                npc.setItemSlot(EquipmentSlot.MAINHAND, bestTool.copy());
            }
            return newBestSlot;
        } else {
            if (!npc.getMainHandItem().isEmpty()) {
                npc.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            }
            return -1;
        }
    }

    public static void damageActiveTool(EngineerCompanionEntity npc, int slot) {
        if (slot != -1) {
            ItemStack invTool = npc.inventory.getStackInSlot(slot);
            // ARCHITECT FIX: 1.21.1 Requires ServerLevel and a functional callback for Item breaking
            if (!invTool.isEmpty() && invTool.isDamageableItem() && npc.level() instanceof ServerLevel serverLevel) {
                invTool.hurtAndBreak(1, serverLevel, null, (item) -> npc.onEquippedItemBroken(item, EquipmentSlot.MAINHAND));
                npc.inventory.setStackInSlot(slot, invTool);

                if (!npc.getMainHandItem().isEmpty()) {
                    npc.getMainHandItem().setDamageValue(invTool.getDamageValue());
                }
            }
        }
    }

    public static int calculateDigDelay(EngineerCompanionEntity npc, BlockState targetState, BlockPos pos) {
        float hardness = targetState.getDestroySpeed(npc.level(), pos);
        if (hardness < 0.0F) return 999;

        ItemStack tool = npc.getMainHandItem();
        float speedMultiplier = tool.isEmpty() ? 1.0F : tool.getDestroySpeed(targetState);

        // 1.21 COMPLIANT: An empty stack naturally evaluates to true if the block requires no tool.
        boolean isCorrectTool = tool.isCorrectToolForDrops(targetState);

        if (speedMultiplier > 1.0F && !tool.isEmpty()) {
            var enchantRegistry = npc.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
            var efficiencyOpt = enchantRegistry.get(net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY);

            if (efficiencyOpt.isPresent()) {
                int effLevel = tool.getEnchantmentLevel(efficiencyOpt.get());
                if (effLevel > 0 && isCorrectTool) {
                    speedMultiplier += (effLevel * effLevel + 1);
                }
            }
        }

        // Vanilla Engine Parity: Status Effects
        if (npc.hasEffect(MobEffects.DIG_SPEED)) {
            speedMultiplier *= 1.0F + (npc.getEffect(MobEffects.DIG_SPEED).getAmplifier() + 1) * 0.2F;
        }

        if (npc.hasEffect(MobEffects.DIG_SLOWDOWN)) {
            float fatigue = switch (npc.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier()) {
                case 0 -> 0.3F;
                case 1 -> 0.09F;
                case 2 -> 0.0027F;
                default -> 8.1E-4F;
            };
            speedMultiplier *= fatigue;
        }

        // Vanilla Engine Parity: Environment
        if (npc.isEyeInFluid(FluidTags.WATER)) {
            speedMultiplier /= 5.0F;
            // Note: Aqua Affinity is omitted here to prevent 1.21 attribute registry crashes on the NPC.
        }

        if (!npc.onGround()) {
            speedMultiplier /= 5.0F;
        }

        // Apply the core Minecraft breaking divisor
        float damagePerTick = speedMultiplier / hardness / (isCorrectTool ? 30.0F : 100.0F);
        if (damagePerTick <= 0) return 999;

        return Math.max(1, (int) Math.ceil(1.0F / damagePerTick));
    }

    public static boolean isInventoryFull(EngineerCompanionEntity npc) {
        int emptyCount = 0;
        for (int i = 13; i <= 26; i++) {
            if (npc.inventory.getStackInSlot(i).isEmpty()) emptyCount++;
        }
        return emptyCount == 0;
    }

    public static boolean hasBlocks(EngineerCompanionEntity npc) {
        Item targetBlock = npc.getDimensionBlockItem();
        for (int i = 9; i <= 26; i++) {
            ItemStack stack = npc.inventory.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == targetBlock) return true;
        }
        return false;
    }

    public static boolean hasLightSources(EngineerCompanionEntity npc) {
        for (int i = 8; i <= 26; i++) {
            if (!npc.inventory.getStackInSlot(i).isEmpty() && npc.inventory.getStackInSlot(i).getItem() == Items.TORCH) return true;
        }
        return false;
    }

    public static void consumeTorch(EngineerCompanionEntity npc) {
        for (int i = 8; i <= 26; i++) {
            ItemStack stack = npc.inventory.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == Items.TORCH) {
                stack.shrink(1);
                return;
            }
        }
    }

    public static boolean placeBridgeBlock(EngineerCompanionEntity npc, Level level, BlockPos pos) {
        ItemStack bridgeStack = npc.inventory.getStackInSlot(9);
        Item targetItem = npc.getDimensionBlockItem();
        Block targetBlock = npc.getDimensionBlock();

        if (bridgeStack.isEmpty() || bridgeStack.getItem() != targetItem) {
            for (int i = 10; i <= 26; i++) {
                ItemStack backup = npc.inventory.getStackInSlot(i);
                if (!backup.isEmpty() && backup.getItem() == targetItem) {
                    ItemStack currentSlot9 = npc.inventory.getStackInSlot(9).copy();
                    npc.inventory.setStackInSlot(9, backup.copy());
                    npc.inventory.setStackInSlot(i, currentSlot9);
                    bridgeStack = npc.inventory.getStackInSlot(9);
                    break;
                }
            }
        }

        if (bridgeStack.getItem() == targetItem) {
            level.setBlockAndUpdate(pos, targetBlock.defaultBlockState());
            npc.swing(InteractionHand.MAIN_HAND, true);
            bridgeStack.shrink(1);
            return true;
        }
        return false;
    }

    public static boolean isOre(BlockState state) {
        if (state.is(net.neoforged.neoforge.common.Tags.Blocks.ORES)) return true;

        if (state.is(net.minecraft.tags.BlockTags.COAL_ORES)) return true;
        if (state.is(net.minecraft.tags.BlockTags.IRON_ORES)) return true;
        if (state.is(net.minecraft.tags.BlockTags.GOLD_ORES)) return true;
        if (state.is(net.minecraft.tags.BlockTags.REDSTONE_ORES)) return true;
        if (state.is(net.minecraft.tags.BlockTags.DIAMOND_ORES)) return true;
        if (state.is(net.minecraft.tags.BlockTags.EMERALD_ORES)) return true;
        if (state.is(net.minecraft.tags.BlockTags.LAPIS_ORES)) return true;
        if (state.is(net.minecraft.tags.BlockTags.COPPER_ORES)) return true;

        return false;
    }
}