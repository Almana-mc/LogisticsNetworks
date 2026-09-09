package me.almana.logisticsnetworks.client.flow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class FlowBundle {
    private final double startedAt;
    private final Map<UUID, Double> sourceTimes = new HashMap<>();
    private final Map<UUID, Double> targetTimes = new HashMap<>();
    private FlowTopology.Bundle topology;
    private List<FlowAnchor> sources = List.of();
    private List<FlowAnchor> targets = List.of();
    private List<FlowSegment> segments = List.of();
    private int axis = -1;

    FlowBundle(double startedAt) {
        this.startedAt = startedAt;
    }

    void update(FlowTopology.Bundle updated, Map<UUID, FlowAnchor> anchors, double now) {
        List<FlowAnchor> nextSources = resolve(updated.sources(), anchors);
        List<FlowAnchor> nextTargets = resolve(updated.targets(), anchors);
        if (nextSources.size() != updated.sources().size() || nextTargets.size() != updated.targets().size()) {
            segments = List.of();
            sources = List.of();
            targets = List.of();
            return;
        }
        if (updated.equals(topology) && nextSources.equals(sources) && nextTargets.equals(targets)) return;
        sourceTimes.keySet().retainAll(updated.sources());
        targetTimes.keySet().retainAll(updated.targets());
        updated.sources().forEach(id -> sourceTimes.putIfAbsent(id, now - startedAt));
        updated.targets().forEach(id -> targetTimes.putIfAbsent(id, now - startedAt));
        if (axis < 0) axis = FlowLayout.axis(nextSources, nextTargets);
        segments = FlowLayout.build(nextSources, nextTargets, axis, updated.key().type().ordinal() * 0.09,
                updated.sources().stream().map(sourceTimes::get).toList(),
                updated.targets().stream().map(targetTimes::get).toList());
        topology = updated;
        sources = nextSources;
        targets = nextTargets;
    }

    double startedAt() {
        return startedAt;
    }

    List<FlowSegment> segments() {
        return segments;
    }

    private static List<FlowAnchor> resolve(List<UUID> ids, Map<UUID, FlowAnchor> anchors) {
        return ids.stream().filter(anchors::containsKey).map(anchors::get).toList();
    }

}
