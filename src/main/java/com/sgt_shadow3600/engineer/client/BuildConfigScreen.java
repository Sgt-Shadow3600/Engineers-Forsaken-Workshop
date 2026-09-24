package com.sgt_shadow3600.engineer.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sgt_shadow3600.engineer.EngineerCompanion;
import com.sgt_shadow3600.engineer.ModDataComponents;
import com.sgt_shadow3600.engineer.network.SaveBuildConfigPayload;
import com.sgt_shadow3600.engineer.network.SchematicListRequestPayload;
import com.sgt_shadow3600.engineer.network.SchematicRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BuildConfigScreen extends Screen {

    public static BuildConfigScreen instance;

    private static final ResourceLocation BG = ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "textures/menu/blank_background.png");
    private static final ResourceLocation LIST_BG = ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "textures/menu/inworld_menu_list_background.png");
    private static final ResourceLocation INV_BG = ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "textures/menu/player_inventory_build_screen.png");

    private final UUID targetNpc;
    private final ItemStack slate;
    private final Screen parent;

    private List<GlobalPos> terminals;
    private int selectedTerminalIndex;
    private BlockPos activePos;

    private static int buildMode = 0;
    private static int lastShape = 0;
    private static int fillMode = 0;
    private static boolean prepMode = false;
    private static int bWidth = 5;
    private static int bLength = 5;
    private static int bHeight = 5;

    private static int offsetX = 0;
    private static int offsetY = 0;
    private static int offsetZ = 0;

    private static ItemStack selectedMaterial = new ItemStack(Items.COBBLESTONE);
    private ItemStack carriedItem = ItemStack.EMPTY;

    private static String selectedSchematicName = "";
    private List<String> serverSchematics = new ArrayList<>();
    private Map<String, Integer> currentMaterials = new HashMap<>();
    private String materialStatus = "Awaiting Data...";

    private EditBox widthBox;
    private EditBox lengthBox;
    private EditBox heightBox;

    private EditBox advSearchBox;
    private Button advPrevBtn;
    private Button advNextBtn;
    private int advPage = 0;
    private final int advItemsPerPage = 5;
    private final List<Button> schematicButtons = new ArrayList<>();
    private final String[] currentDisplayedSchematics = new String[5];

    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;

    private final int chassisW = 248;
    private final int chassisH = 166;
    private final int topTabW = 28;
    private final int topTabH = 32;
    private final int rightTabW = 32;
    private final int rightTabH = 28;

    private int leftPos;
    private int topPos;

    private int prepX, prepY;
    private int matX, matY;

    private final String[] fillNames = {"Fill: Solid", "Fill: Hollow", "Fill: Frame"};

    public BuildConfigScreen(ItemStack slate, UUID targetNpc, Screen parent) {
        super(Component.literal("Configure Build Operations"));
        this.slate = slate;
        this.targetNpc = targetNpc;
        this.parent = parent;
        this.selectedTerminalIndex = slate.getOrDefault(ModDataComponents.ACTIVE_TERMINAL.get(), 0);
        instance = this;
    }

    @Override
    public void removed() {
        super.removed();
        if (instance == this) instance = null;
    }

    // ARCHITECT FIX: Retrieve dynamic level from the Slate's active radar ping
    private int getCompanionLevel() {
        if (CommandSlateScreen.currentRadarData != null && CommandSlateScreen.currentRadarData.isLoaded()) {
            return Math.max(1, CommandSlateScreen.currentRadarData.level());
        }
        return 1;
    }

    public void receiveSchematicList(List<String> schematics) {
        this.serverSchematics = new ArrayList<>(schematics);
        if (!serverSchematics.isEmpty() && !serverSchematics.contains(selectedSchematicName)) {
            selectedSchematicName = serverSchematics.get(0);
            this.currentMaterials.clear();
            PacketDistributor.sendToServer(new SchematicRequestPayload(selectedSchematicName));
        }
        this.clearWidgets();
        this.init();
    }

    public void receiveSchematicData(boolean success, Map<String, Integer> materials) {
        if (success) {
            this.currentMaterials = materials;
            this.materialStatus = "";
        } else {
            this.currentMaterials.clear();
            this.materialStatus = "Failed to load blueprint data.";
        }
    }

    private String cleanSchematicName(String raw) {
        String name = raw;
        if (name.contains(":")) name = name.substring(name.indexOf(":") + 1);
        if (name.contains("/")) name = name.substring(name.lastIndexOf("/") + 1);

        String[] words = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    @Override
    protected void init() {
        super.init();
        this.terminals = slate.getOrDefault(ModDataComponents.BOUND_TERMINALS.get(), List.of());
        if (selectedTerminalIndex >= terminals.size()) selectedTerminalIndex = 0;

        this.leftPos = (this.width - chassisW) / 2;
        this.topPos = (this.height - chassisH) / 2;
        this.activePos = terminals.isEmpty() ? BlockPos.ZERO : terminals.get(selectedTerminalIndex).pos();

        this.prepX = leftPos + chassisW - 32;
        this.prepY = topPos + 8;
        this.matX = leftPos + chassisW - 32;
        this.matY = topPos + 36;

        widthBox = null; lengthBox = null; heightBox = null;
        xBox = null; yBox = null; zBox = null;

        // ARCHITECT FIX: Safety fallback. If standard UI variables force state 1 (Advanced Mode)
        // but the Engineer does not meet the Lv 15 requirement, revert to Simple Mode.
        if (buildMode == 1 && getCompanionLevel() < 15) {
            buildMode = 0;
        }

        if (buildMode == 0) {
            initSimpleMode();
        } else if (buildMode == 1) {
            initAdvancedMode();
        } else {
            initFineTuneMode();
        }
    }

    private int getCalculatedDirection() {
        CustomData data = slate.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();

        if (tag.getBoolean("IsAnchored")) {
            Direction anchorDir = Direction.from3DDataValue(tag.getInt("AnchorDir"));
            Direction rightDir = Direction.from3DDataValue(tag.getInt("AnchorRightDir"));
            int rots = tag.getInt("StructureRotation");

            if (anchorDir.getAxis() != Direction.Axis.Y) {
                for(int i = 0; i < rots; i++) anchorDir = anchorDir.getClockWise();
                return anchorDir.get3DDataValue();
            } else {
                for(int i = 0; i < rots; i++) rightDir = rightDir.getClockWise();
                return rightDir.getCounterClockWise().get3DDataValue();
            }
        }
        return this.minecraft.player != null ? this.minecraft.player.getDirection().get2DDataValue() : 0;
    }

    private SaveBuildConfigPayload createPayload(String actionType) {
        int currentDirection = getCalculatedDirection();
        String blockId = (buildMode == 0) ? BuiltInRegistries.ITEM.getKey(selectedMaterial.getItem()).toString() : "";
        boolean isAdv = (buildMode == 1);
        String schematic = (buildMode == 1) ? selectedSchematicName : "";

        int sShape = (buildMode == 0) ? (10 + lastShape) : 99;

        Map<String, String> subs = new HashMap<>();
        if (!isAdv && !blockId.isEmpty()) {
            subs.put("minecraft:cobblestone", blockId);
        }

        return new SaveBuildConfigPayload(
                currentDirection, sShape, bWidth, bLength, bHeight, fillMode,
                offsetX, offsetY, offsetZ, blockId, isAdv, schematic, prepMode, subs, actionType, activePos
        );
    }

    private void initSimpleMode() {
        int invX = leftPos + 36;
        int invY = topPos;

        if (lastShape == 0) {
            this.widthBox = new EditBox(this.font, invX + 10, invY + 22, 45, 16, Component.literal("Width"));
            this.lengthBox = new EditBox(this.font, invX + 65, invY + 22, 45, 16, Component.literal("Length"));
            this.heightBox = new EditBox(this.font, invX + 120, invY + 22, 45, 16, Component.literal("Height"));
            this.addRenderableWidget(this.widthBox);
            this.addRenderableWidget(this.lengthBox);
            this.addRenderableWidget(this.heightBox);
            this.widthBox.setValue(String.valueOf(bWidth));
            this.lengthBox.setValue(String.valueOf(bLength));
            this.heightBox.setValue(String.valueOf(bHeight));
        } else if (lastShape == 1 || lastShape == 2 || lastShape == 4) {
            this.widthBox = new EditBox(this.font, invX + 35, invY + 22, 45, 16, Component.literal("Width"));
            this.heightBox = new EditBox(this.font, invX + 90, invY + 22, 45, 16, Component.literal("Height"));
            this.addRenderableWidget(this.widthBox);
            this.addRenderableWidget(this.heightBox);
            this.widthBox.setValue(String.valueOf(bWidth));
            this.heightBox.setValue(String.valueOf(bHeight));
        } else if (lastShape == 3) {
            this.widthBox = new EditBox(this.font, invX + 65, invY + 22, 45, 16, Component.literal("Radius"));
            this.addRenderableWidget(this.widthBox);
            this.widthBox.setValue(String.valueOf(bWidth));
        }

        this.addRenderableWidget(Button.builder(Component.literal(fillNames[fillMode]), btn -> {
            fillMode = (fillMode + 1) % 3;
            btn.setMessage(Component.literal(fillNames[fillMode]));
        }).bounds(invX + 10, invY + 42, 75, 20).build());

        CustomData data = slate.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.EMPTY);

        boolean hasBuildConfig = slate.has(ModDataComponents.PENDING_BUILD_CONFIG.get());
        boolean isAnchored = hasBuildConfig && data.copyTag().getBoolean("IsAnchored");

        Component actionText = isAnchored ? Component.literal("Add to Queue") : Component.literal("Set Location");
        this.addRenderableWidget(Button.builder(actionText, btn -> {
            saveInputs();
            String action = isAnchored ? "QUEUE_ANCHORED" : "SAVE_ONLY";
            PacketDistributor.sendToServer(createPayload(action));
            this.onClose();
        }).bounds(invX + 88, invY + 42, 84, 20).build());
    }

    private void initAdvancedMode() {
        int leftPanelX = leftPos + 12;
        int rightPanelX = leftPos + chassisW - 106 - 12;
        int panelY = topPos + 12;

        if (serverSchematics.isEmpty()) {
            PacketDistributor.sendToServer(new SchematicListRequestPayload());
        }

        advSearchBox = new EditBox(this.font, leftPanelX + 6, panelY + 16, 94, 12, Component.literal("Search..."));
        advSearchBox.setMaxLength(30);
        advSearchBox.setResponder(s -> { advPage = 0; updateAdvPagination(); });
        this.addRenderableWidget(advSearchBox);

        schematicButtons.clear();
        for (int i = 0; i < advItemsPerPage; i++) {
            int btnIndex = i;
            Button btn = Button.builder(Component.literal(""), b -> {
                selectedSchematicName = currentDisplayedSchematics[btnIndex];
                this.materialStatus = "Loading...";
                this.currentMaterials.clear();
                PacketDistributor.sendToServer(new SchematicRequestPayload(selectedSchematicName));
            }).bounds(leftPanelX + 6, panelY + 32 + (i * 16), 94, 14).build();
            schematicButtons.add(btn);
            this.addRenderableWidget(btn);
        }

        advPrevBtn = this.addRenderableWidget(Button.builder(Component.literal("<"), b -> { advPage--; updateAdvPagination(); }).bounds(leftPanelX + 6, panelY + 116, 20, 16).build());
        advNextBtn = this.addRenderableWidget(Button.builder(Component.literal(">"), b -> { advPage++; updateAdvPagination(); }).bounds(leftPanelX + 80, panelY + 116, 20, 16).build());

        CustomData data = slate.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.EMPTY);

        boolean hasBuildConfig = slate.has(ModDataComponents.PENDING_BUILD_CONFIG.get());
        boolean isAnchored = hasBuildConfig && data.copyTag().getBoolean("IsAnchored");

        Component actionText = isAnchored ? Component.literal("Add to Queue") : Component.literal("Set Location");
        this.addRenderableWidget(Button.builder(actionText, btn -> {
            saveInputs();
            String action = isAnchored ? "QUEUE_ANCHORED" : "SAVE_ONLY";
            PacketDistributor.sendToServer(createPayload(action));
            this.onClose();
        }).bounds(rightPanelX + 8, panelY + 115, 92, 20).build());

        updateAdvPagination();
    }

    private void initFineTuneMode() {
        int panelTopY = topPos + 12;
        int centerX = leftPos + chassisW / 2;
        int ctrlX = leftPos + 140;

        xBox = new EditBox(this.font, ctrlX + 22, panelTopY + 30, 36, 16, Component.literal("X"));
        xBox.setValue(String.valueOf(offsetX));
        xBox.setResponder(s -> { try { offsetX = Integer.parseInt(s); sendOffsetUpdate(); } catch (NumberFormatException ignored) {} });
        this.addRenderableWidget(xBox);
        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> { offsetX--; xBox.setValue(String.valueOf(offsetX)); sendOffsetUpdate(); }).bounds(ctrlX, panelTopY + 28, 20, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> { offsetX++; xBox.setValue(String.valueOf(offsetX)); sendOffsetUpdate(); }).bounds(ctrlX + 60, panelTopY + 28, 20, 20).build());

        yBox = new EditBox(this.font, ctrlX + 22, panelTopY + 55, 36, 16, Component.literal("Y"));
        yBox.setValue(String.valueOf(offsetY));
        yBox.setResponder(s -> { try { offsetY = Integer.parseInt(s); sendOffsetUpdate(); } catch (NumberFormatException ignored) {} });
        this.addRenderableWidget(yBox);
        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> { offsetY--; yBox.setValue(String.valueOf(offsetY)); sendOffsetUpdate(); }).bounds(ctrlX, panelTopY + 53, 20, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> { offsetY++; yBox.setValue(String.valueOf(offsetY)); sendOffsetUpdate(); }).bounds(ctrlX + 60, panelTopY + 53, 20, 20).build());

        zBox = new EditBox(this.font, ctrlX + 22, panelTopY + 80, 36, 16, Component.literal("Z"));
        zBox.setValue(String.valueOf(offsetZ));
        zBox.setResponder(s -> { try { offsetZ = Integer.parseInt(s); sendOffsetUpdate(); } catch (NumberFormatException ignored) {} });
        this.addRenderableWidget(zBox);
        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> { offsetZ--; zBox.setValue(String.valueOf(offsetZ)); sendOffsetUpdate(); }).bounds(ctrlX, panelTopY + 78, 20, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> { offsetZ++; zBox.setValue(String.valueOf(offsetZ)); sendOffsetUpdate(); }).bounds(ctrlX + 60, panelTopY + 78, 20, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Close & Confirm"), btn -> {
            saveInputs();
            sendOffsetUpdate();
            this.onClose();
        }).bounds(centerX - 55, panelTopY + 112, 110, 20).build());
    }

    private void sendOffsetUpdate() {
        if (slate.has(ModDataComponents.PENDING_BUILD_CONFIG.get())) {
            CompoundTag tag = slate.get(ModDataComponents.PENDING_BUILD_CONFIG.get()).copy();
            tag.putInt("offsetX", offsetX);
            tag.putInt("offsetY", offsetY);
            tag.putInt("offsetZ", offsetZ);
            slate.set(ModDataComponents.PENDING_BUILD_CONFIG.get(), tag);
        }
    }

    private void updateAdvPagination() {
        String query = advSearchBox != null ? advSearchBox.getValue().toLowerCase() : "";
        List<String> filtered = serverSchematics.stream().filter(s -> cleanSchematicName(s).toLowerCase().contains(query)).toList();

        int maxPages = (int) Math.ceil(filtered.size() / (float) advItemsPerPage);
        if (advPage < 0) advPage = 0;
        if (advPage >= maxPages && maxPages > 0) advPage = maxPages - 1;

        for (int i = 0; i < advItemsPerPage; i++) {
            if ((advPage * advItemsPerPage) + i < filtered.size()) {
                String rawName = filtered.get((advPage * advItemsPerPage) + i);
                currentDisplayedSchematics[i] = rawName;

                String dispName = cleanSchematicName(rawName);
                if (dispName.length() > 14) dispName = dispName.substring(0, 13) + "...";

                schematicButtons.get(i).visible = true;
                schematicButtons.get(i).setMessage(Component.literal(dispName));
            } else {
                schematicButtons.get(i).visible = false;
                currentDisplayedSchematics[i] = "";
            }
        }
        if (advPrevBtn != null) advPrevBtn.active = advPage > 0;
        if (advNextBtn != null) advNextBtn.active = advPage < maxPages - 1;
    }

    private void saveInputs() {
        if (widthBox != null) bWidth = parseInput(widthBox.getValue(), bWidth);
        if (lengthBox != null) bLength = parseInput(lengthBox.getValue(), bLength);
        if (heightBox != null) bHeight = parseInput(heightBox.getValue(), bHeight);

        if (xBox != null) offsetX = parseInput(xBox.getValue(), offsetX);
        if (yBox != null) offsetY = parseInput(yBox.getValue(), offsetY);
        if (zBox != null) offsetZ = parseInput(zBox.getValue(), offsetZ);
    }

    private int parseInput(String text, int defaultValue) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1 && !carriedItem.isEmpty()) {
            carriedItem = ItemStack.EMPTY;
            return true;
        }

        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int currentLvl = getCompanionLevel();

        if (mouseX >= prepX && mouseX <= prepX + 26 && mouseY >= prepY && mouseY <= prepY + 26) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            prepMode = !prepMode;
            return true;
        }

        if (buildMode == 0 && mouseX >= matX && mouseX <= matX + 26 && mouseY >= matY && mouseY <= matY + 26) {
            if (!carriedItem.isEmpty()) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                selectedMaterial = carriedItem.copy();
                carriedItem = ItemStack.EMPTY;
                return true;
            }
        }

        for (int i = 0; i < 3; i++) {
            // ARCHITECT FIX: Only process clicks for the Build Tab if Lv >= 4 (Consistency Check)
            if (i == 2 && currentLvl < 4) continue;

            int tx = leftPos + 10 + (i * 29);
            int ty = topPos - 28;
            if (mouseX >= tx && mouseX <= tx + topTabW && mouseY >= ty && mouseY <= ty + topTabH) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                if (i == 0) { this.minecraft.setScreen(new CommandSlateScreen(slate)); return true; }
                else if (i == 1) { this.minecraft.setScreen(new DigConfigScreen(targetNpc, slate, this)); return true; }
            }
        }

        for (int i = 0; i < 3; i++) {
            // ARCHITECT FIX: Gate Advanced Blueprints (Index 1) behind Level 15
            if (i == 1 && currentLvl < 15) continue;

            int tx = leftPos + chassisW - 97 + (i * 29);
            int ty = topPos - 28;
            if (mouseX >= tx && mouseX <= tx + topTabW && mouseY >= ty && mouseY <= ty + topTabH) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                saveInputs();
                buildMode = i;
                this.clearWidgets();
                this.init();
                return true;
            }
        }

        if (buildMode == 0) {
            for (int i = 0; i < 5; i++) {
                int tx = leftPos + chassisW - 4;
                int ty = topPos + 10 + (i * rightTabH);
                if (mouseX >= tx && mouseX <= tx + rightTabW && mouseY >= ty && mouseY <= ty + rightTabH) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    lastShape = i;
                    saveInputs();
                    this.clearWidgets();
                    this.init();
                    return true;
                }
            }

            if (this.minecraft.player != null) {
                int invX = leftPos + 36;
                int invY = topPos;
                Inventory inv = this.minecraft.player.getInventory();

                for (int i = 0; i < 9; i++) {
                    int sx = invX + 8 + (i * 18);
                    int sy = invY + 142;
                    if (mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18) {
                        pickUpItem(inv.getItem(i));
                        return true;
                    }
                }
                for (int i = 9; i < 36; i++) {
                    int row = (i - 9) / 9;
                    int col = (i - 9) % 9;
                    int sx = invX + 8 + (col * 18);
                    int sy = invY + 84 + (row * 18);
                    if (mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18) {
                        pickUpItem(inv.getItem(i));
                        return true;
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void pickUpItem(ItemStack clickedItem) {
        if (!clickedItem.isEmpty() && clickedItem.getItem() != Items.AIR) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            carriedItem = clickedItem.copy();
            carriedItem.setCount(1);
        }
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
            if (i != 2) {
                guiGraphics.blit(ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "textures/menu/tab_top_unselected_" + (i + 1) + ".png"), leftPos + 10 + (i * 29), topPos - 28 + 4, 0, 0, topTabW, topTabH, topTabW, topTabH);
            }
        }

        for (int i = 0; i < 3; i++) {
            // ARCHITECT FIX: Hide Advanced Blueprints (Tab Index 1) background if < 15
            if (i == 1 && currentLvl < 15) continue;

            if (i != buildMode) {
                guiGraphics.blit(ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "textures/menu/tab_top_unselected_" + (i + 1) + ".png"), leftPos + chassisW - 97 + (i * 29), topPos - 28 + 4, 0, 0, topTabW, topTabH, topTabW, topTabH);
            }
        }

        if (buildMode == 0) {
            for (int i = 0; i < 5; i++) {
                if (i != lastShape) {
                    guiGraphics.blit(ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "textures/menu/tab_right_" + (i == 0 ? "top" : (i == 4 ? "bottom" : "middle")) + ".png"), leftPos + chassisW - 4, topPos + 10 + (i * rightTabH), 0, 0, rightTabW, rightTabH, rightTabW, rightTabH);
                }
            }
        }

        guiGraphics.blit(BG, leftPos, topPos, 0, 0, chassisW, chassisH, 256, 256);

        guiGraphics.blit(ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "textures/menu/tab_top_selected_3.png"), leftPos + 10 + (2 * 29), topPos - 28, 0, 0, topTabW, topTabH, topTabW, topTabH);
        guiGraphics.blit(ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "textures/menu/tab_top_selected_" + (buildMode + 1) + ".png"), leftPos + chassisW - 97 + (buildMode * 29), topPos - 28, 0, 0, topTabW, topTabH, topTabW, topTabH);

        if (buildMode == 0) {
            guiGraphics.blit(ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "textures/menu/tab_right_" + (lastShape == 0 ? "top" : (lastShape == 4 ? "bottom" : "middle")) + "_selected.png"), leftPos + chassisW - 1, topPos + 10 + (lastShape * rightTabH), 0, 0, rightTabW, rightTabH, rightTabW, rightTabH);
            guiGraphics.blit(INV_BG, leftPos + 36, topPos, 0, 0, 176, 166, 256, 256);
        } else if (buildMode == 1) {
            drawSunkenPanel(guiGraphics, leftPos + 12, topPos + 12, 106, 142);
        } else if (buildMode == 2) {
            drawSunkenPanel(guiGraphics, leftPos + 12, topPos + 12, 224, 142);
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

        int currentLvl = getCompanionLevel();

        guiGraphics.renderItem(new ItemStack(Items.BOOK), leftPos + 10 + 6, topPos - 20 + 4);
        guiGraphics.renderItem(new ItemStack(Items.IRON_PICKAXE), leftPos + 39 + 6, topPos - 20 + 4);
        guiGraphics.renderItem(new ItemStack(Items.BRICKS), leftPos + 68 + 6, topPos - 20);

        guiGraphics.renderItem(new ItemStack(Items.BRICKS), leftPos + chassisW - 97 + 6, topPos - 20 + (buildMode == 0 ? 0 : 4));

        // ARCHITECT FIX: Only render Map Icon if Advanced Blueprints is unlocked
        if (currentLvl >= 15) {
            guiGraphics.renderItem(new ItemStack(Items.FILLED_MAP), leftPos + chassisW - 68 + 6, topPos - 20 + (buildMode == 1 ? 0 : 4));
        }

        guiGraphics.renderItem(new ItemStack(Items.TARGET), leftPos + chassisW - 39 + 6, topPos - 20 + (buildMode == 2 ? 0 : 4));

        ResourceLocation prepFrame = prepMode ? ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "textures/menu/task_frame_obtained.png") : ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "textures/menu/task_frame_unobtained.png");
        guiGraphics.blit(prepFrame, prepX, prepY, 0, 0, 26, 26, 26, 26);
        if (prepMode) {
            guiGraphics.renderItem(new ItemStack(Items.IRON_SHOVEL), prepX + 5, prepY + 5);
        } else {
            RenderSystem.enableBlend();
            TextureAtlasSprite ghostShovel = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ResourceLocation.withDefaultNamespace("item/empty_slot_shovel"));
            guiGraphics.blit(prepX + 5, prepY + 5, 0, 16, 16, ghostShovel);
            RenderSystem.disableBlend();
        }

        if (buildMode == 0) {
            renderSimpleMode(guiGraphics, mouseX, mouseY);
        } else if (buildMode == 1) {
            renderAdvancedMode(guiGraphics);
        } else {
            renderFineTuneMode(guiGraphics);
        }

        renderTooltips(guiGraphics, mouseX, mouseY);

        if (!carriedItem.isEmpty()) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 400);
            guiGraphics.renderItem(carriedItem, mouseX - 8, mouseY - 8);
            guiGraphics.renderItemDecorations(this.font, carriedItem, mouseX - 8, mouseY - 8);
            guiGraphics.pose().popPose();
        }
    }

    private void renderSimpleMode(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.renderItem(new ItemStack(Items.IRON_BLOCK), leftPos + chassisW + 4, topPos + 10 + 6);
        guiGraphics.renderItem(new ItemStack(Items.STONE_BRICK_STAIRS), leftPos + chassisW + 4, topPos + 10 + rightTabH + 6);
        guiGraphics.renderItem(new ItemStack(Items.COMPASS), leftPos + chassisW + 4, topPos + 10 + (rightTabH * 2) + 6);
        guiGraphics.renderItem(new ItemStack(Items.GLASS), leftPos + chassisW + 4, topPos + 10 + (rightTabH * 3) + 6);
        guiGraphics.renderItem(new ItemStack(Items.CHISELED_SANDSTONE), leftPos + chassisW + 4, topPos + 10 + (rightTabH * 4) + 6);

        int invX = leftPos + 36;
        int invY = topPos;

        if (lastShape == 0) {
            guiGraphics.drawString(this.font, "Width", invX + 10, invY + 12, 0xFFFFFF, false);
            guiGraphics.drawString(this.font, "Length", invX + 65, invY + 12, 0xFFFFFF, false);
            guiGraphics.drawString(this.font, "Height", invX + 120, invY + 12, 0xFFFFFF, false);
        } else if (lastShape == 1 || lastShape == 2 || lastShape == 4) {
            guiGraphics.drawString(this.font, lastShape == 2 ? "Radius" : "Width", invX + 35, invY + 12, 0xFFFFFF, false);
            guiGraphics.drawString(this.font, "Height", invX + 90, invY + 12, 0xFFFFFF, false);
        } else if (lastShape == 3) {
            guiGraphics.drawString(this.font, "Radius", invX + 65, invY + 12, 0xFFFFFF, false);
        }

        guiGraphics.blit(ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "textures/menu/task_frame_unobtained.png"), matX, matY, 0, 0, 26, 26, 26, 26);
        guiGraphics.fill(matX + 4, matY + 4, matX + 22, matY + 22, 0xFF8B8B8B);
        guiGraphics.renderItem(selectedMaterial, matX + 5, matY + 5);

        if (this.minecraft.player != null) {
            Inventory inv = this.minecraft.player.getInventory();
            for (int i = 0; i < 9; i++) {
                int sx = invX + 8 + (i * 18);
                int sy = invY + 142;
                guiGraphics.renderItem(inv.getItem(i), sx, sy);
                guiGraphics.renderItemDecorations(this.font, inv.getItem(i), sx, sy);
                if (mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18) {
                    guiGraphics.fill(sx, sy, sx + 16, sy + 16, 0x80FFFFFF);
                }
            }
            for (int i = 9; i < 36; i++) {
                int row = (i - 9) / 9;
                int col = (i - 9) % 9;
                int sx = invX + 8 + (col * 18);
                int sy = invY + 84 + (row * 18);
                guiGraphics.renderItem(inv.getItem(i), sx, sy);
                guiGraphics.renderItemDecorations(this.font, inv.getItem(i), sx, sy);
                if (mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18) {
                    guiGraphics.fill(sx, sy, sx + 16, sy + 16, 0x80FFFFFF);
                }
            }
        }
    }

    private void renderAdvancedMode(GuiGraphics guiGraphics) {
        int rightPanelX = leftPos + chassisW - 106 - 12;
        int panelY = topPos + 12;

        guiGraphics.drawCenteredString(this.font, "Schematic Directory", leftPos + 12 + 53, panelY + 4, 0xFFFFFF);

        String dispName = cleanSchematicName(selectedSchematicName);
        if (dispName.isEmpty()) dispName = "No Schematic";
        if (dispName.length() > 14) dispName = dispName.substring(0, 13) + "...";

        guiGraphics.drawString(this.font, "Preview:", rightPanelX + 6, panelY + 4, 0xFFFFFF, false);
        guiGraphics.drawString(this.font, dispName, rightPanelX + 6, panelY + 16, 0xAAAAAA, false);
        guiGraphics.drawString(this.font, "Requirements:", rightPanelX + 6, panelY + 30, 0xFFFFFF, false);

        if (!materialStatus.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, materialStatus, rightPanelX + 53, panelY + 52, 0xAAAAAA);
        } else if (!currentMaterials.isEmpty()) {
            int gridX = rightPanelX + 8;
            int gridY = panelY + 42;

            int i = 0;
            for (Map.Entry<String, Integer> entry : currentMaterials.entrySet()) {
                if (i >= 15) break;

                int col = i % 5;
                int row = i / 5;
                int sx = gridX + (col * 18);
                int sy = gridY + (row * 18);

                guiGraphics.fill(sx, sy, sx + 18, sy + 18, 0xFF373737);
                guiGraphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF8b8b8b);

                ResourceLocation itemId = ResourceLocation.tryParse(entry.getKey());
                Item item = itemId != null ? BuiltInRegistries.ITEM.get(itemId) : Items.STONE;
                if (item == Items.AIR) item = Items.STONE;

                guiGraphics.renderItem(new ItemStack(item), sx + 1, sy + 1);

                String countStr = String.valueOf(entry.getValue());
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(0, 0, 200);
                float scale = 0.6f;
                guiGraphics.pose().scale(scale, scale, 1.0f);
                int textW = this.font.width(countStr);
                guiGraphics.drawString(this.font, countStr, (int)((sx + 17) / scale) - textW, (int)((sy + 11) / scale), 0xFFFFFF, true);
                guiGraphics.pose().popPose();

                i++;
            }
        }
    }

    private void renderFineTuneMode(GuiGraphics guiGraphics) {
        int panelTopY = topPos + 12;
        int centerX = leftPos + chassisW / 2;

        guiGraphics.drawCenteredString(this.font, "Fine-Tune Offset Alignment", centerX, panelTopY + 6, 0xFFFFFF);

        int labelX = leftPos + 25;
        guiGraphics.drawString(this.font, "East / West (X)", labelX, panelTopY + 34, 0xFFFFFF, false);
        guiGraphics.drawString(this.font, "Up / Down (Y)", labelX, panelTopY + 59, 0xFFFFFF, false);
        guiGraphics.drawString(this.font, "North / South (Z)", labelX, panelTopY + 84, 0xFFFFFF, false);
    }

    private void renderTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int currentLvl = getCompanionLevel();

        String[] topNames = {"Home / Operations", "Dig Configuration", "Build Configuration"};
        for(int i = 0; i < 3; i++) {
            int tx = leftPos + 10 + (i * 29);
            int ty = topPos - 28;
            if (mouseX >= tx && mouseX <= tx + topTabW && mouseY >= ty && mouseY <= ty + topTabH) {
                guiGraphics.renderTooltip(this.font, Component.literal(topNames[i]), mouseX, mouseY);
            }
        }

        // ARCHITECT FIX: Sub-tab Tooltips for better UX (hiding the advanced one if locked)
        String[] subTabNames = {"Simple Structures", "Advanced Blueprints", "Fine-Tune Offsets"};
        for(int i = 0; i < 3; i++) {
            if (i == 1 && currentLvl < 15) continue;
            int tx = leftPos + chassisW - 97 + (i * 29);
            int ty = topPos - 28;
            if (mouseX >= tx && mouseX <= tx + topTabW && mouseY >= ty && mouseY <= ty + topTabH) {
                guiGraphics.renderTooltip(this.font, Component.literal(subTabNames[i]), mouseX, mouseY);
            }
        }

        if (mouseX >= prepX && mouseX <= prepX + 26 && mouseY >= prepY && mouseY <= prepY + 26) {
            guiGraphics.renderTooltip(this.font, Component.literal(prepMode ? "§bPrep Phase: ENABLED§r" : "§7Prep Phase: DISABLED§r"), mouseX, mouseY);
        }

        if (buildMode == 0) {
            if (mouseX >= matX && mouseX <= matX + 26 && mouseY >= matY && mouseY <= matY + 26) {
                guiGraphics.renderTooltip(this.font, Component.literal("Target Material Drop Slot"), mouseX, mouseY);
            }

            String[] shapeNames = {"Square", "Stairs", "Circle", "Dome", "Pyramid"};
            for (int i = 0; i < 5; i++) {
                int tx = leftPos + chassisW - 4;
                int ty = topPos + 10 + (i * rightTabH);
                if (mouseX >= tx && mouseX <= tx + rightTabW && mouseY >= ty && mouseY <= ty + rightTabH) {
                    guiGraphics.renderTooltip(this.font, Component.literal("Shape: " + shapeNames[i]), mouseX, mouseY);
                }
            }
        } else if (buildMode == 1) {
            if (!currentMaterials.isEmpty()) {
                int rightPanelX = leftPos + chassisW - 106 - 12;
                int panelY = topPos + 12;
                int gridX = rightPanelX + 8;
                int gridY = panelY + 42;

                int i = 0;
                for (Map.Entry<String, Integer> entry : currentMaterials.entrySet()) {
                    if (i >= 15) break;
                    int col = i % 5;
                    int row = i / 5;
                    int sx = gridX + (col * 18);
                    int sy = gridY + (row * 18);

                    if (mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18) {
                        String cleanMatName = cleanSchematicName(entry.getKey());
                        guiGraphics.renderTooltip(this.font, Component.literal(cleanMatName + " x" + entry.getValue()), mouseX, mouseY);
                    }
                    i++;
                }
            }
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}