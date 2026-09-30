package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.integration.storage.DirectItemAccess;
import me.almana.logisticsnetworks.integration.storage.DirectStorageReads;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class DirectSnapshotItemHandler implements DirectItemAccess {
    private final NetworkSnapshot.DirectEndpoint endpoint;
    private final DirectItemStock stock;
    private final List<ItemResource> exports;
    private final Set<ItemResource> exportKeys;

    DirectSnapshotItemHandler(NetworkSnapshot.DirectEndpoint endpoint, DirectItemStock stock) {
        this.endpoint = endpoint;
        this.stock = stock;
        exports = endpoint.exports().stream().map(entry -> ItemResource.of(entry.stack())).toList();
        exportKeys = Set.copyOf(exports);
    }

    @Override
    public int size() {
        return exports.size() + 1;
    }

    @Override
    public ItemResource getResource(int index) {
        return index >= 0 && index < exports.size() ? exports.get(index) : ItemResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        return index >= 0 && index < exports.size() ? stock.amount(endpoint.binding(), exports.get(index)) : 0;
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return index >= 0 && index < size() && !resource.isEmpty() ? Integer.MAX_VALUE : 0;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return index >= 0 && index < size() && !resource.isEmpty();
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return index >= 0 && index < size() ? insert(resource, amount, transaction) : 0;
    }

    @Override
    public int insert(ItemResource resource, int amount, TransactionContext transaction) {
        if (amount <= 0 || resource.isEmpty()) return 0;
        stock.move(resource, amount, transaction);
        return amount;
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (index < 0 || index >= exports.size() || !resource.equals(exports.get(index))) return 0;
        return extract(resource, amount, transaction);
    }

    @Override
    public int extract(ItemResource resource, int amount, TransactionContext transaction) {
        if (!endpoint.exporting() || amount <= 0 || !exportKeys.contains(resource)) return 0;
        return stock.extract(endpoint.binding(), resource, amount, transaction);
    }

    @Override
    public Map<Item, Integer> countItems(Set<Item> candidates) {
        Map<Item, Integer> result = new HashMap<>();
        if (!endpoint.exporting()) {
            for (Item item : candidates) result.put(item, stock.total(item));
            return result;
        }
        for (ItemResource resource : exports) {
            if (candidates.contains(resource.getItem())) {
                result.merge(resource.getItem(), DirectStorageReads.clamp(stock.amount(endpoint.binding(), resource)),
                        (a, b) -> DirectStorageReads.clamp((long) a + b));
            }
        }
        return result;
    }
}
