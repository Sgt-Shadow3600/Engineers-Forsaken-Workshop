package com.sgt_shadow3600.engineer.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sgt_shadow3600.engineer.EngineerCompanion;
import com.sgt_shadow3600.engineer.ModDataComponents;
import com.sgt_shadow3600.engineer.network.SaveDigConfigPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

public class DigConfigScreen extends Screen {

    private static final ResourceLocation BG = ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "textures/menu/blank_background.png");
    private static final ResourceLocation LIST_BG = ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "textures/menu/inworld_menu_list_background.png");

    private final UUID targetNpc;
    private final ItemStack slate;
    private final Screen parent;
    private List<GlobalPos> terminals;
    private int selectedTerminalIndex;

    private EditBox widthBox;
    private EditBox lengthBox;
    private EditBox heightBox;
    private EditBox depthBox;

    private static int lastShape = 0;
    private static int lastWidth = 3;
    private static int lastLength = 5;
    private static int lastDepth = 10;
    private static int lastHeight = 4;
    private static boolean bedrockMode = false;
    private static boolean veinmineMode = false;
    private int startYLevel = 64;

    private final int chassisW = 248;
    private final int chassisH = 166;
    private final int topTabW = 28;
    private final int topTabH = 32;
    private final int rightTabW = 32;
    private final int rightTabH = 28;

    private int leftPos;
    private int topPos;
    private int vmX, vmY;
    private int bmX, bmY;
    private boolean isVerticalDig;

    public DigConfigScreen(UUID targetNpc, ItemStack slate, Screen parent) {
        super(Component.literal("Configure Dig Operations"));
        this.targetNpc = targetNpc;
        this.slate = slate;
        this.parent = parent;
        this.selectedTerminalIndex = slate.getOrDefault(ModDataComponents.ACTIVE_TERMINAL.get(), 0);
        GlobalPos jobPos = slate.get(ModDataComponents.JOB_SITE.get());
        if (jobPos != null) {
            this.startYLevel = jobPos.pos().getY();
        }
    }

    // ARCHITECT FIX: Retrieve dynamic level from the Slate's active radar ping
    private int getCompanionLevel() {
        if (CommandSlateScreen.currentRadarData != null && CommandSlateScreen.currentRadarData.isLoaded()) {
            return Math.max(1, CommandSlateScreen.currentRadarData.level());
        }
        return 1;
    }

    @Override
    protected void init() {
        super.init();
        this.terminals = slate.getOrDefault(ModDataComponents.BOUND_TERMINALS.get(), List.of());
        if (selectedTerminalIndex >= terminals.size()) selectedTerminalIndex = 0;

        this.leftPos = (this.width - chassisW) / 2;
        this.topPos = (this.height - chassisH) / 2;

        int panelStartX = leftPos + 12;
        int panelTopY = topPos + 12;

        this.isVerticalDig = (lastShape == 0 || lastShape == 2 || lastShape == 3);
        this.vmX = panelStartX + 184;
        this.vmY = panelTopY + 5;
        this.bmX = panelStartX + 184;
        this.bmY = panelTopY + 34;

        int minBuildHeight = this.minecraft.level != null ? this.minecraft.level.getMinBuildHeight() : -64;

        if (isVerticalDig) {
            if (!bedrockMode) {
                this.addRenderableWidget(new DepthSlider(panelStartX + 12, panelTopY + 28, 168, 20, startYLevel, minBuildHeight));
            } else {
                Button b = Button.builder(Component.literal("Targeting Bedrock"), btn -> {}).bounds(panelStartX + 12, panelTopY + 28, 168, 20).build();
                b.active = false;
                this.addRenderableWidget(b);
            }
        } else {
            this.depthBox = new EditBox(this.font, panelStartX + 12, panelTopY + 28, 168, 20, Component.literal("Distance"));
            this.depthBox.setValue(String.valueOf(lastDepth == 999 ? 10 : lastDepth));
            this.addRenderableWidget(this.depthBox);
        }

        if (lastShape == 0 || lastShape == 1) {
            this.widthBox = new EditBox(this.font, panelStartX + 47, panelTopY + 72, 50, 20, Component.literal("Width"));
            this.widthBox.setValue(String.valueOf(lastWidth));
            this.addRenderableWidget(this.widthBox);

            this.heightBox = new EditBox(this.font, panelStartX + 127, panelTopY + 72, 50, 20, Component.literal("Height"));
            this.heightBox.setValue(String.valueOf(lastHeight));
            this.addRenderableWidget(this.heightBox);
            this.lengthBox = null;
        } else if (lastShape == 2) {
            this.widthBox = new EditBox(this.font, panelStartX + 47, panelTopY + 72, 50, 20, Component.literal("Width"));
            this.widthBox.setValue(String.valueOf(lastWidth));
            this.addRenderableWidget(this.widthBox);

            this.lengthBox = new EditBox(this.font, panelStartX + 127, panelTopY + 72, 50, 20, Component.literal("Length"));
            this.lengthBox.setValue(String.valueOf(lastLength));
            this.addRenderableWidget(this.lengthBox);
            this.heightBox = null;
        } else if (lastShape == 3) {
            this.lengthBox = new EditBox(this.font, panelStartX + 87, panelTopY + 72, 50, 20, Component.literal("Radius"));
            this.lengthBox.setValue(String.valueOf(lastLength));
            this.addRenderableWidget(this.lengthBox);
            this.widthBox = null;
            this.heightBox = null;
        }

        net.minecraft.core.BlockPos activePos = terminals.isEmpty() ? net.minecraft.core.BlockPos.ZERO : terminals.get(selectedTerminalIndex).pos();
        CustomData data = slate.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.EMPTY);

        boolean hasDigConfig = slate.has(ModDataComponents.PENDING_DIG_CONFIG.get());
        boolean isAnchored = hasDigConfig && data.copyTag().getBoolean("IsAnchored");

        // Force Veinmine off if level requirement isn't met, to avoid packet injection spoofing
        boolean safeVeinmine = veinmineMode && getCompanionLevel() >= 5;

        if (isAnchored) {
            this.addRenderableWidget(Button.builder(Component.literal("Add to Queue"), (btn) -> {
                saveInputs();
                PacketDistributor.sendToServer(new SaveDigConfigPayload(
                        slate.getOrDefault(ModDataComponents.JOB_DIRECTION.get(), 0),
                        lastDepth == 999 ? 999 : lastDepth, lastWidth, lastShape, lastLength, lastHeight, safeVeinmine, "QUEUE_ANCHORED", activePos));
                this.onClose();
            }).bounds(panelStartX + 12, panelTopY + 110, 204, 20).build());
        } else {
            this.addRenderableWidget(Button.builder(Component.literal("Set Location"), (btn) -> {
                saveInputs();
                PacketDistributor.sendToServer(new SaveDigConfigPayload(
                        slate.getOrDefault(ModDataComponents.JOB_DIRECTION.get(), 0),
                        lastDepth == 999 ? 999 : lastDepth, lastWidth, lastShape, lastLength, lastHeight, safeVeinmine, "SAVE_ONLY", activePos));
                this.onClose();
            }).bounds(panelStartX + 12, panelTopY + 110, 100, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("Chain Job"), (btn) -> {
                saveInputs();
                PacketDistributor.sendToServer(new SaveDigConfigPayload(
                        slate.getOrDefault(ModDataComponents.JOB_DIRECTION.get(), 0),
                        lastDepth == 999 ? 999 : lastDepth, lastWidth, lastShape, lastLength, lastHeight, safeVeinmine, "QUEUE_CHAINED", activePos));
                this.onClose();
            }).bounds(panelStartX + 116, panelTopY + 110, 100, 20).build());
        }
    }

    private void saveInputs() {
        if (this.widthBox != null) lastWidth = parseInput(this.widthBox.getValue(), 1);
        if (this.lengthBox != null) lastLength = parseInput(this.lengthBox.getValue(), 5);
        if (this.heightBox != null) lastHeight = parseInput(this.heightBox.getValue(), 4);
        if (this.depthBox != null) lastDepth = parseInput(this.depthBox.getValue(), 10);
    }

    private int parseInput(String text, int defaultValue) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Component getShapeText() {
        String[] shapes = {"Staircase (Angle Down)", "Tunnel Room (Forward)", "Square Shaft (Down)", "Quarry (Circular Shaft)"};
        return Component.literal(shapes[lastShape]);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int currentLvl = getCompanionLevel();

        for (int i = 0; i < 3; i++) {
            // ARCHITECT FIX: Only process clicks for the Build Tab if Lv >= 4
            if (i == 2 && currentLvl < 4) continue;

            int tx = leftPos + 10 + (i * 29);
            int ty = topPos - 28;
            if (mouseX >= tx && mouseX <= tx + topTabW && mouseY >= ty && mouseY <= ty + topTabH) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                if (i == 0) { this.minecraft.setScreen(new CommandSlateScreen(slate)); return true; }
                else if (i == 2) { this.minecraft.setScreen(new BuildConfigScreen(slate, targetNpc, this)); return true; }
            }
        }

        for (int i = 0; i < 4; i++) {
            int tx = leftPos + chassisW - 4;
            int ty = topPos + 10 + (i * rightTabH);
            if (mouseX >= tx && mouseX <= tx + rightTabW && mouseY >= ty && mouseY <= ty + rightTabH) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                lastShape = i;
                this.clearWidgets();
                this.init();
                return true;
            }
        }

        // ARCHITECT FIX: Block Veinmine toggle below Lv 5
        if (currentLvl >= 5 && mouseX >= vmX && mouseX <= vmX + 26 && mouseY >= vmY && mouseY <= vmY + 26) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            veinmineMode = !veinmineMode;
            return true;
        }

        if (isVerticalDig && mouseX >= bmX && mouseX <= bmX + 26 && mouseY >= bmY && mouseY <= bmY + 26) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            bedrockMode = !bedrockMode;
            this.clearWidgets();
            this.init();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft != null && this.minecraft.level != null) {
            this.renderTransparentBackground(guiGraphics);
        } else {
            super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        }

        int currentLvl = getCompanionLevel();

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();

        for (int i = 0; i < 3; i++) {
            // ARCHITECT FIX: Skip drawing the unselected Build Tab graphic entirely if locked
            if (i == 2 && currentLvl < 4) continue;

            if (i != 1) {
                guiGraphics.blit(ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "textures/menu/tab_top_unselected_" + (i + 1) + ".png"), leftPos + 10 + (i * 29), topPos - 28 + 4, 0, 0, topTabW, topTabH, topTabW, topTabH);
            }
        }

        for (int i = 0; i < 4; i++) {
            if (i != lastShape) {
                guiGraphics.blit(ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "textures/menu/tab_right_" + (i == 0 ? "top" : (i == 3 ? "bottom" : "middle")) + ".png"), leftPos + chassisW - 4, topPos + 10 + (i * rightTabH), 0, 0, rightTabW, rightTabH, rightTabW, rightTabH);
            }
        }

        guiGraphics.blit(BG, leftPos, topPos, 0, 0, chassisW, chassisH, 256, 256);

        guiGraphics.blit(ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "textures/menu/tab_top_selected_2.png"), leftPos + 10 + (1 * 29), topPos - 28, 0, 0, topTabW, topTabH, topTabW, topTabH);
        guiGraphics.blit(ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "textures/menu/tab_right_" + (lastShape == 0 ? "top" : (lastShape == 3 ? "bottom" : "middle")) + "_selected.png"), leftPos + chassisW - 1, topPos + 10 + (lastShape * rightTabH), 0, 0, rightTabW, rightTabH, rightTabW, rightTabH);

        for (int cx = 0; cx < 224; cx += 16) {
            for (int cy = 0; cy < 142; cy += 16) {
                guiGraphics.blit(LIST_BG, leftPos + 12 + cx, topPos + 12 + cy, 0, 0, Math.min(16, 224 - cx), Math.min(16, 142 - cy), 16, 16);
            }
        }

        guiGraphics.fill(leftPos + 11, topPos + 11, leftPos + 237, topPos + 12, 0xFF111111);
        guiGraphics.fill(leftPos + 11, topPos + 11, leftPos + 12, topPos + 155, 0xFF111111);
        guiGraphics.fill(leftPos + 11, topPos + 154, leftPos + 237, topPos + 155, 0x55FFFFFF);
        guiGraphics.fill(leftPos + 236, topPos + 11, leftPos + 237, topPos + 155, 0x55FFFFFF);

        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int currentLvl = getCompanionLevel();

        guiGraphics.renderItem(new ItemStack(Items.BOOK), leftPos + 10 + 6, topPos - 20 + 4);
        guiGraphics.renderItem(new ItemStack(Items.IRON_PICKAXE), leftPos + 39 + 6, topPos - 20);

        // ARCHITECT FIX: Only render Bricks icon if tab is unlocked
        if (currentLvl >= 4) {
            guiGraphics.renderItem(new ItemStack(Items.BRICKS), leftPos + 68 + 6, topPos - 20 + 4);
        }

        guiGraphics.renderItem(new ItemStack(Items.STONE_STAIRS), leftPos + chassisW + 4, topPos + 10 + 6);
        guiGraphics.renderItem(new ItemStack(Items.CHISELED_STONE_BRICKS), leftPos + chassisW + 4, topPos + 10 + rightTabH + 6);
        guiGraphics.renderItem(new ItemStack(Items.LADDER), leftPos + chassisW + 4, topPos + 10 + (rightTabH * 2) + 6);
        guiGraphics.renderItem(new ItemStack(Items.HOPPER), leftPos + chassisW + 4, topPos + 10 + (rightTabH * 3) + 6);

        int ink = 0xFFFFFF;
        int titleWidth = this.font.width(getShapeText());
        guiGraphics.drawString(this.font, getShapeText(), leftPos + (chassisW / 2) - (titleWidth / 2), topPos + 15, 0x55FF55, false);

        int panelStartX = leftPos + 12;
        int panelTopY = topPos + 12;

        if (!isVerticalDig) {
            guiGraphics.drawString(this.font, "Horizontal Distance", panelStartX + 14, panelTopY + 17, ink, false);
        }

        if (lastShape == 0 || lastShape == 1) {
            guiGraphics.drawString(this.font, "Width", panelStartX + 57, panelTopY + 61, ink, false);
            guiGraphics.drawString(this.font, "Height", panelStartX + 137, panelTopY + 61, ink, false);
        } else if (lastShape == 2) {
            guiGraphics.drawString(this.font, "Width", panelStartX + 57, panelTopY + 61, ink, false);
            guiGraphics.drawString(this.font, "Length", panelStartX + 137, panelTopY + 61, ink, false);
        } else if (lastShape == 3) {
            guiGraphics.drawString(this.font, "Radius", panelStartX + 95, panelTopY + 61, ink, false);
        }

        // ARCHITECT FIX: Only render Veinmine Toggle UI if Lv 5+
        if (currentLvl >= 5) {
            ResourceLocation vmFrame = veinmineMode ? ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "textures/menu/task_frame_obtained.png") : ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "textures/menu/task_frame_unobtained.png");
            guiGraphics.blit(vmFrame, vmX, vmY, 0, 0, 26, 26, 26, 26);
            if (veinmineMode) {
                guiGraphics.renderItem(new ItemStack(Items.DIAMOND), vmX + 5, vmY + 5);
            } else {
                RenderSystem.enableBlend();
                TextureAtlasSprite ghostDiamond = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ResourceLocation.withDefaultNamespace("item/empty_slot_diamond"));
                guiGraphics.blit(vmX + 5, vmY + 5, 0, 16, 16, ghostDiamond);
                RenderSystem.disableBlend();
            }
        }

        if (isVerticalDig) {
            ResourceLocation bmFrame = bedrockMode ? ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "textures/menu/task_frame_obtained.png") : ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "textures/menu/task_frame_unobtained.png");
            guiGraphics.blit(bmFrame, bmX, bmY, 0, 0, 26, 26, 26, 26);
            guiGraphics.renderItem(bedrockMode ? new ItemStack(Items.BEDROCK) : new ItemStack(Items.GRASS_BLOCK), bmX + 5, bmY + 5);
        }
        renderTooltips(guiGraphics, mouseX, mouseY);
    }

    private void renderTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int currentLvl = getCompanionLevel();
        String[] topNames = {"Home / Operations", "Dig Configuration", "Build Configuration"};
        for(int i = 0; i < 3; i++) {
            // ARCHITECT FIX: Hide tooltip for locked tab
            if (i == 2 && currentLvl < 4) continue;

            int tx = leftPos + 10 + (i * 29);
            int ty = topPos - 28;
            if (mouseX >= tx && mouseX <= tx + topTabW && mouseY >= ty && mouseY <= ty + topTabH) {
                guiGraphics.renderTooltip(this.font, Component.literal(topNames[i]), mouseX, mouseY);
            }
        }

        String[] shapeNames = {"Staircase", "Tunnel Room", "Square Shaft", "Quarry"};
        for (int i = 0; i < 4; i++) {
            int tx = leftPos + chassisW - 4;
            int ty = topPos + 10 + (i * rightTabH);
            if (mouseX >= tx && mouseX <= tx + rightTabW && mouseY >= ty && mouseY <= ty + rightTabH) {
                guiGraphics.renderTooltip(this.font, Component.literal("Shape: " + shapeNames[i]), mouseX, mouseY);
            }
        }

        if (currentLvl >= 5 && mouseX >= vmX && mouseX <= vmX + 26 && mouseY >= vmY && mouseY <= vmY + 26) {
            guiGraphics.renderTooltip(this.font, Component.literal(veinmineMode ? "§bVeinminer: ACTIVE§r" : "§7Veinminer: DISABLED§r"), mouseX, mouseY);
        }

        if (isVerticalDig && mouseX >= bmX && mouseX <= bmX + 26 && mouseY >= bmY && mouseY <= bmY + 26) {
            guiGraphics.renderTooltip(this.font, Component.literal(bedrockMode ? "§8Target Depth: BEDROCK§r" : "§aTarget Depth: CUSTOM§r"), mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private class DepthSlider extends AbstractSliderButton {
        private final int startY;
        private final int minBuildHeight;

        public DepthSlider(int x, int y, int w, int h, int startY, int minBuildHeight) {
            super(x, y, w, h, Component.empty(), 0.0);
            this.startY = startY;
            this.minBuildHeight = minBuildHeight;

            int maxDepth = Math.max(1, startY - minBuildHeight);
            if (lastDepth == 999) lastDepth = 10;
            this.value = (double)lastDepth / maxDepth;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            int maxDepth = startY - minBuildHeight;
            int currentD = Math.max(1, (int)(this.value * maxDepth));
            this.setMessage(Component.literal("Depth: " + currentD + " Blocks (Stop Y: " + (startY - currentD) + ")"));
        }

        @Override
        protected void applyValue() {
            int maxDepth = startY - minBuildHeight;
            lastDepth = Math.max(1, (int)(this.value * maxDepth));
        }
    }
}