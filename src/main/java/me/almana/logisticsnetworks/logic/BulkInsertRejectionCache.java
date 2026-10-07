package me.almana.logisticsnetworks.logic;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;

final class BulkInsertRejectionCache {
    private static final int MAX_REJECTIONS = 64;

    private record Candidate(ItemResource resource, int amount) {
    }

    private final IdentityHashMap<ResourceHandler<ItemResource>, Set<Candidate>> rejected = new IdentityHashMap<>();

    boolean isRejected(ResourceHandler<ItemResource> handler, ItemResource resource, int amount) {
        Set<Candidate> candidates = rejected.get(handler);
        return candidates != null && candidates.contains(new Candidate(resource, amount));
    }

    void reject(ResourceHandler<ItemResource> handler, ItemResource resource, int amount) {
        Set<Candidate> candidates = rejected.computeIfAbsent(handler, key -> new HashSet<>());
        if (candidates.size() < MAX_REJECTIONS) {
            candidates.add(new Candidate(resource, amount));
        }
    }

    void clear() {
        rejected.clear();
    }
}
