package com.sgt_shadow3600.engineer.network;

import com.sgt_shadow3600.engineer.block.TerminalBlockEntity;
import com.sgt_shadow3600.engineer.entity.EngineerCompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class RadarPingHandler {

    public static void handleData(final RadarPingPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ServerLevel level = player.serverLevel();
                BlockPos pos = payload.pos();

                if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof TerminalBlockEntity terminal) {

                    if (terminal.coreInTransit && terminal.deathPos != null) {
                        String cords = terminal.deathPos.getX() + " " + terminal.deathPos.getY() + " " + terminal.deathPos.getZ();
                        PacketDistributor.sendToPlayer(player, new RadarPongPayload(997, 1, 0, 0, 30, 0, false, cords, new CompoundTag(), false, new CompoundTag()));
                        return;
                    }

                    if (terminal.respawnTimer > 0) {
                        PacketDistributor.sendToPlayer(player, new RadarPongPayload(1000, terminal.respawnTimer / 20, 0, 0, 30, 0, false, "", new CompoundTag(), true, new CompoundTag()));
                        return;
                    }

                    if (terminal.isNpcActive && terminal.activeNpcId != null) {
                        Entity e = level.getEntity(terminal.activeNpcId);
                        if (e instanceof EngineerCompanionEntity npc) {
                            int tLevel = npc.experienceManager.getLevel();
                            int xp = npc.experienceManager.getExperience();
                            int health = (int) npc.getHealth();
                            int maxHealth = (int) npc.getMaxHealth();
                            int armor = npc.getArmorValue();
                            String missing = npc.getMissingSupplies();
                            boolean defensive = npc.isDefensiveMode();

                            CompoundTag tools = new CompoundTag();
                            for (int i = 0; i < 8; i++) {
                                ItemStack stack = npc.inventory.getStackInSlot(i);
                                if (!stack.isEmpty()) {
                                    tools.put("tool_" + i, stack.saveOptional(npc.registryAccess()));
                                }
                            }

                            CompoundTag queueData = new CompoundTag();
                            ListTag qList = new ListTag();

                            if (npc.jobController.jobSite != null) {
                                CompoundTag jt = new CompoundTag();
                                com.sgt_shadow3600.engineer.entity.ai.utils.JobDefinition activeJob =
                                        new com.sgt_shadow3600.engineer.entity.ai.utils.JobDefinition(
                                                npc.jobController.jobSite, npc.jobController.jobDirection, npc.jobController.jobDepth, npc.jobController.jobWidth,
                                                npc.jobController.jobShape, npc.jobController.jobLength, npc.jobController.jobHeight, npc.jobController.veinmine,
                                                npc.jobController.clearSite, npc.jobController.isAdvancedJob, npc.jobController.schematicName, npc.jobController.blockSubstitutions
                                        );
                                jt.putString("desc", "[Active] " + formatDesc(activeJob));
                                jt.putBoolean("active", true);
                                qList.add(jt);
                            }

                            int visualCount = 0;
                            for (com.sgt_shadow3600.engineer.entity.ai.utils.JobDefinition job : npc.jobController.jobQueue) {
                                if (visualCount >= 10) {
                                    CompoundTag overflow = new CompoundTag();
                                    overflow.putString("desc", "+ " + (npc.jobController.jobQueue.size() - 10) + " more queued...");
                                    overflow.putBoolean("active", false);
                                    qList.add(overflow);
                                    break;
                                }
                                CompoundTag jt = new CompoundTag();
                                jt.putString("desc", formatDesc(job));
                                jt.putBoolean("active", false);
                                qList.add(jt);
                                visualCount++;
                            }
                            queueData.put("list", qList);

                            PacketDistributor.sendToPlayer(player, new RadarPongPayload(npc.getCurrentTask().ordinal(), tLevel, xp, health, maxHealth, armor, true, missing, tools, defensive, queueData));
                            return;
                        }
                    }

                    CompoundTag stored = terminal.storedNpcData;
                    if (!stored.isEmpty()) {
                        int tLevel = stored.contains("CompanionLevel") ? stored.getInt("CompanionLevel") : 1;
                        int xp = stored.contains("CompanionXP") ? stored.getInt("CompanionXP") : 0;
                        int health = stored.contains("Health") ? (int) stored.getFloat("Health") : 30;
                        int maxHealth = stored.contains("SavedMaxHealth") ? (int) stored.getFloat("SavedMaxHealth") : 30;
                        int armor = stored.contains("SavedArmor") ? stored.getInt("SavedArmor") : 0;
                        boolean defensive = stored.getBoolean("DefensiveMode");

                        int taskId = 999;
                        if (health < maxHealth) {
                            boolean isNewCore = !stored.contains("Inventory");
                            taskId = isNewCore ? 996 : 998;
                        }

                        CompoundTag queueData = new CompoundTag();
                        ListTag qList = new ListTag();
                        if (stored.contains("JobQueue")) {
                            ListTag storedQueue = stored.getList("JobQueue", 10);
                            int visualCount = 0;
                            for (int i = 0; i < storedQueue.size(); i++) {
                                if (visualCount >= 10) {
                                    CompoundTag overflow = new CompoundTag();
                                    overflow.putString("desc", "+ " + (storedQueue.size() - 10) + " more queued...");
                                    overflow.putBoolean("active", false);
                                    qList.add(overflow);
                                    break;
                                }
                                com.sgt_shadow3600.engineer.entity.ai.utils.JobDefinition job = com.sgt_shadow3600.engineer.entity.ai.utils.JobDefinition.fromNBT(storedQueue.getCompound(i));
                                CompoundTag jt = new CompoundTag();
                                jt.putString("desc", formatDesc(job));
                                jt.putBoolean("active", false);
                                qList.add(jt);
                                visualCount++;
                            }
                        }
                        queueData.put("list", qList);

                        PacketDistributor.sendToPlayer(player, new RadarPongPayload(taskId, tLevel, xp, health, maxHealth, armor, true, "", new CompoundTag(), defensive, queueData));
                        return;
                    }

                    PacketDistributor.sendToPlayer(player, new RadarPongPayload(0, 1, 0, 0, 30, 0, false, "", new CompoundTag(), false, new CompoundTag()));
                } else {
                    PacketDistributor.sendToPlayer(player, new RadarPongPayload(0, 1, 0, 0, 30, 0, false, "", new CompoundTag(), false, new CompoundTag()));
                }
            }
        });
    }

    private static String formatDesc(com.sgt_shadow3600.engineer.entity.ai.utils.JobDefinition job) {
        if (job.isAdvanced && job.schematicName != null && !job.schematicName.isEmpty()) {
            String cleanName = job.schematicName.contains(":") ? job.schematicName.substring(job.schematicName.indexOf(":") + 1) : job.schematicName;
            if (cleanName.contains("/")) cleanName = cleanName.substring(cleanName.lastIndexOf("/") + 1);
            return "Build " + cleanName;
        }
        if (job.shape == 0) return "Stair " + job.width + "x" + job.height + "x" + job.depth;
        if (job.shape == 1) return "Tunnel " + job.width + "x" + job.height + "x" + job.depth;
        if (job.shape == 2) return "Shaft " + job.width + "x" + job.length + "x" + job.depth;
        if (job.shape == 3) return "Quarry r" + job.length + " d" + job.depth;
        if (job.shape >= 10 || job.shape == 99) return "Build Operation";
        return "Task";
    }
}