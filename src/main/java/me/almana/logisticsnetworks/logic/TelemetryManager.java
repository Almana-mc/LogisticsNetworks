package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.menu.ComputerMenu;
import me.almana.logisticsnetworks.network.SyncTelemetryPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TelemetryManager {

    public static final int HISTORY_SIZE = 120;
    private static final int SYNC_INTERVAL = 20;

    record ViewerTarget(UUID networkId, int channelIndex) {}
    record WatchKey(UUID networkId, int channelIndex) {}

    private final Map<ServerPlayer, ViewerTarget> viewerTargets = new HashMap<>();
    private final Set<UUID> activeNetworks = new HashSet<>();
    private final Map<WatchKey, long[]> histories = new HashMap<>();
    private final Map<WatchKey, Integer> historyIndices = new HashMap<>();
    private final Map<UUID, int[]> channelTypeOrdinals = new HashMap<>();
    private int tickCounter;

    public void subscribe(UUID networkId, int channelIndex,
            ServerPlayer player, NetworkRegistry registry, MinecraftServer server) {
        unsubscribe(player);

        ViewerTarget target = new ViewerTarget(networkId, channelIndex);
        viewerTargets.put(player, target);
        rebuildActiveNetworks();

        WatchKey key = new WatchKey(networkId, channelIndex);
        histories.computeIfAbsent(key, k -> new long[HISTORY_SIZE]);
        historyIndices.putIfAbsent(key, 0);

        drainNetwork(networkId, registry, server);
        sendToPlayer(player, target);
    }

    public void unsubscribe(ServerPlayer player) {
        ViewerTarget old = viewerTargets.remove(player);
        if (old != null) {
            rebuildActiveNetworks();
            cleanupUnwatchedHistories();
        }
    }

    public void unsubscribeAll(ServerPlayer player) {
        unsubscribe(player);
    }

    public boolean isActive(UUID networkId) {
        return activeNetworks.contains(networkId);
    }

    public void tick(NetworkRegistry registry, MinecraftServer server) {
        if (viewerTargets.isEmpty()) return;

        int sizeBefore = viewerTargets.size();
        viewerTargets.keySet().removeIf(p -> p.isRemoved() || !(p.containerMenu instanceof ComputerMenu));
        if (viewerTargets.isEmpty()) {
            activeNetworks.clear();
            histories.clear();
            historyIndices.clear();
            channelTypeOrdinals.clear();
            return;
        }
        rebuildActiveNetworks();
        if (viewerTargets.size() < sizeBefore) {
            cleanupUnwatchedHistories();
        }

        tickCounter++;
        if (tickCounter < SYNC_INTERVAL) return;
        tickCounter = 0;

        for (UUID networkId : activeNetworks) {
            drainNetwork(networkId, registry, server);
        }

        for (Map.Entry<ServerPlayer, ViewerTarget> entry : viewerTargets.entrySet()) {
            sendToPlayer(entry.getKey(), entry.getValue());
        }
    }

    private void drainNetwork(UUID networkId, NetworkRegistry registry, MinecraftServer server) {
        LogisticsNetwork network = registry.getNetwork(networkId);
        if (network == null) return;

        long[] aggregated = new long[LogisticsNodeEntity.CHANNEL_COUNT];
        int[] typeOrdinals = new int[LogisticsNodeEntity.CHANNEL_COUNT];
        Arrays.fill(typeOrdinals, -1);

        for (UUID nodeId : network.getNodeUuids()) {
            LogisticsNodeEntity node = findNode(server, nodeId, network.getNodeDimension(nodeId));
            if (node == null) continue;

            for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
                ChannelData channel = node.getChannel(i);
                aggregated[i] += channel.getTelemetry().drainFlow();
                if (typeOrdinals[i] < 0 && channel.isEnabled()) {
                    typeOrdinals[i] = channel.getType().ordinal();
                }
            }
        }

        channelTypeOrdinals.put(networkId, typeOrdinals);

        for (int i = 0; i < aggregated.length; i++) {
            WatchKey key = new WatchKey(networkId, i);
            long[] history = histories.get(key);
            if (history != null) {
                int idx = historyIndices.getOrDefault(key, 0);
                history[idx] = aggregated[i];
                historyIndices.put(key, (idx + 1) % HISTORY_SIZE);
            }
        }
    }

    private void sendToPlayer(ServerPlayer player, ViewerTarget target) {
        WatchKey key = new WatchKey(target.networkId(), target.channelIndex());
        long[] history = histories.get(key);
        if (history == null) return;

        int typeOrdinal = channelType(target.networkId(), target.channelIndex());
        PacketDistributor.sendToPlayer(player, new SyncTelemetryPayload(
                target.networkId(), target.channelIndex(),
                typeOrdinal, history.clone(), historyIndices.getOrDefault(key, 0)));
    }

    private int channelType(UUID networkId, int channelIndex) {
        int[] types = channelTypeOrdinals.get(networkId);
        if (types == null) return 0;
        int ordinal = types[channelIndex];
        return ordinal < 0 ? 0 : ordinal;
    }

    private void rebuildActiveNetworks() {
        activeNetworks.clear();
        for (ViewerTarget target : viewerTargets.values()) {
            activeNetworks.add(target.networkId());
        }
    }

    private void cleanupUnwatchedHistories() {
        Set<WatchKey> watched = new HashSet<>();
        for (ViewerTarget target : viewerTargets.values()) {
            watched.add(new WatchKey(target.networkId(), target.channelIndex()));
        }
        histories.keySet().retainAll(watched);
        historyIndices.keySet().retainAll(watched);
        channelTypeOrdinals.keySet().retainAll(activeNetworks);
    }

    private static LogisticsNodeEntity findNode(MinecraftServer server, UUID nodeId,
            @Nullable ResourceKey<Level> cachedDim) {
        if (cachedDim != null) {
            ServerLevel level = server.getLevel(cachedDim);
            if (level == null)
                return null;
            return level.getEntity(nodeId) instanceof LogisticsNodeEntity node ? node : null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(nodeId) instanceof LogisticsNodeEntity node)
                return node;
        }
        return null;
    }
}
