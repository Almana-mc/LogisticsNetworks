package me.almana.logisticsnetworks.integration.storage;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public final class DirectStorageReads {
    private final boolean fresh;
    private final Map<Object, Map<Item, Long>> itemCounts = new IdentityHashMap<>();
    private final CountJournal journal = new CountJournal();

    public DirectStorageReads(boolean fresh) {
        this.fresh = fresh;
    }

    public boolean fresh() {
        return fresh;
    }

    public Map<Item, Integer> countItems(StorageEndpoint endpoint, Set<Item> items) {
        Map<Item, Long> known = itemCounts.computeIfAbsent(endpoint.networkIdentity(), ignored -> new HashMap<>());
        Set<Item> missing = new HashSet<>(items);
        missing.removeAll(known.keySet());
        if (!missing.isEmpty()) {
            Map<Item, Long> fetched = endpoint.itemCounts(missing, fresh);
            for (Item item : missing) known.put(item, fetched.getOrDefault(item, 0L));
        }
        Map<Item, Integer> result = new HashMap<>();
        for (Item item : items) result.put(item, clamp(known.getOrDefault(item, 0L)));
        return result;
    }

    public void moved(Object network, Item item, int delta, TransactionContext transaction) {
        Map<Item, Long> known = itemCounts.get(network);
        if (known == null || !known.containsKey(item)) return;
        journal.updateSnapshots(transaction);
        long amount = known.get(item);
        known.put(item, delta > 0 && amount > Long.MAX_VALUE - delta ? Long.MAX_VALUE : Math.max(0, amount + delta));
    }

    public static int clamp(long amount) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, amount));
    }

    private final class CountJournal extends SnapshotJournal<Map<Object, Map<Item, Long>>> {
        @Override
        protected Map<Object, Map<Item, Long>> createSnapshot() {
            Map<Object, Map<Item, Long>> snapshot = new IdentityHashMap<>();
            itemCounts.forEach((network, counts) -> snapshot.put(network, new HashMap<>(counts)));
            return snapshot;
        }

        @Override
        protected void revertToSnapshot(Map<Object, Map<Item, Long>> snapshot) {
            itemCounts.clear();
            itemCounts.putAll(snapshot);
        }
    }
}
