package com.sgt_shadow3600.engineer.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sgt_shadow3600.engineer.EngineerCompanion;
import com.sgt_shadow3600.engineer.ModDataComponents;
import com.sgt_shadow3600.engineer.entity.EngineerCompanionEntity;
import com.sgt_shadow3600.engineer.network.NpcActionPayload;
import com.sgt_shadow3600.engineer.network.RadarPingPayload;
import com.sgt_shadow3600.engineer.network.RadarPongPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class CommandSlateScreen extends Screen {

    private static final ResourceLocation BG = ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "textures/menu/blank_background.png");
    private static final ResourceLocation LIST_BG = ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "textures/menu/inworld_menu_list_background.png");

    private final ItemStack slate;
    private List<GlobalPos> terminals;
    private int selectedTerminalIndex;

    public static RadarPongPayload currentRadarData = null;
    private int pingTimer = 0;

    private int activeSubTab = 0;
    private String pendingConfirmAction = "";
    private int confirmTimeout = 0;

    private final int chassisW = 248;
    private final int chassisH = 166;
    private final int topTabW = 28;
    private final int topTabH = 32;

    private int leftPos;
    private int topPos;
    private BlockPos activePos;

    private Button executeBtn;
    private Button clearQueueBtn;
    private Button purgeBtn;
    private Button pauseResumeBtn;

    private Button patrolBtn;
    private Button followBtn;
    private Button combatStyleBtn;

    private Button returnHomeBtn;
    private Button locateBtn;
    private Button abortBtn;

    public CommandSlateScreen(ItemStack slate) {
        super(Component.literal("Command Deck Uplink"));
        this.slate = slate;
        currentRadarData = null;
        this.selectedTerminalIndex = slate.getOrDefault(ModDataComponents.ACTIVE_TERMINAL.get(), 0);
    }

    private int getCompanionLevel() {
        if (currentRadarData != null && currentRadarData.isLoaded()) {
            return Math.max(1, currentRadarData.level());
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

        if (terminals.isEmpty()) return;

        this.activePos = terminals.get(selectedTerminalIndex).pos();

        int btnWidth = 100;
        int btnHeight = 20;
        int spacing = 22;
        int contentX = leftPos + 15;
        int startY = topPos + 24;

        int bottomY = topPos + 138;

        returnHomeBtn = this.addRenderableWidget(Button.builder(Component.literal("Return Home"), btn -> {
            if (isDoingLogistics() && !pendingConfirmAction.equals("RECALL")) {
                pendingConfirmAction = "RECALL";
                confirmTimeout = 60;
                btn.setMessage(Component.literal("Confirm?"));
                return;
            }
            PacketDistributor.sendToServer(new NpcActionPayload(activePos, "RECALL"));
            pendingConfirmAction = "";
            btn.setMessage(Component.literal("Return Home"));
        }).bounds(contentX, bottomY, btnWidth, btnHeight).build());

        locateBtn = this.addRenderableWidget(Button.builder(Component.literal("Locate Unit"), btn -> {
            PacketDistributor.sendToServer(new NpcActionPayload(activePos, "LOCATE"));
        }).bounds(contentX, bottomY - 22, btnWidth, btnHeight).build());

        abortBtn = this.addRenderableWidget(Button.builder(Component.literal("§cAbort Active Job§r"), btn -> {
            PacketDistributor.sendToServer(new NpcActionPayload(activePos, "CANCEL_ACTIVE"));
        }).bounds(contentX, bottomY - 44, btnWidth, btnHeight).build());
        abortBtn.visible = false;

        executeBtn = this.addRenderableWidget(Button.builder(Component.literal("Execute Queue"), btn -> {
            PacketDistributor.sendToServer(new NpcActionPayload(activePos, "START_QUEUE"));
            this.onClose();
        }).bounds(contentX, startY, btnWidth, btnHeight).build());

        clearQueueBtn = this.addRenderableWidget(Button.builder(Component.literal("Clear Queue"), btn -> {
            PacketDistributor.sendToServer(new NpcActionPayload(activePos, "CLEAR_QUEUE"));
            this.onClose();
        }).bounds(contentX, startY + spacing, btnWidth, btnHeight).build());

        purgeBtn = this.addRenderableWidget(Button.builder(Component.literal("Purge Slate Config"), btn -> {
            PacketDistributor.sendToServer(new NpcActionPayload(BlockPos.ZERO, "PURGE"));
            this.onClose();
        }).bounds(contentX, startY + (spacing * 2), btnWidth, btnHeight).build());

        pauseResumeBtn = this.addRenderableWidget(Button.builder(Component.literal("Pause Job"), btn -> {
            if (currentRadarData != null) {
                int task = currentRadarData.task();
                if (task == EngineerCompanionEntity.CompanionTask.SYSTEM_HALT.ordinal()) {
                    PacketDistributor.sendToServer(new NpcActionPayload(activePos, "RESUME"));
                } else {
                    PacketDistributor.sendToServer(new NpcActionPayload(activePos, "PAUSE"));
                }
            }
        }).bounds(contentX, startY + (spacing * 3), btnWidth, btnHeight).build());
        pauseResumeBtn.active = false;

        patrolBtn = this.addRenderableWidget(Button.builder(Component.literal("Patrol Area"), btn -> {
            if (isDoingLogistics() && !pendingConfirmAction.equals("PATROL")) {
                pendingConfirmAction = "PATROL";
                confirmTimeout = 60;
                btn.setMessage(Component.literal("Confirm?"));
                return;
            }
            PacketDistributor.sendToServer(new NpcActionPayload(activePos, "PATROL"));
            pendingConfirmAction = "";
            btn.setMessage(Component.literal("Patrol Area"));
        }).bounds(contentX, startY, btnWidth, btnHeight).build());

        followBtn = this.addRenderableWidget(Button.builder(Component.literal("Follow"), btn -> {
            if (isDoingLogistics() && !pendingConfirmAction.equals("FOLLOW")) {
                pendingConfirmAction = "FOLLOW";
                confirmTimeout = 60;
                btn.setMessage(Component.literal("Confirm?"));
                return;
            }
            PacketDistributor.sendToServer(new NpcActionPayload(activePos, "FOLLOW"));
            pendingConfirmAction = "";
            btn.setMessage(Component.literal("Follow"));
        }).bounds(contentX, startY + spacing, btnWidth, btnHeight).build());

        combatStyleBtn = this.addRenderableWidget(Button.builder(Component.literal("Defensive"), btn -> {
            PacketDistributor.sendToServer(new NpcActionPayload(activePos, "TOGGLE_COMBAT"));
        }).bounds(contentX, startY + (spacing * 2), btnWidth, btnHeight).build());

        updateSubTabVisibility();
    }

    private boolean isDoingLogistics() {
        if (currentRadarData == null || !currentRadarData.isLoaded()) return false;
        int t = currentRadarData.task();
        return t == EngineerCompanionEntity.CompanionTask.DIGGING.ordinal() || t == EngineerCompanionEntity.CompanionTask.BUILD_HUT.ordinal() || t == EngineerCompanionEntity.CompanionTask.SYSTEM_HALT.ordinal();
    }

    @Override
    public void tick() {
        super.tick();

        if (confirmTimeout > 0) {
            confirmTimeout--;
            if (confirmTimeout == 0) {
                pendingConfirmAction = "";
                if (patrolBtn != null) patrolBtn.setMessage(Component.literal("Patrol Area"));
                if (followBtn != null) followBtn.setMessage(Component.literal("Follow"));
                if (returnHomeBtn != null) returnHomeBtn.setMessage(Component.literal("Return Home"));
            }
        }

        int currentLvl = getCompanionLevel();

        boolean isReconstructing = false;
        if (currentRadarData != null) {
            int t = currentRadarData.task();
            if (t == 996 || t == 998 || (t == 999 && currentRadarData.health() < currentRadarData.maxHealth())) {
                isReconstructing = true;
            }
        }

        if (patrolBtn != null) patrolBtn.active = (currentLvl >= 2) && !isReconstructing;
        if (followBtn != null) followBtn.active = !isReconstructing;

        if (returnHomeBtn != null) {
            boolean canRecall = (currentLvl >= 1);
            if (currentRadarData != null && (currentRadarData.task() >= 997 || isReconstructing)) {
                canRecall = false;
            }
            returnHomeBtn.active = canRecall;
        }

        if (executeBtn != null && clearQueueBtn != null) {
            boolean hasQueueItems = false;
            if (currentRadarData != null && currentRadarData.isLoaded()) {
                CompoundTag qData = currentRadarData.queueData();
                if (qData != null && qData.contains("list")) {
                    ListTag list = qData.getList("list", 10);
                    hasQueueItems = !list.isEmpty();
                }
            }
            executeBtn.active = hasQueueItems && !isReconstructing;
            clearQueueBtn.active = hasQueueItems;
        }

        if (purgeBtn != null) {
            boolean hasConfig = slate.has(ModDataComponents.PENDING_DIG_CONFIG.get()) || slate.has(ModDataComponents.PENDING_BUILD_CONFIG.get());
            purgeBtn.active = hasConfig;
        }

        if (abortBtn != null) {
            if (activeSubTab == 0 && currentRadarData != null && currentRadarData.isLoaded()) {
                abortBtn.visible = isDoingLogistics();
            } else {
                abortBtn.visible = false;
            }
        }
        if (locateBtn != null) locateBtn.visible = (activeSubTab == 0);

        if (pauseResumeBtn != null) {
            if (currentRadarData != null && currentRadarData.isLoaded()) {
                int task = currentRadarData.task();
                if (task == EngineerCompanionEntity.CompanionTask.SYSTEM_HALT.ordinal()) {
                    pauseResumeBtn.setMessage(Component.literal("Resume Job"));
                    pauseResumeBtn.active = true;
                } else if (task == EngineerCompanionEntity.CompanionTask.DIGGING.ordinal() || task == EngineerCompanionEntity.CompanionTask.BUILD_HUT.ordinal()) {
                    pauseResumeBtn.setMessage(Component.literal("Pause Job"));
                    pauseResumeBtn.active = true;
                } else {
                    pauseResumeBtn.setMessage(Component.literal("Pause Job"));
                    pauseResumeBtn.active = false;
                }
            } else {
                pauseResumeBtn.active = false;
            }
        }

        if (combatStyleBtn != null) {
            if (currentRadarData != null && currentRadarData.isLoaded()) {
                combatStyleBtn.setMessage(Component.literal(currentRadarData.defensive() ? "Defensive" : "Offensive"));
            } else {
                combatStyleBtn.setMessage(Component.literal("Defensive"));
            }
        }
    }

    private void updateSubTabVisibility() {
        boolean isJob = (activeSubTab == 1);
        boolean isCombat = (activeSubTab == 2);

        if (executeBtn != null) executeBtn.visible = isJob;
        if (clearQueueBtn != null) clearQueueBtn.visible = isJob;
        if (purgeBtn != null) purgeBtn.visible = isJob;
        if (pauseResumeBtn != null) pauseResumeBtn.visible = isJob;

        if (patrolBtn != null) patrolBtn.visible = isCombat;
        if (followBtn != null) followBtn.visible = isCombat;
        if (combatStyleBtn != null) combatStyleBtn.visible = isCombat;
    }

    private ResourceLocation getTopTab(int index, boolean isSelected) {
        String state = isSelected ? "selected" : "unselected";
        return ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "textures/menu/tab_top_" + state + "_" + (index + 1) + ".png");
    }

    private ResourceLocation getLeftTabTexture(int index, boolean isSelected) {
        String state = isSelected ? "_selected" : "";
        if (index == 0) return ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "textures/menu/tab_left_top" + state + ".png");
        if (index == 2) return ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "textures/menu/tab_left_bottom" + state + ".png");
        return ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "textures/menu/tab_left_middle" + state + ".png");
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        if (activeSubTab == 0 && currentRadarData != null) {
            CompoundTag qData = currentRadarData.queueData();
            if (qData != null && qData.contains("list")) {
                ListTag list = qData.getList("list", 10);
                int qSize = list.size();
                int startY = topPos + 15;
                int startX = leftPos + 15;

                int renderedIndex = 0;
                for(int i = 0; i < Math.min(qSize, 4); i++) {
                    CompoundTag jt = list.getCompound(i);
                    if (!jt.getBoolean("active")) {
                        int itemY = startY + 12 + (renderedIndex * 15);
                        if (mouseY >= itemY && mouseY < itemY + 12) {
                            int realIdx = i - (list.getCompound(0).getBoolean("active") ? 1 : 0);
                            if (mouseX >= startX + 70 && mouseX < startX + 82) {
                                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                                PacketDistributor.sendToServer(new NpcActionPayload(activePos, "Q_UP_" + realIdx)); return true;
                            }
                            if (mouseX >= startX + 84 && mouseX < startX + 96) {
                                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                                PacketDistributor.sendToServer(new NpcActionPayload(activePos, "Q_DN_" + realIdx)); return true;
                            }
                            if (mouseX >= startX + 98 && mouseX < startX + 110) {
                                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                                PacketDistributor.sendToServer(new NpcActionPayload(activePos, "Q_RM_" + realIdx)); return true;
                            }
                        }
                    }
                    renderedIndex++;
                }
            }
        }

        int currentLvl = getCompanionLevel();
        for (int i = 0; i < 3; i++) {
            if (i == 2 && currentLvl < 4) continue;

            int tx = leftPos + 10 + (i * 29);
            int ty = topPos - 28;
            if (mouseX >= tx && mouseX <= tx + topTabW && mouseY >= ty && mouseY <= ty + topTabH) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                if (i == 1) { this.minecraft.setScreen(new DigConfigScreen(null, slate, this)); return true; }
                else if (i == 2) { this.minecraft.setScreen(new BuildConfigScreen(slate, null, this)); return true; }
            }
        }

        for (int i = 0; i < 3; i++) {
            int tx = leftPos - 28;
            int ty = topPos + 10 + (i * 29);
            if (mouseX >= tx && mouseX <= tx + 28 && mouseY >= ty && mouseY <= ty + 32) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                if (activeSubTab != i) {
                    activeSubTab = i;
                    updateSubTabVisibility();
                }
                return true;
            }
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

        for (int i = 1; i < 3; i++) {
            if (i == 2 && currentLvl < 4) continue;
            guiGraphics.blit(getTopTab(i, false), leftPos + 10 + (i * 29), topPos - 28 + 4, 0, 0, topTabW, topTabH, topTabW, topTabH);
        }

        for (int i = 0; i < 3; i++) {
            if (i != activeSubTab) guiGraphics.blit(getLeftTabTexture(i, false), leftPos - 28 + 4, topPos + 10 + (i * 29), 0, 0, 28, 32, 28, 32);
        }

        guiGraphics.blit(BG, leftPos, topPos, 0, 0, chassisW, chassisH, 256, 256);

        guiGraphics.blit(getTopTab(0, true), leftPos + 10, topPos - 28, 0, 0, topTabW, topTabH, topTabW, topTabH);
        guiGraphics.blit(getLeftTabTexture(activeSubTab, true), leftPos - 28, topPos + 10 + (activeSubTab * 29), 0, 0, 28, 32, 28, 32);

        if (!terminals.isEmpty()) {
            drawSunkenPanel(guiGraphics, leftPos + 130, topPos + 12, 106, 142);
        }
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawSunkenPanel(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        for (int cx = 0; cx < width; cx += 16) {
            for (int cy = 0; cy < height; cy += 16) {
                guiGraphics.blit(LIST_BG, x + cx, y + cy, 0, 0, Math.min(16, width - cx), Math.min(16, height - cy), 16, 16);
            }
        }
        guiGraphics.fill(x - 1, y - 1, x + width + 1, y, 0xFF111111);
        guiGraphics.fill(x - 1, y - 1, x, y + height + 1, 0xFF111111);
        guiGraphics.fill(x - 1, y + height, x + width + 1, y + height + 1, 0x55FFFFFF);
        guiGraphics.fill(x + width, y - 1, x + width + 1, y + height + 1, 0x55FFFFFF);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (terminals.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, Component.literal("NO TERMINALS FOUND."), leftPos + (chassisW / 2), topPos + 60, 0xFF5555);
            return;
        }

        int currentLvl = getCompanionLevel();

        guiGraphics.renderItem(new ItemStack(Items.BOOK), leftPos + 10 + 6, topPos - 20);
        guiGraphics.renderItem(new ItemStack(Items.IRON_PICKAXE), leftPos + 39 + 6, topPos - 20 + 4);

        if (currentLvl >= 4) {
            guiGraphics.renderItem(new ItemStack(Items.BRICKS), leftPos + 68 + 6, topPos - 20 + 4);
        }

        guiGraphics.renderItem(new ItemStack(Items.SPYGLASS), leftPos - 20, topPos + 18);
        guiGraphics.renderItem(new ItemStack(Items.WRITABLE_BOOK), leftPos - 20, topPos + 47);
        guiGraphics.renderItem(new ItemStack(Items.IRON_SWORD), leftPos - 20, topPos + 76);

        renderTelemetry(guiGraphics, leftPos + 134, topPos + 20, 100);

        if (activeSubTab == 0) renderStatusTab(guiGraphics, mouseX, mouseY);
        if (activeSubTab == 1) renderJobTab(guiGraphics);
        if (activeSubTab == 2) renderCombatTab(guiGraphics);

        renderTooltips(guiGraphics, mouseX, mouseY);

        pingTimer++;
        if (pingTimer > 20 && selectedTerminalIndex < terminals.size()) {
            PacketDistributor.sendToServer(new RadarPingPayload(terminals.get(selectedTerminalIndex).pos()));
            pingTimer = 0;
        }
    }

    private void renderStatusTab(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startX = leftPos + 15;
        int startY = topPos + 15;
        int vanillaColor = 4210752;

        if (currentRadarData != null && currentRadarData.isLoaded()) {

            CompoundTag qData = currentRadarData.queueData();
            if (qData != null && qData.contains("list")) {
                ListTag list = qData.getList("list", 10);
                int qSize = list.size();
                guiGraphics.drawString(this.font, "Job Queue (" + qSize + ")", startX, startY, vanillaColor, false);

                int renderedIndex = 0;
                for(int i = 0; i < Math.min(qSize, 4); i++) {
                    CompoundTag jt = list.getCompound(i);
                    String desc = jt.getString("desc");
                    if (desc.isEmpty()) desc = "Unknown Job";

                    int itemY = startY + 12 + (renderedIndex * 15);
                    if (jt.getBoolean("active")) {
                        guiGraphics.drawString(this.font, desc, startX, itemY + 2, 0x00AA00, false);
                    } else {
                        guiGraphics.drawString(this.font, desc, startX, itemY + 2, 0x444444, false);
                        drawSmallVanillaButton(guiGraphics, startX + 70, itemY, 12, 12, "↑", mouseX, mouseY);
                        drawSmallVanillaButton(guiGraphics, startX + 84, itemY, 12, 12, "↓", mouseX, mouseY);
                        drawSmallVanillaButton(guiGraphics, startX + 98, itemY, 12, 12, "X", mouseX, mouseY);
                    }
                    renderedIndex++;
                }
            } else {
                guiGraphics.drawString(this.font, "Job Queue (0)", startX, startY, vanillaColor, false);
            }

            if (currentRadarData.missingSupplies() != null && !currentRadarData.missingSupplies().isEmpty() && currentRadarData.task() != 997) {
                guiGraphics.drawString(this.font, "WARNING: Needs Items", startX, topPos + 75, 0xFF5555, false);
                String[] needs = currentRadarData.missingSupplies().split(", ");
                guiGraphics.pose().pushPose();
                guiGraphics.pose().scale(0.8f, 0.8f, 0.8f);
                for (int i = 0; i < Math.min(needs.length, 2); i++) {
                    guiGraphics.drawString(this.font, "- " + needs[i], (int)(startX / 0.8f), (int)((topPos + 85 + (i*10)) / 0.8f), 0xFF5555, false);
                }
                guiGraphics.pose().popPose();
            }

        } else {
            guiGraphics.drawString(this.font, "Job Queue (0)", startX, startY, vanillaColor, false);
        }
    }

    private void drawSmallVanillaButton(GuiGraphics guiGraphics, int x, int y, int width, int height, String text, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        int bgColor = hovered ? 0xFF999999 : 0xFFC6C6C6;
        int borderColorBlack = 0xFF000000;
        int borderColorWhite = 0xFFFFFFFF;
        int borderColorDarkGray = 0xFF555555;

        guiGraphics.fill(x, y, x + width, y + height, borderColorBlack);
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, bgColor);
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + 2, borderColorWhite);
        guiGraphics.fill(x + 1, y + 1, x + 2, y + height - 1, borderColorWhite);
        guiGraphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, borderColorDarkGray);
        guiGraphics.fill(x + width - 2, y + 1, x + width - 1, y + height - 1, borderColorDarkGray);

        int textWidth = this.font.width(text);
        guiGraphics.drawString(this.font, text, x + (width / 2) - (textWidth / 2) + 1, y + (height / 2) - 4, 0xFFFFFF, false);
    }

    private void renderJobTab(GuiGraphics guiGraphics) {
        guiGraphics.drawString(this.font, "Logistics & Labor", leftPos + 15, topPos + 12, 4210752, false);
    }

    private void renderCombatTab(GuiGraphics guiGraphics) {
        guiGraphics.drawString(this.font, "Tactical Directives", leftPos + 15, topPos + 12, 4210752, false);
    }

    private void renderTelemetry(GuiGraphics guiGraphics, int teleX, int teleY, int panelWidth) {
        int rightX = teleX - 2;
        int ink = 0xAAAAAA;
        int redInk = 0xFFFF55;
        int greenInk = 0x55FF55;

        if (currentRadarData != null) {

            if (currentRadarData.task() == 997) {
                guiGraphics.drawCenteredString(this.font, "UNIT DESTROYED", teleX + (panelWidth / 2) - 4, teleY, 0xFF5555);
                guiGraphics.drawString(this.font, "Logic Core at:", rightX, teleY + 15, 0xAAAAAA, false);
                guiGraphics.drawString(this.font, currentRadarData.missingSupplies(), rightX, teleY + 25, 0xFFFF55, false);

                guiGraphics.pose().pushPose();
                guiGraphics.pose().scale(0.8f, 0.8f, 0.8f);
                guiGraphics.drawString(this.font, "Terminal requires a", (int)(rightX / 0.8f), (int)((teleY + 45) / 0.8f), 0xFF5555, false);
                guiGraphics.drawString(this.font, "Logic Core to", (int)(rightX / 0.8f), (int)((teleY + 55) / 0.8f), 0xFF5555, false);
                guiGraphics.drawString(this.font, "Rebuild Engineer.", (int)(rightX / 0.8f), (int)((teleY + 65) / 0.8f), 0xFF5555, false);
                guiGraphics.pose().popPose();

            } else if (currentRadarData.task() == 999 || currentRadarData.task() == 998 || currentRadarData.task() == 996 || currentRadarData.task() == 1000 || currentRadarData.isLoaded()) {

                guiGraphics.drawCenteredString(this.font, "Level " + Math.max(1, currentRadarData.level()), teleX + (panelWidth / 2) - 4, teleY, greenInk);

                int maxXp = (int) (50 * Math.pow(Math.max(1, currentRadarData.level()), 1.5));
                float progress = Math.min(1.0f, Math.max(0.0f, (float) currentRadarData.xp() / Math.max(1, maxXp)));

                int barWidth = 60;
                int barHeight = 3;
                int barX = teleX + (panelWidth / 2) - (barWidth / 2) - 4;
                int barY = teleY + 11;

                guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF555555);
                guiGraphics.fill(barX, barY, barX + (int)(barWidth * progress), barY + barHeight, 0xFF55FF55);

                renderHearts(guiGraphics, currentRadarData.health(), currentRadarData.maxHealth(), currentRadarData.armor(), rightX, teleY + 17);

                int toolY = teleY + 38;
                for (int i = 0; i < 4; i++) {
                    int slotX = rightX + (i * 20);
                    guiGraphics.fill(slotX, toolY, slotX + 18, toolY + 18, 0xFF373737);
                    guiGraphics.fill(slotX + 1, toolY + 1, slotX + 17, toolY + 17, 0xFF8B8B8B);

                    if (currentRadarData.tools() != null && currentRadarData.tools().contains("tool_" + i) && this.minecraft.level != null) {
                        try {
                            ItemStack stack = ItemStack.parseOptional(this.minecraft.level.registryAccess(), currentRadarData.tools().getCompound("tool_" + i));
                            guiGraphics.renderItem(stack, slotX + 1, toolY + 1);
                            guiGraphics.renderItemDecorations(this.font, stack, slotX + 1, toolY + 1);
                        } catch (Exception ignored) {}
                    }
                }

                int armorY = toolY + 20;
                for (int i = 4; i < 8; i++) {
                    int slotX = rightX + ((i - 4) * 20);
                    guiGraphics.fill(slotX, armorY, slotX + 18, armorY + 18, 0xFF373737);
                    guiGraphics.fill(slotX + 1, armorY + 1, slotX + 17, armorY + 17, 0xFF8B8B8B);

                    if (currentRadarData.tools() != null && currentRadarData.tools().contains("tool_" + i) && this.minecraft.level != null) {
                        try {
                            ItemStack stack = ItemStack.parseOptional(this.minecraft.level.registryAccess(), currentRadarData.tools().getCompound("tool_" + i));
                            guiGraphics.renderItem(stack, slotX + 1, armorY + 1);
                            guiGraphics.renderItemDecorations(this.font, stack, slotX + 1, armorY + 1);
                        } catch (Exception ignored) {}
                    }
                }

                String combatMode = currentRadarData.defensive() ? "Defensive" : "Offensive";
                guiGraphics.drawString(this.font, "Combat: " + combatMode, rightX, armorY + 22, ink, false);

                String taskName;
                int t = currentRadarData.task();

                // ARCHITECT FIX: Safely map internal states directly to strings without touching Enums
                if (t == 996) {
                    taskName = "Building Unit";
                    guiGraphics.drawString(this.font, "Task: " + taskName, rightX, armorY + 32, redInk, false);
                } else if (t == 998 || (t == 999 && currentRadarData.health() < currentRadarData.maxHealth())) {
                    taskName = "Reconstructing";
                    guiGraphics.drawString(this.font, "Task: " + taskName, rightX, armorY + 32, redInk, false);
                } else if (t == 1000) {
                    taskName = "Rebooting";
                    guiGraphics.drawString(this.font, "Task: " + taskName, rightX, armorY + 32, redInk, false);
                } else if (t == 999) {
                    taskName = "Sleeping";
                    guiGraphics.drawString(this.font, "Task: " + taskName, rightX, armorY + 32, ink, false);
                } else {
                    taskName = "Unknown";
                    if (t < EngineerCompanionEntity.CompanionTask.values().length) {
                        EngineerCompanionEntity.CompanionTask currentTaskEnum = EngineerCompanionEntity.CompanionTask.values()[t];
                        if (currentTaskEnum == EngineerCompanionEntity.CompanionTask.SYSTEM_HALT) {
                            taskName = "STUCK/PAUSED";
                        } else if (currentTaskEnum == EngineerCompanionEntity.CompanionTask.GUARD) {
                            taskName = "PATROLLING";
                        } else {
                            taskName = currentTaskEnum.name();
                        }
                    }
                    guiGraphics.drawString(this.font, "Task: " + taskName, rightX, armorY + 32, ink, false);
                }

                int currentLvl = Math.max(1, currentRadarData.level());
                String nextAbility = "";
                int nextLvl = -1;

                if (currentLvl < 2) { nextAbility = "Patrol Mode"; nextLvl = 2; }
                else if (currentLvl < 4) { nextAbility = "Simple Structs"; nextLvl = 4; }
                else if (currentLvl < 5) { nextAbility = "Vein Mining"; nextLvl = 5; }
                else if (currentLvl < 10) { nextAbility = "Windcharge"; nextLvl = 10; }
                else if (currentLvl < 15) { nextAbility = "Adv. Blueprints"; nextLvl = 15; }
                else if (currentLvl < 20) { nextAbility = "Void Healing"; nextLvl = 20; }
                else if (currentLvl < 25) { nextAbility = "Sonic Blast"; nextLvl = 25; }
                else if (currentLvl < 30) { nextAbility = "Engineer's Mind"; nextLvl = 30; }

                int unlockY = armorY + 45;
                if (nextLvl != -1) {
                    guiGraphics.drawString(this.font, "Next Unlock:", rightX, unlockY, 0xAAAAAA, false);
                    guiGraphics.drawString(this.font, "[Lv." + nextLvl + "] " + nextAbility, rightX, unlockY + 10, 0xFFAA00, false);
                } else {
                    guiGraphics.drawString(this.font, "Abilities: MAXED", rightX, unlockY + 5, 0x55FF55, false);
                }

            } else {
                guiGraphics.drawString(this.font, "Link: LOST", rightX, teleY + 15, redInk, false);
                guiGraphics.drawString(this.font, "(Out of Range)", rightX, teleY + 30, ink, false);
            }
        } else {
            guiGraphics.drawString(this.font, "PINGING...", rightX, teleY + 15, ink, false);
        }
    }

    private void renderHearts(GuiGraphics guiGraphics, int hp, int maxHp, int armor, int x, int y) {
        // Prevent div-by-zero if maxHp somehow drops to 0 during deserialization
        if (maxHp <= 0) maxHp = 30;

        int filledHearts = (int) Math.ceil(((float) hp / maxHp) * 10.0f);
        int filledArmor = (int) Math.ceil(armor / 2.0f);

        for (int h = 0; h < 10; h++) {
            ResourceLocation hSprite = (h < filledHearts) ? ResourceLocation.withDefaultNamespace("hud/heart/full") : ResourceLocation.withDefaultNamespace("hud/heart/container");
            ResourceLocation aSprite = (h < filledArmor) ? ResourceLocation.withDefaultNamespace("hud/armor_full") : ResourceLocation.withDefaultNamespace("hud/armor_empty");

            guiGraphics.blitSprite(hSprite, x + (h * 8), y, 9, 9);
            guiGraphics.blitSprite(aSprite, x + (h * 8), y + 10, 9, 9);
        }
    }

    private void renderTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int currentLvl = getCompanionLevel();
        String[] topNames = {"Home / Operations", "Dig Configuration", "Build Configuration"};
        for(int i = 0; i < 3; i++) {
            if (i == 2 && currentLvl < 4) continue;

            int tx = leftPos + 10 + (i * 29);
            int ty = topPos - 28;
            if (mouseX >= tx && mouseX <= tx + topTabW && mouseY >= ty && mouseY <= ty + topTabH) {
                guiGraphics.renderTooltip(this.font, Component.literal(topNames[i]), mouseX, mouseY);
            }
        }

        String[] leftNames = {"Status Readout", "Job Directives", "Combat Directives"};
        for(int i = 0; i < 3; i++) {
            int tx = leftPos - 28;
            int ty = topPos + 10 + (i * 29);
            if (mouseX >= tx && mouseX <= tx + 28 && mouseY >= ty && mouseY <= ty + 32) {
                guiGraphics.renderTooltip(this.font, Component.literal(leftNames[i]), mouseX, mouseY);
            }
        }

        if (activeSubTab == 0 && currentRadarData != null) {
            int startX = leftPos + 15;
            if (currentRadarData.task() == EngineerCompanionEntity.CompanionTask.DIGGING.ordinal() || currentRadarData.task() == EngineerCompanionEntity.CompanionTask.BUILD_HUT.ordinal()) {
                if (mouseX >= startX + 90 && mouseX <= startX + 110 && mouseY >= topPos + 35 && mouseY <= topPos + 45) {
                    guiGraphics.renderTooltip(this.font, Component.literal("Abort Active Job"), mouseX, mouseY);
                }
            }
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}