package com.sgt_shadow3600.engineer.item;

import com.sgt_shadow3600.engineer.ModDataComponents;
import com.sgt_shadow3600.engineer.block.TerminalBlock;
import com.sgt_shadow3600.engineer.world.TerminalNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;

import java.util.ArrayList;
import java.util.List;

public class CommandSlateItem extends Item {

    public CommandSlateItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!level.isClientSide && entity instanceof ServerPlayer player) {
            // Check terminal validity every 2 seconds
            if (level.getGameTime() % 40 == 0) {
                TerminalNetwork network = TerminalNetwork.get(level.getServer());
                List<GlobalPos> validTerminalList = new ArrayList<>();

                // The Slate actively connects to the UUID TerminalNetwork autonomously
                if (network.hasActiveTerminal(player.getUUID())) {
                    GlobalPos gp = network.getActiveTerminal(player.getUUID());
                    ServerLevel targetLevel = level.getServer().getLevel(gp.dimension());

                    if (targetLevel != null && targetLevel.isLoaded(gp.pos())) {
                        if (!(targetLevel.getBlockState(gp.pos()).getBlock() instanceof TerminalBlock)) {
                            network.removeTerminal(player.getUUID(), gp);
                        } else {
                            validTerminalList.add(gp);
                        }
                    } else {
                        // Keep it in the list if the chunk is just unloaded
                        validTerminalList.add(gp);
                    }
                }

                List<GlobalPos> current = stack.getOrDefault(ModDataComponents.BOUND_TERMINALS.get(), List.of());
                if (!current.equals(validTerminalList)) {
                    stack.set(ModDataComponents.BOUND_TERMINALS.get(), validTerminalList);
                    stack.set(ModDataComponents.ACTIVE_TERMINAL.get(), 0);
                }
            }
        }
        super.inventoryTick(stack, level, entity, slotId, isSelected);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        // Intercept Shift-Left-Click to clear the Anchor
        if (player.isCrouching()) {
            ItemStack stack = player.getMainHandItem();
            if (stack.getItem() == this) {
                CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
                net.minecraft.nbt.CompoundTag tag = data.copyTag();

                if (tag.getBoolean("IsAnchored")) {
                    tag.putBoolean("IsAnchored", false);

                    // ARCHITECT FIX: 1.21.1 Standard for writing Generic NBT back to an item
                    CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);

                    if (!level.isClientSide) {
                        player.displayClientMessage(Component.literal("§aAnchor Cleared.§r"), true);
                    }
                }
            }
            // Prevent actually breaking/punching the block with the Slate
            return false;
        }
        return super.canAttackBlock(state, level, pos, player);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();

        if (player == null) return InteractionResult.PASS;

        // Ensure we don't accidentally intercept when shift-clicking the Terminal Block itself
        if (player.isCrouching() && !(level.getBlockState(pos).getBlock() instanceof TerminalBlock)) {

            // ANCHORING LOGIC (Deployment has been moved exclusively to the UI)
            List<GlobalPos> terminals = stack.getOrDefault(ModDataComponents.BOUND_TERMINALS.get(), List.of());
            if (terminals.isEmpty()) {
                if (!level.isClientSide) player.displayClientMessage(Component.literal("§cNo active Terminal linked! Place a Terminal block to claim it.§r"), true);
                return InteractionResult.SUCCESS;
            }

            boolean hasDig = stack.has(ModDataComponents.PENDING_DIG_CONFIG.get());
            boolean hasBuild = stack.has(ModDataComponents.PENDING_BUILD_CONFIG.get());

            if (!hasDig && !hasBuild) {
                if (!level.isClientSide) player.displayClientMessage(Component.literal("§cNo Configuration saved! Open Slate UI to configure first.§r"), true);
                return InteractionResult.SUCCESS;
            }

            CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            net.minecraft.nbt.CompoundTag tag = data.copyTag();

            // Set or Overwrite the Anchor Position
            tag.putBoolean("IsAnchored", true);
            tag.putInt("AnchorX", pos.getX());
            tag.putInt("AnchorY", pos.getY());
            tag.putInt("AnchorZ", pos.getZ());

            Direction rawDir;
            if (player.getXRot() > 45.0F) rawDir = Direction.DOWN;
            else if (player.getXRot() < -45.0F) rawDir = Direction.UP;
            else rawDir = player.getDirection();

            Direction rightDir = (rawDir.getAxis() == Direction.Axis.Y) ? player.getDirection().getClockWise() : rawDir.getClockWise();

            tag.putInt("AnchorDir", rawDir.get3DDataValue());
            tag.putInt("AnchorRightDir", rightDir.get3DDataValue());
            tag.putInt("StructureRotation", 0);

            // ARCHITECT FIX: Write updated NBT tag back into the DataComponent
            CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);

            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal("§eAnchor Locked. Shift-Scroll to rotate. Open UI to Add to Queue. Shift-Left-Click to clear.§r"), true);
            }

            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return super.useOn(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            ClientAccess.openScreen(stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    // ARCHITECT FIX: Dedicated Server Crash Prevention
    // Isolates Client-Side imports inside a nested class so the server never attempts to load them.
    private static class ClientAccess {
        @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
        public static void openScreen(ItemStack stack) {
            net.minecraft.client.Minecraft.getInstance().setScreen(new com.sgt_shadow3600.engineer.client.CommandSlateScreen(stack));
        }
    }
}