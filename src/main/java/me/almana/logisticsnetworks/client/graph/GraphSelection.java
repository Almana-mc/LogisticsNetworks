package me.almana.logisticsnetworks.client.graph;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class GraphSelection {
    public enum State {
        NONE,
        PARTIAL,
        ALL
    }

    private final Set<UUID> nodes = new LinkedHashSet<>();
    private boolean initialized;

    public Set<UUID> nodes() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(nodes));
    }

    public void click(Collection<UUID> vertexNodes, boolean control) {
        initialized = true;
        if (!control) {
            replace(vertexNodes);
            return;
        }
        if (nodes.containsAll(vertexNodes)) nodes.removeAll(vertexNodes);
        else nodes.addAll(vertexNodes);
    }

    public void box(Collection<UUID> boxedNodes, boolean control) {
        initialized = true;
        if (!control) nodes.clear();
        nodes.addAll(boxedNodes);
    }

    public void replace(Collection<UUID> selectedNodes) {
        initialized = true;
        nodes.clear();
        nodes.addAll(selectedNodes);
    }

    public void initialize(Collection<UUID> selectedNodes) {
        if (!initialized) replace(selectedNodes);
    }

    public void retain(Collection<UUID> availableNodes) {
        nodes.retainAll(availableNodes);
    }

    public State state(Collection<UUID> vertexNodes) {
        int selected = 0;
        for (UUID node : vertexNodes) {
            if (nodes.contains(node)) selected++;
        }
        if (selected == 0) return State.NONE;
        return selected == vertexNodes.size() ? State.ALL : State.PARTIAL;
    }
}
