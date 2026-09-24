package com.sgt_shadow3600.engineer.entity.ai.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.HashMap;
import java.util.Map;

public class JobDefinition {
    public BlockPos site;
    public int direction;
    public int depth;
    public int width;
    public int shape;
    public int length;
    public int height;
    public boolean veinmine;

    // Building specific variables
    public boolean clearSite;
    public boolean isAdvanced;
    public String schematicName;
    public Map<String, String> blockSubstitutions;

    // Legacy/Digging Constructor
    public JobDefinition(BlockPos site, int direction, int depth, int width, int shape, int length, int height, boolean veinmine) {
        this(site, direction, depth, width, shape, length, height, veinmine, false, false, "", new HashMap<>());
    }

    // Full Construction
    public JobDefinition(BlockPos site, int direction, int depth, int width, int shape, int length, int height, boolean veinmine, boolean clearSite, boolean isAdvanced, String schematicName, Map<String, String> blockSubstitutions) {
        this.site = site;
        this.direction = direction;
        this.depth = depth;
        this.width = width;
        this.shape = shape;
        this.length = length;
        this.height = height;
        this.veinmine = veinmine;
        this.clearSite = clearSite;
        this.isAdvanced = isAdvanced;
        this.schematicName = schematicName != null ? schematicName : "";
        this.blockSubstitutions = blockSubstitutions != null ? blockSubstitutions : new HashMap<>();
    }

    public static JobDefinition fromNBT(CompoundTag tag) {
        Map<String, String> subs = new HashMap<>();
        if (tag.contains("substitutions", Tag.TAG_LIST)) {
            ListTag list = tag.getList("substitutions", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag subTag = list.getCompound(i);
                subs.put(subTag.getString("original"), subTag.getString("replacement"));
            }
        }

        return new JobDefinition(
                new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")),
                tag.getInt("dir"),
                tag.getInt("depth"),
                tag.getInt("width"),
                tag.getInt("shape"),
                tag.getInt("length"),
                tag.getInt("height"),
                tag.getBoolean("veinmine"),
                tag.getBoolean("clearSite"),
                tag.getBoolean("isAdvanced"),
                tag.getString("schematicName"),
                subs
        );
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", site.getX());
        tag.putInt("y", site.getY());
        tag.putInt("z", site.getZ());
        tag.putInt("dir", direction);
        tag.putInt("depth", depth);
        tag.putInt("width", width);
        tag.putInt("shape", shape);
        tag.putInt("length", length);
        tag.putInt("height", height);

        // FIX: Changed from getBoolean to putBoolean
        tag.putBoolean("veinmine", veinmine);
        tag.putBoolean("clearSite", clearSite);
        tag.putBoolean("isAdvanced", isAdvanced);
        tag.putString("schematicName", schematicName);

        ListTag list = new ListTag();
        for (Map.Entry<String, String> entry : blockSubstitutions.entrySet()) {
            CompoundTag subTag = new CompoundTag();
            subTag.putString("original", entry.getKey());
            subTag.putString("replacement", entry.getValue());
            list.add(subTag);
        }
        tag.put("substitutions", list);

        return tag;
    }
}