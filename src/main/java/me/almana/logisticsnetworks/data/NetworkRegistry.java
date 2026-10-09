package me.almana.logisticsnetworks.data;

import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.NodeAccessMode;
import me.almana.logisticsnetworks.component.ComponentCodecs;
import me.almana.logisticsnetworks.integration.ftbteams.FTBTeamsCompat;
import me.almana.logisticsnetworks.logic.NodeAccessPolicy;
import me.almana.logisticsnetworks.logic.TelemetryManager;
import me.almana.logisticsnetworks.logic.async.AsyncTransferRuntime;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;

import org.slf4j.Logger;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

public class NetworkRegistry extends SavedData {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA_NAME = "logistics_networks";
    private static final String KEY_NETWORKS = "networks";
    private static final String LEGACY_KEY_NETWORKS = "Networks";
    // Undecodable networks stay raw
    private static final Codec<NetworkRegistry> CODEC = Codec.of(
            Codec.either(LogisticsNetwork.CODEC, ComponentCodecs.TAG).listOf().fieldOf(KEY_NETWORKS).codec()
                    .comap(NetworkRegistry::entries),
            Codec.PASSTHROUGH.map(NetworkRegistry::load));
    private static final SavedDataType<NetworkRegistry> DATA_TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("logisticsnetworks", DATA_NAME),
            NetworkRegistry::new,
            CODEC);

    // Limits & Warnings for beta
    private static final int WARNING_NODE_COUNT = 200;

    private final Map<UUID, LogisticsNetwork> networks = new HashMap<>();
    private final List<Tag> undecodableNetworks = new ArrayList<>();
    private final NetworkDispatcher dispatcher = new NetworkDispatcher();
    private long reloadVersion = AsyncTransferRuntime.reloadVersion();
    private final TelemetryManager telemetryManager = new TelemetryManager();
    private final ServerRackLinks rackLinks = new ServerRackLinks();

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

    public Collection<LogisticsNetwork> getVisibleNetworks(ServerPlayer player) {
        return NodeAccessPolicy.isAdminMode(player)
                ? getAllNetworks().values()
                : getNetworksForPlayer(player.getUUID());
    }

    public void deleteNetwork(UUID id) {
        if (networks.remove(id) != null) {
            dispatcher.delete(id);
            setDirty();
        }
    }

    public void stampCreatedAt(UUID networkId) {
        LogisticsNetwork network = networks.get(networkId);
        if (network != null && network.stampCreatedAtIfMissing()) {
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

    public void wakeNetwork(UUID networkId) {
        if (networks.containsKey(networkId)) markDirty(networkId);
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
            if (network.getNodeUuids().size() > WARNING_NODE_COUNT) {
                if (Config.debugMode) LOGGER.warn("Network {} has exceeded {} nodes (Count: {}). Performance may degrade.",
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

    private List<Either<LogisticsNetwork, Tag>> entries() {
        List<Either<LogisticsNetwork, Tag>> entries = new ArrayList<>();
        networks.values().forEach(network -> entries.add(Either.left(network)));
        undecodableNetworks.forEach(tag -> entries.add(Either.right(tag)));
        return entries;
    }

    private static NetworkRegistry load(Dynamic<?> data) {
        NetworkRegistry registry = new NetworkRegistry();
        String key = data.get(KEY_NETWORKS).result().isPresent() ? KEY_NETWORKS : LEGACY_KEY_NETWORKS;
        for (Dynamic<?> entry : data.get(key).asList(Function.identity())) {
            ComponentCodecs.parse(LogisticsNetwork.CODEC, entry).ifPresentOrElse(
                    network -> registry.networks.put(network.getId(), network),
                    () -> registry.undecodableNetworks.add(entry.convert(NbtOps.INSTANCE).getValue()));
        }
        if (!registry.networks.isEmpty()) {
            registry.networks.keySet().forEach(registry.dispatcher::markDirty);
            if (Config.debugMode) LOGGER.info("Loaded {} networks.", registry.networks.size());
        }
        // Persist legacy-format random colours
        if (data.get(LEGACY_KEY_NETWORKS).result().isPresent()) {
            registry.setDirty();
        }
        return registry;
    }
}
