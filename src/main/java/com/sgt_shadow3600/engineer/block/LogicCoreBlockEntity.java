package com.sgt_shadow3600.engineer.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;

public class LogicCoreBlockEntity extends BlockEntity {
    private CompoundTag npcData = new CompoundTag();
    private BlockPos terminalPos = null;

    public LogicCoreBlockEntity(BlockPos pos, BlockState state) {
        super(com.sgt_shadow3600.engineer.EngineerCompanion.LOGIC_CORE_BE.get(), pos, state);
    }

    public void setNpcData(CompoundTag tag) { this.npcData = tag; setChanged(); }
    public CompoundTag getNpcData() { return this.npcData; }

    public void setTerminalPos(BlockPos pos) { this.terminalPos = pos; setChanged(); }
    public BlockPos getTerminalPos() { return this.terminalPos; }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("NPCData", npcData);
        if (terminalPos != null) tag.putLong("TerminalPos", terminalPos.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("NPCData")) npcData = tag.getCompound("NPCData");
        if (tag.contains("TerminalPos")) terminalPos = BlockPos.of(tag.getLong("TerminalPos"));
    }

    // ARCHITECT FIX: Prevent server from broadcasting the massive NPC memory NBT
    // to any client that loads the chunk. Keep it safe on the disk!
    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return new CompoundTag();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}