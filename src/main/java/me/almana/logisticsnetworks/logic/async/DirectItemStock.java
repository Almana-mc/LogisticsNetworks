package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.integration.storage.DirectStorageReads;
import me.almana.logisticsnetworks.integration.storage.ItemResource;
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.Map;

final class DirectItemStock {
    private final Map<ItemResource, Long> amounts = new HashMap<>();
    private final Map<Integer, Map<ItemResource, Long>> buffers = new HashMap<>();
    private final Map<Item, Long> totals = new HashMap<>();
    private final Map<Item, Long> changes = new HashMap<>();

    void include(NetworkSnapshot.DirectEndpoint endpoint) {
        endpoint.counts().forEach(totals::putIfAbsent);
        Map<ItemResource, Long> buffer = buffers.computeIfAbsent(endpoint.binding(), ignored -> new HashMap<>());
        for (var stored : endpoint.exports()) {
            ItemResource key = new ItemResource(stored.stack());
            amounts.merge(key, stored.amount(), Math::max);
            buffer.putIfAbsent(key, stored.bufferedAmount());
        }
    }

    long amount(int binding, ItemResource key) {
        return add(amounts.getOrDefault(key, 0L), buffer(binding).getOrDefault(key, 0L));
    }

    int total(Item item) {
        return DirectStorageReads.clamp(add(totals.getOrDefault(item, 0L), changes.getOrDefault(item, 0L)));
    }

    void move(ItemResource key, int delta) {
        amounts.put(key, add(amounts.getOrDefault(key, 0L), delta));
        changes.merge(key.item(), (long) delta, Long::sum);
    }

    int extract(int binding, ItemResource key, int requested, boolean simulate) {
        int networkMoved = Math.min(requested,
                DirectStorageReads.clamp(amounts.getOrDefault(key, 0L)));
        Map<ItemResource, Long> buffer = buffer(binding);
        int bufferMoved = Math.min(requested - networkMoved,
                DirectStorageReads.clamp(buffer.getOrDefault(key, 0L)));
        if (!simulate) {
            if (networkMoved > 0) move(key, -networkMoved);
            if (bufferMoved > 0) buffer.put(key, add(buffer.getOrDefault(key, 0L), -bufferMoved));
        }
        return networkMoved + bufferMoved;
    }

    private Map<ItemResource, Long> buffer(int binding) {
        return buffers.computeIfAbsent(binding, ignored -> new HashMap<>());
    }

    private static long add(long amount, long delta) {
        return delta > 0 && amount > Long.MAX_VALUE - delta ? Long.MAX_VALUE : Math.max(0, amount + delta);
    }
}
