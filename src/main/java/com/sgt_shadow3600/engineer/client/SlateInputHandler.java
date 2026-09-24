package com.sgt_shadow3600.engineer.client;

import com.sgt_shadow3600.engineer.EngineerCompanion;
import com.sgt_shadow3600.engineer.item.CommandSlateItem;
import com.sgt_shadow3600.engineer.network.RotateAnchorPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = EngineerCompanion.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class SlateInputHandler {

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player != null && player.isCrouching() && player.getMainHandItem().getItem() instanceof CommandSlateItem) {
            double delta = event.getScrollDeltaY();
            if (delta != 0) {
                PacketDistributor.sendToServer(new RotateAnchorPayload(delta > 0 ? 1 : -1));
                event.setCanceled(true);
            }
        }
    }
}