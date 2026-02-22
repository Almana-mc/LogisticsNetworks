package me.almana.logisticsnetworks.data;

import com.mojang.logging.LogUtils;
import me.almana.logisticsnetworks.logic.TransferEngine;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import org.slf4j.Logger;

import java.util.*;

public class NetworkRegistry extends SavedData {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA_NAME = "logistics_networks";
    private static final String KEY_NETWORKS = "Networks";

    // Limits & Warnings for beta
    private static final int WARNING_NODE_COUNT = 200;
    private static final int WARNING_DISPATCH_COUNT = 50;

    private final Map<UUID, LogisticsNetwork> networks = new HashMap<>();
    private final Set<UUID> dirtyNetworks = new HashSet<>();

    public NetworkRegistry() {
    }

    public static NetworkRegistry get(ServerLevel level) {
        DimensionDataStorage storage = level.getServer().overworld().getDataStorage();
        return storage.computeIfAbsent(new SavedData.Factory<>(
                NetworkRegistry::new,
                NetworkRegistry::load,
                null), DATA_NAME);
    }

    public void processDirtyNetworks(MinecraftServer server) {
        if (dirtyNetworks.isEmpty())
            return;

        if (dirtyNetworks.size() > WARNING_DISPATCH_COUNT) {
            LOGGER.warn("High load: Dispatching {} dirty networks in one tick.", dirtyNetworks.size());
        }

        Set<UUID> snapshot = new HashSet<>(dirtyNetworks);
        dirtyNetworks.clear();

        for (UUID id : snapshot) {
            LogisticsNetwork network = networks.get(id);
            if (network == null)
                continue;

            try {
                boolean moreWork = TransferEngine.processNetwork(network, server);
                if (moreWork) {
                    dirtyNetworks.add(id);
                }
            } catch (Exception e) {
                LOGGER.error("Error processing network {}: {}", id, e.getMessage(), e);
            }
        }
    }

    public LogisticsNetwork createNetwork() {
        return createNetwork(null);
    }

    public LogisticsNetwork createNetwork(@org.jetbrains.annotations.Nullable String name) {
        UUID id = UUID.randomUUID();
        LogisticsNetwork network = new LogisticsNetwork(id);
        if (name != null && !name.isBlank()) {
            network.setName(name);
        }
        networks.put(id, network);
        setDirty();
        return network;
    }

    public void deleteNetwork(UUID id) {
        if (networks.remove(id) != null) {
            dirtyNetworks.remove(id);
            setDirty();
        }
    }

    public LogisticsNetwork getNetwork(UUID id) {
        return networks.get(id);
    }

    public Map<UUID, LogisticsNetwork> getAllNetworks() {
        return Collections.unmodifiableMap(networks);
    }

    public void markNetworkDirty(UUID networkId) {
        if (networks.containsKey(networkId)) {
            dirtyNetworks.add(networkId);
            networks.get(networkId).markCacheDirty();
        }
    }

    public void addNodeToNetwork(UUID networkId, UUID nodeId) {
        LogisticsNetwork network = networks.get(networkId);
        if (network != null) {
            network.addNode(nodeId);
            if (network.getNodeUuids().size() > WARNING_NODE_COUNT) {
                LOGGER.warn("Network {} has exceeded {} nodes (Count: {}). Performance may degrade.",
                        networkId, WARNING_NODE_COUNT, network.getNodeUuids().size());
            }
            markNetworkDirty(networkId);
            setDirty();
        }
    }

    public void removeNodeFromNetwork(UUID networkId, UUID nodeId) {
        LogisticsNetwork network = networks.get(networkId);
        if (network != null) {
            network.removeNode(nodeId);
            markNetworkDirty(networkId);

            if (network.getNodeUuids().isEmpty()) {
                LOGGER.info("Network {} is empty, deleting.", networkId);
                deleteNetwork(networkId);
            }
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag compoundTag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (LogisticsNetwork network : networks.values()) {
            list.add(network.save());
        }
        compoundTag.put(KEY_NETWORKS, list);
        return compoundTag;
    }

    public static NetworkRegistry load(CompoundTag compoundTag, HolderLookup.Provider provider) {
        NetworkRegistry registry = new NetworkRegistry();
        if (compoundTag.contains(KEY_NETWORKS, Tag.TAG_LIST)) {
            ListTag list = compoundTag.getList(KEY_NETWORKS, Tag.TAG_COMPOUND);
            for (Tag t : list) {
                if (t instanceof CompoundTag ct) {
                    try {
                        LogisticsNetwork network = LogisticsNetwork.load(ct);
                        registry.networks.put(network.getId(), network);
                    } catch (Exception e) {
                        LOGGER.error("Skipping malformed network: {}", e.getMessage());
                    }
                }
            }
        }
        if (!registry.networks.isEmpty()) {
            registry.dirtyNetworks.addAll(registry.networks.keySet());
            LOGGER.info("Loaded {} networks.", registry.networks.size());
        }

        return registry;
    }
}
