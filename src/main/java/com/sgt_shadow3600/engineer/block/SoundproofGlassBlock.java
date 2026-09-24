package com.sgt_shadow3600.engineer.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class SoundproofGlassBlock extends TransparentBlock {

    // 1.21 Requirement: Blocks extending TransparentBlock need a codec
    public static final MapCodec<SoundproofGlassBlock> CODEC = simpleCodec(SoundproofGlassBlock::new);

    public SoundproofGlassBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends TransparentBlock> codec() {
        return CODEC;
    }
}