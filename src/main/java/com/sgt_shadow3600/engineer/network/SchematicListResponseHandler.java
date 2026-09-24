package com.sgt_shadow3600.engineer.network;

import com.sgt_shadow3600.engineer.client.BuildConfigScreen;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class SchematicListResponseHandler {

    public static void handleData(final SchematicListResponsePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (BuildConfigScreen.instance != null) {
                BuildConfigScreen.instance.receiveSchematicList(payload.schematics());
            }
        });
    }
}