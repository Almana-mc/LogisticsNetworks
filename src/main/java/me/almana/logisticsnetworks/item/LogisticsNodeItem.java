package me.almana.logisticsnetworks.item;

import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.registration.ModTags;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class LogisticsNodeItem extends Item {

    public LogisticsNodeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        Player player = context.getPlayer();

        if (level.isClientSide)
            return InteractionResult.SUCCESS;

        // Check compatibility
        if (level.isEmptyBlock(clickedPos))
            return InteractionResult.FAIL;

        BlockState state = level.getBlockState(clickedPos);
        if (isBlacklisted(state)) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.logisticsnetworks.block_blacklisted"),
                        true);
            }
            return InteractionResult.FAIL;
        }

        // Verify valid attachment (no existing node)
        if (hasNodeAttached(level, clickedPos)) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.logisticsnetworks.node_already_exists"),
                        true);
            }
            return InteractionResult.FAIL;
        }

        // Place new node
        return placeNode(level, clickedPos, context);
    }

    private boolean isBlacklisted(BlockState state) {
        return state.is(ModTags.NODE_BLACKLIST_BLOCKS) || state.is(ModTags.NODE_COMPATIBILITY_BLACKLIST_BLOCKS);
    }

    private boolean hasNodeAttached(Level level, BlockPos pos) {
        List<LogisticsNodeEntity> existingNodes = level.getEntitiesOfClass(LogisticsNodeEntity.class,
                new AABB(pos).inflate(0.1));
        for (LogisticsNodeEntity node : existingNodes) {
            if (node.getAttachedPos().equals(pos))
                return true;
        }
        return false;
    }

    private InteractionResult placeNode(Level level, BlockPos pos, UseOnContext context) {
        LogisticsNodeEntity node = Registration.LOGISTICS_NODE.get().create(level);
        if (node == null)
            return InteractionResult.FAIL;

        node.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        node.setAttachedPos(pos);
        node.setValid(true);

        if (level.addFreshEntity(node)) {
            level.playSound(null, pos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);
            context.getItemInHand().shrink(1);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.FAIL;
    }
}
