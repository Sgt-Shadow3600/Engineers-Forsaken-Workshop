package com.sgt_shadow3600.engineer.block;

import com.sgt_shadow3600.engineer.inventory.TerminalMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TerminalBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    protected static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    public TerminalBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) { return false; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() < level.getMaxBuildHeight() - 1 && level.getBlockState(pos.above()).canBeReplaced(context)) {
            return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(HALF, DoubleBlockHalf.LOWER);
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);

        if (!level.isClientSide && state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            if (level.getBlockEntity(pos) instanceof TerminalBlockEntity terminal) {
                if (placer instanceof Player player) {
                    terminal.ownerId = player.getUUID();
                    terminal.sync();
                }
            }
        }
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) != DoubleBlockHalf.UPPER) {
            return super.canSurvive(state, level, pos);
        } else {
            BlockState stateBelow = level.getBlockState(pos.below());
            return stateBelow.is(this) && stateBelow.getValue(HALF) == DoubleBlockHalf.LOWER;
        }
    }

    @Override
    public BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (facing.getAxis() == Direction.Axis.Y && half == DoubleBlockHalf.LOWER == (facing == Direction.UP)) {
            return facingState.is(this) && facingState.getValue(HALF) != half ? state : Blocks.AIR.defaultBlockState();
        }
        return half == DoubleBlockHalf.LOWER && facing == Direction.DOWN && !state.canSurvive(level, currentPos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, facing, facingState, level, currentPos, facingPos);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new TerminalBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || state.getValue(HALF) == DoubleBlockHalf.UPPER) return null;
        return createTickerHelper(type, com.sgt_shadow3600.engineer.EngineerCompanion.TERMINAL_BE.get(), TerminalBlockEntity::tick);
    }

    // ARCHITECT FIX: Suppress the generic cast warning to clean up the build logs
    @SuppressWarnings("unchecked")
    @Nullable
    protected static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(BlockEntityType<A> type, BlockEntityType<E> targetType, BlockEntityTicker<? super E> ticker) {
        return targetType == type ? (BlockEntityTicker<A>) ticker : null;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide && state.getValue(HALF) == DoubleBlockHalf.LOWER) {
                if (level.getBlockEntity(pos) instanceof TerminalBlockEntity terminal) {
                    if (terminal.ownerId != null && level.getServer() != null) {
                        com.sgt_shadow3600.engineer.world.TerminalNetwork.get(level.getServer()).removeTerminal(terminal.ownerId, net.minecraft.core.GlobalPos.of(level.dimension(), pos));
                    }
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            BlockPos bePos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
            if (level.getBlockEntity(bePos) instanceof TerminalBlockEntity terminal) {
                if (terminal.ownerId != null && !terminal.ownerId.equals(player.getUUID())) {
                    player.displayClientMessage(Component.literal("§cAccess Denied: You do not own this terminal."), true);
                    return ItemInteractionResult.sidedSuccess(level.isClientSide);
                }

                if (stack.getItem() == com.sgt_shadow3600.engineer.EngineerCompanion.LOGIC_CORE_ITEM.get()) {
                    if (terminal.isNpcActive || terminal.respawnTimer > 0) {
                        player.displayClientMessage(Component.literal("§cTerminal already has an active unit or is currently rebooting."), true);
                        return ItemInteractionResult.SUCCESS;
                    }

                    var network = com.sgt_shadow3600.engineer.world.TerminalNetwork.get(level.getServer());
                    net.minecraft.core.GlobalPos thisPos = net.minecraft.core.GlobalPos.of(level.dimension(), bePos);

                    if (network.hasActiveTerminal(player.getUUID())) {
                        if (!thisPos.equals(network.getActiveTerminal(player.getUUID()))) {
                            player.displayClientMessage(Component.literal("§cTemporal allowances only allow one active Engineer."), true);
                            return ItemInteractionResult.SUCCESS;
                        }
                    } else {
                        network.setActiveTerminal(player.getUUID(), thisPos);
                    }

                    CompoundTag npcData = stack.getOrDefault(com.sgt_shadow3600.engineer.ModDataComponents.STORED_NPC_DATA.get(), new CompoundTag());

                    if (!player.isCreative()) {
                        stack.shrink(1);
                    }

                    terminal.restoreFromCore(npcData);
                    player.displayClientMessage(Component.literal("§aLogic Core Inserted. Initiating Reconstruction Sequence..."), true);
                    return ItemInteractionResult.SUCCESS;
                }

                player.openMenu(new SimpleMenuProvider((id, inv, p) -> new TerminalMenu(id, inv, terminal), Component.literal("Operations Terminal")), bePos);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            BlockPos bePos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
            if (level.getBlockEntity(bePos) instanceof TerminalBlockEntity terminal) {
                if (terminal.ownerId != null && !terminal.ownerId.equals(player.getUUID())) {
                    player.displayClientMessage(Component.literal("§cAccess Denied: You do not own this terminal."), true);
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
                player.openMenu(new SimpleMenuProvider((id, inv, p) -> new TerminalMenu(id, inv, terminal), Component.literal("Operations Terminal")), bePos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockPos bePos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
            if (level.getBlockEntity(bePos) instanceof TerminalBlockEntity terminal) {
                if (terminal.isNpcActive && terminal.activeNpcId != null) {
                    net.minecraft.world.entity.Entity e = ((net.minecraft.server.level.ServerLevel)level).getEntity(terminal.activeNpcId);
                    if (e instanceof com.sgt_shadow3600.engineer.entity.EngineerCompanionEntity npc) {
                        terminal.storeNpc(npc);
                    }
                }

                if (player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.LOWER) {
                    ItemStack itemstack = new ItemStack(this);
                    terminal.saveToItem(itemstack, level.registryAccess());
                    net.minecraft.world.entity.item.ItemEntity itementity = new net.minecraft.world.entity.item.ItemEntity(
                            level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, itemstack
                    );
                    itementity.setDefaultPickUpDelay();
                    level.addFreshEntity(itementity);
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            BlockEntity blockentity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
            if (blockentity instanceof TerminalBlockEntity terminal) {
                ItemStack itemstack = new ItemStack(this);
                blockentity.saveToItem(itemstack, builder.getLevel().registryAccess());
                return List.of(itemstack);
            }
        }
        return List.of();
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        BlockPos bePos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        ItemStack itemstack = new ItemStack(this);
        if (level instanceof Level realLevel) {
            if (realLevel.getBlockEntity(bePos) instanceof TerminalBlockEntity terminal) {
                terminal.saveToItem(itemstack, realLevel.registryAccess());
            }
        }
        return itemstack;
    }
}