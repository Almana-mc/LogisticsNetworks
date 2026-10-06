package me.almana.logisticsnetworks.block;

import com.mojang.serialization.MapCodec;
import me.almana.logisticsnetworks.menu.NodeMenu;
import me.almana.logisticsnetworks.menu.ServerRackMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class ServerRackBlock extends TwoCellBlock implements EntityBlock {

    public static final MapCodec<ServerRackBlock> CODEC = simpleCodec(p -> new ServerRackBlock());

    public ServerRackBlock() {
        super(BlockBehaviour.Properties.of()
                .strength(0.5f)
                .sound(SoundType.METAL)
                .noOcclusion());
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected Direction extension(Direction facing) {
        return Direction.UP;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(MAIN) ? new ServerRackBlockEntity(pos, state) : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockPos main = mainPos(state, pos);
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(main) instanceof ServerRackBlockEntity) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new ServerRackMenu(id, inv, main),
                    Component.translatable("container.logisticsnetworks.server_rack")),
                    buf -> buf.writeBlockPos(main));
            if (serverPlayer.containerMenu instanceof ServerRackMenu menu) {
                NodeMenu.sendAvailableNetworkListToClient(serverPlayer);
                menu.sync(serverPlayer);
            }
        }
        return InteractionResult.CONSUME;
    }
}
