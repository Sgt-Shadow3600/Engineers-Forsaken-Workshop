package com.sgt_shadow3600.engineer.entity.ai.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.*;

public class BlueprintSession {
    public record PendingBlock(BlockPos pos, BlockState state) {}

    private final Queue<PendingBlock> buildQueue = new LinkedList<>();
    private int totalBlocks = 0;

    private int boundWidth = 0;
    private int boundHeight = 0;
    private int boundLength = 0;

    public BlueprintSession(ServerLevel level, JobDefinition job, int skipCount) {
        List<StructureTemplate.StructureBlockInfo> rawBlocks = new ArrayList<>();

        if (job.isAdvanced && !job.schematicName.isEmpty()) {
            extractAdvancedBlocks(level, job, rawBlocks);
        } else {
            extractSimpleBlocks(job, rawBlocks);
        }

        if (rawBlocks.isEmpty()) return;

        Direction forwardDir = Direction.from3DDataValue(job.direction);
        if (forwardDir.getAxis() == Direction.Axis.Y) forwardDir = Direction.NORTH;
        Direction rightDir = forwardDir.getClockWise();
        Rotation rot = getRotationFromDirection(forwardDir);

        Map<Block, Block> subMap = new HashMap<>();
        for (Map.Entry<String, String> entry : job.blockSubstitutions.entrySet()) {
            ResourceLocation origRl = ResourceLocation.tryParse(entry.getKey());
            ResourceLocation replRl = ResourceLocation.tryParse(entry.getValue());
            if (origRl != null && replRl != null) {
                Block orig = BuiltInRegistries.BLOCK.get(origRl);
                Block repl = BuiltInRegistries.BLOCK.get(replRl);
                if (orig != Blocks.AIR && repl != Blocks.AIR) subMap.put(orig, repl);
            }
        }

        // 1. The Universal Normalization Matrix
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (var info : rawBlocks) {
            if (info.pos().getX() < minX) minX = info.pos().getX();
            if (info.pos().getY() < minY) minY = info.pos().getY();
            if (info.pos().getZ() < minZ) minZ = info.pos().getZ();
            if (info.pos().getX() > maxX) maxX = info.pos().getX();
            if (info.pos().getY() > maxY) maxY = info.pos().getY();
            if (info.pos().getZ() > maxZ) maxZ = info.pos().getZ();
        }
        int width = maxX - minX;

        this.boundWidth = (maxX - minX) + 1;
        this.boundHeight = (maxY - minY) + 1;
        this.boundLength = (maxZ - minZ) + 1;

        List<PendingBlock> normalizedList = new ArrayList<>();

        for (var info : rawBlocks) {
            int nx = info.pos().getX() - minX;
            int ny = info.pos().getY() - minY;
            int nz = info.pos().getZ() - minZ;

            int cx = nx - (width / 2);
            int cz = nz + 1;

            BlockPos worldPos = job.site.relative(rightDir, cx).relative(forwardDir, cz).above(ny);

            BlockState state = info.state();
            if (subMap.containsKey(state.getBlock())) state = subMap.get(state.getBlock()).defaultBlockState();
            state = state.rotate(rot);

            normalizedList.add(new PendingBlock(worldPos, state));
        }

        TreeMap<Integer, List<PendingBlock>> layers = new TreeMap<>();
        for (PendingBlock pb : normalizedList) {
            layers.computeIfAbsent(pb.pos().getY(), k -> new ArrayList<>()).add(pb);
        }

        BlockPos currentTrackerPos = job.site;

        for (Map.Entry<Integer, List<PendingBlock>> entry : layers.entrySet()) {
            List<PendingBlock> layerBlocks = entry.getValue();
            while (!layerBlocks.isEmpty()) {
                PendingBlock closest = null;
                double minFoundDist = Double.MAX_VALUE;
                for (PendingBlock pb : layerBlocks) {
                    double dist = pb.pos().distSqr(currentTrackerPos);
                    if (dist < minFoundDist) {
                        minFoundDist = dist;
                        closest = pb;
                    }
                }
                buildQueue.add(closest);
                layerBlocks.remove(closest);
                currentTrackerPos = closest.pos();
                totalBlocks++;
            }
        }

        for (int i = 0; i < skipCount; i++) buildQueue.poll();
    }

