package com.sgt_shadow3600.engineer.item;

import com.sgt_shadow3600.engineer.EngineerCompanion;
import com.sgt_shadow3600.engineer.entity.EchoSentinelEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class EchoSentinelItem extends Item {

    public EchoSentinelItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        ItemStack itemstack = context.getItemInHand();
        BlockPos blockpos = context.getClickedPos();
        Direction direction = context.getClickedFace();
        BlockState blockstate = level.getBlockState(blockpos);

        // Calculate exact placement position (on top of the clicked block)
        BlockPos spawnPos = blockstate.getCollisionShape(level, blockpos).isEmpty() ? blockpos : blockpos.relative(direction);

        EchoSentinelEntity sentinel = EngineerCompanion.ECHO_SENTINEL_ENTITY.get().create(serverLevel);
        if (sentinel != null) {
            // Snap to the exact center of the block grid
            sentinel.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
            sentinel.setPersistenceRequired(); // Ensure it never despawns
            serverLevel.addFreshEntity(sentinel);

            if (!context.getPlayer().isCreative()) {
                itemstack.shrink(1);
            }
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }
}