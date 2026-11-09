package me.almana.logisticsnetworks.integration.storage;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenCustomHashMap;
import me.almana.logisticsnetworks.utils.HashUtils;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public final class DirectStorageReads {
    private final boolean fresh;
    private final Map<Object, Object2LongMap<Item>> itemCounts = new IdentityHashMap<>();
    private final CountJournal journal = new CountJournal();

    public DirectStorageReads(boolean fresh) {
        this.fresh = fresh;
    }

    public boolean fresh() {
        return fresh;
    }

    public Object2IntMap<Item> countItems(StorageEndpoint endpoint, Set<Item> items) {
        var known = itemCounts.computeIfAbsent(endpoint.networkIdentity(), ignored -> HashUtils.itemToLongMap());
        Set<Item> missing = new HashSet<>(items);
        missing.removeAll(known.keySet());
        if (!missing.isEmpty()) {
            var fetched = endpoint.itemCounts(missing, fresh);
            for (Item item : missing) known.put(item, fetched.getOrDefault(item, 0L));
        }
        var result = HashUtils.itemToIntMap();
        for (Item item : items) result.put(item, clamp(known.getOrDefault(item, 0L)));
        return result;
    }

    public void moved(Object network, Item item, int delta, TransactionContext transaction) {
        var known = itemCounts.get(network);
        if (known == null || !known.containsKey(item)) return;
        journal.updateSnapshots(transaction);
        known.computeLong(item, (k, amount) -> delta > 0 && amount > Long.MAX_VALUE - delta ? Long.MAX_VALUE : Math.max(0, amount + delta));
    }

    public static int clamp(long amount) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, amount));
    }

    private final class CountJournal extends SnapshotJournal<Map<Object, Object2LongMap<Item>>> {
        @Override
        protected Map<Object, Object2LongMap<Item>> createSnapshot() {
            Map<Object, Object2LongMap<Item>> snapshot = new IdentityHashMap<>();
            itemCounts.forEach((network, counts) -> snapshot.put(network,
                    new Object2LongOpenCustomHashMap<>(counts, HashUtils.ITEM_STRATEGY)));
            return snapshot;
        }

        @Override
        protected void revertToSnapshot(Map<Object, Object2LongMap<Item>> snapshot) {
            itemCounts.clear();
            itemCounts.putAll(snapshot);
        }
    }
}
