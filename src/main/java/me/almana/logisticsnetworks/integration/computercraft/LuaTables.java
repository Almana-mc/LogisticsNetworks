package me.almana.logisticsnetworks.integration.computercraft;

import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.FlowResource;
import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.data.graph.GraphNode;
import me.almana.logisticsnetworks.data.graph.GraphPosition;
import me.almana.logisticsnetworks.data.graph.NetworkGraph;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.logic.TelemetryManager;
import me.almana.logisticsnetworks.network.SyncChannelListPayload;
import me.almana.logisticsnetworks.network.SyncNetworkNodesPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

final class LuaTables {

    private LuaTables() {
    }

    static Map<String, Object> network(LogisticsNetwork network, boolean starred) {
        return Map.of(
                "id", network.getId().toString(),
                "name", Objects.requireNonNullElse(network.getName(), ""),
                "nodeCount", network.getNodeUuids().size(),
                "starred", starred,
                "color", network.getColor(),
                "createdAt", network.getCreatedAt());
    }

    static List<Map<String, Object>> channels(LogisticsNetwork network,
            List<SyncChannelListPayload.ChannelEntry> entries) {
        List<Map<String, Object>> channels = new ArrayList<>(LogisticsNodeEntity.CHANNEL_COUNT);
        for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
            Map<String, Object> channel = new HashMap<>();
            channel.put("index", i);
            channel.put("name", Objects.requireNonNullElse(network.getChannelName(i), ""));
            channel.put("nodeCount", 0);
            channels.add(channel);
        }
        for (SyncChannelListPayload.ChannelEntry entry : entries) {
            Map<String, Object> channel = channels.get(entry.channelIndex());
            channel.put("type", typeName(ChannelType.values()[entry.typeOrdinal()]));
            channel.put("nodeCount", entry.nodeCount());
        }
        return channels;
    }

    static Map<String, Object> node(SyncNetworkNodesPayload.NodeInfo info) {
        return Map.of(
                "id", info.nodeId().toString(),
                "label", info.nodeLabel(),
                "block", info.blockName(),
                "pos", pos(info.nodePos()),
                "attachedPos", pos(info.attachedPos()),
                "dimension", info.dimension().toString(),
                "visible", info.visible(),
                "highlighted", info.highlighted());
    }

    static Map<String, Object> graph(LogisticsNetwork network, List<GraphNode> nodes) {
        NetworkGraph graph = NetworkGraph.from(nodes);
        Map<String, GraphPosition> positions = NetworkGraph.initialPositions(graph.vertices(),
                network.getGraphPositions());
        return Map.of(
                "name", Objects.requireNonNullElse(network.getName(), ""),
                "totalNodes", network.getNodeUuids().size(),
                "vertices", graph.vertices().stream()
                        .map(vertex -> vertex(vertex, positions.get(vertex.key()))).toList(),
                "edges", graph.edges().stream().map(LuaTables::edge).toList());
    }

    static Map<String, Object> sample(List<TelemetryManager.ChannelDrain> drains) {
        List<Map<String, Object>> channels = new ArrayList<>(drains.size());
        for (int i = 0; i < drains.size(); i++) {
            TelemetryManager.ChannelDrain drain = drains.get(i);
            Map<String, Object> channel = new HashMap<>();
            channel.put("index", i);
            channel.put("total", drain.total());
            channel.put("resources", drain.top().stream()
                    .map(entry -> resource(entry.getKey(), entry.getValue())).toList());
            if (drain.typeOrdinal() >= 0) {
                channel.put("type", typeName(ChannelType.values()[drain.typeOrdinal()]));
            }
            channels.add(channel);
        }
        return Map.of("time", System.currentTimeMillis(), "channels", channels);
    }

    private static Map<String, Object> vertex(NetworkGraph.Vertex vertex, GraphPosition position) {
        return Map.of(
                "key", vertex.key(),
                "label", vertex.label(),
                "x", position.x(),
                "y", position.y(),
                "nodes", vertex.members().stream().map(node -> node.nodeId().toString()).toList());
    }

    private static Map<String, Object> edge(NetworkGraph.Edge edge) {
        List<Integer> channels = new ArrayList<>();
        for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
            if ((edge.channels() & (1 << i)) != 0) {
                channels.add(i);
            }
        }
        return Map.of("from", edge.source(), "to", edge.target(), "type", typeName(edge.type()),
                "channels", channels);
    }

    private static Map<String, Object> resource(FlowResource resource, long amount) {
        return switch (resource) {
            case FlowResource.Item item -> Map.of("kind", "item",
                    "name", BuiltInRegistries.ITEM.getKey(item.stack().getItem()).toString(),
                    "displayName", item.stack().getHoverName().getString(),
                    "amount", amount);
            case FlowResource.Fluid fluid -> Map.of("kind", "fluid",
                    "name", BuiltInRegistries.FLUID.getKey(fluid.stack().getFluid()).toString(),
                    "displayName", fluid.stack().getHoverName().getString(),
                    "amount", amount);
            case FlowResource.Chemical chemical -> Map.of("kind", "chemical",
                    "name", chemical.id(),
                    "displayName", chemical.id(),
                    "amount", amount);
        };
    }

    private static Map<String, Object> pos(BlockPos pos) {
        return Map.of("x", pos.getX(), "y", pos.getY(), "z", pos.getZ());
    }

    private static String typeName(ChannelType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }
}
