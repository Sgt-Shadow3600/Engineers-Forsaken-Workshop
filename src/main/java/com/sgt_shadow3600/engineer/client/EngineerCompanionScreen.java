package com.sgt_shadow3600.engineer.client;

import com.sgt_shadow3600.engineer.inventory.EngineerCompanionMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class EngineerCompanionScreen extends AbstractContainerScreen<EngineerCompanionMenu> {

    // Uses the vanilla 3-row generic chest background
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    public EngineerCompanionScreen(EngineerCompanionMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        // ARCHITECT FIX: super.render() automatically handles tooltips in 1.21.1.
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // Draws exactly 3 rows worth of the chest UI (27 slots)
        guiGraphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, 3 * 18 + 17);

        // Draws the player inventory section directly below it
        guiGraphics.blit(TEXTURE, x, y + 3 * 18 + 17, 0, 126, this.imageWidth, 96);
    }
}