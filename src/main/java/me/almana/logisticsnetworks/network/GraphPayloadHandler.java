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
import java.util.List;
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
            LogisticsNodeEntity node = payload.nodeId().map(id -> findNode(player.level().getServer(), id)).orElse(null);
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
        return null;
    }

    public static void open(ServerPlayer player, GraphMenuContext context, LogisticsNodeEntity node, int channel) {
        int selectedChannel = Math.clamp(channel, 0, LogisticsNodeEntity.CHANNEL_COUNT - 1);
        boolean preserveCursor = getContext(player.containerMenu) != null;
        player.openMenu(new GraphMenuProvider(
                (id, inventory, ignored) -> new NodeGraphMenu(id, inventory, context, node, selectedChannel),
                Component.translatable("gui.logisticsnetworks.graph.title"), preserveCursor), buf -> {
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
            if (!Float.isFinite(payload.x()) || !Float.isFinite(payload.y())
                    || Math.abs(payload.x()) > 1_000_000 || Math.abs(payload.y()) > 1_000_000) return;
            NetworkRegistry registry = NetworkRegistry.get(player.level());
            LogisticsNetwork network = registry.getNetwork(payload.networkId());
            List<GraphNode> nodes = loadedNodes(player.level().getServer(), network);
            if (nodes.stream().noneMatch(node -> NetworkGraph.key(node).equals(payload.key()))) return;
            network.setGraphPosition(payload.key(), new GraphPosition(payload.x(), payload.y()));
            registry.setDirty();
            broadcast(player.level().getServer(), network.getId());
        });
    }

    public static void handleReset(ResetGraphLayoutPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !authorized(player, payload.networkId())) return;
            NetworkRegistry registry = NetworkRegistry.get(player.level());
            registry.getNetwork(payload.networkId()).resetGraphPositions();
            registry.setDirty();
            broadcast(player.level().getServer(), payload.networkId());
        });
    }

    public static void handleReturn(ReturnToComputerPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !authorized(player, payload.networkId())) return;
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
        return graph != null && graph.networkId().equals(networkId) && graph.stillValid(player);
    }

    public static void preserveLabelPosition(LogisticsNodeEntity node, String newLabel) {
        if (node.getNetworkId() == null || !(node.level() instanceof ServerLevel level)) return;
        LogisticsNetwork network = NetworkRegistry.get(level).getNetwork(node.getNetworkId());
        if (network == null) return;
        String oldLabel = node.getNodeLabel().trim();
        newLabel = newLabel.trim();
        String oldKey = oldLabel.isEmpty() ? "node:" + node.getUUID() : "label:" + oldLabel;
        String newKey = newLabel.isEmpty() ? "node:" + node.getUUID() : "label:" + newLabel;
        GraphPosition oldPosition = network.getGraphPositions().get(oldKey);
        if (oldPosition != null && !network.getGraphPositions().containsKey(newKey)) {
            boolean split = !oldLabel.isEmpty() && network.getNodeUuids().stream()
                    .filter(id -> !id.equals(node.getUUID()))
                    .map(id -> findNode(level.getServer(), id))
                    .anyMatch(other -> other != null && other.getNodeLabel().trim().equals(oldLabel));
            float offset = oldPosition.x() + 46 <= 1_000_000 ? 46 : -46;
            GraphPosition position = split ? new GraphPosition(oldPosition.x() + offset, oldPosition.y()) : oldPosition;
            network.setGraphPosition(newKey, position);
            NetworkRegistry.get(level).setDirty();
        }
    }

    public static void broadcast(MinecraftServer server, UUID networkId) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof NodeGraphMenu && authorized(player, networkId)) sendSnapshot(player);
        }
    }

    private static void sendSnapshot(ServerPlayer player) {
        if (!(player.containerMenu instanceof NodeGraphMenu menu) || !menu.stillValid(player)) return;
        if (menu.getNode() != null && !menu.getGraphContext().canEdit(player, menu.getNode())) {
            open(player, menu.getGraphContext(), null, 0);
            return;
        }
        LogisticsNetwork network = NetworkRegistry.get(player.level()).getNetwork(menu.getGraphNetworkId());
        List<GraphNode> nodes = loadedNodes(player.level().getServer(), network);
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
        NetworkRegistry.get(player.level()).setDirty();
    }

    private static List<GraphNode> loadedNodes(MinecraftServer server, LogisticsNetwork network) {
        List<GraphNode> nodes = new ArrayList<>();
        for (UUID nodeId : network.getNodeUuids()) {
            LogisticsNodeEntity node = findNode(server, nodeId);
            if (node == null || !node.isAlive() || !node.isValidNode()
                    || !network.getId().equals(node.getNetworkId())) continue;
            List<GraphChannel> channels = new ArrayList<>();
            for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
                ChannelData channel = node.getChannel(i);
                if (channel.isEnabled() && channel.getType() != ChannelType.CHEMICAL
                        && channel.getType() != ChannelType.SOURCE) {
                    channels.add(new GraphChannel(i, channel.getType(), channel.getMode()));
                }
            }
            String blockName = BuiltInRegistries.BLOCK.getKey(node.level().getBlockState(node.getAttachedPos()).getBlock()).toString();
            nodes.add(new GraphNode(nodeId, node.getNodeLabel(), blockName, node.getAttachedPos(),
                    node.level().dimension().identifier(), NodeUpgradeData.hasDimensionalUpgrade(node), channels));
        }
        nodes.sort(Comparator.comparing(GraphNode::nodeId));
        return nodes;
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
