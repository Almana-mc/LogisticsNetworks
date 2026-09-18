package me.almana.logisticsnetworks.integration.storage;

import me.almana.logisticsnetworks.logic.async.ThreadGuard;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class DirectItemHandler implements DirectItemAccess {
    private final StorageEndpoint endpoint;
    @Nullable
    private final IItemHandler buffer;
    private final DirectStorageReads reads;
    private final boolean exporting;
    private List<StorageEndpoint.StoredItem> exports;

    DirectItemHandler(StorageEndpoint endpoint, @Nullable IItemHandler buffer,
            DirectStorageReads reads, boolean exporting) {
        this.endpoint = endpoint;
        this.buffer = buffer;
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
        if (exports == null) exports = exporting ? readExports() : List.of();
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
        return stored.stack().copyWithCount(DirectStorageReads.clamp(
                add(stored.amount(), stored.bufferedAmount())));
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
        int networkMoved = Math.min(amount,
                DirectStorageReads.clamp(endpoint.extractItem(stack, amount, simulate)));
        int bufferMoved = extractBuffer(stack, amount - networkMoved, simulate);
        int moved = networkMoved + bufferMoved;
        if (!simulate && networkMoved > 0) {
            reads.moved(endpoint.networkIdentity(), stack.getItem(), -networkMoved);
        }
        return moved == 0 ? ItemStack.EMPTY : stack.copyWithCount(moved);
    }

    @Override
    public Map<Item, Integer> countItems(Set<Item> items) {
        ThreadGuard.requireServerThread();
        if (!exporting) return reads.countItems(endpoint, items);
        Map<Item, Integer> result = new HashMap<>();
        for (var entry : readExports()) {
            if (items.contains(entry.stack().getItem())) {
                result.merge(entry.stack().getItem(), DirectStorageReads.clamp(
                                add(entry.amount(), entry.bufferedAmount())),
                        (a, b) -> DirectStorageReads.clamp((long) a + b));
            }
        }
        return result;
    }

    private List<StorageEndpoint.StoredItem> readExports() {
        Map<ItemResource, StorageEndpoint.StoredItem> items = new LinkedHashMap<>();
        for (var stored : endpoint.exportableItems(reads.fresh())) {
            ItemStack stack = stored.stack().copyWithCount(1);
            ItemResource key = new ItemResource(stack);
            var current = items.get(key);
            long amount = add(current == null ? 0 : current.amount(), stored.amount());
            long buffered = current == null ? 0 : current.bufferedAmount();
            items.put(key, new StorageEndpoint.StoredItem(stack, amount, buffered));
        }
        if (buffer == null) return List.copyOf(items.values());
        for (int slot = 0; slot < buffer.getSlots(); slot++) {
            ItemStack stack = buffer.getStackInSlot(slot);
            if (stack.isEmpty() || !endpoint.canExportItem(stack)) continue;
            ItemResource key = new ItemResource(stack);
            var current = items.get(key);
            long amount = current == null ? 0 : current.amount();
            long buffered = add(current == null ? 0 : current.bufferedAmount(), stack.getCount());
            items.put(key, new StorageEndpoint.StoredItem(stack.copyWithCount(1), amount, buffered));
        }
        return List.copyOf(items.values());
    }

    private int extractBuffer(ItemStack stack, int amount, boolean simulate) {
        if (buffer == null || amount <= 0) return 0;
        int moved = 0;
        for (int slot = 0; slot < buffer.getSlots() && moved < amount; slot++) {
            if (!ItemStack.isSameItemSameComponents(stack, buffer.getStackInSlot(slot))) continue;
            moved += buffer.extractItem(slot, amount - moved, simulate).getCount();
        }
        return moved;
    }

    private static long add(long amount, long delta) {
        return delta > 0 && amount > Long.MAX_VALUE - delta ? Long.MAX_VALUE : Math.max(0, amount + delta);
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
