package com.sgt_shadow3600.engineer.entity.managers;

import com.sgt_shadow3600.engineer.Config;
import com.sgt_shadow3600.engineer.entity.EngineerCompanionEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class EngineerInventoryManager extends ItemStackHandler {

    private final EngineerCompanionEntity companion;

    public EngineerInventoryManager(EngineerCompanionEntity companion) {
        super(27);
        this.companion = companion;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (companion.isRogue()) return true;

        return switch (slot) {
            case 0 -> stack.getItem() instanceof PickaxeItem;
            case 1 -> stack.getItem() instanceof ShovelItem;
            case 2 -> stack.getItem() instanceof AxeItem;
            case 3 -> stack.getItem() instanceof SwordItem;
            case 4 -> stack.getItem() instanceof ArmorItem armor && armor.getType() == ArmorItem.Type.HELMET;
            case 5 -> stack.getItem() instanceof ArmorItem armor && armor.getType() == ArmorItem.Type.CHESTPLATE;
            case 6 -> stack.getItem() instanceof ArmorItem armor && armor.getType() == ArmorItem.Type.LEGGINGS;
            case 7 -> stack.getItem() instanceof ArmorItem armor && armor.getType() == ArmorItem.Type.BOOTS;
            case 8 -> stack.getItem() == Items.TORCH || stack.getItem() == Items.LANTERN || stack.getItem() instanceof ShieldItem;
            default -> true;
        };
    }

    @Override
    protected void onContentsChanged(int slot) {
        super.onContentsChanged(slot);
        switch (slot) {
            case 4 -> companion.setItemSlot(EquipmentSlot.HEAD, getStackInSlot(4));
            case 5 -> companion.setItemSlot(EquipmentSlot.CHEST, getStackInSlot(5));
            case 6 -> companion.setItemSlot(EquipmentSlot.LEGS, getStackInSlot(6));
            case 7 -> companion.setItemSlot(EquipmentSlot.FEET, getStackInSlot(7));
            case 8 -> companion.setItemSlot(EquipmentSlot.OFFHAND, getStackInSlot(8));
        }
    }

    public void tickVacuum(Level level) {
        if (companion.isRogue() || level.isClientSide) return;

        AABB pickupBox = companion.getBoundingBox().inflate(2.0D);

        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, pickupBox);
        for (ItemEntity itemEntity : items) {
            if (!itemEntity.isRemoved() && !itemEntity.getItem().isEmpty()) {
                ItemStack remainder = ItemHandlerHelper.insertItemStacked(this, itemEntity.getItem(), false);
                if (remainder.isEmpty()) {
                    itemEntity.discard();
                    companion.onItemPickup(itemEntity);
                } else {
                    itemEntity.setItem(remainder);
                }
            }
        }

        List<ExperienceOrb> orbs = level.getEntitiesOfClass(ExperienceOrb.class, pickupBox);
        for (ExperienceOrb orb : orbs) {
            if (!orb.isRemoved()) {
                companion.experienceManager.addExperience(orb.getValue());
                orb.discard();
                companion.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 0.1F, (level.random.nextFloat() - level.random.nextFloat()) * 0.35F + 0.9F);
            }
        }
    }

    public void syncInventoryDisplayData() {
        int blocks = 0; int torches = 0;
        Item dimBlock = companion.getDimensionBlockItem();
        for (int i=8; i<=26; i++) {
            ItemStack st = this.getStackInSlot(i);
            if (!st.isEmpty()) {
                if (i >= 9 && st.getItem() == dimBlock) blocks += st.getCount();
                if (st.getItem() == Items.TORCH) torches += st.getCount();
            }
        }
        companion.setSyncedBlocks(blocks);
        companion.setSyncedTorches(torches);
        companion.setMissingSupplies(calculateMissingSupplies());
    }

    private String calculateMissingSupplies() {
        EngineerCompanionEntity.CompanionTask task = companion.getCurrentTask();
        if (task != EngineerCompanionEntity.CompanionTask.DIGGING && task != EngineerCompanionEntity.CompanionTask.SYSTEM_HALT && task != EngineerCompanionEntity.CompanionTask.GUARD && task != EngineerCompanionEntity.CompanionTask.BUILD_HUT) return "";

        StringBuilder reason = new StringBuilder();
        if (task == EngineerCompanionEntity.CompanionTask.DIGGING || task == EngineerCompanionEntity.CompanionTask.SYSTEM_HALT || task == EngineerCompanionEntity.CompanionTask.BUILD_HUT) {
            if (companion.isNeedsBuildingBlocks()) reason.append("Blocks, ");
            if (task == EngineerCompanionEntity.CompanionTask.DIGGING && this.getStackInSlot(0).isEmpty()) reason.append("Pickaxe, ");
            if (task == EngineerCompanionEntity.CompanionTask.DIGGING && this.getStackInSlot(8).isEmpty()) reason.append("Torches, ");
        }
        return reason.length() > 0 ? reason.substring(0, reason.length() - 2) : "";
    }

    public boolean isMissingOrBroken(int slot) {
        ItemStack tool = this.getStackInSlot(slot);
        if (tool.isEmpty()) return true;
        if (!tool.isDamageableItem()) return false;
        boolean isValuable = tool.isEnchanted() || (tool.getItem() instanceof TieredItem ti && (ti.getTier() == net.minecraft.world.item.Tiers.DIAMOND || ti.getTier() == net.minecraft.world.item.Tiers.NETHERITE));
        return isValuable && (tool.getMaxDamage() - tool.getDamageValue() <= 1);
    }

    public void equipRogueGear(ServerLevelAccessor level) {
        List<EquipmentSlot> armorSlots = Arrays.asList(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
        Collections.shuffle(armorSlots);

        Map<EquipmentSlot, Item> armorChoices = new HashMap<>();
        armorChoices.put(armorSlots.get(0), getArmorForSlot(armorSlots.get(0), "netherite"));
        armorChoices.put(armorSlots.get(1), getArmorForSlot(armorSlots.get(1), "diamond"));
        armorChoices.put(armorSlots.get(2), getArmorForSlot(armorSlots.get(2), "golden"));
        armorChoices.put(armorSlots.get(3), getArmorForSlot(armorSlots.get(3), "golden"));

        float dropChance = Config.ROGUE_LOOT_DROP_CHANCE.get().floatValue();

        for (Map.Entry<EquipmentSlot, Item> entry : armorChoices.entrySet()) {
            ItemStack armorStack = new ItemStack(entry.getValue());
            EnchantmentHelper.enchantItem(level.getRandom(), armorStack, 30, companion.registryAccess(), Optional.empty());
            companion.setItemSlot(entry.getKey(), armorStack);
            companion.setDropChance(entry.getKey(), dropChance);
        }

        Item[] weapons = {Items.NETHERITE_SWORD, Items.DIAMOND_AXE, Items.MACE, Items.TRIDENT};
        ItemStack weaponStack = new ItemStack(weapons[level.getRandom().nextInt(weapons.length)]);
        EnchantmentHelper.enchantItem(level.getRandom(), weaponStack, 30, companion.registryAccess(), Optional.empty());

        companion.setItemSlot(EquipmentSlot.MAINHAND, weaponStack);
        companion.setDropChance(EquipmentSlot.MAINHAND, dropChance);

        this.setStackInSlot(3, weaponStack);
        this.setStackInSlot(4, companion.getItemBySlot(EquipmentSlot.HEAD));
        this.setStackInSlot(5, companion.getItemBySlot(EquipmentSlot.CHEST));
        this.setStackInSlot(6, companion.getItemBySlot(EquipmentSlot.LEGS));
        this.setStackInSlot(7, companion.getItemBySlot(EquipmentSlot.FEET));
    }

    private Item getArmorForSlot(EquipmentSlot slot, String material) {
        return switch (material) {
            case "netherite" -> slot == EquipmentSlot.HEAD ? Items.NETHERITE_HELMET : slot == EquipmentSlot.CHEST ? Items.NETHERITE_CHESTPLATE : slot == EquipmentSlot.LEGS ? Items.NETHERITE_LEGGINGS : Items.NETHERITE_BOOTS;
            case "diamond" -> slot == EquipmentSlot.HEAD ? Items.DIAMOND_HELMET : slot == EquipmentSlot.CHEST ? Items.DIAMOND_CHESTPLATE : slot == EquipmentSlot.LEGS ? Items.DIAMOND_LEGGINGS : Items.DIAMOND_BOOTS;
            default -> slot == EquipmentSlot.HEAD ? Items.GOLDEN_HELMET : slot == EquipmentSlot.CHEST ? Items.GOLDEN_CHESTPLATE : slot == EquipmentSlot.LEGS ? Items.GOLDEN_LEGGINGS : Items.GOLDEN_BOOTS;
        };
    }

    public void damageArmorSlot(ServerLevel serverLevel, EquipmentSlot slot, int invSlot, int damage) {
        ItemStack equipStack = companion.getItemBySlot(slot);
        if (!equipStack.isEmpty() && equipStack.getItem() instanceof ArmorItem) {
            equipStack.hurtAndBreak(damage, serverLevel, null, (item) -> companion.onEquippedItemBroken(item, slot));
            if (!companion.isRogue()) {
                this.setStackInSlot(invSlot, equipStack.isEmpty() ? ItemStack.EMPTY : equipStack.copy());
            }
        }
    }

    public void syncEquipmentDurability(EquipmentSlot equipmentSlot, int invSlot) {
        if (companion.isRogue()) return;
        ItemStack equipStack = companion.getItemBySlot(equipmentSlot);
        ItemStack invStack = this.getStackInSlot(invSlot);

        if (equipStack.isEmpty() && !invStack.isEmpty()) {
            this.setStackInSlot(invSlot, ItemStack.EMPTY);
        } else if (!equipStack.isEmpty() && !invStack.isEmpty() && equipStack.isDamageableItem()) {
            if (equipStack.getDamageValue() != invStack.getDamageValue()) {
                invStack.setDamageValue(equipStack.getDamageValue());
            }
        }
    }

    public CompoundTag saveNBT(HolderLookup.Provider provider) { return this.serializeNBT(provider); }
    public void loadNBT(HolderLookup.Provider provider, CompoundTag tag) {
        this.deserializeNBT(provider, tag);
        if (this.getSlots() < 27) this.setSize(27);
    }
}