    public Queue<PendingBlock> getQueue() { return buildQueue; }
    public int getTotalBlocks() { return totalBlocks; }
    public int getBoundWidth() { return boundWidth; }
    public int getBoundHeight() { return boundHeight; }
    public int getBoundLength() { return boundLength; }

    public Map<Item, Integer> getNextRequiredMaterials(int limit) {
        Map<Item, Integer> needed = new HashMap<>();
        int count = 0;
        for (PendingBlock pb : buildQueue) {
            if (count >= limit) break;
            Item item = pb.state().getBlock().asItem();
            if (item != net.minecraft.world.item.Items.AIR) {
                needed.put(item, needed.getOrDefault(item, 0) + 1);
            }
            count++;
        }
        return needed;
    }

    private void extractAdvancedBlocks(ServerLevel level, JobDefinition job, List<StructureTemplate.StructureBlockInfo> rawBlocks) {
        StructureTemplateManager manager = level.getServer().getStructureManager();
        ResourceLocation rl = ResourceLocation.tryParse(job.schematicName);
        if (rl == null) return;
        Optional<StructureTemplate> optTemplate = manager.get(rl);
        if (optTemplate.isEmpty()) return;

        CompoundTag tag = optTemplate.get().save(new CompoundTag());

        ListTag paletteTag = null;
        if (tag.contains("palette", Tag.TAG_LIST)) paletteTag = tag.getList("palette", Tag.TAG_COMPOUND);
        else if (tag.contains("palettes", Tag.TAG_LIST)) {
            ListTag palettesList = tag.getList("palettes", Tag.TAG_LIST);
            if (!palettesList.isEmpty()) paletteTag = palettesList.getList(0);
        }

        if (paletteTag != null && tag.contains("blocks", Tag.TAG_LIST)) {
            BlockState[] paletteDict = new BlockState[paletteTag.size()];
            net.minecraft.core.HolderGetter<Block> blockGetter = level.holderLookup(net.minecraft.core.registries.Registries.BLOCK);
            for (int i = 0; i < paletteTag.size(); i++) paletteDict[i] = net.minecraft.nbt.NbtUtils.readBlockState(blockGetter, paletteTag.getCompound(i));

            ListTag blocksTag = tag.getList("blocks", Tag.TAG_COMPOUND);
            for (int i = 0; i < blocksTag.size(); i++) {
                CompoundTag blockTag = blocksTag.getCompound(i);
                ListTag posTag = blockTag.getList("pos", Tag.TAG_INT);
                BlockPos pos = new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));
                int stateIndex = blockTag.getInt("state");
                if (stateIndex >= 0 && stateIndex < paletteDict.length) {
                    BlockState state = paletteDict[stateIndex];
                    if (!state.isAir() && !state.is(Blocks.STRUCTURE_VOID) && !state.is(Blocks.JIGSAW)) {
                        rawBlocks.add(new StructureTemplate.StructureBlockInfo(pos, state, null));
                    }
                }
            }
        }
    }

    private void extractSimpleBlocks(JobDefinition job, List<StructureTemplate.StructureBlockInfo> rawBlocks) {
        BlockState defaultBlock = Blocks.COBBLESTONE.defaultBlockState();
        if (!job.blockSubstitutions.isEmpty()) {
            String repl = job.blockSubstitutions.values().iterator().next();
            ResourceLocation rl = ResourceLocation.tryParse(repl);
            Block b = rl != null ? BuiltInRegistries.BLOCK.get(rl) : null;
            if (b != null && b != Blocks.AIR) defaultBlock = b.defaultBlockState();
        }
        List<BlockPos> shapePositions = generateSimpleShapeOffsets(job.shape, job.width, job.length, job.height, job.depth);
        for (BlockPos p : shapePositions) rawBlocks.add(new StructureTemplate.StructureBlockInfo(p, defaultBlock, null));
    }

    public static List<BlockPos> generateSimpleShapeOffsets(int shape, int w, int l, int h, int fillMode) {
        Set<BlockPos> blocks = new HashSet<>();
        if (shape == 10) {
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
        } else if (shape == 11) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    blocks.add(new BlockPos(x, y, y));
                    if (fillMode == 0) {
                        for(int fillY = 0; fillY < y; fillY++) blocks.add(new BlockPos(x, fillY, y));
                    }
                }
            }
        } else if (shape == 12) {
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
        } else if (shape == 13) {
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
        } else if (shape == 14) {
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

    private Rotation getRotationFromDirection(Direction dir) {
        return switch (dir) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }
}