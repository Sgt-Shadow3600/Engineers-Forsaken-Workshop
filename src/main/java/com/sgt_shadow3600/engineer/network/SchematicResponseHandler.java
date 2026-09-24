package com.sgt_shadow3600.engineer.network;

import com.sgt_shadow3600.engineer.client.BuildConfigScreen;
import com.sgt_shadow3600.engineer.client.ClientSchematicCache;
import com.sgt_shadow3600.engineer.client.SlateRenderHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class SchematicResponseHandler {

    public static void handleData(final SchematicResponsePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {

            // Release the packet lock so the renderer can try again if it failed
            SlateRenderHandler.PENDING_REQUESTS.remove(payload.schematicName());

            if (payload.success() && payload.structureData() != null) {
                // Instantly inject the streamed 3D geometry directly into the Client Renderer!
                ClientSchematicCache.cacheFromNetwork(payload.schematicName(), payload.structureData());
            }

            // Still safely update the UI if the player has it open
            if (BuildConfigScreen.instance != null) {
                // MAKE SURE this line says payload.materials() and NOT payload.materialsLast()
                BuildConfigScreen.instance.receiveSchematicData(payload.success(), payload.materials());
            }
        });
    }
}