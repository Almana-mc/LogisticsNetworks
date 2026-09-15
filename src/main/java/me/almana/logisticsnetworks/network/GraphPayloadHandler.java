package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.data.graph.GraphChannel;
import me.almana.logisticsnetworks.data.graph.GraphNode;
import me.almana.logisticsnetworks.data.graph.GraphPosition;
import me.almana.logisticsnetworks.data.graph.NetworkGraph;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.integration.create.CreateCompat;
import me.almana.logisticsnetworks.integration.storage.LinkedStorage;
import me.almana.logisticsnetworks.integration.storage.StorageLink;
import me.almana.logisticsnetworks.logic.LabelUpgradeSync;
import me.almana.logisticsnetworks.logic.TransferEngine;
import me.almana.logisticsnetworks.menu.ComputerMenu;
import me.almana.logisticsnetworks.menu.FilterMenu;
import me.almana.logisticsnetworks.menu.GraphMenuContext;
import me.almana.logisticsnetworks.menu.NodeGraphMenu;
import me.almana.logisticsnetworks.menu.NodeMenuSync;
import me.almana.logisticsnetworks.upgrade.NodeUpgradeData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuConstructor;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GraphPayloadHandler {
    private GraphPayloadHandler() {
    }

    public static void handleOpen(RequestOpenGraphPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            GraphMenuContext requested = new GraphMenuContext(payload.computerPos(),
                    payload.computerDimension(), payload.networkId());
            if (!canOpen(player, requested) || !player.containerMenu.getCarried().isEmpty()) return;
            LogisticsNodeEntity node = payload.nodeId().map(id -> findNode(player.getServer(), id)).orElse(null);
            if (node != null && !requested.canEdit(player, node)) return;
            open(player, requested, node, payload.selectedChannel());
        });
    }

    private static boolean canOpen(ServerPlayer player, GraphMenuContext requested) {
        if (!requested.stillValid(player)) return false;
        if (player.containerMenu instanceof ComputerMenu menu) {
            return menu.getComputerPos().equals(requested.computerPos()) && menu.stillValid(player);
        }
        return requested.equals(getContext(player.containerMenu));
    }

    public static GraphMenuContext getContext(AbstractContainerMenu menu) {
        if (menu instanceof NodeGraphMenu graph) return graph.getGraphContext();
        if (menu instanceof FilterMenu filter) return filter.getGraphContext();
        if (menu instanceof me.almana.logisticsnetworks.menu.NodeMenu node) return node.getReturnContext();
        return null;
    }

    public static void open(ServerPlayer player, GraphMenuContext context, LogisticsNodeEntity node, int channel) {
        if (context.origin() == GraphMenuContext.Origin.TABLE) {
            openNodeSettings(player, context, node, channel);
            return;
        }
        int selectedChannel = Math.clamp(channel, 0, LogisticsNodeEntity.CHANNEL_COUNT - 1);
        boolean preserveCursor = getContext(player.containerMenu) != null;
        player.openMenu(new GraphMenuProvider(
                (id, inventory, ignored) -> new NodeGraphMenu(id, inventory, context, node, selectedChannel),
                Component.translatable("gui.logisticsnetworks.network_graph"), preserveCursor), buf -> {
                    context.write(buf);
                    buf.writeBoolean(node != null);
                    if (node != null) NodeMenuSync.write(buf, node, player.registryAccess(), selectedChannel);
                });
        if (player.containerMenu instanceof NodeGraphMenu menu) menu.sendNetworkListToClient(player);
        sendSnapshot(player);
    }

    public static void handleRequest(RequestNetworkGraphPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && authorized(player, payload.networkId())) {
                sendSnapshot(player);
            }
        });
    }

    public static void handleMove(MoveGraphVertexPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !authorized(player, payload.networkId())) return;
            moveVertices(player, payload.networkId(), Map.of(
                    payload.key(), new GraphPosition(payload.x(), payload.y())));
        });
    }

    public static void handleMoveVertices(MoveGraphVerticesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !authorized(player, payload.networkId())) return;
            if (payload.positions().isEmpty() || payload.positions().size() > MoveGraphVerticesPayload.MAX_VERTICES) return;
            moveVertices(player, payload.networkId(), payload.positions());
        });
    }

    public static void handleSetLabels(SetNodeLabelsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || payload.nodeIds().isEmpty() || payload.nodeIds().size() > SetNodeLabelsPayload.MAX_NODES) return;
            GraphMenuContext menuContext = labelContext(player, payload.networkId());
            if (menuContext == null) return;
            LogisticsNetwork network = NetworkRegistry.get(player.serverLevel()).getNetwork(payload.networkId());
            if (network == null) return;

            Set<UUID> ids = new LinkedHashSet<>(payload.nodeIds());
            if (ids.size() != payload.nodeIds().size()) return;
            List<LogisticsNodeEntity> nodes = new ArrayList<>(ids.size());
            for (UUID nodeId : ids) {
                LogisticsNodeEntity node = findNode(player.getServer(), nodeId);
                if (node == null || !menuContext.canEdit(player, node)) return;
                nodes.add(node);
            }
            String label = payload.label().trim();
            if (label.length() > 48) label = label.substring(0, 48);
            StorageLink link = LinkedStorage.findAccessibleLink(player, null);
            LabelUpgradeSync.synchronizeLabels(player, network, nodes, payload.settingsSource(), label, link);
        });
    }

    public static void handleOpenNodeSettings(RequestOpenNodeSettingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof ComputerMenu menu) || !menu.stillValid(player)) return;
            GraphMenuContext table = new GraphMenuContext(menu.getComputerPos(),
                    player.level().dimension().location(), payload.networkId(), GraphMenuContext.Origin.TABLE);
            LogisticsNodeEntity node = findNode(player.getServer(), payload.nodeId());
            if (node == null || !table.canEdit(player, node) || !player.containerMenu.getCarried().isEmpty()) return;
            openNodeSettings(player, table, node, 0);
        });
    }

    public static void handleReset(ResetGraphLayoutPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !authorized(player, payload.networkId())) return;
            NetworkRegistry registry = NetworkRegistry.get(player.serverLevel());
            registry.getNetwork(payload.networkId()).resetGraphPositions();
            registry.setDirty();
            broadcast(player.getServer(), payload.networkId());
        });
    }

    public static void handleReturn(ReturnToComputerPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !authorizedReturn(player, payload.networkId())) return;
            if (!player.containerMenu.getCarried().isEmpty()) return;
            GraphMenuContext graph = getContext(player.containerMenu);
            player.openMenu(new SimpleMenuProvider(
                    (id, inventory, ignored) -> new ComputerMenu(id, inventory, graph.computerPos()),
                    Component.translatable("block.logisticsnetworks.computer")),
                    buf -> buf.writeBlockPos(graph.computerPos()));
            if (player.containerMenu instanceof ComputerMenu menu) menu.requestNetworkList(player);
        });
    }

    private static boolean authorized(ServerPlayer player, UUID networkId) {
        GraphMenuContext graph = getContext(player.containerMenu);
        return graph != null && graph.origin() == GraphMenuContext.Origin.GRAPH
                && graph.networkId().equals(networkId) && graph.stillValid(player);
    }

    private static boolean authorizedReturn(ServerPlayer player, UUID networkId) {
        GraphMenuContext context = getContext(player.containerMenu);
        return context != null && context.networkId().equals(networkId) && context.stillValid(player);
    }

    private static GraphMenuContext labelContext(ServerPlayer player, UUID networkId) {
        GraphMenuContext context = getContext(player.containerMenu);
        if (context != null) {
            return context.networkId().equals(networkId) && context.stillValid(player) ? context : null;
        }
        if (!(player.containerMenu instanceof ComputerMenu menu) || !menu.stillValid(player)) return null;
        GraphMenuContext table = new GraphMenuContext(menu.getComputerPos(),
                player.level().dimension().location(), networkId, GraphMenuContext.Origin.TABLE);
        return table.stillValid(player) ? table : null;
    }

    private static void openNodeSettings(ServerPlayer player, GraphMenuContext context,
                                         LogisticsNodeEntity node, int channel) {
        if (node == null || !context.canEdit(player, node) || !player.containerMenu.getCarried().isEmpty()) return;
        int selectedChannel = Math.clamp(channel, 0, LogisticsNodeEntity.CHANNEL_COUNT - 1);
        player.openMenu(new GraphMenuProvider((id, inventory, ignored) -> {
            me.almana.logisticsnetworks.menu.NodeMenu menu =
                    new me.almana.logisticsnetworks.menu.NodeMenu(id, inventory, node);
            menu.setSelectedChannel(selectedChannel);
            menu.setRemoteAccess(true);
            menu.setReturnContext(context);
            return menu;
        }, Component.translatable("gui.logisticsnetworks.node_config"), true), buf -> {
            NodeMenuSync.write(buf, node, player.registryAccess(), selectedChannel);
            buf.writeBoolean(true);
            context.write(buf);
        });
        if (player.containerMenu instanceof me.almana.logisticsnetworks.menu.NodeMenu menu) {
            menu.sendNetworkListToClient(player);
        }
    }

    private static void moveVertices(ServerPlayer player, UUID networkId,
                                     Map<String, GraphPosition> requestedPositions) {
        for (Map.Entry<String, GraphPosition> entry : requestedPositions.entrySet()) {
            GraphPosition position = entry.getValue();
            if (entry.getKey().length() > 128 || !Float.isFinite(position.x()) || !Float.isFinite(position.y())
                    || Math.abs(position.x()) > 1_000_000 || Math.abs(position.y()) > 1_000_000) return;
        }
        NetworkRegistry registry = NetworkRegistry.get(player.serverLevel());
        LogisticsNetwork network = registry.getNetwork(networkId);
        if (network == null) return;
        Map<String, List<LogisticsNodeEntity>> vertices = new LinkedHashMap<>();
        for (GraphNode graphNode : loadedNodes(player.getServer(), network)) {
            LogisticsNodeEntity node = findNode(player.getServer(), graphNode.nodeId());
            if (node != null) vertices.computeIfAbsent(NetworkGraph.key(graphNode), key -> new ArrayList<>()).add(node);
        }
        for (String key : requestedPositions.keySet()) {
            List<LogisticsNodeEntity> members = vertices.get(key);
            if (members == null || members.isEmpty()) return;
            GraphMenuContext context = getContext(player.containerMenu);
            if (members.stream().anyMatch(node -> !context.canEdit(player, node))) return;
        }
        requestedPositions.forEach(network::setGraphPosition);
        registry.setDirty();
        broadcast(player.getServer(), networkId);
    }

    public static void preserveLabelPosition(LogisticsNodeEntity node, String newLabel) {
        if (node.getNetworkId() == null || !(node.level() instanceof ServerLevel level)) return;
        LogisticsNetwork network = NetworkRegistry.get(level).getNetwork(node.getNetworkId());
        if (network == null) return;
        String oldKey = node.getNodeLabel().isEmpty() ? "node:" + node.getUUID() : "label:" + node.getNodeLabel();
        String newKey = newLabel.isEmpty() ? "node:" + node.getUUID() : "label:" + newLabel;
        GraphPosition oldPosition = network.getGraphPositions().get(oldKey);
        if (oldPosition != null && !network.getGraphPositions().containsKey(newKey)) {
            boolean split = !node.getNodeLabel().isEmpty() && network.getNodeUuids().stream()
                    .filter(id -> !id.equals(node.getUUID()))
                    .map(id -> findNode(level.getServer(), id))
                    .anyMatch(other -> other != null && other.getNodeLabel().equals(node.getNodeLabel()));
            GraphPosition position = split ? new GraphPosition(oldPosition.x() + 46, oldPosition.y()) : oldPosition;
            network.setGraphPosition(newKey, position);
            NetworkRegistry.get(level).setDirty();
        }
    }

    public static void broadcast(MinecraftServer server, UUID networkId) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof NodeGraphMenu && authorized(player, networkId)) sendSnapshot(player);
        }
    }

    public static void refreshTable(ServerPlayer player, UUID networkId) {
        if (!(player.containerMenu instanceof ComputerMenu)) return;
        LogisticsNetwork network = NetworkRegistry.get(player.serverLevel()).getNetwork(networkId);
        if (network == null) return;
        List<SyncNetworkNodesPayload.NodeInfo> nodes = new ArrayList<>();
        for (UUID nodeId : network.getNodeUuids()) {
            LogisticsNodeEntity node = findNode(player.getServer(), nodeId);
            if (node == null || !node.isAlive() || !node.isValidNode() || !CreateCompat.isResolved(node)) continue;
            var attached = CreateCompat.getAttachedBlockState(node);
            nodes.add(new SyncNetworkNodesPayload.NodeInfo(nodeId, node.blockPosition(), node.getAttachedPos(),
                    BuiltInRegistries.BLOCK.getKey(attached.getBlock()).toString(), node.getNodeLabel(),
                    node.level().dimension().location(), node.isRenderVisible(), node.isHighlighted()));
        }
        PacketDistributor.sendToPlayer(player, new SyncNetworkNodesPayload(networkId, nodes));
    }

    private static void sendSnapshot(ServerPlayer player) {
        if (!(player.containerMenu instanceof NodeGraphMenu menu) || !menu.stillValid(player)) return;
        if (menu.getNode() != null && !menu.getGraphContext().canEdit(player, menu.getNode())) {
            open(player, menu.getGraphContext(), null, 0);
            return;
        }
        LogisticsNetwork network = NetworkRegistry.get(player.serverLevel()).getNetwork(menu.getGraphNetworkId());
        List<GraphNode> nodes = loadedNodes(player.getServer(), network);
        ensurePositions(player, network, nodes);
        PacketDistributor.sendToPlayer(player, new SyncNetworkGraphPayload(network.getId(), network.getName(),
                network.getNodeUuids().size(), nodes,
                new LinkedHashMap<>(network.getGraphPositions())));
        if (menu.getNode() != null) {
            for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
                PacketDistributor.sendToPlayer(player, new SyncChannelDataPayload(menu.getNodeId(), i,
                        menu.getNode().getChannel(i).save(player.registryAccess())));
            }
        }
    }

    private static void ensurePositions(ServerPlayer player, LogisticsNetwork network, List<GraphNode> nodes) {
        var vertices = new java.util.TreeMap<String, NetworkGraph.Vertex>();
        for (GraphNode node : nodes) {
            vertices.putIfAbsent(NetworkGraph.key(node),
                    new NetworkGraph.Vertex(NetworkGraph.key(node), node.label(), List.of(node)));
        }
        var positions = NetworkGraph.initialPositions(List.copyOf(vertices.values()), network.getGraphPositions());
        if (positions.equals(network.getGraphPositions())) return;
        positions.forEach(network::setGraphPosition);
        NetworkRegistry.get(player.serverLevel()).setDirty();
    }

    private static List<GraphNode> loadedNodes(MinecraftServer server, LogisticsNetwork network) {
        List<GraphNode> nodes = new ArrayList<>();
        for (UUID nodeId : network.getNodeUuids()) {
            LogisticsNodeEntity node = findNode(server, nodeId);
            if (node == null || !node.isAlive() || !node.isValidNode()
                    || !network.getId().equals(node.getNetworkId()) || !CreateCompat.isResolved(node)) continue;
            List<GraphChannel> channels = new ArrayList<>();
            for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
                ChannelData channel = node.getChannel(i);
                if (channel.isEnabled() && supportsChannel(node, channel)) {
                    channels.add(new GraphChannel(i, channel.getType(), channel.getMode()));
                }
            }
            String blockName = BuiltInRegistries.BLOCK.getKey(CreateCompat.getAttachedBlockState(node).getBlock()).toString();
            nodes.add(new GraphNode(nodeId, node.getNodeLabel(), blockName, node.getAttachedPos(),
                    node.level().dimension().location(), NodeUpgradeData.hasDimensionalUpgrade(node), channels));
        }
        nodes.sort(Comparator.comparing(GraphNode::nodeId));
        return nodes;
    }

    private static boolean supportsChannel(LogisticsNodeEntity node, ChannelData channel) {
        if (!TransferEngine.canRunChannel(node, channel)) return false;
        if (channel.getType() == ChannelType.CHEMICAL) return NodeUpgradeData.hasMekanismChemicalUpgrade(node);
        if (channel.getType() == ChannelType.SOURCE) return NodeUpgradeData.hasArsSourceUpgrade(node);
        return true;
    }

    private static LogisticsNodeEntity findNode(MinecraftServer server, UUID nodeId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(nodeId);
            if (entity instanceof LogisticsNodeEntity node) return node;
        }
        return null;
    }

    static final class GraphMenuProvider implements MenuProvider {
        private final MenuConstructor menuConstructor;
        private final Component title;
        private final boolean preserveCursor;

        GraphMenuProvider(MenuConstructor menuConstructor, Component title, boolean preserveCursor) {
            this.menuConstructor = menuConstructor;
            this.title = title;
            this.preserveCursor = preserveCursor;
        }

        @Override
        public Component getDisplayName() {
            return title;
        }

        @Override
        public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
            return menuConstructor.createMenu(containerId, inventory, player);
        }

        @Override
        public boolean shouldTriggerClientSideContainerClosingOnOpen() {
            return !preserveCursor;
        }
    }
}
