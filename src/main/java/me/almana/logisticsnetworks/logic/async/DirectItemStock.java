package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.integration.storage.DirectStorageReads;
import me.almana.logisticsnetworks.integration.storage.ItemResource;
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.Map;

final class DirectItemStock {
    private final Map<ItemResource, Long> amounts = new HashMap<>();
    private final Map<Item, Long> totals = new HashMap<>();
    private final Map<Item, Long> changes = new HashMap<>();

    void include(NetworkSnapshot.DirectEndpoint endpoint) {
        endpoint.counts().forEach(totals::putIfAbsent);
        for (var stored : endpoint.exports()) {
            amounts.putIfAbsent(new ItemResource(stored.stack()), stored.amount());
        }
    }

    long amount(ItemResource key) {
        return amounts.getOrDefault(key, 0L);
    }

    int total(Item item) {
        return DirectStorageReads.clamp(add(totals.getOrDefault(item, 0L), changes.getOrDefault(item, 0L)));
    }

    void move(ItemResource key, int delta) {
        amounts.put(key, add(amount(key), delta));
        changes.merge(key.item(), (long) delta, Long::sum);
    }

    private static long add(long amount, long delta) {
        return delta > 0 && amount > Long.MAX_VALUE - delta ? Long.MAX_VALUE : Math.max(0, amount + delta);
    }
}
