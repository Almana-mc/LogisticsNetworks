package me.almana.logisticsnetworks.block;

import me.almana.logisticsnetworks.menu.ComputerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;

import com.mojang.serialization.MapCodec;

import org.jetbrains.annotations.Nullable;

public class ComputerBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<ComputerBlock> CODEC = simpleCodec(p -> new ComputerBlock());

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        // Front faces player at south
        VoxelShape desktop = Shapes.or(
                box(11, 0, 1, 14, 2, 6),
                box(0, 0, 0, 9, 16, 16),
                box(12, 4, 12, 32, 16, 14),
                box(20, 1, 14, 24, 12, 16),
                box(17, 0, 12, 27, 1, 16),
                box(16, 0, 0, 32, 1, 8));
        for (int turns = 0; turns < 4; turns++) {
            SHAPES[turns] = rotate(desktop, turns);
        }
    }

    static VoxelShape rotate(VoxelShape shape, int turns) {
        for (int i = 0; i < turns; i++) {
            VoxelShape turned = Shapes.empty();
            for (AABB b : shape.toAabbs()) {
                turned = Shapes.or(turned, Shapes.box(1 - b.maxZ, b.minY, b.minX, 1 - b.minZ, b.maxY, b.maxX));
            }
            shape = turned;
        }
        return shape;
    }

    public ComputerBlock() {
        super(BlockBehaviour.Properties.of()
                .strength(0.5f)
                .sound(SoundType.METAL)
                .noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ComputerBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof ComputerBlockEntity computer) {
            computer.setOwner(player.getUUID());
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (level.getBlockEntity(pos) == null) {
            level.setBlockEntity(new ComputerBlockEntity(pos, state));
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(
                    new SimpleMenuProvider(
                            (id, inv, p) -> new ComputerMenu(id, inv, pos),
                            Component.translatable("container.logisticsnetworks.computer")),
                    buf -> buf.writeBlockPos(pos));

            if (serverPlayer.containerMenu instanceof ComputerMenu computerMenu) {
                computerMenu.requestNetworkList(serverPlayer);
            }
        }

        return InteractionResult.CONSUME;
    }
}
