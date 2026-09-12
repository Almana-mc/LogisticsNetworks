package me.almana.logisticsnetworks.logic;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

final class StackedInsertion implements IItemHandler {
    private static final int MAX_RECORDED_SLOTS = 64;

    private final IItemHandler target;
    private final int slotCount;
    private ItemStack candidate;
    private int firstSlot = -1;
    private IntArrayList otherSlots;
    private boolean overflow;

    StackedInsertion(IItemHandler target) {
        this.target = target;
        this.slotCount = target.getSlots();
    }

    ItemStack simulate(ItemStack stack) {
        candidate = stack;
        return ItemHandlerHelper.insertItemStacked(this, stack, true);
    }

    ItemStack commit(ItemStack stack) {
        if (overflow || firstSlot < 0 || target.getSlots() != slotCount
                || !ItemStack.isSameItemSameComponents(candidate, stack)) {
            return ItemHandlerHelper.insertItemStacked(target, stack, false);
        }
        ItemStack remainder = target.insertItem(firstSlot, stack, false);
        if (otherSlots != null) {
            for (int i = 0; i < otherSlots.size() && !remainder.isEmpty(); i++) {
                remainder = target.insertItem(otherSlots.getInt(i), remainder, false);
            }
        }
        return remainder.isEmpty() ? ItemStack.EMPTY
                : ItemHandlerHelper.insertItemStacked(target, remainder, false);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        ItemStack remainder = target.insertItem(slot, stack, simulate);
        if (remainder.getCount() < stack.getCount() && !overflow) {
            recordSlot(slot);
        }
        return remainder;
    }

    private void recordSlot(int slot) {
        if (firstSlot < 0) {
            firstSlot = slot;
            return;
        }
        if (otherSlots == null) {
            otherSlots = new IntArrayList(4);
        }
        if (otherSlots.size() < MAX_RECORDED_SLOTS - 1) {
            otherSlots.add(slot);
        } else {
            overflow = true;
        }
    }

    @Override
    public int getSlots() {
        return target.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return target.getStackInSlot(slot);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return target.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return target.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return target.isItemValid(slot, stack);
    }
}
