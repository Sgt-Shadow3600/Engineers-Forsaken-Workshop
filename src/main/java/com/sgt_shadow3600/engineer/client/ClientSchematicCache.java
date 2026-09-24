package com.sgt_shadow3600.engineer.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ClientSchematicCache {
    public record BlockPosState(BlockPos pos, BlockState state) {}

    private static final int MAX_CACHE_SIZE = 10;
    private static final Map<String, List<BlockPosState>> CACHE = new java.util.LinkedHashMap<String, List<BlockPosState>>(MAX_CACHE_SIZE + 1, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<BlockPosState>> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };

    public static boolean hasCache(String name) {
        return CACHE.containsKey(name);
    }

    public static List<BlockPosState> getOrLoadAdvanced(String schematicName) {
        return CACHE.getOrDefault(schematicName, new ArrayList<>());
    }

    public static void cacheFromNetwork(String schematicName, CompoundTag tag) {
        List<BlockPosState> blocks = new ArrayList<>();
        try {
            if (tag.contains("palette") && tag.contains("blocks")) {
                ListTag paletteTag = tag.getList("palette", Tag.TAG_COMPOUND);
                BlockState[] paletteDict = new BlockState[paletteTag.size()];

                for (int i = 0; i < paletteTag.size(); i++) {
                    CompoundTag blockStateTag = paletteTag.getCompound(i);
                    ResourceLocation blockRl = ResourceLocation.tryParse(blockStateTag.getString("Name"));
                    Block block = blockRl != null ? BuiltInRegistries.BLOCK.get(blockRl) : null;
                    paletteDict[i] = block != null ? block.defaultBlockState() : Blocks.AIR.defaultBlockState();
                }

                ListTag blocksTag = tag.getList("blocks", Tag.TAG_COMPOUND);
                Map<BlockPos, BlockState> rawBlocks = new HashMap<>();

                for (int i = 0; i < blocksTag.size(); i++) {
                    CompoundTag blockTag = blocksTag.getCompound(i);
                    ListTag posTag = blockTag.getList("pos", Tag.TAG_INT);
                    BlockPos pos = new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));
                    int stateIndex = blockTag.getInt("state");

                    if (stateIndex >= 0 && stateIndex < paletteDict.length) {
                        BlockState state = paletteDict[stateIndex];
                        if (!state.isAir() && !state.is(Blocks.JIGSAW) && !state.is(Blocks.STRUCTURE_VOID)) {
                            rawBlocks.put(pos, state);
                        }
                    }
                }

                for (Map.Entry<BlockPos, BlockState> entry : rawBlocks.entrySet()) {
                    BlockPos p = entry.getKey();
                    boolean exposed = false;
                    for (Direction d : Direction.values()) {
                        if (!rawBlocks.containsKey(p.relative(d))) {
                            exposed = true;
                            break;
                        }
                    }
                    if (exposed) {
                        blocks.add(new BlockPosState(p, entry.getValue()));
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }

        CACHE.put(schematicName, blocks);
    }

    public static List<BlockPosState> getOrLoadSimple(int shape, int w, int l, int h, int fillMode, String blockName) {
        String key = shape + "_" + w + "_" + l + "_" + h + "_" + fillMode + "_" + blockName;
        if (CACHE.containsKey(key)) return CACHE.get(key);

        List<BlockPosState> blocks = new ArrayList<>();
        ResourceLocation rl = ResourceLocation.tryParse(blockName);
        Block block = rl != null ? BuiltInRegistries.BLOCK.get(rl) : null;
        BlockState state = block != null ? block.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();

        if (shape >= 10) {
            List<BlockPos> rawBlocks = generateSimpleShapeOffsets(shape, w, l, h, fillMode);
            for(BlockPos p : rawBlocks) {
                blocks.add(new BlockPosState(p, state));
            }
            CACHE.put(key, blocks);
            return blocks;
        }

        // Legacy Shape Fallbacks (Digging)
        Set<BlockPos> solidBlocks = new HashSet<>();
        int minX, maxX, minZ, maxZ;
        if (shape == 2 || shape == 3) {
            minX = -w; maxX = w;
            minZ = -w; maxZ = w;
        } else if (shape == 0) {
            minX = -(l - 1) / 2; maxX = l / 2;
            minZ = 0; maxZ = w - 1;
        } else {
            minX = -(l - 1) / 2; maxX = l / 2;
            minZ = -(w - 1) / 2; maxZ = w / 2;
        }

        double cx = minX + (maxX - minX) / 2.0;
        double cz = minZ + (maxZ - minZ) / 2.0;

        for (int x = minX; x <= maxX; x++) {
            for (int y = 0; y < h; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean keep = false;

                    if (shape == 0 || shape == 1) {
                        keep = true;
                    } else if (shape == 2) {
                        if ((x*x) + (z*z) <= w*w) keep = true;
                    } else if (shape == 3) {
                        double dy = ((double)y / h) * w;
                        if ((x*x) + (dy*dy) + (z*z) <= w*w) keep = true;
                    } else if (shape == 4) {
                        double distFromCenterX = Math.abs(x - cx);
                        double distFromCenterZ = Math.abs(z - cz);
                        double maxDistX = (maxX - minX) / 2.0;
                        double maxDistZ = (maxZ - minZ) / 2.0;

                        double pct = (double)y / h;
                        double currentMaxDistX = maxDistX - (pct * maxDistX);
                        double currentMaxDistZ = maxDistZ - (pct * maxDistZ);

                        if (distFromCenterX <= currentMaxDistX + 0.1 && distFromCenterZ <= currentMaxDistZ + 0.1) keep = true;
                    }

                    if (keep) solidBlocks.add(new BlockPos(x, y, z));
                }
            }
        }

        for (BlockPos pos : solidBlocks) {
            boolean exposedXZ = !solidBlocks.contains(pos.north()) || !solidBlocks.contains(pos.south()) ||
                    !solidBlocks.contains(pos.east()) || !solidBlocks.contains(pos.west());
            boolean exposedY = !solidBlocks.contains(pos.above()) || !solidBlocks.contains(pos.below());

            boolean keep = false;
            if (fillMode == 0 || fillMode == 1) {
                keep = exposedXZ || exposedY;
            } else if (fillMode == 2) {
                keep = exposedXZ;
            }

            if (keep) blocks.add(new BlockPosState(pos, state));
        }

        CACHE.put(key, blocks);
        return blocks;
    }

    // ARCHITECT MATRIX: Unifies the geometry math for both Client Holograms and Server AI Placements
    public static List<BlockPos> generateSimpleShapeOffsets(int shape, int w, int l, int h, int fillMode) {
        Set<BlockPos> blocks = new HashSet<>();

        if (shape == 10) { // Box / Platform
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    for (int z = 0; z < l; z++) {
                        boolean isEdgeX = (x == 0 || x == w - 1);
                        boolean isEdgeY = (y == 0 || y == h - 1);
                        boolean isEdgeZ = (z == 0 || z == l - 1);
                        boolean keep = (fillMode == 0) || (fillMode == 1 && (isEdgeX || isEdgeY || isEdgeZ)) || (fillMode == 2 && ((isEdgeX && isEdgeY) || (isEdgeY && isEdgeZ) || (isEdgeX && isEdgeZ)));
                        if (keep) blocks.add(new BlockPos(x, y, z));
                    }
                }
            }
        } else if (shape == 11) { // Stairs
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    blocks.add(new BlockPos(x, y, y));
                    if (fillMode == 0) { // Solid
                        for(int fillY = 0; fillY < y; fillY++) blocks.add(new BlockPos(x, fillY, y));
                    }
                }
            }
        } else if (shape == 12) { // Cylinder
            int r = w;
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (x*x + z*z <= r*r) {
                        for (int y = 0; y < h; y++) {
                            boolean isEdgeXZ = (x*x + z*z >= (r-1)*(r-1));
                            boolean isEdgeY = (y == 0 || y == h - 1);
                            boolean keep = (fillMode == 0) || (fillMode == 1 && (isEdgeXZ || isEdgeY)) || (fillMode == 2 && (isEdgeXZ && isEdgeY));
                            if (keep) blocks.add(new BlockPos(x, y, z));
                        }
                    }
                }
            }
        } else if (shape == 13) { // Dome
            int r = w;
            for (int x = -r; x <= r; x++) {
                for (int y = 0; y <= r; y++) {
                    for (int z = -r; z <= r; z++) {
                        if (x*x + y*y + z*z <= r*r) {
                            boolean isEdge = (x*x + y*y + z*z >= (r-1.5)*(r-1.5));
                            if (fillMode == 0 || isEdge) blocks.add(new BlockPos(x, y, z));
                        }
                    }
                }
            }
        } else if (shape == 14) { // Pyramid
            int r = w;
            for (int y = 0; y <= r; y++) {
                int currentR = r - y;
                for (int x = -currentR; x <= currentR; x++) {
                    for (int z = -currentR; z <= currentR; z++) {
                        boolean isEdge = (Math.abs(x) == currentR || Math.abs(z) == currentR || y == 0);
                        if (fillMode == 0 || isEdge) blocks.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return new ArrayList<>(blocks);
    }
}