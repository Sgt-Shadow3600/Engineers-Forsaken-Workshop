package com.sgt_shadow3600.engineer.network;

import com.sgt_shadow3600.engineer.client.CommandSlateScreen;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class RadarPongHandler {

    public static void handleData(final RadarPongPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            // Feeds the live telemetry directly into the Map Screen!
            CommandSlateScreen.currentRadarData = payload;
        });
    }
}