package me.almana.logisticsnetworks.client.flow;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class FlowFrame {
    private final Map<UUID, FlowTopology.Node> nodes = new HashMap<>();
    private final Map<UUID, FlowAnchor> anchors = new HashMap<>();

    void record(FlowTopology.Node node, FlowAnchor anchor) {
        nodes.put(node.id(), node);
        anchors.put(node.id(), anchor);
    }

    boolean contains(UUID id) {
        return anchors.containsKey(id);
    }

    List<FlowTopology.Node> nodes() {
        return nodes.values().stream().sorted(Comparator.comparing(FlowTopology.Node::id)).toList();
    }

    Map<UUID, FlowAnchor> anchors() {
        return anchors;
    }

    void clear() {
        nodes.clear();
        anchors.clear();
    }
}
