package me.almana.logisticsnetworks.integration.storage;

import me.almana.logisticsnetworks.logic.async.ThreadGuard;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class DirectItemHandler implements DirectItemAccess {
    private final StorageEndpoint endpoint;
    private final DirectStorageReads reads;
    private final boolean exporting;
    private List<StorageEndpoint.StoredItem> exports;

    DirectItemHandler(StorageEndpoint endpoint, DirectStorageReads reads, boolean exporting) {
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
    public int getSlots() {
        return exports().size() + 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= exports().size() || !endpoint.isValid()) return ItemStack.EMPTY;
        var stored = exports().get(slot);
        return stored.stack().copyWithCount(DirectStorageReads.clamp(stored.amount()));
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return slot >= 0 && slot < getSlots() ? insert(stack, simulate) : stack;
    }

    @Override
    public ItemStack insert(ItemStack stack, boolean simulate) {
        ThreadGuard.requireServerThread();
        if (stack.isEmpty() || !endpoint.isValid()) return stack;
        int moved = Math.min(stack.getCount(), DirectStorageReads.clamp(endpoint.insertItem(stack, stack.getCount(), simulate)));
        if (!simulate && moved > 0) reads.moved(endpoint.networkIdentity(), stack.getItem(), moved);
        return moved == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - moved);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot < 0 || slot >= exports().size()) return ItemStack.EMPTY;
        return extract(exports().get(slot).stack(), amount, simulate);
    }

    @Override
    public ItemStack extract(ItemStack stack, int amount, boolean simulate) {
        ThreadGuard.requireServerThread();
        if (!exporting || amount <= 0 || !endpoint.isValid() || !endpoint.canExportItem(stack)) return ItemStack.EMPTY;
        int moved = Math.min(amount, DirectStorageReads.clamp(endpoint.extractItem(stack, amount, simulate)));
        if (!simulate && moved > 0) reads.moved(endpoint.networkIdentity(), stack.getItem(), -moved);
        return moved == 0 ? ItemStack.EMPTY : stack.copyWithCount(moved);
    }

    @Override
    public Map<Item, Integer> countItems(Set<Item> items) {
        ThreadGuard.requireServerThread();
        if (!exporting) return reads.countItems(endpoint, items);
        Map<Item, Integer> result = new HashMap<>();
        for (var entry : endpoint.exportableItems(reads.fresh())) {
            if (items.contains(entry.stack().getItem())) {
                result.merge(entry.stack().getItem(), DirectStorageReads.clamp(entry.amount()),
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
        return !stack.isEmpty() && endpoint.isValid();
    }
}
