package com.sgt_shadow3600.engineer.event;

import com.sgt_shadow3600.engineer.entity.EngineerCompanionEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@EventBusSubscriber(modid = "engineer", bus = EventBusSubscriber.Bus.GAME)
public class MobTargetingEvent {

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // We ensure we only modify server-side monster logic (Zombies, Skeletons, modded hostiles, etc.)
        if (!event.getLevel().isClientSide && event.getEntity() instanceof Monster monster) {

            // Priority 2 ensures they don't override HurtByTargetGoal (Priority 1).
            // The boolean flag 'true' forces the mob to check line of sight before acquiring the target.
            monster.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(monster, EngineerCompanionEntity.class, true));
        }
    }
}