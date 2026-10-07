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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;

import com.mojang.serialization.MapCodec;

import org.jetbrains.annotations.Nullable;

public class ComputerBlock extends TwoCellBlock implements EntityBlock {

    public static final MapCodec<ComputerBlock> CODEC = simpleCodec(ComputerBlock::new);

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    private static final VoxelShape[] MAIN_SHAPES = new VoxelShape[4];
    private static final VoxelShape[] EXTENSION_SHAPES = new VoxelShape[4];

    static {
        // Unrotated is facing south
        VoxelShape main = Shapes.or(
                box(11, 0, 1, 14, 2, 6),
                box(0, 0, 0, 9, 16, 16),
                box(12, 4, 12, 16, 16, 14));
        VoxelShape extension = Shapes.or(
                box(0, 4, 12, 16, 16, 14),
                box(4, 1, 14, 8, 12, 16),
                box(1, 0, 12, 11, 1, 16),
                box(0, 0, 0, 16, 1, 8));
        for (int turns = 0; turns < 4; turns++) {
            MAIN_SHAPES[turns] = rotateShape(main, turns);
            EXTENSION_SHAPES[turns] = rotateShape(extension, turns);
        }
    }

    private static VoxelShape rotateShape(VoxelShape shape, int turns) {
        for (int i = 0; i < turns; i++) {
            VoxelShape turned = Shapes.empty();
            for (AABB b : shape.toAabbs()) {
                turned = Shapes.or(turned, Shapes.box(1 - b.maxZ, b.minY, b.minX, 1 - b.minZ, b.maxY, b.maxX));
            }
            shape = turned;
        }
        return shape;
    }

    public ComputerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected Direction extension(Direction facing) {
        return facing.getCounterClockWise();
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return mirror == Mirror.NONE ? state : super.mirror(state, mirror).cycle(MAIN);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(MAIN) ? MAIN_SHAPES : EXTENSION_SHAPES)[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(MAIN) ? new ComputerBlockEntity(pos, state) : null;
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
        BlockPos main = mainPos(state, pos);
        if (!state.getValue(MAIN) && !isPartner(state, level.getBlockState(main))) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (level.getBlockEntity(main) == null) {
            level.setBlockEntity(new ComputerBlockEntity(main, level.getBlockState(main)));
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(
                    new SimpleMenuProvider(
                            (id, inv, p) -> new ComputerMenu(id, inv, main),
                            Component.translatable("container.logisticsnetworks.computer")),
                    buf -> buf.writeBlockPos(main));

            if (serverPlayer.containerMenu instanceof ComputerMenu computerMenu) {
                computerMenu.requestNetworkList(serverPlayer);
            }
        }

        return InteractionResult.CONSUME;
    }
}
