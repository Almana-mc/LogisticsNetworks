package me.almana.logisticsnetworks.block;

import me.almana.logisticsnetworks.menu.ComputerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;

public class ComputerBlock extends Block {

    // Custom shape for the laptop/computer model - includes base and screen
    private static final VoxelShape BASE_SHAPE = Shapes.box(0.0, 0.0, 0.0625, 1.0, 0.0625, 1.0);
    private static final VoxelShape SCREEN_SHAPE = Shapes.box(0.0, 0.0625, 0.0, 1.0, 0.9375, 0.25);
    private static final VoxelShape SHAPE = Shapes.or(BASE_SHAPE, SCREEN_SHAPE);

    public ComputerBlock() {
        super(BlockBehaviour.Properties.of()
                .strength(0.5f)
                .sound(SoundType.METAL)
                .noOcclusion());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(
                    new SimpleMenuProvider(
                            (id, inv, p) -> new ComputerMenu(id, inv, pos),
                            Component.translatable("container.logisticsnetworks.computer")),
                    buf -> buf.writeBlockPos(pos));

            // Request network list after menu is opened
            if (serverPlayer.containerMenu instanceof ComputerMenu computerMenu) {
                computerMenu.requestNetworkList(serverPlayer);
            }
        }

        return InteractionResult.CONSUME;
    }
}
