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
    private Map<Item, Long> totals = new HashMap<>();

    void include(NetworkSnapshot.DirectEndpoint endpoint) {
        endpoint.counts().forEach(totals::putIfAbsent);
        for (var stored : endpoint.exports()) {
            amounts.putIfAbsent(ItemResource.of(stored.stack()), stored.amount());
        }
    }

    long amount(ItemResource key) {
        return amounts.getOrDefault(key, 0L);
    }

    int total(Item item) {
        return DirectStorageReads.clamp(totals.getOrDefault(item, 0L));
    }

    void move(ItemResource key, int delta, TransactionContext transaction) {
        journal.updateSnapshots(transaction);
        amounts.put(key, add(amount(key), delta));
        totals.put(key.getItem(), add(totals.getOrDefault(key.getItem(), 0L), delta));
    }

    private record State(Map<ItemResource, Long> amounts, Map<Item, Long> totals) {
    }

    private final class StockJournal extends SnapshotJournal<State> {
        @Override
        protected State createSnapshot() {
            return new State(new HashMap<>(amounts), new HashMap<>(totals));
        }

        @Override
        protected void revertToSnapshot(State snapshot) {
            amounts = snapshot.amounts();
            totals = snapshot.totals();
        }
    }

    private static long add(long amount, long delta) {
        return delta > 0 && amount > Long.MAX_VALUE - delta ? Long.MAX_VALUE : Math.max(0, amount + delta);
    }
}
