package me.almana.logisticsnetworks.block;

import me.almana.logisticsnetworks.menu.ComputerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraftforge.network.NetworkHooks;

import java.util.EnumMap;
import java.util.Map;

public class ComputerBlock extends HorizontalDirectionalBlock {

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        VoxelShape baseN = Shapes.box(0.0, 0.0, 0.0625, 1.0, 0.0625, 1.0);
        VoxelShape screenN = Shapes.box(0.0, 0.0625, 0.0, 1.0, 0.9375, 0.25);
        SHAPES.put(Direction.NORTH, Shapes.or(baseN, screenN));

        VoxelShape baseS = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.0625, 0.9375);
        VoxelShape screenS = Shapes.box(0.0, 0.0625, 0.75, 1.0, 0.9375, 1.0);
        SHAPES.put(Direction.SOUTH, Shapes.or(baseS, screenS));

        VoxelShape baseW = Shapes.box(0.0625, 0.0, 0.0, 1.0, 0.0625, 1.0);
        VoxelShape screenW = Shapes.box(0.0, 0.0625, 0.0, 0.25, 0.9375, 1.0);
        SHAPES.put(Direction.WEST, Shapes.or(baseW, screenW));

        VoxelShape baseE = Shapes.box(0.0, 0.0, 0.0, 0.9375, 0.0625, 1.0);
        VoxelShape screenE = Shapes.box(0.75, 0.0625, 0.0, 1.0, 0.9375, 1.0);
        SHAPES.put(Direction.EAST, Shapes.or(baseE, screenE));
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
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer,
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
