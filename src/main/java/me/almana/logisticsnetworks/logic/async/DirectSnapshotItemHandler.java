package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.integration.storage.DirectItemAccess;
import me.almana.logisticsnetworks.integration.storage.DirectStorageReads;
import me.almana.logisticsnetworks.integration.storage.ItemResource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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
        exports = endpoint.exports().stream().map(entry -> new ItemResource(entry.stack())).toList();
        exportKeys = Set.copyOf(exports);
    }

    @Override
    public int getSlots() {
        return exports.size() + 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= exports.size()) return ItemStack.EMPTY;
        ItemResource key = exports.get(slot);
        return key.toStack(DirectStorageReads.clamp(stock.amount(key)));
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return slot < 0 || slot >= getSlots() ? stack : insert(stack, simulate);
    }

    @Override
    public ItemStack insert(ItemStack stack, boolean simulate) {
        if (!simulate && !stack.isEmpty()) stock.move(new ItemResource(stack), stack.getCount());
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot < 0 || slot >= exports.size()) return ItemStack.EMPTY;
        return extract(exports.get(slot), amount, simulate);
    }

    @Override
    public ItemStack extract(ItemStack stack, int amount, boolean simulate) {
        return extract(new ItemResource(stack), amount, simulate);
    }

    private ItemStack extract(ItemResource key, int amount, boolean simulate) {
        if (!endpoint.exporting() || amount <= 0 || !exportKeys.contains(key)) return ItemStack.EMPTY;
        int moved = Math.min(amount, DirectStorageReads.clamp(stock.amount(key)));
        if (!simulate && moved > 0) stock.move(key, -moved);
        return moved == 0 ? ItemStack.EMPTY : key.toStack(moved);
    }

    @Override
    public Map<Item, Integer> countItems(Set<Item> candidates) {
        Map<Item, Integer> result = new HashMap<>();
        if (!endpoint.exporting()) {
            for (Item item : candidates) result.put(item, stock.total(item));
            return result;
        }
        for (ItemResource key : exports) {
            if (candidates.contains(key.item())) {
                result.merge(key.item(), DirectStorageReads.clamp(stock.amount(key)),
                        (a, b) -> DirectStorageReads.clamp((long) a + b));
            }
        }
        return result;
    }

    @Override
    public int getSlotLimit(int slot) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return true;
    }
}
