package com.sgt_shadow3600.engineer.entity.ai.utils;

import com.sgt_shadow3600.engineer.block.TerminalBlockEntity;
import com.sgt_shadow3600.engineer.entity.EngineerCompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;

import java.util.Map;

public class LogisticsUtils {

    public static boolean needsToDump(EngineerCompanionEntity npc) {
        int emptySlots = 0;
        for (int i = 10; i <= 26; i++) {
            if (npc.inventory.getStackInSlot(i).isEmpty()) emptySlots++;
        }
        return emptySlots <= 1; // Trigger dump if 1 or 0 slots left
    }

    public static boolean needsHealing(EngineerCompanionEntity npc) {
        return npc.getHealth() < npc.getMaxHealth() * 0.4f;
    }

    public static boolean terminalHasBlocks(EngineerCompanionEntity npc) {
        if (npc.getLinkedTerminal() == null) return false;
        if (npc.level() instanceof ServerLevel sl) {
            ServerLevel terminalLevel = sl.getServer().getLevel(npc.getLinkedTerminal().dimension());
            if (terminalLevel != null) {
                if (!terminalLevel.isLoaded(npc.getLinkedTerminal().pos())) {
                    return true;
                }
                if (terminalLevel.getBlockEntity(npc.getLinkedTerminal().pos()) instanceof TerminalBlockEntity terminal) {

                    boolean isBuilding = (npc.getCurrentTask() == EngineerCompanionEntity.CompanionTask.BUILD_HUT || npc.getCurrentTask() == EngineerCompanionEntity.CompanionTask.SYSTEM_HALT);
                    if (isBuilding && npc.jobController.blueprintSession != null) {

                        Map<Item, Integer> neededMaterials = npc.jobController.blueprintSession.getNextRequiredMaterials(64);
                        if (!neededMaterials.isEmpty()) {
                            for (Item neededItem : neededMaterials.keySet()) {
                                if (neededMaterials.get(neededItem) > 0) {
                                    for (int i = 0; i <= 53; i++) {
                                        ItemStack stack = terminal.inventory.getStackInSlot(i);
                                        if (!stack.isEmpty() && stack.getItem() == neededItem) return true;
                                    }
                                }
                            }
                            return false;
                        }
                    }

                    Item targetBlock = npc.getDimensionBlockItem();
                    for (int i = 0; i <= 53; i++) {
                        ItemStack stack = terminal.inventory.getStackInSlot(i);
                        if (!stack.isEmpty() && stack.getItem() == targetBlock) return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean hasEquipmentUpgradeAvailable(EngineerCompanionEntity npc, ServerLevel terminalLevel, BlockPos terminalPos) {
        boolean isFar = npc.distanceToSqr(terminalPos.getX() + 0.5, terminalPos.getY() + 0.5, terminalPos.getZ() + 0.5) > 256.0;

        boolean hasCriticalNeed = false;
        for (int i = 0; i <= 7; i++) {
            if (npc.inventory.getStackInSlot(i).isEmpty() || npc.inventory.isMissingOrBroken(i)) {
                hasCriticalNeed = true;
                break;
            }
        }

        if (isFar && !hasCriticalNeed) return false;
        if (!terminalLevel.isLoaded(terminalPos)) return hasCriticalNeed;

        if (!(terminalLevel.getBlockEntity(terminalPos) instanceof TerminalBlockEntity terminal)) return false;

        for (int i = 0; i <= 7; i++) {
            ItemStack npcStack = npc.inventory.getStackInSlot(i);
            boolean slotEmpty = npcStack.isEmpty();
            boolean brokenValuable = npc.inventory.isMissingOrBroken(i);
            float npcScore = getEquipmentScore(npcStack);

            for (int t = 0; t <= 53; t++) {
                ItemStack termStack = terminal.inventory.getStackInSlot(t);
                if (termStack.isEmpty()) continue;

                if (isItemValidForSlot(termStack, i)) {
                    if (slotEmpty || brokenValuable) return true;
                    if (!isFar && getEquipmentScore(termStack) > npcScore) return true;
                }
            }
        }
        return false;
    }

    public static void executeLogistics(EngineerCompanionEntity npc, ServerLevel terminalLevel, BlockPos terminalPos) {
        if (!(terminalLevel.getBlockEntity(terminalPos) instanceof TerminalBlockEntity terminal)) return;

        boolean isBuilding = (npc.getCurrentTask() == EngineerCompanionEntity.CompanionTask.BUILD_HUT || npc.getCurrentTask() == EngineerCompanionEntity.CompanionTask.SYSTEM_HALT) && npc.jobController.blueprintSession != null;
        Map<Item, Integer> immediateMaterials = isBuilding ? npc.jobController.blueprintSession.getNextRequiredMaterials(64) : new java.util.HashMap<>();

        // 1. DEPOSIT GATHERED MATERIALS
        for (int i = 10; i <= 26; i++) {
            ItemStack stack = npc.inventory.getStackInSlot(i);

            if (!stack.isEmpty()) {
                // Keep the stack if it's explicitly needed in the upcoming build queue
                if (isBuilding && immediateMaterials.getOrDefault(stack.getItem(), 0) > 0) continue;

                ItemStack remainder = insertIntoTerminal(terminal, stack, 0, 53);
                npc.inventory.setStackInSlot(i, remainder);

                if (!remainder.isEmpty() && needsToDump(npc)) {
                    npc.spawnAtLocation(remainder.copy());
                    npc.inventory.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
        }

        // 2. HEALTH RESTORATION
        if (needsHealing(npc)) npc.heal(2.0f);

        // 3. SYNCHRONIZE LOADOUT
        for (int i = 0; i <= 8; i++) {
            syncEquipmentSlot(npc, terminal, i);
        }

        // 4. RESTOCK BUILDING BLOCKS
        if (isBuilding) {
            for (Item neededItem : immediateMaterials.keySet()) {
                if (immediateMaterials.get(neededItem) > 0) {
                    for (int slot = 9; slot <= 26; slot++) {
                        restockSpecificItem(npc, terminal, slot, neededItem, 0, 53);
                    }
                }
            }

            if (!npc.jobController.jobQueue.isEmpty()) {
                Item blockingItem = npc.jobController.blueprintSession.getQueue().peek().state().getBlock().asItem();
                if (blockingItem == Items.AIR || hasItemInInventory(npc, blockingItem)) {
                    npc.setNeedsBuildingBlocks(false);
                }
            } else {
                npc.setNeedsBuildingBlocks(false);
            }

            if (npc.getCurrentTask() == EngineerCompanionEntity.CompanionTask.SYSTEM_HALT && !npc.isNeedsBuildingBlocks()) {
                npc.setCurrentTask(EngineerCompanionEntity.CompanionTask.BUILD_HUT);
            }

        } else {
            Item targetBlock = npc.getDimensionBlockItem();
            restockItem(npc, terminal, 9, targetBlock, 0, 53);
            if (!npc.inventory.getStackInSlot(9).isEmpty()) {
                npc.setNeedsBuildingBlocks(false);
            }
        }
    }

    private static boolean hasItemInInventory(EngineerCompanionEntity npc, Item targetItem) {
        for (int i = 8; i <= 26; i++) {
            ItemStack stack = npc.inventory.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == targetItem) return true;
        }
        return false;
    }

    private static void syncEquipmentSlot(EngineerCompanionEntity npc, TerminalBlockEntity terminal, int slot) {
        ItemStack npcStack = npc.inventory.getStackInSlot(slot);

        if (slot == 8) {
            int needed = npcStack.isEmpty() ? 64 : 64 - npcStack.getCount();
            if (needed > 0) {
                for (int t = 0; t <= 53; t++) {
                    ItemStack termStack = terminal.inventory.getStackInSlot(t);
                    if (!termStack.isEmpty() && (termStack.getItem() == Items.TORCH || termStack.getItem() == Items.LANTERN || termStack.getItem() instanceof net.minecraft.world.item.ShieldItem)) {
                        if (npcStack.isEmpty() || ItemStack.isSameItemSameComponents(npcStack, termStack)) {
                            ItemStack extracted = terminal.inventory.extractItem(t, needed, false);
                            if (npcStack.isEmpty()) {
                                npc.inventory.setStackInSlot(slot, extracted);
                                npcStack = npc.inventory.getStackInSlot(slot);
                            } else {
                                npcStack.grow(extracted.getCount());
                            }
                            needed -= extracted.getCount();
                            if (needed <= 0) break;
                        }
                    }
                }
            }
        } else {
            float bestScore = getEquipmentScore(npcStack);
            int bestTermSlot = -1;
            boolean needsReplacement = npcStack.isEmpty() || npc.inventory.isMissingOrBroken(slot);

            for (int t = 0; t <= 53; t++) {
                ItemStack termStack = terminal.inventory.getStackInSlot(t);
                if (!termStack.isEmpty() && isItemValidForSlot(termStack, slot)) {
                    float score = getEquipmentScore(termStack);
                    if (score > bestScore || (needsReplacement && bestTermSlot == -1)) {
                        bestScore = score;
                        bestTermSlot = t;
                    }
                }
            }

            if (bestTermSlot != -1) {
                ItemStack bestItem = terminal.inventory.getStackInSlot(bestTermSlot);
                terminal.inventory.setStackInSlot(bestTermSlot, npcStack);
                npc.inventory.setStackInSlot(slot, bestItem);
            }
        }
    }

    private static boolean isItemValidForSlot(ItemStack stack, int slot) {
        if (stack.isEmpty()) return false;
        return switch (slot) {
            case 0 -> stack.getItem() instanceof net.minecraft.world.item.PickaxeItem;
            case 1 -> stack.getItem() instanceof net.minecraft.world.item.ShovelItem;
            case 2 -> stack.getItem() instanceof net.minecraft.world.item.AxeItem;
            case 3 -> stack.getItem() instanceof net.minecraft.world.item.SwordItem;
            case 4 -> stack.getItem() instanceof net.minecraft.world.item.ArmorItem armor && armor.getType() == net.minecraft.world.item.ArmorItem.Type.HELMET;
            case 5 -> stack.getItem() instanceof net.minecraft.world.item.ArmorItem armor && armor.getType() == net.minecraft.world.item.ArmorItem.Type.CHESTPLATE;
            case 6 -> stack.getItem() instanceof net.minecraft.world.item.ArmorItem armor && armor.getType() == net.minecraft.world.item.ArmorItem.Type.LEGGINGS;
            case 7 -> stack.getItem() instanceof net.minecraft.world.item.ArmorItem armor && armor.getType() == net.minecraft.world.item.ArmorItem.Type.BOOTS;
            default -> false;
        };
    }

    private static float getEquipmentScore(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (stack.getItem() instanceof TieredItem tiered) {
            float score = tiered.getTier().getSpeed();
            if (stack.getItem() instanceof SwordItem) score = tiered.getTier().getAttackDamageBonus();
            if (stack.isDamageableItem()) score *= 1.0f - ((float)stack.getDamageValue() / stack.getMaxDamage());
            return score;
        } else if (stack.getItem() instanceof ArmorItem armor) {
            float score = armor.getDefense();
            if (stack.isDamageableItem()) score *= 1.0f - ((float)stack.getDamageValue() / stack.getMaxDamage());
            return score;
        }
        return 0;
    }

    private static void restockItem(EngineerCompanionEntity npc, TerminalBlockEntity terminal, int slot, Item exactItem, int termStart, int termEnd) {
        restockSpecificItem(npc, terminal, slot, exactItem, termStart, termEnd);
    }

    private static boolean restockSpecificItem(EngineerCompanionEntity npc, TerminalBlockEntity terminal, int slot, Item exactItem, int termStart, int termEnd) {
        ItemStack current = npc.inventory.getStackInSlot(slot);

        if (!current.isEmpty() && current.getItem() != exactItem) return false;

        int needed = current.isEmpty() ? exactItem.getDefaultMaxStackSize() : exactItem.getDefaultMaxStackSize() - current.getCount();
        boolean fetched = false;

        if (needed > 0) {
            for (int i = termStart; i <= termEnd; i++) {
                ItemStack termStack = terminal.inventory.getStackInSlot(i);
                if (!termStack.isEmpty() && termStack.getItem() == exactItem) {
                    ItemStack extracted = terminal.inventory.extractItem(i, needed, false);
                    if (current.isEmpty()) {
                        npc.inventory.setStackInSlot(slot, extracted);
                        current = npc.inventory.getStackInSlot(slot);
                    } else {
                        current.grow(extracted.getCount());
                    }
                    needed -= extracted.getCount();
                    fetched = true;
                    if (needed <= 0) break;
                }
            }
        }
        return fetched;
    }

    private static ItemStack insertIntoTerminal(TerminalBlockEntity terminal, ItemStack stack, int startSlot, int endSlot) {
        for (int i = startSlot; i <= endSlot; i++) {
            stack = terminal.inventory.insertItem(i, stack, false);
            if (stack.isEmpty()) break;
        }
        return stack;
    }
}