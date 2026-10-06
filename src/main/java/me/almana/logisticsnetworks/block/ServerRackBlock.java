package me.almana.logisticsnetworks.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class ServerRackBlock extends TwoCellBlock {

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
}
