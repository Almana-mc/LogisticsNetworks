package me.almana.logisticsnetworks.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class ServerRackBlock extends TwoCellBlock {

    public static final MapCodec<ServerRackBlock> CODEC = simpleCodec(ServerRackBlock::new);

    public ServerRackBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected Direction extension(Direction facing) {
        return Direction.UP;
    }
}
