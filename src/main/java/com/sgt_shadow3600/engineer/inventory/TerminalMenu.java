package com.sgt_shadow3600.engineer.inventory;

import com.sgt_shadow3600.engineer.block.TerminalBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

public class TerminalMenu extends AbstractContainerMenu {

    public final TerminalBlockEntity blockEntity;

    public TerminalMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, (TerminalBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()));
    }

    public TerminalMenu(int containerId, Inventory playerInv, TerminalBlockEntity entity) {
        super(ModMenus.TERMINAL_MENU.get(), containerId);
        this.blockEntity = entity;

        // Terminal Inventory (Rows 0 to 5 = Storage)
        for (int row = 0; row < 6; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new SlotItemHandler(entity.inventory, col + row * 9, 8 + col * 18, 18 + row * 18));
            }
        }

        // Player Backpack
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }

        // Player Hotbar
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 198));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack slotItem = slot.getItem();
            itemstack = slotItem.copy();

            // Move from Terminal to Player
            if (index < 54) {
                if (!this.moveItemStackTo(slotItem, 54, this.slots.size(), true)) return ItemStack.EMPTY;
            }
            // Move from Player to Terminal
            else {
                if (!this.moveItemStackTo(slotItem, 0, 54, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (slotItem.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return itemstack;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}