package me.almana.logisticsnetworks.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.PushReaction;
import org.jetbrains.annotations.Nullable;

public abstract class TwoCellBlock extends HorizontalDirectionalBlock {

    public static final BooleanProperty MAIN = BooleanProperty.create("main");

    protected TwoCellBlock(Properties properties) {
        super(properties.pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MAIN, true));
    }

    protected abstract Direction extension(Direction facing);

    public BlockPos partnerPos(BlockState state, BlockPos pos) {
        Direction direction = extension(state.getValue(FACING));
        return pos.relative(state.getValue(MAIN) ? direction : direction.getOpposite());
    }

    public BlockPos mainPos(BlockState state, BlockPos pos) {
        return state.getValue(MAIN) ? pos : partnerPos(state, pos);
    }

    public boolean isPartner(BlockState state, BlockState other) {
        return other.is(this) && other.getValue(FACING) == state.getValue(FACING)
                && other.getValue(MAIN) != state.getValue(MAIN);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MAIN);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection());
        BlockPos other = partnerPos(state, context.getClickedPos());
        Level level = context.getLevel();
        if (!level.isInWorldBounds(other) || !level.getWorldBorder().isWithinBounds(other)
                || !level.getBlockState(other).canBeReplaced(context)) {
            return null;
        }
        return state;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack) {
        level.setBlock(partnerPos(state, pos), state.setValue(MAIN, false), Block.UPDATE_ALL);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
            boolean movedByPiston) {
        BlockPos other = partnerPos(state, pos);
        if (isPartner(state, level.getBlockState(other))) {
            level.setBlock(other, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
}
