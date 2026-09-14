package me.almana.logisticsnetworks.integration.storage;

import me.almana.logisticsnetworks.logic.async.ThreadGuard;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class DirectItemHandler implements DirectItemAccess {
    private final StorageEndpoint endpoint;
    private final DirectStorageReads reads;
    private final boolean exporting;
    private final ChangeJournal journal = new ChangeJournal();
    private List<StorageEndpoint.StoredItem> exports;
    private Map<ItemResource, Long> changes = new LinkedHashMap<>();

    DirectItemHandler(StorageEndpoint endpoint, DirectStorageReads reads, boolean exporting) {
        ThreadGuard.requireServerThread();
        this.endpoint = endpoint;
        this.reads = reads;
        this.exporting = exporting;
    }

    StorageEndpoint endpoint() {
        return endpoint;
    }

    boolean exporting() {
        return exporting;
    }

    List<StorageEndpoint.StoredItem> exports() {
        ThreadGuard.requireServerThread();
        if (exports == null) exports = exporting ? endpoint.exportableItems(reads.fresh()) : List.of();
        return exports;
    }

    @Override
    public int size() {
        return exports().size() + 1;
    }

    @Override
    public ItemResource getResource(int index) {
        return index >= 0 && index < exports().size()
                ? ItemResource.of(exports().get(index).stack()) : ItemResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        if (index < 0 || index >= exports().size()) return 0;
        StorageEndpoint.StoredItem stored = exports().get(index);
        return add(stored.amount(), changes.getOrDefault(ItemResource.of(stored.stack()), 0L));
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return index >= 0 && index < size() && !resource.isEmpty() ? Integer.MAX_VALUE : 0;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return index >= 0 && index < size() && !resource.isEmpty() && endpoint.isValid();
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return index >= 0 && index < size() ? insert(resource, amount, transaction) : 0;
    }

    @Override
    public int insert(ItemResource resource, int amount, TransactionContext transaction) {
        ThreadGuard.requireServerThread();
        if (amount <= 0 || resource.isEmpty() || !endpoint.isValid()) return 0;
        long current = changes.getOrDefault(resource, 0L);
        int accepted = acceptedInsert(resource, amount, current);
        if (accepted > 0) move(resource, accepted, transaction);
        return accepted;
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (index < 0 || index >= exports().size() || !resource.equals(getResource(index))) return 0;
        return extract(resource, amount, transaction);
    }

    @Override
    public int extract(ItemResource resource, int amount, TransactionContext transaction) {
        ThreadGuard.requireServerThread();
        if (!exporting || amount <= 0 || resource.isEmpty() || !endpoint.isValid()
                || !endpoint.canExportItem(resource.toStack(1))) return 0;
        long current = changes.getOrDefault(resource, 0L);
        int accepted = acceptedExtract(resource, amount, current);
        if (accepted > 0) move(resource, -accepted, transaction);
        return accepted;
    }

    @Override
    public Map<Item, Integer> countItems(Set<Item> items) {
        ThreadGuard.requireServerThread();
        if (!exporting) return reads.countItems(endpoint, items);
        Map<Item, Long> totals = new HashMap<>();
        for (StorageEndpoint.StoredItem stored : endpoint.exportableItems(reads.fresh())) {
            Item item = stored.stack().getItem();
            if (items.contains(item)) totals.merge(item, stored.amount(), DirectItemHandler::saturatingAdd);
        }
        for (Map.Entry<ItemResource, Long> change : changes.entrySet()) {
            Item item = change.getKey().getItem();
            if (items.contains(item)) totals.merge(item, change.getValue(), DirectItemHandler::add);
        }
        Map<Item, Integer> result = new HashMap<>();
        for (Item item : items) result.put(item, DirectStorageReads.clamp(totals.getOrDefault(item, 0L)));
        return result;
    }

    private void move(ItemResource resource, int delta, TransactionContext transaction) {
        journal.updateSnapshots(transaction);
        reads.moved(endpoint.networkIdentity(), resource.getItem(), delta, transaction);
        changes.merge(resource, (long) delta, Long::sum);
    }

    private int acceptedInsert(ItemResource resource, int amount, long current) {
        int cancelled = (int) Math.min(amount, Math.max(0, -current));
        int remaining = amount - cancelled;
        if (remaining == 0) return cancelled;
        long pending = Math.max(0, current);
        long accepted = endpoint.insertItem(resource.toStack(1), pending + remaining, true) - pending;
        return cancelled + clampRequest(accepted, remaining);
    }

    private int acceptedExtract(ItemResource resource, int amount, long current) {
        int cancelled = (int) Math.min(amount, Math.max(0, current));
        int remaining = amount - cancelled;
        if (remaining == 0) return cancelled;
        long pending = Math.max(0, -current);
        long accepted = endpoint.extractItem(resource.toStack(1), pending + remaining, true) - pending;
        return cancelled + clampRequest(accepted, remaining);
    }

    private final class ChangeJournal extends SnapshotJournal<Map<ItemResource, Long>> {
        @Override
        protected Map<ItemResource, Long> createSnapshot() {
            return new LinkedHashMap<>(changes);
        }

        @Override
        protected void revertToSnapshot(Map<ItemResource, Long> snapshot) {
            changes = snapshot;
        }

        @Override
        protected void onRootCommit(Map<ItemResource, Long> originalState) {
            Map<ItemResource, Long> committed = changes;
            changes = new LinkedHashMap<>();
            for (Map.Entry<ItemResource, Long> entry : committed.entrySet()) {
                if (entry.getValue() > 0) {
                    endpoint.insertItem(entry.getKey().toStack(1), entry.getValue(), false);
                } else if (entry.getValue() < 0) {
                    endpoint.extractItem(entry.getKey().toStack(1), -entry.getValue(), false);
                }
            }
        }
    }

    private static long add(long amount, long delta) {
        return delta > 0 && amount > Long.MAX_VALUE - delta ? Long.MAX_VALUE : Math.max(0, amount + delta);
    }

    private static long saturatingAdd(long left, long right) {
        return right > 0 && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static int clampRequest(long amount, int request) {
        return (int) Math.min(Math.max(0, amount), request);
    }
}
