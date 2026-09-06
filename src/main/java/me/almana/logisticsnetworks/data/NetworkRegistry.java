package me.almana.logisticsnetworks.data;

import com.mojang.logging.LogUtils;
import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.NodeAccessMode;
import me.almana.logisticsnetworks.integration.ftbteams.FTBTeamsCompat;
import me.almana.logisticsnetworks.logic.NodeAccessPolicy;
import me.almana.logisticsnetworks.logic.TelemetryManager;
import me.almana.logisticsnetworks.logic.async.AsyncTransferRuntime;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;

import org.slf4j.Logger;

import java.util.*;
import java.util.function.BooleanSupplier;
import org.jetbrains.annotations.Nullable;

public class NetworkRegistry extends SavedData {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA_NAME = "logistics_networks";
    private static final String KEY_NETWORKS = "Networks";
    private static final SavedDataType<NetworkRegistry> DATA_TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("logisticsnetworks", DATA_NAME),
            NetworkRegistry::new,
            CompoundTag.CODEC.xmap(NetworkRegistry::load, NetworkRegistry::saveTag));

    // Limits & Warnings for beta
    private static final int WARNING_NODE_COUNT = 200;

    private final Map<UUID, LogisticsNetwork> networks = new HashMap<>();
    private final NetworkDispatcher dispatcher = new NetworkDispatcher();
    private long reloadVersion = AsyncTransferRuntime.reloadVersion();
    private final TelemetryManager telemetryManager = new TelemetryManager();

    public NetworkRegistry() {
    }

    public static NetworkRegistry get(ServerLevel level) {
        SavedDataStorage storage = level.getServer().overworld().getDataStorage();
        return storage.computeIfAbsent(DATA_TYPE);
    }

    public void processDirtyNetworks(MinecraftServer server) {
        if (Config.networkTickingEnabled) dispatcher.processDirtyNetworks(networks, server);
    }

    public boolean refreshAsyncPlanning() {
        long requestedVersion = AsyncTransferRuntime.reloadVersion();
        if (reloadVersion != requestedVersion) {
            reloadVersion = requestedVersion;
            dispatcher.resetForReload();
            networks.keySet().forEach(this::invalidateNetwork);
            AsyncTransferRuntime.stop();
        }
        return dispatcher.refreshAsyncMode(Config.asyncPlanning && Config.networkTickingEnabled);
    }

    public void dispatchDirty(MinecraftServer server) {
        if (Config.networkTickingEnabled) dispatcher.dispatchDirty(this, networks, server);
    }

    public void commitCompleted(MinecraftServer server, BooleanSupplier hasTime) {
        if (Config.networkTickingEnabled) dispatcher.commitCompleted(networks, server, hasTime);
    }

    public void processDegradedRecovery(MinecraftServer server) {
        if (Config.networkTickingEnabled) dispatcher.processDegradedRecovery(networks, server);
    }

    public void stopAsyncPlanning() {
        dispatcher.shutdown();
    }

    public LogisticsNetwork createNetwork() {
        return createNetwork(null, null);
    }

    public LogisticsNetwork createNetwork(@Nullable String name,
            @Nullable UUID ownerUuid) {
        UUID id = UUID.randomUUID();
        LogisticsNetwork network = new LogisticsNetwork(id);
        if (name != null && !name.isBlank()) {
            network.setName(name);
        }
        network.setOwnerUuid(ownerUuid);
        networks.put(id, network);
        setDirty();
        return network;
    }

    public List<LogisticsNetwork> getNetworksForPlayer(UUID playerUuid) {
        Set<UUID> teammateIds = Config.nodeAccessMode == NodeAccessMode.ALL
                ? Collections.emptySet()
                : FTBTeamsCompat.getTeammateIds(playerUuid);
        List<LogisticsNetwork> result = new ArrayList<>();
        for (LogisticsNetwork network : networks.values()) {
            if (NodeAccessPolicy.canAccess(network.getOwnerUuid(), playerUuid, teammateIds)) {
                result.add(network);
            }
        }
        return result;
    }

    public void deleteNetwork(UUID id) {
        if (networks.remove(id) != null) {
            dispatcher.delete(id);
            setDirty();
        }
    }

    public LogisticsNetwork getNetwork(UUID id) {
        return networks.get(id);
    }

    public Map<UUID, LogisticsNetwork> getAllNetworks() {
        return Collections.unmodifiableMap(networks);
    }

    public TelemetryManager getTelemetryManager() {
        return telemetryManager;
    }

    public void wakeNetwork(UUID networkId) {
        if (networks.containsKey(networkId)) dispatcher.markDirty(networkId);
    }

    public void invalidateNetwork(UUID networkId) {
        LogisticsNetwork network = networks.get(networkId);
        if (network != null) {
            dispatcher.markDirty(networkId);
            network.markCacheDirty();
        }
    }

    public void addNodeToNetwork(UUID networkId, UUID nodeId) {
        LogisticsNetwork network = networks.get(networkId);
        if (network != null) {
            network.addNode(nodeId);
            if (network.getNodeUuids().size() > WARNING_NODE_COUNT) {
                if (Config.debugMode) LOGGER.warn("Network {} has exceeded {} nodes (Count: {}). Performance may degrade.",
                        networkId, WARNING_NODE_COUNT, network.getNodeUuids().size());
            }
            dispatcher.markDirty(networkId);
            setDirty();
        }
    }

    public void removeNodeFromNetwork(UUID networkId, UUID nodeId) {
        LogisticsNetwork network = networks.get(networkId);
        if (network != null) {
            network.removeNode(nodeId);
            dispatcher.markDirty(networkId);

            if (network.getNodeUuids().isEmpty()) {
                if (Config.debugMode) LOGGER.info("Network {} is empty, deleting.", networkId);
                deleteNetwork(networkId);
            }
            setDirty();
        }
    }

    public CompoundTag saveTag() {
        CompoundTag compoundTag = new CompoundTag();
        ListTag list = new ListTag();
        for (LogisticsNetwork network : networks.values()) {
            list.add(network.save());
        }
        compoundTag.put(KEY_NETWORKS, list);
        return compoundTag;
    }

    public static NetworkRegistry load(CompoundTag compoundTag) {
        NetworkRegistry registry = new NetworkRegistry();
        boolean assignedDefaultColor = false;
        if (compoundTag.contains(KEY_NETWORKS)) {
            ListTag list = compoundTag.getListOrEmpty(KEY_NETWORKS);
            for (Tag t : list) {
                if (t instanceof CompoundTag ct) {
                    try {
                        if (!ct.contains("Color")) {
                            assignedDefaultColor = true;
                        }
                        LogisticsNetwork network = LogisticsNetwork.load(ct);
                        registry.networks.put(network.getId(), network);
                    } catch (Exception e) {
                        if (Config.debugMode) LOGGER.error("Skipping malformed network: {}", e.getMessage());
                    }
                }
            }
        }
        if (!registry.networks.isEmpty()) {
            registry.networks.keySet().forEach(registry.dispatcher::markDirty);
            if (Config.debugMode) LOGGER.info("Loaded {} networks.", registry.networks.size());
        }
        if (assignedDefaultColor) {
            registry.setDirty();
        }

        return registry;
    }
}
