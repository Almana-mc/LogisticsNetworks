package me.almana.logisticsnetworks.menu;

import me.almana.logisticsnetworks.block.ServerRackBlockEntity;
import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.ChannelMode;
import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.data.ServerRackConfig;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.logic.NodeAccessPolicy;
import me.almana.logisticsnetworks.logic.TransferEngine;
import me.almana.logisticsnetworks.network.SyncServerRackPayload;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ServerRackMenu extends AbstractContainerMenu {

    private final BlockPos rackPos;

    public ServerRackMenu(int containerId, Inventory playerInv, BlockPos rackPos) {
        super(Registration.SERVER_RACK_MENU.get(), containerId);
        this.rackPos = rackPos;
    }

    public ServerRackMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, buf.readBlockPos());
    }

    public BlockPos getRackPos() {
        return rackPos;
    }

    public void update(ServerPlayer player, ServerRackConfig next) {
        if (player.serverLevel().getBlockEntity(rackPos) instanceof ServerRackBlockEntity rack
                && canApply(player, rack.getConfig(), next)) {
            rack.setConfig(next);
        }
        sync(player);
    }

    public void sync(ServerPlayer player) {
        if (!(player.serverLevel().getBlockEntity(rackPos) instanceof ServerRackBlockEntity rack)) {
            return;
        }
        ServerRackConfig config = rack.getConfig();
        NetworkRegistry registry = NetworkRegistry.get(player.serverLevel());
        MinecraftServer server = player.getServer();
        PacketDistributor.sendToPlayer(player, new SyncServerRackPayload(rackPos, config,
                side(registry, server, config.left()), side(registry, server, config.right())));
    }

    private static SyncServerRackPayload.Side side(NetworkRegistry registry, MinecraftServer server,
            Optional<UUID> networkId) {
        LogisticsNetwork network = networkId.map(registry::getNetwork).orElse(null);
        if (network == null) {
            return SyncServerRackPayload.Side.EMPTY;
        }
        List<String> names = new ArrayList<>(LogisticsNodeEntity.CHANNEL_COUNT);
        int[] exports = new int[LogisticsNodeEntity.CHANNEL_COUNT];
        int[] imports = new int[LogisticsNodeEntity.CHANNEL_COUNT];
        for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
            names.add(network.getChannelName(i));
        }
        for (UUID nodeId : network.getNodeUuids()) {
            LogisticsNodeEntity node = TransferEngine.findNode(server, nodeId, network.getNodeDimension(nodeId));
            if (node == null) {
                continue;
            }
            for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
                ChannelData channel = node.getChannel(i);
                if (!channel.isEnabled()) {
                    continue;
                }
                int bit = 1 << channel.getType().ordinal();
                if (channel.getMode() == ChannelMode.EXPORT) {
                    exports[i] |= bit;
                } else {
                    imports[i] |= bit;
                }
            }
        }
        return new SyncServerRackPayload.Side(true, network.getName(), network.getColor(), names, exports, imports);
    }

    private static boolean canApply(ServerPlayer player, ServerRackConfig current, ServerRackConfig next) {
        if (next.left().isPresent() && next.left().equals(next.right())) {
            return false;
        }
        NetworkRegistry registry = NetworkRegistry.get(player.serverLevel());
        return canUse(player, registry, current.left(), next.left())
                && canUse(player, registry, current.right(), next.right());
    }

    // Old and new must be reachable
    private static boolean canUse(ServerPlayer player, NetworkRegistry registry, Optional<UUID> current,
            Optional<UUID> next) {
        for (Optional<UUID> id : List.of(current, next)) {
            if (id.isEmpty()) {
                continue;
            }
            LogisticsNetwork network = registry.getNetwork(id.get());
            boolean allowed = network == null
                    ? id.equals(current)
                    : NodeAccessPolicy.canAccess(network.getOwnerUuid(), player);
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(rackPos.getX() + 0.5, rackPos.getY() + 0.5, rackPos.getZ() + 0.5) < 64.0;
    }
}
