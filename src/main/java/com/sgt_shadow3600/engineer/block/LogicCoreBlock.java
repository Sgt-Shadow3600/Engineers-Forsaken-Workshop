package com.sgt_shadow3600.engineer.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class LogicCoreBlock extends Block implements EntityBlock {

    // ARCHITECT FIX: Highly optimized, statically cached VoxelShape union derived from Blockbench geometry.
    // Avoids calculating 9 separate block-box intersections on every tick/raytrace.
    private static final VoxelShape SHAPE = Shapes.or(
            // Netherite Outer Frame
            Block.box(0, 0, 0, 16, 2, 16),
            Block.box(14, 2, 0, 16, 14, 16),
            Block.box(0, 2, 0, 2, 14, 16),
            Block.box(0, 14, 0, 16, 16, 16),
            // Inner Sculk Inserts
            Block.box(2, 2, 2, 14, 4, 14),
            Block.box(2, 4, 2, 4, 12, 14),
            Block.box(12, 4, 2, 14, 12, 14),
            Block.box(2, 12, 2, 14, 14, 14),
            // Floating Diamond Core
            Block.box(5, 5, 6, 11, 11, 12)
    );

    public LogicCoreBlock(Properties properties) {
        super(properties);
    }

    // Bind the complex hitboxes to the block
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LogicCoreBlockEntity(pos, state);
    }

    // Guarantees the drop fires when a player breaks it, even in Creative Mode
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            this.executeCoreDrop(level, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // Handles explosions, pistons, or any non-player block removal
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            this.executeCoreDrop(level, pos);
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    // Centralized drop logic to ensure we never lose data and never drop duplicates
    private void executeCoreDrop(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof LogicCoreBlockEntity be) {
            CompoundTag npcData = be.getNpcData();

            // If it's already empty, it was already dropped (e.g., playerWillDestroy ran right before onRemove)
            if (npcData == null || npcData.isEmpty()) return;

            // Notify Terminal that the core is in transit and save it
            if (be.getTerminalPos() != null && level.getBlockEntity(be.getTerminalPos()) instanceof TerminalBlockEntity terminal) {
                terminal.coreInTransit = true;
                terminal.sync();
            }

            // 1.21.1 Strict Data Component Typing
            ItemStack drop = new ItemStack(com.sgt_shadow3600.engineer.EngineerCompanion.LOGIC_CORE_ITEM.get());
            drop.set(com.sgt_shadow3600.engineer.ModDataComponents.STORED_NPC_DATA.get(), npcData);

            ItemEntity itementity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
            itementity.setDefaultPickUpDelay();
            level.addFreshEntity(itementity);

            // Wipe the BE data so it doesn't drop a duplicate if another block-break event fires
            be.setNpcData(new CompoundTag());
        }
    }
}