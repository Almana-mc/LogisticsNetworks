package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.FlowResource;
import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.menu.ComputerMenu;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public class TelemetryManager {

    static final int TOP_RESOURCES = 16;
    private static final int SYNC_INTERVAL = 20;

    public record ChannelDrain(int typeOrdinal, long total, List<Map.Entry<FlowResource, Long>> top) {
        static ChannelDrain of(int typeOrdinal, long total, Map<FlowResource, Long> resources) {
            return new ChannelDrain(typeOrdinal, total, resources.entrySet().stream()
                    .sorted(Map.Entry.<FlowResource, Long>comparingByValue().reversed())
                    .limit(TOP_RESOURCES)
                    .toList());
        }
    }

    private record Viewer(UUID networkId, TelemetryDictionary dictionary) {
    }

    private record Watcher(UUID networkId, Predicate<List<ChannelDrain>> sink) {
    }

    private final Map<ServerPlayer, Viewer> viewers = new HashMap<>();
    private final Map<Object, Watcher> watchers = new HashMap<>();
    private final Set<UUID> activeNetworks = new HashSet<>();
    private int tickCounter;

    public void subscribe(UUID networkId, ServerPlayer player, NetworkRegistry registry, MinecraftServer server) {
        viewers.put(player, new Viewer(networkId, new TelemetryDictionary()));
        activate(networkId, registry, server);
    }

    public void watch(Object key, UUID networkId, Predicate<List<ChannelDrain>> sink,
            NetworkRegistry registry, MinecraftServer server) {
        watchers.put(key, new Watcher(networkId, sink));
        activate(networkId, registry, server);
    }

    private void activate(UUID networkId, NetworkRegistry registry, MinecraftServer server) {
        boolean wasActive = activeNetworks.contains(networkId);
        rebuildActiveNetworks();
        LogisticsNetwork network = registry.getNetwork(networkId);
        if (!wasActive && network != null) {
            // Discard flow from before viewing
            drainNetwork(network, server);
        }
    }

    public void unsubscribe(ServerPlayer player) {
        if (viewers.remove(player) != null) {
            rebuildActiveNetworks();
        }
    }

    public boolean isActive(UUID networkId) {
        return activeNetworks.contains(networkId);
    }

    public void tick(NetworkRegistry registry, MinecraftServer server) {
        if (viewers.isEmpty() && watchers.isEmpty()) return;
        if (viewers.keySet().removeIf(p -> p.isRemoved() || !(p.containerMenu instanceof ComputerMenu))) {
            rebuildActiveNetworks();
        }
        if (++tickCounter < SYNC_INTERVAL) return;
        tickCounter = 0;

        Map<UUID, List<ChannelDrain>> drained = new HashMap<>();
        for (UUID networkId : activeNetworks) {
            LogisticsNetwork network = registry.getNetwork(networkId);
            if (network != null) {
                drained.put(networkId, drainNetwork(network, server));
            }
        }
        viewers.forEach((player, viewer) -> {
            List<ChannelDrain> channels = drained.get(viewer.networkId());
            if (channels != null) {
                PacketDistributor.sendToPlayer(player, viewer.dictionary().encode(viewer.networkId(), channels));
            }
        });
        if (watchers.values().removeIf(watcher -> {
            List<ChannelDrain> channels = drained.get(watcher.networkId());
            return channels == null || !watcher.sink().test(channels);
        })) {
            rebuildActiveNetworks();
        }
    }

    private static List<ChannelDrain> drainNetwork(LogisticsNetwork network, MinecraftServer server) {
        int count = LogisticsNodeEntity.CHANNEL_COUNT;
        long[] totals = new long[count];
        int[] types = new int[count];
        Arrays.fill(types, -1);
        List<Map<FlowResource, Long>> resources = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            resources.add(new HashMap<>());
        }

        for (UUID nodeId : network.getNodeUuids()) {
            LogisticsNodeEntity node = findNode(server, nodeId, network.getNodeDimension(nodeId));
            if (node == null) continue;
            for (int i = 0; i < count; i++) {
                ChannelData channel = node.getChannel(i);
                totals[i] += channel.getTelemetry().drainFlow();
                channel.getTelemetry().drainResources(resources.get(i));
                if (types[i] < 0 && channel.isEnabled()) {
                    types[i] = channel.getType().ordinal();
                }
            }
        }

        List<ChannelDrain> channels = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            channels.add(ChannelDrain.of(types[i], totals[i], resources.get(i)));
        }
        return channels;
    }

    private void rebuildActiveNetworks() {
        activeNetworks.clear();
        for (Viewer viewer : viewers.values()) {
            activeNetworks.add(viewer.networkId());
        }
        for (Watcher watcher : watchers.values()) {
            activeNetworks.add(watcher.networkId());
        }
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
