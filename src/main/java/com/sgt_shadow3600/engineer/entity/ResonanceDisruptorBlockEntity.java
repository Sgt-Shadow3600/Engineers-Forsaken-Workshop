package com.sgt_shadow3600.engineer.entity;

import com.sgt_shadow3600.engineer.EngineerCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ResonanceDisruptorBlockEntity extends BlockEntity implements VibrationSystem {

    private final VibrationSystem.Data vibrationData;
    private final VibrationSystem.User vibrationUser;
    private final VibrationSystem.Listener vibrationListener; // Required Listener Object

    private int cooldown = 0;

    public ResonanceDisruptorBlockEntity(BlockPos pos, BlockState state) {
        super(EngineerCompanion.RESONANCE_DISRUPTOR_BE.get(), pos, state);
        this.vibrationData = new VibrationSystem.Data();

        this.vibrationUser = new VibrationSystem.User() {
            @Override
            public int getListenerRadius() {
                return 8;
            }

            @Override
            public net.minecraft.world.level.gameevent.PositionSource getPositionSource() {
                return new net.minecraft.world.level.gameevent.BlockPositionSource(pos);
            }

            @Override
            public boolean canReceiveVibration(ServerLevel level, BlockPos pos, Holder<GameEvent> gameEvent, GameEvent.Context context) {
                return cooldown <= 0;
            }

            @Override
            public void onReceiveVibration(ServerLevel level, BlockPos pos, Holder<GameEvent> gameEvent, @Nullable net.minecraft.world.entity.Entity entity, @Nullable net.minecraft.world.entity.Entity player, float distance) {
                triggerDisruption(level, pos);
            }
        };

        // Initialize the listener so the chunk can access it
        this.vibrationListener = new VibrationSystem.Listener(this);
    }

    public VibrationSystem.Listener getListener() {
        return this.vibrationListener;
    }

    @Override
    public VibrationSystem.Data getVibrationData() {
        return this.vibrationData;
    }

    @Override
    public VibrationSystem.User getVibrationUser() {
        return this.vibrationUser;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ResonanceDisruptorBlockEntity entity) {
        if (level.isClientSide) return;

        if (entity.cooldown > 0) {
            entity.cooldown--;
        }

        VibrationSystem.Ticker.tick(level, entity.getVibrationData(), entity.getVibrationUser());
    }

    public void triggerDisruption(ServerLevel level, BlockPos pos) {
        if (this.cooldown > 0) return;

        this.cooldown = 400;

        level.playSound(null, pos, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.BLOCKS, 2.0F, 0.6F + level.random.nextFloat() * 0.2F);
        level.sendParticles(ParticleTypes.SONIC_BOOM, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 1, 0, 0, 0, 0);

        AABB effectBounds = new AABB(pos).inflate(50.0D);
        List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, effectBounds);

        for (ServerPlayer player : players) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 2400, 2, false, true, true));
            player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.GUARDIAN_ELDER_EFFECT, 1.0F));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Cooldown", this.cooldown);
        VibrationSystem.Data.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, this.vibrationData)
                .ifSuccess(vibeData -> tag.put("VibrationData", vibeData));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.cooldown = tag.getInt("Cooldown");
        if (tag.contains("VibrationData")) {
            VibrationSystem.Data.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("VibrationData"))
                    .ifSuccess(data -> {});
        }
    }
}