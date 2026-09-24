package com.sgt_shadow3600.engineer.network;

import com.sgt_shadow3600.engineer.Config;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SchematicListRequestHandler {

    public static void handleData(final SchematicListRequestPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            List<String> available = new ArrayList<>();

            // 1. Local Custom Schematics
            if (Config.ALLOW_CUSTOM_SCHEMATICS.get()) {
                Path schematicDir = player.server.getServerDirectory().resolve("engineer_schematics");
                if (Files.exists(schematicDir) && Files.isDirectory(schematicDir)) {
                    File[] files = schematicDir.toFile().listFiles((dir, name) -> name.endsWith(".nbt"));
                    if (files != null) {
                        for (File f : files) {
                            available.add(f.getName().replace(".nbt", ""));
                        }
                    }
                }
            }

            // 2. Native Structures (Filtered & Whitelisted)
            boolean allowAll = Config.ALLOW_ALL_NATIVE_STRUCTURES.get();
            List<? extends String> allowedPrefixes = Config.ALLOWED_NATIVE_PREFIXES.get();
            List<? extends String> allowedSpecific = Config.ALLOWED_SPECIFIC_STRUCTURES.get();

            player.server.getStructureManager().listTemplates().forEach(rl -> {
                String path = rl.toString();

                // HARD FILTER: Prevent any Vanilla mob, animal, or villager NBT from cluttering the UI
                if (path.contains("entities/") || path.contains("villagers/") || path.contains("animals/")) return;

                if (allowAll) {
                    available.add(path);
                } else {
                    if (allowedSpecific != null && allowedSpecific.contains(path)) {
                        available.add(path);
                    } else if (allowedPrefixes != null) {
                        for (String prefix : allowedPrefixes) {
                            if (path.startsWith(prefix)) {
                                available.add(path);
                                break;
                            }
                        }
                    }
                }
            });

            // Alphabetical sort for a clean UI
            available.sort(String::compareToIgnoreCase);

            PacketDistributor.sendToPlayer(player, new SchematicListResponsePayload(available));
        });
    }
}