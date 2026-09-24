package com.sgt_shadow3600.engineer.network;

import com.sgt_shadow3600.engineer.Config;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class SchematicRequestHandler {

    // Simple record to transport async processing results
    private record ParseResult(boolean success, CompoundTag tag, Map<String, Integer> materials) {}

    public static void handleData(final SchematicRequestPayload payload, final IPayloadContext context) {
        // Safe to grab the player directly from the context on the network thread
        if (!(context.player() instanceof ServerPlayer player)) return;

        String inputName = payload.schematicName();
        boolean isNative = inputName.contains(":");

        if (isNative) {
            // NATIVE PATHWAY: Must run on Main Thread due to StructureTemplateManager
            context.enqueueWork(() -> {
                if (inputName.contains("entities/") || inputName.contains("villagers/") || inputName.contains("animals/")) {
                    sendFailure(player, inputName);
                    return;
                }

                boolean allowAll = Config.ALLOW_ALL_NATIVE_STRUCTURES.get();
                List<? extends String> allowedPrefixes = Config.ALLOWED_NATIVE_PREFIXES.get();
                List<? extends String> allowedSpecific = Config.ALLOWED_SPECIFIC_STRUCTURES.get();
                boolean isAllowed = allowAll || (allowedSpecific != null && allowedSpecific.contains(inputName));

                if (!isAllowed && allowedPrefixes != null) {
                    for (String prefix : allowedPrefixes) {
                        if (inputName.startsWith(prefix)) { isAllowed = true; break; }
                    }
                }

                if (!isAllowed) { sendFailure(player, inputName); return; }

                Map<String, Integer> materials = new HashMap<>();
                try {
                    ResourceLocation rl = ResourceLocation.parse(inputName.replace(".nbt", ""));
                    StructureTemplateManager manager = player.server.getStructureManager();
                    Optional<StructureTemplate> optTemplate = manager.get(rl);

                    if (optTemplate.isPresent()) {
                        CompoundTag templateTag = optTemplate.get().save(new CompoundTag());
                        if (extractMaterialsFromTag(templateTag, materials)) {
                            sendSuccess(player, inputName, materials, templateTag);
                            return;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                sendFailure(player, inputName);
            });
        } else {
            // CUSTOM PATHWAY: Heavy Disk I/O, must be offloaded to worker thread (TPS ASSASSIN DEFEATED)
            if (!Config.ALLOW_CUSTOM_SCHEMATICS.get()) {
                context.enqueueWork(() -> sendFailure(player, inputName));
                return;
            }

            Path schematicDir = player.server.getServerDirectory().resolve("engineer_schematics");
            String fileName = inputName.endsWith(".nbt") ? inputName : inputName + ".nbt";

            CompletableFuture.supplyAsync(() -> {
                Map<String, Integer> materials = new HashMap<>();
                try {
                    if (!Files.exists(schematicDir)) Files.createDirectories(schematicDir);
                    Path nbtFile = schematicDir.resolve(fileName);

                    if (Files.exists(nbtFile)) {
                        CompoundTag tag = NbtIo.readCompressed(nbtFile, NbtAccounter.unlimitedHeap());
                        if (extractMaterialsFromTag(tag, materials)) {
                            return new ParseResult(true, tag, materials);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return new ParseResult(false, null, materials);
            }).thenAcceptAsync(result -> {
                // Hop back to the main thread just to send the network payload safely
                context.enqueueWork(() -> {
                    if (result.success) {
                        sendSuccess(player, inputName, result.materials, result.tag);
                    } else {
                        sendFailure(player, inputName);
                    }
                });
            });
        }
    }

    private static void sendFailure(ServerPlayer player, String name) {
        PacketDistributor.sendToPlayer(player, new SchematicResponsePayload(false, name, new HashMap<>(), new CompoundTag()));
    }

    private static void sendSuccess(ServerPlayer player, String name, Map<String, Integer> materials, CompoundTag fullTag) {
        // Strip data to minimize packet size over the network (keeps bandwidth usage low)
        CompoundTag stripped = new CompoundTag();
        if (fullTag.contains("palette") && fullTag.contains("blocks")) {
            stripped.put("palette", fullTag.getList("palette", Tag.TAG_COMPOUND));
            stripped.put("blocks", fullTag.getList("blocks", Tag.TAG_COMPOUND));
        }
        PacketDistributor.sendToPlayer(player, new SchematicResponsePayload(true, name, materials, stripped));
    }

    private static boolean extractMaterialsFromTag(CompoundTag tag, Map<String, Integer> materials) {
        if (!tag.contains("palette") || !tag.contains("blocks")) return false;

        ListTag paletteTag = tag.getList("palette", Tag.TAG_COMPOUND);
        String[] paletteDict = new String[paletteTag.size()];

        for (int i = 0; i < paletteTag.size(); i++) {
            CompoundTag blockStateTag = paletteTag.getCompound(i);
            paletteDict[i] = blockStateTag.getString("Name");
        }

        ListTag blocksTag = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocksTag.size(); i++) {
            CompoundTag blockTag = blocksTag.getCompound(i);
            int stateIndex = blockTag.getInt("state");

            if (stateIndex >= 0 && stateIndex < paletteDict.length) {
                String blockName = paletteDict[stateIndex];
                if (!blockName.contains("air") && !blockName.contains("jigsaw")) {
                    materials.put(blockName, materials.getOrDefault(blockName, 0) + 1);
                }
            }
        }
        return true;
    }
}