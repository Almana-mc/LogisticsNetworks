package me.almana.logisticsnetworks.integration.computercraft;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.NotAttachedException;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import me.almana.logisticsnetworks.block.ComputerBlockEntity;
import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.logic.NodeAccessPolicy;
import me.almana.logisticsnetworks.logic.TelemetryManager;
import me.almana.logisticsnetworks.network.GraphPayloadHandler;
import me.almana.logisticsnetworks.network.ServerPayloadHandler;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ComputerPeripheral implements IPeripheral {

    private final ComputerBlockEntity computer;
    private final Map<IComputerAccess, Set<UUID>> watching = new ConcurrentHashMap<>();
    private final Map<WatchKey, Map<String, Object>> lastSamples = new ConcurrentHashMap<>();

    private record WatchKey(IComputerAccess access, UUID networkId) {
    }

    public ComputerPeripheral(ComputerBlockEntity computer) {
        this.computer = computer;
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(PeripheralCapability.get(), Registration.COMPUTER_BLOCK_ENTITY.get(),
                (computer, side) -> new ComputerPeripheral(computer));
    }

    @Override
    public String getType() {
        return "logistics_computer";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof ComputerPeripheral peripheral && peripheral.computer == computer;
    }

    @Override
    public void detach(IComputerAccess access) {
        watching.remove(access);
        lastSamples.keySet().removeIf(key -> key.access() == access);
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> listNetworks() throws LuaException {
        Set<UUID> starred = computer.getStarredNetworks();
        return registry().getNetworksForPlayer(owner()).stream()
                .map(network -> LuaTables.network(network, starred.contains(network.getId())))
                .toList();
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getChannels(String net) throws LuaException {
        LogisticsNetwork network = resolve(net);
        return LuaTables.channels(network, ServerPayloadHandler.channelEntries(network, server()));
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getNodes(String net) throws LuaException {
        return ServerPayloadHandler.nodeInfos(resolve(net), server()).stream().map(LuaTables::node).toList();
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getGraph(String net) throws LuaException {
        LogisticsNetwork network = resolve(net);
        return LuaTables.graph(network, GraphPayloadHandler.loadedNodes(server(), network));
    }

    @LuaFunction(mainThread = true)
    public final boolean watch(IComputerAccess access, String net) throws LuaException {
        UUID networkId = resolve(net).getId();
        watching.computeIfAbsent(access, ignored -> ConcurrentHashMap.newKeySet()).add(networkId);
        WatchKey key = new WatchKey(access, networkId);
        registry().getTelemetryManager().watch(key, networkId, channels -> deliver(key, channels),
                registry(), server());
        return true;
    }

    @LuaFunction(mainThread = true)
    public final boolean unwatch(IComputerAccess access, String net) throws LuaException {
        UUID networkId = resolve(net).getId();
        Set<UUID> networks = watching.get(access);
        if (networks != null) {
            networks.remove(networkId);
        }
        lastSamples.remove(new WatchKey(access, networkId));
        return true;
    }

    @LuaFunction(mainThread = true)
    public final @Nullable Map<String, Object> getFlow(IComputerAccess access, String net) throws LuaException {
        UUID networkId = resolve(net).getId();
        if (!isWatching(access, networkId)) {
            throw new LuaException("Network is not watched; call watch first");
        }
        return lastSamples.get(new WatchKey(access, networkId));
    }

    private boolean deliver(WatchKey key, List<TelemetryManager.ChannelDrain> channels) {
        if (!isWatching(key.access(), key.networkId()) || !canAccess(registry().getNetwork(key.networkId()))) {
            lastSamples.remove(key);
            return false;
        }
        Map<String, Object> sample = LuaTables.sample(channels);
        lastSamples.put(key, sample);
        try {
            key.access().queueEvent("logistics_flow", key.access().getAttachmentName(),
                    key.networkId().toString(), sample);
            return true;
        } catch (NotAttachedException e) {
            detach(key.access());
            return false;
        }
    }

    private boolean isWatching(IComputerAccess access, UUID networkId) {
        Set<UUID> networks = watching.get(access);
        return networks != null && networks.contains(networkId);
    }

    private boolean canAccess(LogisticsNetwork network) {
        UUID owner = computer.getOwner();
        return owner != null && NodeAccessPolicy.canAccess(network.getOwnerUuid(), owner);
    }

    private LogisticsNetwork resolve(String net) throws LuaException {
        List<LogisticsNetwork> matches = registry().getNetworksForPlayer(owner()).stream()
                .filter(network -> network.getId().toString().equals(net) || net.equals(network.getName()))
                .toList();
        if (matches.isEmpty()) {
            throw new LuaException("Unknown network '" + net + "'");
        }
        if (matches.size() > 1) {
            throw new LuaException("Multiple networks named '" + net + "'; use the id");
        }
        return matches.getFirst();
    }

    private UUID owner() throws LuaException {
        UUID owner = computer.getOwner();
        if (owner == null) {
            throw new LuaException("Logistics Computer has no owner; break and place it again");
        }
        return owner;
    }

    private NetworkRegistry registry() {
        return NetworkRegistry.get((ServerLevel) computer.getLevel());
    }

    private MinecraftServer server() {
        return computer.getLevel().getServer();
    }
}
