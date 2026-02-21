package me.almana.logisticsnetworks.item;

import me.almana.logisticsnetworks.data.NodeClipboardConfig;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import me.almana.logisticsnetworks.registration.ModTags;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;

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

        // Check that the block has at least one storage capability
        if (!hasAnyStorageCapability(level, clickedPos)) {
            if (player != null) {
                player.displayClientMessage(
                        Component.translatable("message.logisticsnetworks.no_storage_capability"), true);
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

    private boolean hasAnyStorageCapability(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel))
            return false;

        Direction[] directions = { null,
                Direction.DOWN, Direction.UP, Direction.NORTH,
                Direction.SOUTH, Direction.WEST, Direction.EAST };

        for (Direction dir : directions) {
            if (serverLevel.getCapability(Capabilities.ItemHandler.BLOCK, pos, dir) != null)
                return true;
            if (serverLevel.getCapability(Capabilities.FluidHandler.BLOCK, pos, dir) != null)
                return true;
            if (serverLevel.getCapability(Capabilities.EnergyStorage.BLOCK, pos, dir) != null)
                return true;
        }

        return MekanismCompat.hasChemicalStorage(serverLevel, pos);
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
            tryAutoPasteFromOffhandWrench(context, node);
            level.playSound(null, pos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);
            context.getItemInHand().shrink(1);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.FAIL;
    }

    private void tryAutoPasteFromOffhandWrench(UseOnContext context, LogisticsNodeEntity node) {
        Player player = context.getPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        ItemStack offhand = serverPlayer.getOffhandItem();
        if (!(offhand.getItem() instanceof WrenchItem)) {
            return;
        }

        NodeClipboardConfig clipboard = WrenchItem.getClipboard(offhand, serverPlayer.registryAccess());
        if (clipboard == null || clipboard.isEffectivelyEmpty()) {
            return;
        }

        NodeClipboardConfig.PasteResult result = clipboard.applyToNode(serverPlayer, node, offhand);
        switch (result) {
            case SUCCESS -> {
            }
            case MISSING_ITEMS -> serverPlayer.displayClientMessage(
                    Component.translatable("message.logisticsnetworks.clipboard.paste.missing_items"), true);
            case INVENTORY_FULL -> serverPlayer.displayClientMessage(
                    Component.translatable("message.logisticsnetworks.clipboard.paste.no_space"), true);
            case INCOMPATIBLE_TARGET -> serverPlayer.displayClientMessage(
                    Component.translatable("message.logisticsnetworks.clipboard.paste.incompatible"), true);
            case CLIPBOARD_INVALID -> serverPlayer.displayClientMessage(
                    Component.translatable("message.logisticsnetworks.clipboard.invalid"), true);
        }
    }
}
