package me.almana.logisticsnetworks.integration.storage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class PlacementQueue<T> {

    private final Set<T> queued = new LinkedHashSet<>();
    private final Set<T> active = new LinkedHashSet<>();

    boolean add(T target) {
        return !active.contains(target) && queued.add(target);
    }

    boolean contains(T target) {
        return active.contains(target) || queued.contains(target);
    }

    List<T> startBatch() {
        if (!active.isEmpty() || queued.isEmpty()) return List.of();
        active.addAll(queued);
        queued.clear();
        return new ArrayList<>(active);
    }

    void complete(T target) {
        active.remove(target);
        queued.remove(target);
    }

    boolean isEmpty() {
        return active.isEmpty() && queued.isEmpty();
    }
}
