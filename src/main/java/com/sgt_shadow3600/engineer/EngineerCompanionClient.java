package com.sgt_shadow3600.engineer;

import com.sgt_shadow3600.engineer.client.EchoSentinelRenderer;
import com.sgt_shadow3600.engineer.client.EngineerCompanionScreen;
import com.sgt_shadow3600.engineer.client.EngineerRenderer;
import com.sgt_shadow3600.engineer.client.TerminalScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = EngineerCompanion.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class EngineerCompanionClient {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Maps the logical entity to the graphical renderer
        event.registerEntityRenderer(EngineerCompanion.ENGINEER_NPC.get(), EngineerRenderer::new);
        event.registerEntityRenderer(EngineerCompanion.ROGUE_ENGINEER_NPC.get(), EngineerRenderer::new);

        // NEW: Registering the Echo Sentinel Turret Renderer
        event.registerEntityRenderer(EngineerCompanion.ECHO_SENTINEL_ENTITY.get(), EchoSentinelRenderer::new);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(com.sgt_shadow3600.engineer.inventory.ModMenus.TERMINAL_MENU.get(), TerminalScreen::new);
        event.register(com.sgt_shadow3600.engineer.inventory.ModMenus.COMPANION_MENU.get(), EngineerCompanionScreen::new);
    }
}