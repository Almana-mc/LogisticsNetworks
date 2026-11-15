package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.data.NodeRef;
import me.almana.logisticsnetworks.data.ServerRackLinks;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.integration.create.CreateCompat;
import me.almana.logisticsnetworks.logic.TransferEngine.ImportTarget;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class BridgedImports {

    // Matches LogisticsNetwork import order
    static final Comparator<ImportTarget> ORDER = Comparator
            .comparingInt((ImportTarget target) -> -target.channel().getPriority())
            .thenComparing(target -> target.node().getUUID());

    private BridgedImports() {
    }

    static List<ImportTarget> extend(List<ImportTarget> local, ChannelType type, int channel,
            List<ServerRackLinks.Link> links, NetworkRegistry registry, MinecraftServer server,
            NodeSignals signals, Map<UUID, Boolean> dimensionalCache) {
        List<ImportTarget> merged = null;
        for (ServerRackLinks.Link link : links) {
            if (link.channel() != channel) {
                continue;
            }
            LogisticsNetwork peer = registry.getNetwork(link.peer());
            if (peer == null) {
                continue;
            }
            if (peer.isCacheDirty()) {
                peer.rebuildCache(registry);
                peer.clearCacheDirty();
            }
            List<NodeRef> refs = NetworkImports.refs(peer)[type.ordinal()][link.peerChannel()];
            if (refs.isEmpty()) {
                continue;
            }
            if (merged == null) {
                merged = new ArrayList<>(local);
            }
            addTargets(refs, peer, link.peerChannel(), server, signals, merged, dimensionalCache);
        }
        if (merged == null) {
            return local;
        }
        merged.sort(ORDER);
        return merged;
    }

    private static void addTargets(List<NodeRef> refs, LogisticsNetwork peer, int channel, MinecraftServer server,
            NodeSignals signals, List<ImportTarget> out, Map<UUID, Boolean> dimensionalCache) {
        for (NodeRef ref : refs) {
            LogisticsNodeEntity node = TransferEngine.findNode(server, ref.nodeId(),
                    peer.getNodeDimension(ref.nodeId()));
            if (node == null || !node.isValidNode()) {
                continue;
            }
            ChannelData data = node.getChannel(channel);
            if (!TransferEngine.canRunChannel(node, data) || !CreateCompat.isResolved(node)
                    || !signals.active(node, data.getRedstoneMode())) {
                continue;
            }
            out.add(new ImportTarget(node, data, channel));
            dimensionalCache.put(node.getUUID(), peer.getDimensionalCache().getOrDefault(node.getUUID(), false));
        }
    }
}
