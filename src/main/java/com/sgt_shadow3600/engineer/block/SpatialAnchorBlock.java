package com.sgt_shadow3600.engineer.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SpatialAnchorBlock extends Block {

    // ARCHITECT FIX: Statically cached VoxelShape union derived directly from your Blockbench JSON geometry.
    private static final VoxelShape SHAPE = Shapes.or(
            // Copper Base
            Block.box(0, 0, 0, 16, 8, 16),
            // Rod Bases
            Block.box(7, 8, 2, 9, 12, 4),
            Block.box(12, 8, 12, 14, 12, 14),
            Block.box(2, 8, 12, 4, 12, 14),
            // Rod Tops
            Block.box(6, 12, 1, 10, 16, 5),
            Block.box(11, 12, 11, 15, 16, 15),
            Block.box(1, 12, 11, 5, 16, 15),
            // Floating Ender Pearl Shard
            Block.box(7, 13, 8, 9, 15, 10)
    );

    public SpatialAnchorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    // Bind the calculated hitboxes to the block
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}