package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.ChannelMode;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.network.TransferVisualPayload;
import me.almana.logisticsnetworks.registration.Registration;
import me.almana.logisticsnetworks.upgrade.NodeUpgradeData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import me.almana.logisticsnetworks.network.NetworkHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class TransferVisualBatch {

    private static final double RANGE_SQUARED = 64.0 * 64.0;

    private final MinecraftServer server;
    private final int color;
    private final Map<Key, Entry> entries = new LinkedHashMap<>();

    private TransferVisualBatch(MinecraftServer server, LogisticsNetwork network) {
        this.server = server;
        this.color = network.getColor();
        build(network);
    }

    static void sendTopologies(NetworkRegistry registry, MinecraftServer server) {
        boolean hasViewer = server.getPlayerList().getPlayers().stream()
                .anyMatch(player -> player.isHolding(Registration.WRENCH.get()));
        if (!hasViewer) {
            return;
        }
        for (LogisticsNetwork network : registry.getAllNetworks().values()) {
            new TransferVisualBatch(server, network).send();
        }
    }

    private void build(LogisticsNetwork network) {
        List<LogisticsNodeEntity> nodes = resolveNodes(network);
        Map<Route, List<LogisticsNodeEntity>> receivers = new HashMap<>();
        Set<UUID> dimensional = new HashSet<>();

        for (LogisticsNodeEntity node : nodes) {
            if (NodeUpgradeData.hasDimensionalUpgrade(node)) {
                dimensional.add(node.getUUID());
            }
            for (int channelIndex = 0; channelIndex < LogisticsNodeEntity.CHANNEL_COUNT; channelIndex++) {
                ChannelData channel = node.getChannel(channelIndex);
                if (channel != null && channel.isEnabled() && channel.getMode() == ChannelMode.IMPORT) {
                    receivers.computeIfAbsent(new Route(channelIndex, channel.getType()), key -> new ArrayList<>())
                            .add(node);
                }
            }
        }

        for (LogisticsNodeEntity source : nodes) {
            for (int channelIndex = 0; channelIndex < LogisticsNodeEntity.CHANNEL_COUNT; channelIndex++) {
                ChannelData channel = source.getChannel(channelIndex);
                if (channel == null || !channel.isEnabled() || channel.getMode() != ChannelMode.EXPORT) {
                    continue;
                }
                for (LogisticsNodeEntity target : receivers.getOrDefault(
                        new Route(channelIndex, channel.getType()), List.of())) {
                    if (source != target && canReach(source, target, dimensional)) {
                        entries.putIfAbsent(new Key(source.getUUID(), target.getUUID(), channel.getType()),
                                new Entry(source, target, channel.getType()));
                    }
                }
            }
        }
    }

    private List<LogisticsNodeEntity> resolveNodes(LogisticsNetwork network) {
        List<LogisticsNodeEntity> nodes = new ArrayList<>();
        for (UUID nodeId : network.getNodeUuids()) {
            LogisticsNodeEntity node = findNode(nodeId, null);
            if (node != null && node.isValidNode()) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    private LogisticsNodeEntity findNode(UUID nodeId, ResourceKey<Level> dimension) {
        if (dimension != null) {
            ServerLevel level = server.getLevel(dimension);
            return level != null && level.getEntity(nodeId) instanceof LogisticsNodeEntity node ? node : null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(nodeId);
            if (entity instanceof LogisticsNodeEntity node) {
                return node;
            }
        }
        return null;
    }

    private void send() {
        if (entries.isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isHolding(Registration.WRENCH.get())) {
                continue;
            }
            List<TransferVisualPayload.Path> paths = pathsFor(player);
            if (!paths.isEmpty()) {
                NetworkHandler.sendToPlayer(player, new TransferVisualPayload(color, paths));
            }
        }
    }

    private List<TransferVisualPayload.Path> pathsFor(ServerPlayer player) {
        List<TransferVisualPayload.Path> paths = new ArrayList<>();
        for (Entry entry : entries.values()) {
            boolean sourceLocal = player.level().dimension().equals(entry.source.level().dimension());
            boolean targetLocal = player.level().dimension().equals(entry.target.level().dimension());
            if (sourceLocal && targetLocal && (near(player, entry.source) || near(player, entry.target))) {
                paths.add(entry.path(TransferVisualPayload.Shape.FULL));
            } else if (sourceLocal && near(player, entry.source)) {
                paths.add(entry.path(TransferVisualPayload.Shape.OUTBOUND));
            } else if (targetLocal && near(player, entry.target)) {
                paths.add(entry.path(TransferVisualPayload.Shape.INBOUND));
            }
        }
        return paths;
    }

    private static boolean canReach(LogisticsNodeEntity source, LogisticsNodeEntity target, Set<UUID> dimensional) {
        return source.level().dimension().equals(target.level().dimension())
                || dimensional.contains(source.getUUID()) && dimensional.contains(target.getUUID());
    }

    private static boolean near(ServerPlayer player, LogisticsNodeEntity node) {
        return player.distanceToSqr(node.position()) <= RANGE_SQUARED;
    }

    private record Route(int channelIndex, ChannelType type) {
    }

    private record Key(UUID sourceId, UUID targetId, ChannelType type) {
    }

    private record Entry(LogisticsNodeEntity source, LogisticsNodeEntity target, ChannelType type) {

        TransferVisualPayload.Path path(TransferVisualPayload.Shape shape) {
            int sourceEntityId = shape == TransferVisualPayload.Shape.INBOUND ? -1 : source.getId();
            int targetEntityId = shape == TransferVisualPayload.Shape.OUTBOUND ? -1 : target.getId();
            return new TransferVisualPayload.Path(source.getUUID(), target.getUUID(), sourceEntityId, targetEntityId,
                    source.getAttachedPos(), target.getAttachedPos(), type.ordinal(), shape);
        }
    }
}
