package me.almana.logisticsnetworks.data;

import com.mojang.logging.LogUtils;
import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.component.ComponentCodecs;
import me.almana.logisticsnetworks.logic.NodeAccessPolicy;
import me.almana.logisticsnetworks.logic.TelemetryManager;
import me.almana.logisticsnetworks.logic.TransferCapabilityCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import org.slf4j.Logger;

import java.util.*;
import java.util.function.BooleanSupplier;
import org.jetbrains.annotations.Nullable;

public class NetworkRegistry extends SavedData {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA_NAME = "logistics_networks";
    private static final String KEY_NETWORKS = "networks";
    private static final String LEGACY_KEY_NETWORKS = "Networks";

    // Limits & Warnings for beta
    private static final int WARNING_NODE_COUNT = 200;
    private final Map<UUID, LogisticsNetwork> networks = new HashMap<>();
    private final List<Tag> undecodableNetworks = new ArrayList<>();
    private final TelemetryManager telemetryManager = new TelemetryManager();
    private final TransferCapabilityCache capabilityCache = new TransferCapabilityCache();
    private final NetworkDispatcher dispatcher = new NetworkDispatcher();
    private final ServerRackLinks rackLinks = new ServerRackLinks();

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
        dispatcher.processDirtyNetworks(networks, server);
    }

    public boolean refreshAsyncPlanning() {
        return dispatcher.refreshAsyncMode(Config.asyncPlanning);
    }

    public void dispatchDirty(MinecraftServer server) {
        dispatcher.dispatchDirty(this, networks, server, capabilityCache);
    }

    public void commitCompleted(MinecraftServer server, BooleanSupplier hasTime) {
        dispatcher.commitCompleted(networks, server, capabilityCache, hasTime);
    }

    public void processDegradedRecovery(MinecraftServer server) {
        dispatcher.processDegradedRecovery(networks, server);
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
        List<LogisticsNetwork> result = new ArrayList<>();
        for (LogisticsNetwork network : networks.values()) {
            if (NodeAccessPolicy.canAccess(network.getOwnerUuid(), playerUuid)) {
                result.add(network);
            }
        }
        return result;
    }

    public Collection<LogisticsNetwork> getVisibleNetworks(ServerPlayer player) {
        return NodeAccessPolicy.isAdminMode(player)
                ? getAllNetworks().values()
                : getNetworksForPlayer(player.getUUID());
    }

    public void deleteNetwork(UUID id) {
        boolean removed = networks.remove(id) != null;
        dispatcher.delete(id);
        if (removed) {
            setDirty();
        }
    }

    public LogisticsNetwork getNetwork(UUID id) {
        return networks.get(id);
    }

    public void stampCreatedAt(UUID networkId) {
        LogisticsNetwork network = networks.get(networkId);
        if (network != null && network.stampCreatedAtIfMissing()) {
            setDirty();
        }
    }

    public Map<UUID, LogisticsNetwork> getAllNetworks() {
        return Collections.unmodifiableMap(networks);
    }

    public TelemetryManager getTelemetryManager() {
        return telemetryManager;
    }

    public TransferCapabilityCache getCapabilityCache() {
        return capabilityCache;
    }

    public ServerRackLinks getRackLinks() {
        return rackLinks;
    }

    public void putRack(GlobalPos pos, ServerRackConfig config) {
        rackLinks.put(pos, config).forEach(this::invalidateNetwork);
    }

    public void removeRack(GlobalPos pos) {
        rackLinks.remove(pos).forEach(this::invalidateNetwork);
    }

    // Rack peers share wakeups
    private void markDirty(UUID networkId) {
        dispatcher.markDirty(networkId);
        for (ServerRackLinks.Link link : rackLinks.linksFor(networkId)) {
            if (networks.containsKey(link.peer())) {
                dispatcher.markDirty(link.peer());
            }
        }
    }

    public void evictCapabilities(ServerLevel level, BlockPos attachedPos) {
        capabilityCache.evict(level.dimension(), attachedPos);
    }

    public void wakeNetwork(UUID networkId) {
        if (networks.containsKey(networkId)) {
            markDirty(networkId);
        }
    }

    public void invalidateNetwork(UUID networkId) {
        LogisticsNetwork network = networks.get(networkId);
        if (network != null) {
            markDirty(networkId);
            network.markCacheDirty();
        }
    }

    public void addNodeToNetwork(UUID networkId, UUID nodeId) {
        LogisticsNetwork network = networks.get(networkId);
        if (network != null) {
            network.addNode(nodeId);
            if (Config.debugMode && network.getNodeUuids().size() > WARNING_NODE_COUNT) {
                LOGGER.warn("Network {} has exceeded {} nodes (Count: {}). Performance may degrade.",
                        networkId, WARNING_NODE_COUNT, network.getNodeUuids().size());
            }
            markDirty(networkId);
            setDirty();
        }
    }

    public void removeNodeFromNetwork(UUID networkId, UUID nodeId) {
        LogisticsNetwork network = networks.get(networkId);
        if (network != null) {
            network.removeNode(nodeId);
            markDirty(networkId);

            if (network.getNodeUuids().isEmpty()) {
                if (Config.debugMode) LOGGER.info("Network {} is empty, deleting.", networkId);
                deleteNetwork(networkId);
            }
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag compoundTag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (LogisticsNetwork network : networks.values()) {
            list.add(ComponentCodecs.encode(LogisticsNetwork.CODEC, provider, network));
        }
        undecodableNetworks.forEach(tag -> list.add(tag.copy()));
        compoundTag.put(KEY_NETWORKS, list);
        return compoundTag;
    }

    public static NetworkRegistry load(CompoundTag compoundTag, HolderLookup.Provider provider) {
        NetworkRegistry registry = new NetworkRegistry();
        String key = compoundTag.contains(KEY_NETWORKS, Tag.TAG_LIST) ? KEY_NETWORKS : LEGACY_KEY_NETWORKS;
        for (Tag tag : compoundTag.getList(key, Tag.TAG_COMPOUND)) {
            ComponentCodecs.parse(LogisticsNetwork.CODEC, provider, tag).ifPresentOrElse(
                    network -> registry.networks.put(network.getId(), network),
                    () -> registry.undecodableNetworks.add(tag.copy()));
        }
        if (!registry.networks.isEmpty()) {
            registry.networks.keySet().forEach(registry.dispatcher::markDirty);
            if (Config.debugMode) LOGGER.info("Loaded {} networks.", registry.networks.size());
        }
        // Persist legacy-format random colours
        if (compoundTag.contains(LEGACY_KEY_NETWORKS)) {
            registry.setDirty();
        }
        return registry;
    }
}
