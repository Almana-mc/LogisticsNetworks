package me.almana.logisticsnetworks.logic.async;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

public final class SnapshotItemHandler implements ResourceHandler<ItemResource> {
    private final int totalSlots;
    private final Int2ObjectOpenHashMap<ItemStack> stacks;
    private final Int2IntOpenHashMap slotLimits;
    private final int[] bulkSlotLimits;
    private final Int2ObjectOpenHashMap<SlotJournal> journals = new Int2ObjectOpenHashMap<>();

    public SnapshotItemHandler(NetworkSnapshot.ItemEndpoint endpoint) {
        totalSlots = endpoint.totalSlots();
        bulkSlotLimits = endpoint.bulkSlotLimits();
        int[] occupied = endpoint.occupiedSlots();
        ItemStack[] captured = endpoint.occupiedStacks();
        int[] limits = endpoint.occupiedSlotLimits();
        stacks = new Int2ObjectOpenHashMap<>(occupied.length);
        slotLimits = new Int2IntOpenHashMap(occupied.length);
        slotLimits.defaultReturnValue(endpoint.defaultSlotLimit());
        for (int i = 0; i < occupied.length; i++) {
            stacks.put(occupied[i], captured[i]);
            slotLimits.put(occupied[i], limits[i]);
        }
    }

    public boolean supportsBulkInsertion() { return bulkSlotLimits != null; }

    @Override
    public int size() { return totalSlots; }

    @Override
    public ItemResource getResource(int index) { return ItemResource.of(stack(index)); }

    @Override
    public long getAmountAsLong(int index) { return stack(index).getCount(); }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        if (bulkSlotLimits != null) return bulkSlotLimits[index];
        int limit = slotLimits.get(index);
        return resource.isEmpty() ? limit : Math.min(limit, resource.getMaxStackSize());
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return index >= 0 && index < totalSlots && !resource.isEmpty();
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return insertAt(index, resource, amount, transaction, false);
    }

    @Override
    public int insert(ItemResource resource, int amount, TransactionContext transaction) {
        if (bulkSlotLimits == null) {
            return ResourceHandler.super.insert(resource, amount, transaction);
        }
        int remaining = amount;
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (int slot = 0; slot < totalSlots && remaining > 0; slot++) {
                if (stack(slot).isEmpty() != (pass == 1)) continue;
                remaining -= insertAt(slot, resource, remaining, transaction, true);
            }
        }
        return amount - remaining;
    }

    private int insertAt(int index, ItemResource resource, int amount, TransactionContext transaction,
            boolean bulk) {
        if (amount <= 0 || !isValid(index, resource)) return 0;
        ItemStack current = stack(index);
        if (!current.isEmpty() && !resource.matches(current)) return 0;
        int capacity = bulk ? bulkSlotLimits[index] : getCapacityAsInt(index, resource);
        int inserted = Math.min(amount, Math.max(0, capacity - current.getCount()));
        if (inserted > 0) {
            journals.computeIfAbsent(index, SlotJournal::new).updateSnapshots(transaction);
            stacks.put(index, resource.toStack(current.getCount() + inserted));
        }
        return inserted;
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (amount <= 0 || !isValid(index, resource)) return 0;
        ItemStack current = stack(index);
        if (!resource.matches(current)) return 0;
        int extracted = Math.min(amount, Math.min(current.getCount(), resource.getMaxStackSize()));
        if (extracted > 0) {
            journals.computeIfAbsent(index, SlotJournal::new).updateSnapshots(transaction);
            setStack(index, current.copyWithCount(current.getCount() - extracted));
        }
        return extracted;
    }

    private ItemStack stack(int index) { return stacks.getOrDefault(index, ItemStack.EMPTY); }

    private void setStack(int index, ItemStack stack) {
        if (stack.isEmpty()) stacks.remove(index);
        else stacks.put(index, stack);
    }

    private final class SlotJournal extends SnapshotJournal<ItemStack> {
        private final int index;
        private SlotJournal(int index) { this.index = index; }
        @Override
        protected ItemStack createSnapshot() { return stack(index); }
        @Override
        protected void revertToSnapshot(ItemStack snapshot) { setStack(index, snapshot); }
    }
}
