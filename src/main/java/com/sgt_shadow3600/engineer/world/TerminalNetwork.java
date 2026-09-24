package com.sgt_shadow3600.engineer.world;

import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.HolderLookup;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TerminalNetwork extends SavedData {
    // Tracks exactly ONE active terminal per player
    private final Map<UUID, GlobalPos> activeTerminals = new HashMap<>();

    public static TerminalNetwork get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TerminalNetwork::new, TerminalNetwork::load, null),
                "engineer_terminal_network"
        );
    }

    public TerminalNetwork() {}

    public boolean hasActiveTerminal(UUID playerId) {
        return activeTerminals.containsKey(playerId);
    }

    public GlobalPos getActiveTerminal(UUID playerId) {
        return activeTerminals.get(playerId);
    }

    public void setActiveTerminal(UUID playerId, GlobalPos pos) {
        activeTerminals.put(playerId, pos);
        setDirty();
    }

    public void removeTerminal(UUID playerId, GlobalPos pos) {
        if (pos.equals(activeTerminals.get(playerId))) {
            activeTerminals.remove(playerId);
            setDirty();
        }
    }

    public static TerminalNetwork load(CompoundTag tag, HolderLookup.Provider registries) {
        TerminalNetwork data = new TerminalNetwork();
        if (tag.contains("ActiveTerminals")) {
            CompoundTag playersTag = tag.getCompound("ActiveTerminals");
            for (String key : playersTag.getAllKeys()) {
                try {
                    UUID uuid = UUID.fromString(key);
                    GlobalPos.CODEC.parse(NbtOps.INSTANCE, playersTag.get(key)).result().ifPresent(pos -> {
                        data.activeTerminals.put(uuid, pos);
                    });
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag playersTag = new CompoundTag();
        for (Map.Entry<UUID, GlobalPos> entry : activeTerminals.entrySet()) {
            GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, entry.getValue()).result().ifPresent(nbt -> {
                playersTag.put(entry.getKey().toString(), nbt);
            });
        }
        tag.put("ActiveTerminals", playersTag);
        return tag;
    }
}