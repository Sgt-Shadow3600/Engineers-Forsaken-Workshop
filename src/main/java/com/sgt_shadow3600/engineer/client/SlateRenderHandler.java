package com.sgt_shadow3600.engineer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sgt_shadow3600.engineer.EngineerCompanion;
import com.sgt_shadow3600.engineer.ModDataComponents;
import com.sgt_shadow3600.engineer.block.TerminalBlock;
import com.sgt_shadow3600.engineer.item.CommandSlateItem;
import com.sgt_shadow3600.engineer.network.SchematicRequestPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = EngineerCompanion.MODID, value = Dist.CLIENT)
public class SlateRenderHandler {

    public static final Set<String> PENDING_REQUESTS = new HashSet<>();

    public static class GhostVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        public GhostVertexConsumer(VertexConsumer delegate) { this.delegate = delegate; }
        @Override public VertexConsumer addVertex(float x, float y, float z) { delegate.addVertex(x, y, z); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { delegate.setColor(r, g, b, (a * 160) / 255); return this; }
        @Override public VertexConsumer setUv(float u, float v) { delegate.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { delegate.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { delegate.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { delegate.setNormal(x, y, z); return this; }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof CommandSlateItem)) return;

        List<GlobalPos> terminals = held.getOrDefault(ModDataComponents.BOUND_TERMINALS.get(), List.of());
        if (terminals.isEmpty()) return;

        CustomData data = held.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        boolean isAnchored = tag.getBoolean("IsAnchored");

        if (!isAnchored && !player.isCrouching()) return;

        BlockPos targetPos;
        Direction rawDir;
        Direction rightDir;

        if (isAnchored) {
            targetPos = new BlockPos(tag.getInt("AnchorX"), tag.getInt("AnchorY"), tag.getInt("AnchorZ"));
            rawDir = Direction.from3DDataValue(tag.getInt("AnchorDir"));
            rightDir = Direction.from3DDataValue(tag.getInt("AnchorRightDir"));

            // ARCHITECT FIX: Safely apply the Slate's internal scroll rotation to the Anchor matrices.
            int rots = tag.getInt("StructureRotation");
            if (rawDir.getAxis() != Direction.Axis.Y) {
                for (int i = 0; i < rots; i++) {
                    rawDir = rawDir.getClockWise();
                    rightDir = rightDir.getClockWise();
                }
            } else {
                for (int i = 0; i < rots; i++) {
                    rightDir = rightDir.getClockWise();
                }
            }
        } else {
            HitResult hit = player.pick(20.0D, event.getPartialTick().getGameTimeDeltaTicks(), false);
            if (hit.getType() == HitResult.Type.BLOCK) {
                targetPos = ((BlockHitResult) hit).getBlockPos();
            } else {
                return;
            }

            if (player.level().getBlockState(targetPos).getBlock() instanceof TerminalBlock) return;

            if (player.getXRot() > 45.0F) rawDir = Direction.DOWN;
            else if (player.getXRot() < -45.0F) rawDir = Direction.UP;
            else rawDir = player.getDirection();

            rightDir = (rawDir.getAxis() == Direction.Axis.Y) ? player.getDirection().getClockWise() : rawDir.getClockWise();
        }

        if (held.has(ModDataComponents.PENDING_BUILD_CONFIG.get())) {
            renderBuildHologram(event, mc, held.get(ModDataComponents.PENDING_BUILD_CONFIG.get()), targetPos, rawDir, rightDir, isAnchored, player);
        } else if (held.has(ModDataComponents.PENDING_DIG_CONFIG.get())) {
            renderDigHologram(event, held.get(ModDataComponents.PENDING_DIG_CONFIG.get()), targetPos, rawDir, rightDir, isAnchored, player);
        }
    }

    private static void renderBuildHologram(RenderLevelStageEvent event, Minecraft mc, CompoundTag buildConfig, BlockPos targetPos, Direction pushDir, Direction realRightDir, boolean isAnchored, Player player) {

        // ARCHITECT FIX: Enforce horizontal constraints for Building projections.
        // If anchored to the ceiling or floor, derive forward facing from the spinning right vector.
        if (pushDir.getAxis() == Direction.Axis.Y) {
            pushDir = realRightDir.getCounterClockWise();
        }

        boolean isAdvanced = buildConfig.getBoolean("isAdvanced");
        List<ClientSchematicCache.BlockPosState> blocks;
        int shape = buildConfig.getInt("shape");

        if (isAdvanced || shape == 99) {
            String name = buildConfig.getString("schematicName");
            blocks = ClientSchematicCache.getOrLoadAdvanced(name);
            if (blocks.isEmpty() && !ClientSchematicCache.hasCache(name)) {
                if (!PENDING_REQUESTS.contains(name)) {
                    PENDING_REQUESTS.add(name);
                    PacketDistributor.sendToServer(new SchematicRequestPayload(name));
                }
                return;
            }
        } else {
            blocks = ClientSchematicCache.getOrLoadSimple(
                    shape, buildConfig.getInt("width"),
                    buildConfig.getInt("length"), buildConfig.getInt("height"),
                    buildConfig.getInt("fillMode"), buildConfig.getString("blockName")
            );
        }

        if (blocks.isEmpty()) return;

        PoseStack poseStack = event.getPoseStack();
        VertexConsumer rawBuffer = mc.renderBuffers().bufferSource().getBuffer(RenderType.translucent());
        VertexConsumer ghostBuffer = new GhostVertexConsumer(rawBuffer);
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();

        net.minecraft.world.level.block.Rotation rot = net.minecraft.world.level.block.Rotation.NONE;
        if (pushDir == Direction.EAST) rot = net.minecraft.world.level.block.Rotation.CLOCKWISE_90;
        else if (pushDir == Direction.SOUTH) rot = net.minecraft.world.level.block.Rotation.CLOCKWISE_180;
        else if (pushDir == Direction.WEST) rot = net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90;

        int offX = buildConfig.getInt("offsetX");
        int offY = buildConfig.getInt("offsetY");
        int offZ = buildConfig.getInt("offsetZ");

        // The Universal Normalization Matrix
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (ClientSchematicCache.BlockPosState bps : blocks) {
            if (bps.pos().getX() < minX) minX = bps.pos().getX();
            if (bps.pos().getY() < minY) minY = bps.pos().getY();
            if (bps.pos().getZ() < minZ) minZ = bps.pos().getZ();
            if (bps.pos().getX() > maxX) maxX = bps.pos().getX();
            if (bps.pos().getZ() > maxZ) maxZ = bps.pos().getZ();
        }

        int width = maxX - minX;

        // Render Loop
        for (ClientSchematicCache.BlockPosState bps : blocks) {

            // Bring everything to a 0,0,0 local origin
            int nx = bps.pos().getX() - minX;
            int ny = bps.pos().getY() - minY;
            int nz = bps.pos().getZ() - minZ;

            // Center horizontally and Push forward by 1 block!
            int cx = nx - (width / 2);
            int cz = nz + 1;

            // Map dynamically to the newly anchored/rotated axes
            BlockPos worldPos = targetPos.relative(realRightDir, cx).relative(pushDir, cz).above(ny).offset(offX, offY, offZ);

            poseStack.pushPose();
            poseStack.translate(worldPos.getX() - camPos.x, worldPos.getY() - camPos.y, worldPos.getZ() - camPos.z);
            poseStack.translate(0.005, 0.005, 0.005);
            poseStack.scale(0.99f, 0.99f, 0.99f);

            dispatcher.renderBatched(bps.state().rotate(rot), worldPos, mc.level, poseStack, ghostBuffer, false, mc.level.random);
            poseStack.popPose();
        }
    }

    private static void renderDigHologram(RenderLevelStageEvent event, List<Integer> config, BlockPos targetPos, Direction rawDir, Direction rightDir, boolean isAnchored, Player player) {
        if (config == null || config.size() < 5) return;

        int d = config.get(0); int w = config.get(1); int shape = config.get(2);
        int l = config.get(3); int h = config.get(4);
        int renderDepth = Math.min(d, 32);

        Direction dir = (rawDir.getAxis() == Direction.Axis.Y && !isAnchored) ? player.getDirection() : rawDir;
        if (isAnchored && rawDir.getAxis() == Direction.Axis.Y) dir = rightDir.getCounterClockWise();

        int[] bounds = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        boolean hasBounds = false;

        if (shape == 0) {
            int w_min = -(w - 1) / 2, w_max = w / 2;
            for (int cw : new int[]{w_min, w_max}) {
                for (int cl : new int[]{0, renderDepth - 1}) {
                    for (int ch : new int[]{0, h - 1}) {
                        expandBounds(targetPos.relative(rightDir, cw).relative(dir, cl).below(cl).above(ch), bounds);
                        hasBounds = true;
                    }
                }
            }
        } else if (shape == 1) {
            int w_min = -(w - 1) / 2, w_max = w / 2;
            for (int cw : new int[]{w_min, w_max}) {
                for (int cl : new int[]{0, renderDepth - 1}) {
                    for (int ch : new int[]{0, h - 1}) {
                        expandBounds(targetPos.relative(rightDir, cw).relative(dir, cl).above(ch), bounds);
                        hasBounds = true;
                    }
                }
            }
        } else if (shape == 2) {
            int w_min = -(w - 1) / 2, w_max = w / 2;
            int l_min = -(l - 1) / 2, l_max = l / 2;
            for (int cw : new int[]{w_min, w_max}) {
                for (int cl : new int[]{l_min, l_max}) {
                    for (int cy : new int[]{-2, renderDepth - 1}) {
                        expandBounds(targetPos.relative(rightDir, cw).relative(dir, cl).below(cy), bounds);
                        hasBounds = true;
                    }
                }
            }
        } else if (shape == 3) {
            for (int cx : new int[]{-l, l}) {
                for (int cz : new int[]{-l, l}) {
                    for (int cy : new int[]{-2, renderDepth - 1}) {
                        expandBounds(targetPos.offset(cx, -cy, cz), bounds);
                        hasBounds = true;
                    }
                }
            }
        }

        if (!hasBounds) return;

        AABB renderBox = new AABB(bounds[0], bounds[1], bounds[2], bounds[3] + 1, bounds[4] + 1, bounds[5] + 1);

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        renderBox = renderBox.move(-camPos.x, -camPos.y, -camPos.z);

        PoseStack poseStack = event.getPoseStack();
        VertexConsumer buffer = Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(RenderType.lines());

        float red = isAnchored ? 0.8F : 0.0F;
        float green = isAnchored ? 0.2F : 1.0F;
        float blue = 0.8F;

        LevelRenderer.renderLineBox(poseStack, buffer, renderBox, red, green, blue, 0.6F);
    }

    private static void expandBounds(BlockPos p, int[] bounds) {
        if (p.getX() < bounds[0]) bounds[0] = p.getX();
        if (p.getY() < bounds[1]) bounds[1] = p.getY();
        if (p.getZ() < bounds[2]) bounds[2] = p.getZ();
        if (p.getX() > bounds[3]) bounds[3] = p.getX();
        if (p.getY() > bounds[4]) bounds[4] = p.getY();
        if (p.getZ() > bounds[5]) bounds[5] = p.getZ();
    }
}