package com.sgt_shadow3600.engineer.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEventListener;
import org.jetbrains.annotations.Nullable;

public class ResonanceDisruptorBlock extends BaseEntityBlock {

    public static final MapCodec<ResonanceDisruptorBlock> CODEC = simpleCodec(ResonanceDisruptorBlock::new);

    public ResonanceDisruptorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ResonanceDisruptorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return createTickerHelper(blockEntityType, com.sgt_shadow3600.engineer.EngineerCompanion.RESONANCE_DISRUPTOR_BE.get(),
                level.isClientSide ? null : ResonanceDisruptorBlockEntity::tick);
    }

    // --- CRITICAL ARCHITECTURE FIX ---
    @Nullable
    @Override
    public <T extends BlockEntity> GameEventListener getListener(ServerLevel level, T blockEntity) {
        if (blockEntity instanceof ResonanceDisruptorBlockEntity disruptor) {
            return disruptor.getListener();
        }
        return super.getListener(level, blockEntity);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof ResonanceDisruptorBlockEntity disruptor) {
                disruptor.triggerDisruption(serverLevel, pos);
            }
        }
        super.stepOn(level, pos, state, entity);
    }
}