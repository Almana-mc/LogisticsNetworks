package me.almana.logisticsnetworks.item;

import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.menu.NodeMenu;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.FriendlyByteBuf;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class WrenchItem extends Item {

    public WrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide)
            return InteractionResult.SUCCESS;

        BlockPos clickedPos = context.getClickedPos();
        Player player = context.getPlayer();
        if (player == null)
            return InteractionResult.FAIL;

        LogisticsNodeEntity node = findNodeAt(level, clickedPos);
        if (node == null) {
            return InteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown()) {
            return removeNode(level, node, player);
        } else {
            return openNodeGui(node, player);
        }
    }

    @Nullable
    private LogisticsNodeEntity findNodeAt(Level level, BlockPos pos) {
        List<LogisticsNodeEntity> nodes = level.getEntitiesOfClass(LogisticsNodeEntity.class,
                new AABB(pos).inflate(0.5));
        for (LogisticsNodeEntity node : nodes) {
            if (node.getAttachedPos().equals(pos) && node.isActive()) {
                return node;
            }
        }
        return null;
    }

    private InteractionResult removeNode(Level level, LogisticsNodeEntity node, Player player) {
        if (level instanceof ServerLevel serverLevel && node.getNetworkId() != null) {
            NetworkRegistry.get(serverLevel).removeNodeFromNetwork(node.getNetworkId(), node.getUUID());
        }

        node.dropUpgrades();
        node.spawnAtLocation(Registration.LOGISTICS_NODE_ITEM.get());
        node.discard();

        level.playSound(null, node.blockPosition(), SoundEvents.METAL_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);
        player.displayClientMessage(Component.translatable("message.logisticsnetworks.node_removed"), true);

        return InteractionResult.CONSUME;
    }

    private InteractionResult openNodeGui(LogisticsNodeEntity node, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("gui.logisticsnetworks.node_config");
                }

                @Override
                public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player p) {
                    return new NodeMenu(containerId, playerInv, node);
                }
            }, buf -> {
                Util.writeNodeSyncData(buf, node, player.registryAccess());
            });

            if (serverPlayer.containerMenu instanceof NodeMenu menu) {
                menu.sendNetworkListToClient(serverPlayer);
            }
        }
        return InteractionResult.CONSUME;
    }

    // Inner utility class to keep main class clean or move to a shared Utility
    private static class Util {
        static void writeNodeSyncData(FriendlyByteBuf buf, LogisticsNodeEntity node,
                HolderLookup.Provider provider) {
            buf.writeVarInt(node.getId());
            for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
                buf.writeNbt(node.getChannel(i).save(provider));
            }
            for (int i = 0; i < LogisticsNodeEntity.UPGRADE_SLOT_COUNT; i++) {
                buf.writeNbt(node.getUpgradeItem(i).saveOptional(provider));
            }
        }
    }
}
