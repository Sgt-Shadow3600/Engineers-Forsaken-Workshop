package com.sgt_shadow3600.engineer.inventory;

import com.mojang.datafixers.util.Pair;
import com.sgt_shadow3600.engineer.entity.EngineerCompanionEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class EngineerCompanionMenu extends AbstractContainerMenu {
    public final EngineerCompanionEntity npc;

    public EngineerCompanionMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, getEntity(playerInventory, extraData.readInt()));
    }

    private static EngineerCompanionEntity getEntity(Inventory playerInv, int id) {
        Entity entity = playerInv.player.level().getEntity(id);
        return entity instanceof EngineerCompanionEntity ? (EngineerCompanionEntity) entity : null;
    }

    public EngineerCompanionMenu(int containerId, Inventory playerInventory, EngineerCompanionEntity npc) {
        super(ModMenus.COMPANION_MENU.get(), containerId);
        this.npc = npc;

        IItemHandler npcInventory = npc != null ? npc.inventory : new ItemStackHandler(27);

        // Row 1: Indices 0-8
        for (int col = 0; col < 9; col++) {
            final int slotIndex = col;
            this.addSlot(new SlotItemHandler(npcInventory, slotIndex, 8 + col * 18, 18) {
                @Override
                public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                    if (slotIndex == 0) return Pair.of(InventoryMenu.BLOCK_ATLAS, ResourceLocation.withDefaultNamespace("item/empty_slot_pickaxe"));
                    if (slotIndex == 1) return Pair.of(InventoryMenu.BLOCK_ATLAS, ResourceLocation.withDefaultNamespace("item/empty_slot_shovel"));
                    if (slotIndex == 2) return Pair.of(InventoryMenu.BLOCK_ATLAS, ResourceLocation.withDefaultNamespace("item/empty_slot_axe"));
                    if (slotIndex == 3) return Pair.of(InventoryMenu.BLOCK_ATLAS, ResourceLocation.withDefaultNamespace("item/empty_slot_sword"));
                    if (slotIndex == 4) return Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_HELMET);
                    if (slotIndex == 5) return Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE);
                    if (slotIndex == 6) return Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS);
                    if (slotIndex == 7) return Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS);
                    if (slotIndex == 8) return Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD);
                    return super.getNoItemIcon();
                }
            });
        }

        // Row 2: Indices 9-17
        for (int col = 0; col < 9; col++) {
            this.addSlot(new SlotItemHandler(npcInventory, 9 + col, 8 + col * 18, 36));
        }

        // Row 3: Indices 18-26
        for (int col = 0; col < 9; col++) {
            this.addSlot(new SlotItemHandler(npcInventory, 18 + col, 8 + col * 18, 54));
        }

        // Player Inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // Player Hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return npc != null && npc.isAlive() && npc.distanceTo(player) < 8.0f;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            itemstack = stackInSlot.copy();

            if (index < 27) {
                if (!this.moveItemStackTo(stackInSlot, 27, this.slots.size(), true)) return ItemStack.EMPTY;
            } else {
                if (!this.moveItemStackTo(stackInSlot, 0, 27, false)) return ItemStack.EMPTY;
            }

            if (stackInSlot.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return itemstack;
    }
}