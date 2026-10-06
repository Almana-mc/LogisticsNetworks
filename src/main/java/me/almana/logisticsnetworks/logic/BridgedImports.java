package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.data.NodeRef;
import me.almana.logisticsnetworks.data.RedstoneMode;
import me.almana.logisticsnetworks.data.ServerRackLinks;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.integration.create.CreateCompat;
import me.almana.logisticsnetworks.logic.TransferEngine.ImportTarget;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class BridgedImports {

    // Matches LogisticsNetwork import order
    private static final Comparator<ImportTarget> ORDER = Comparator
            .comparingInt((ImportTarget target) -> -target.channel().getPriority())
            .thenComparing(target -> target.node().getUUID());

    private BridgedImports() {
    }

    @SuppressWarnings("unchecked")
    static void merge(List<ServerRackLinks.Link> links, NetworkRegistry registry, MinecraftServer server,
            List<ImportTarget>[][] imports, Map<UUID, Boolean> dimensionalCache) {
        for (int type = 0; type < imports.length; type++) {
            imports[type] = imports[type].clone();
        }
        for (ServerRackLinks.Link link : links) {
            LogisticsNetwork peer = registry.getNetwork(link.peer());
            if (peer == null) {
                continue;
            }
            if (peer.isCacheDirty()) {
                peer.rebuildCache(registry);
                peer.clearCacheDirty();
            }
            List<NodeRef>[][] peerRefs = new List[][] { peer.getItemImports(), peer.getFluidImports(),
                    peer.getEnergyImports(), peer.getChemicalImports(), peer.getSourceImports() };
            for (int type = 0; type < imports.length; type++) {
                List<NodeRef> refs = peerRefs[type][link.peerChannel()];
                if (refs.isEmpty()) {
                    continue;
                }
                List<ImportTarget> slot = new ArrayList<>(imports[type][link.channel()]);
                addTargets(refs, peer, link.peerChannel(), server, slot, dimensionalCache);
                List<ImportTarget> merged = new ArrayList<>(new LinkedHashSet<>(slot));
                merged.sort(ORDER);
                imports[type][link.channel()] = merged;
            }
        }
    }

    private static void addTargets(List<NodeRef> refs, LogisticsNetwork peer, int channel, MinecraftServer server,
            List<ImportTarget> out, Map<UUID, Boolean> dimensionalCache) {
        for (NodeRef ref : refs) {
            LogisticsNodeEntity node = TransferEngine.findNode(server, ref.nodeId(),
                    peer.getNodeDimension(ref.nodeId()));
            if (node == null || !node.isValidNode()) {
                continue;
            }
            ChannelData data = node.getChannel(channel);
            if (!TransferEngine.canRunChannel(node, data) || !CreateCompat.isResolved(node)
                    || !TransferEngine.isRedstoneActive(data.getRedstoneMode(), signal(node, data))) {
                continue;
            }
            out.add(new ImportTarget(node, data, channel));
            dimensionalCache.put(node.getUUID(), peer.getDimensionalCache().getOrDefault(node.getUUID(), false));
        }
    }

    private static int signal(LogisticsNodeEntity node, ChannelData data) {
        if (data.getRedstoneMode() == RedstoneMode.IGNORED || node.isMountedOnCreate()) {
            return 0;
        }
        return node.level().getBestNeighborSignal(node.getAttachedPos());
    }
}
