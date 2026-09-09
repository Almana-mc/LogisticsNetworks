package me.almana.logisticsnetworks.data.graph;

import me.almana.logisticsnetworks.data.ChannelMode;
import me.almana.logisticsnetworks.data.ChannelType;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public record NetworkGraph(List<Vertex> vertices, List<Edge> edges) {

    private static final float GRID_X = 220.0F;
    private static final float GRID_Y = 140.0F;
    private static final float NODE_WIDTH = 180.0F;
    private static final float NODE_HEIGHT = 100.0F;
    private static final Comparator<GraphNode> NODE_ORDER = Comparator.comparing(GraphNode::nodeId);
    private static final Comparator<EdgeKey> EDGE_ORDER = Comparator.comparing(EdgeKey::source)
            .thenComparing(EdgeKey::target)
            .thenComparing(EdgeKey::type);

    public NetworkGraph {
        vertices = List.copyOf(vertices);
        edges = List.copyOf(edges);
    }

    public static NetworkGraph from(List<GraphNode> nodes) {
        Map<String, List<GraphNode>> groups = new TreeMap<>();
        for (GraphNode node : nodes) {
            groups.computeIfAbsent(key(node), ignored -> new ArrayList<>()).add(node);
        }

        List<Vertex> vertices = new ArrayList<>(groups.size());
        for (Map.Entry<String, List<GraphNode>> entry : groups.entrySet()) {
            entry.getValue().sort(NODE_ORDER);
            vertices.add(new Vertex(entry.getKey(), vertexLabel(entry.getKey(), entry.getValue().getFirst()),
                    entry.getValue()));
        }
        return new NetworkGraph(vertices, buildEdges(nodes));
    }

    public static String key(GraphNode node) {
        String label = node.label().trim();
        return label.isEmpty() ? "node:" + node.nodeId() : "label:" + label;
    }

    public static Map<String, GraphPosition> initialPositions(List<Vertex> vertices,
            Map<String, GraphPosition> existing) {
        Map<String, GraphPosition> positions = new LinkedHashMap<>(existing);
        List<Vertex> sorted = vertices.stream().sorted(Comparator.comparing(Vertex::key)).toList();
        int columns = Math.max(1, (int) Math.ceil(Math.sqrt(sorted.size())));
        int cell = 0;
        for (Vertex vertex : sorted) {
            if (positions.containsKey(vertex.key())) {
                continue;
            }
            GraphPosition position;
            do {
                position = gridPosition(cell++, columns);
            } while (overlaps(position, positions.values()));
            positions.put(vertex.key(), position);
        }
        return positions;
    }

    private static List<Edge> buildEdges(List<GraphNode> nodes) {
        Map<RouteKey, RouteBucket> routes = new HashMap<>();
        for (GraphNode node : nodes) {
            String nodeKey = key(node);
            for (GraphChannel channel : node.channels()) {
                RouteKey route = new RouteKey(channel.index(), channel.type());
                routes.computeIfAbsent(route, ignored -> new RouteBucket()).add(nodeKey, node, channel.mode());
            }
        }

        Map<EdgeKey, Integer> masks = new TreeMap<>(EDGE_ORDER);
        for (Map.Entry<RouteKey, RouteBucket> entry : routes.entrySet()) {
            entry.getValue().connect(entry.getKey(), masks);
        }
        return masks.entrySet().stream()
                .map(entry -> entry.getKey().edge(entry.getValue()))
                .toList();
    }

    private static String vertexLabel(String key, GraphNode node) {
        return key.startsWith("label:") ? key.substring("label:".length()) : node.blockName();
    }

    private static GraphPosition gridPosition(int cell, int columns) {
        return new GraphPosition((cell % columns) * GRID_X, (cell / columns) * GRID_Y);
    }

    private static boolean overlaps(GraphPosition candidate, Iterable<GraphPosition> positions) {
        for (GraphPosition position : positions) {
            if (Math.abs(candidate.x() - position.x()) < NODE_WIDTH
                    && Math.abs(candidate.y() - position.y()) < NODE_HEIGHT) {
                return true;
            }
        }
        return false;
    }

    public record Vertex(String key, String label, List<GraphNode> members) {

        public Vertex {
            members = List.copyOf(members);
        }
    }

    public record Edge(String source, String target, ChannelType type, int channels) {
    }

    private record EdgeKey(String source, String target, ChannelType type) {

        private Edge edge(int channels) {
            return new Edge(source, target, type, channels);
        }
    }

    private record RouteKey(int index, ChannelType type) {
    }

    private static final class RouteBucket {
        private final Map<String, Endpoint> exports = new HashMap<>();
        private final Map<Identifier, Set<String>> importsByDimension = new HashMap<>();
        private final Set<String> dimensionalImports = new HashSet<>();

        private void add(String key, GraphNode node, ChannelMode mode) {
            if (mode == ChannelMode.EXPORT) {
                exports.computeIfAbsent(key, ignored -> new Endpoint()).add(node);
                return;
            }
            importsByDimension.computeIfAbsent(node.dimension(), ignored -> new HashSet<>()).add(key);
            if (node.dimensional()) {
                dimensionalImports.add(key);
            }
        }

        private void connect(RouteKey route, Map<EdgeKey, Integer> masks) {
            for (Map.Entry<String, Endpoint> source : exports.entrySet()) {
                Set<String> targets = new HashSet<>();
                for (Identifier dimension : source.getValue().dimensions) {
                    targets.addAll(importsByDimension.getOrDefault(dimension, Set.of()));
                }
                if (source.getValue().dimensional) {
                    targets.addAll(dimensionalImports);
                }
                for (String target : targets) {
                    if (!source.getKey().equals(target)) {
                        EdgeKey edge = new EdgeKey(source.getKey(), target, route.type());
                        masks.merge(edge, 1 << route.index(), (left, right) -> left | right);
                    }
                }
            }
        }
    }

    private static final class Endpoint {
        private final Set<Identifier> dimensions = new HashSet<>();
        private boolean dimensional;

        private void add(GraphNode node) {
            dimensions.add(node.dimension());
            dimensional |= node.dimensional();
        }
    }
}
