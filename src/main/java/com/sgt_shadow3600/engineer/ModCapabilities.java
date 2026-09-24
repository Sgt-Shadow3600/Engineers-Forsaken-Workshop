package com.sgt_shadow3600.engineer;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public class ModCapabilities {

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Properly registers the Terminal's ItemHandler capability for automation integration (Hoppers, Pipes)
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                EngineerCompanion.TERMINAL_BE.get(),
                (blockEntity, side) -> blockEntity.inventory
        );
    }
}