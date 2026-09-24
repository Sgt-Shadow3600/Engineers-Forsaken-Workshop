package com.sgt_shadow3600.engineer.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class SoundproofTintedGlassBlock extends TransparentBlock {

    // 1.21 Requirement: Blocks need a codec
    public static final MapCodec<SoundproofTintedGlassBlock> CODEC = simpleCodec(SoundproofTintedGlassBlock::new);

    public SoundproofTintedGlassBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends TransparentBlock> codec() {
        return CODEC;
    }

    // --- TINTED GLASS LOGIC ---
    // By overriding these two methods, this block behaves exactly like vanilla Tinted Glass

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return level.getMaxLightLevel();
    }
}