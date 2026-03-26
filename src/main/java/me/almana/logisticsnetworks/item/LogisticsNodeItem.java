package me.almana.logisticsnetworks.item;

import me.almana.logisticsnetworks.data.NodeClipboardConfig;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.logic.NodePlacementHelper;
import net.minecraft.core.BlockPos;
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

public class LogisticsNodeItem extends Item {

    public LogisticsNodeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        Player player = context.getPlayer();

        if (level.isClientSide())
            return InteractionResult.SUCCESS;

        NodePlacementHelper.ValidationResult validation = NodePlacementHelper.validatePlacement(level, clickedPos);
        if (validation != NodePlacementHelper.ValidationResult.OK) {
            if (player != null) {
                switch (validation) {
                    case BLACKLISTED -> WrenchItem.sendPlayerMessage(player,
                            Component.translatable("message.logisticsnetworks.block_blacklisted"), true);
                    case NO_STORAGE_CAPABILITY -> WrenchItem.sendPlayerMessage(player,
                            Component.translatable("message.logisticsnetworks.no_storage_capability"), true);
                    case NODE_ALREADY_EXISTS -> WrenchItem.sendPlayerMessage(player,
                            Component.translatable("message.logisticsnetworks.node_already_exists"), true);
                    case AIR, OK -> {
                    }
                }
            }
            return InteractionResult.FAIL;
        }

        // Place new node
        return placeNode(level, clickedPos, context);
    }

    private InteractionResult placeNode(Level level, BlockPos pos, UseOnContext context) {
        Player player = context.getPlayer();
        LogisticsNodeEntity node = NodePlacementHelper.placeNode(level, pos, player != null ? player.getUUID() : null);
        if (node == null)
            return InteractionResult.FAIL;

        tryAutoPasteFromOffhandWrench(context, node);
        level.playSound(null, pos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);
        context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
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
            case MISSING_ITEMS -> WrenchItem.sendPlayerMessage(serverPlayer,
                    Component.translatable("message.logisticsnetworks.clipboard.paste.missing_items"), true);
            case INVENTORY_FULL -> WrenchItem.sendPlayerMessage(serverPlayer,
                    Component.translatable("message.logisticsnetworks.clipboard.paste.no_space"), true);
            case INCOMPATIBLE_TARGET -> WrenchItem.sendPlayerMessage(serverPlayer,
                    Component.translatable("message.logisticsnetworks.clipboard.paste.incompatible"), true);
            case CLIPBOARD_INVALID -> WrenchItem.sendPlayerMessage(serverPlayer,
                    Component.translatable("message.logisticsnetworks.clipboard.invalid"), true);
        }
    }
}
