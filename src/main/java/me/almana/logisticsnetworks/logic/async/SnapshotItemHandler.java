package me.almana.logisticsnetworks.logic.async;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

public final class SnapshotItemHandler implements IItemHandlerModifiable {

    private final int totalSlots;
    private final Int2ObjectOpenHashMap<ItemStack> stacks;
    private final Int2IntOpenHashMap slotLimits;

    public SnapshotItemHandler(NetworkSnapshot.ItemEndpoint endpoint) {
        totalSlots = endpoint.totalSlots();
        int[] occupied = endpoint.occupiedSlots();
        stacks = new Int2ObjectOpenHashMap<>(occupied.length);
        slotLimits = new Int2IntOpenHashMap(occupied.length);
        slotLimits.defaultReturnValue(endpoint.defaultSlotLimit());
        for (int i = 0; i < occupied.length; i++) {
            stacks.put(occupied[i], endpoint.occupiedStacks()[i]);
            slotLimits.put(occupied[i], endpoint.occupiedSlotLimits()[i]);
        }
    }

    @Override
    public int getSlots() {
        return totalSlots;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        ItemStack stack = stacks.get(slot);
        return stack == null ? ItemStack.EMPTY : stack;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            stacks.remove(slot);
        } else {
            stacks.put(slot, stack);
        }
    }

    @Override
    public int getSlotLimit(int slot) {
        return slotLimits.get(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || slot < 0 || slot >= totalSlots) {
            return stack;
        }
        ItemStack existing = getStackInSlot(slot);
        int limit = Math.min(getSlotLimit(slot), stack.getMaxStackSize());
        if (!existing.isEmpty()) {
            if (!ItemStack.isSameItemSameComponents(existing, stack)) {
                return stack;
            }
            limit = Math.min(limit, existing.getMaxStackSize());
        }

        int space = limit - (existing.isEmpty() ? 0 : existing.getCount());
        if (space <= 0) {
            return stack;
        }

        int inserted = Math.min(space, stack.getCount());
        if (!simulate) {
            if (existing.isEmpty()) {
                setStackInSlot(slot, stack.copyWithCount(inserted));
            } else {
                ItemStack grown = existing.copy();
                grown.grow(inserted);
                setStackInSlot(slot, grown);
            }
        }
        return inserted >= stack.getCount()
                ? ItemStack.EMPTY
                : stack.copyWithCount(stack.getCount() - inserted);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0 || slot < 0 || slot >= totalSlots) {
            return ItemStack.EMPTY;
        }
        ItemStack existing = getStackInSlot(slot);
        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }

        int extracted = Math.min(amount, existing.getCount());
        if (!simulate) {
            ItemStack left = existing.copy();
            left.shrink(extracted);
            setStackInSlot(slot, left);
        }
        return existing.copyWithCount(extracted);
    }
}
