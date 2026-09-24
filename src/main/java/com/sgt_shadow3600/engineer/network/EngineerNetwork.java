package com.sgt_shadow3600.engineer.network;

import com.sgt_shadow3600.engineer.EngineerCompanion;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = EngineerCompanion.MODID, bus = EventBusSubscriber.Bus.MOD)
public class EngineerNetwork {

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        // Enforce protocol versioning. Increment this if payload structures change in updates!
        final PayloadRegistrar registrar = event.registrar("1.0");

        // THE 3 NEW CORE COMMAND PAYLOADS
        registrar.playToServer(NpcActionPayload.TYPE, NpcActionPayload.STREAM_CODEC, NpcActionHandler::handleData);
        registrar.playToServer(SaveDigConfigPayload.TYPE, SaveDigConfigPayload.STREAM_CODEC, SaveDigConfigHandler::handleData);
        registrar.playToServer(SaveBuildConfigPayload.TYPE, SaveBuildConfigPayload.STREAM_CODEC, SaveBuildConfigHandler::handleData);

        // SCHEMATIC SYNC PAYLOADS
        registrar.playToServer(SchematicRequestPayload.TYPE, SchematicRequestPayload.STREAM_CODEC, SchematicRequestHandler::handleData);
        registrar.playToClient(SchematicResponsePayload.TYPE, SchematicResponsePayload.STREAM_CODEC, SchematicResponseHandler::handleData);
        registrar.playToServer(SchematicListRequestPayload.TYPE, SchematicListRequestPayload.STREAM_CODEC, SchematicListRequestHandler::handleData);
        registrar.playToClient(SchematicListResponsePayload.TYPE, SchematicListResponsePayload.STREAM_CODEC, SchematicListResponseHandler::handleData);

        // TELEMETRY RADAR PAYLOADS
        registrar.playToServer(RadarPingPayload.TYPE, RadarPingPayload.STREAM_CODEC, RadarPingHandler::handleData);
        registrar.playToClient(RadarPongPayload.TYPE, RadarPongPayload.STREAM_CODEC, RadarPongHandler::handleData);

        // ANCHOR ROTATION
        registrar.playToServer(RotateAnchorPayload.TYPE, RotateAnchorPayload.STREAM_CODEC, RotateAnchorHandler::handleData);
    }
}