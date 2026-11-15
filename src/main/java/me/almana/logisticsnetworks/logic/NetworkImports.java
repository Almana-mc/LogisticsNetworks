package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.data.NodeRef;
import me.almana.logisticsnetworks.data.ServerRackLinks;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.logic.TransferEngine.ImportTarget;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// Import targets resolved on demand
public final class NetworkImports {
    private final List<NodeRef>[][] refs;
    private final Map<UUID, LogisticsNodeEntity> nodes;
    private final NodeSignals signals;
    private final List<ServerRackLinks.Link> links;
    private final NetworkRegistry registry;
    private final MinecraftServer server;
    private final Map<UUID, Boolean> dimensionalCache;
    @SuppressWarnings("unchecked")
    private final List<ImportTarget>[][] resolved =
            new List[ChannelType.values().length][LogisticsNodeEntity.CHANNEL_COUNT];

    NetworkImports(LogisticsNetwork network, Map<UUID, LogisticsNodeEntity> nodes, NodeSignals signals,
            List<ServerRackLinks.Link> links, NetworkRegistry registry, MinecraftServer server,
            Map<UUID, Boolean> dimensionalCache) {
        this.refs = refs(network);
        this.nodes = nodes;
        this.signals = signals;
        this.links = links;
        this.registry = registry;
        this.server = server;
        this.dimensionalCache = dimensionalCache;
    }

    public List<ImportTarget> get(ChannelType type, int channel) {
        List<ImportTarget>[] row = resolved[type.ordinal()];
        if (row[channel] == null) {
            List<NodeRef> local = refs[type.ordinal()][channel];
            List<ImportTarget> targets = local.isEmpty() ? List.of()
                    : TransferEngine.resolveImports(local, channel, nodes, signals);
            row[channel] = links.isEmpty() ? targets
                    : BridgedImports.extend(targets, type, channel, links, registry, server, signals,
                            dimensionalCache);
        }
        return row[channel];
    }

    // Indexed by ChannelType ordinal
    @SuppressWarnings("unchecked")
    static List<NodeRef>[][] refs(LogisticsNetwork network) {
        return new List[][] { network.getItemImports(), network.getFluidImports(), network.getEnergyImports(),
                network.getChemicalImports(), network.getSourceImports() };
    }
}
