package com.sgt_shadow3600.engineer.event;

import com.sgt_shadow3600.engineer.EngineerCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;

@EventBusSubscriber(modid = "engineer", bus = EventBusSubscriber.Bus.GAME)
public class SpatialAnchorEventHandler {

    // 16 block radius (32x32x32 bounding box equivalent)
    private static final int INHIBIT_RADIUS = 16;

    @SubscribeEvent
    public static void onEntityTeleport(EntityTeleportEvent event) {
        Entity entity = event.getEntity();

        // TPS OPTIMIZATION: Only process Endermen and Shulkers. Instantly ignore pearls, players, and chorus fruit.
        if (entity.getType() != EntityType.ENDERMAN && entity.getType() != EntityType.SHULKER) return;

        Level level = entity.level();
        if (level.isClientSide()) return;

        BlockPos sourcePos = entity.blockPosition();
        BlockPos targetPos = BlockPos.containing(event.getTarget());

        // Scan for the Spatial Anchor near the departure OR arrival point
        boolean sourceJammed = isAnchorNearby(level, sourcePos);
        boolean targetJammed = !sourceJammed && isAnchorNearby(level, targetPos);

        if (sourceJammed || targetJammed) {
            event.setCanceled(true);

            // Play a localized sound to indicate the warp was jammed
            // Using ENDERMAN_HURT for both since it conveys a "failed warp" effectively
            level.playSound(null, sourcePos, SoundEvents.ENDERMAN_HURT, SoundSource.NEUTRAL, 1.0F, 0.5F);

            if (targetJammed) {
                level.playSound(null, targetPos, SoundEvents.ENDERMAN_HURT, SoundSource.NEUTRAL, 1.0F, 0.5F);
            }
        }
    }

    private static boolean isAnchorNearby(Level level, BlockPos center) {
        // Highly optimized chunk-section scan. Aborts early upon finding the first match.
        // NOTE: Relies on SPATIAL_ANCHOR being registered in EngineerCompanion.
        return BlockPos.findClosestMatch(center, INHIBIT_RADIUS, INHIBIT_RADIUS,
                pos -> level.getBlockState(pos).is(EngineerCompanion.SPATIAL_ANCHOR.get())).isPresent();
    }
}