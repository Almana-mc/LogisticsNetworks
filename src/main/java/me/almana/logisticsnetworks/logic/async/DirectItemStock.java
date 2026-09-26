package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.integration.storage.DirectStorageReads;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.HashMap;
import java.util.Map;

final class DirectItemStock {
    private final StockJournal journal = new StockJournal();
    private Map<ItemResource, Long> amounts = new HashMap<>();
    private Map<Integer, Map<ItemResource, Long>> buffers = new HashMap<>();
    private Map<Item, Long> totals = new HashMap<>();

    void include(NetworkSnapshot.DirectEndpoint endpoint) {
        endpoint.counts().forEach(totals::putIfAbsent);
        Map<ItemResource, Long> buffer = buffer(endpoint.binding());
        for (var stored : endpoint.exports()) {
            ItemResource key = ItemResource.of(stored.stack());
            amounts.merge(key, stored.amount(), Math::max);
            buffer.putIfAbsent(key, stored.bufferedAmount());
        }
    }

    long amount(int binding, ItemResource key) {
        return add(amounts.getOrDefault(key, 0L), buffer(binding).getOrDefault(key, 0L));
    }

    int total(Item item) {
        return DirectStorageReads.clamp(totals.getOrDefault(item, 0L));
    }

    void move(ItemResource key, int delta, TransactionContext transaction) {
        journal.updateSnapshots(transaction);
        amounts.put(key, add(amounts.getOrDefault(key, 0L), delta));
        totals.put(key.getItem(), add(totals.getOrDefault(key.getItem(), 0L), delta));
    }

    int extract(int binding, ItemResource key, int requested, TransactionContext transaction) {
        int networkMoved = Math.min(requested, DirectStorageReads.clamp(amounts.getOrDefault(key, 0L)));
        int bufferMoved = Math.min(requested - networkMoved,
                DirectStorageReads.clamp(buffer(binding).getOrDefault(key, 0L)));
        if (networkMoved > 0) move(key, -networkMoved, transaction);
        if (bufferMoved > 0) {
            journal.updateSnapshots(transaction);
            Map<ItemResource, Long> buffer = buffer(binding);
            buffer.put(key, add(buffer.getOrDefault(key, 0L), -bufferMoved));
        }
        return networkMoved + bufferMoved;
    }

    private Map<ItemResource, Long> buffer(int binding) {
        return buffers.computeIfAbsent(binding, ignored -> new HashMap<>());
    }

    private record State(Map<ItemResource, Long> amounts, Map<Integer, Map<ItemResource, Long>> buffers,
            Map<Item, Long> totals) {
    }

    private final class StockJournal extends SnapshotJournal<State> {
        @Override
        protected State createSnapshot() {
            Map<Integer, Map<ItemResource, Long>> bufferCopy = new HashMap<>();
            buffers.forEach((binding, buffer) -> bufferCopy.put(binding, new HashMap<>(buffer)));
            return new State(new HashMap<>(amounts), bufferCopy, new HashMap<>(totals));
        }

        @Override
        protected void revertToSnapshot(State snapshot) {
            amounts = snapshot.amounts();
            buffers = snapshot.buffers();
            totals = snapshot.totals();
        }
    }

    private static long add(long amount, long delta) {
        return delta > 0 && amount > Long.MAX_VALUE - delta ? Long.MAX_VALUE : Math.max(0, amount + delta);
    }
}
