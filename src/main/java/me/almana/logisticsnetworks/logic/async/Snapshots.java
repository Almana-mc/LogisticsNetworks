package me.almana.logisticsnetworks.logic.async;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

public final class Snapshots {

    private Snapshots() {
    }

    public static NetworkSnapshot.ItemEndpoint captureItems(IItemHandler handler) {
        ThreadGuard.requireServerThread();

        int slots = handler.getSlots();
        List<Integer> occupied = new ArrayList<>();
        List<ItemStack> copies = new ArrayList<>();
        List<Integer> limits = new ArrayList<>();

        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            occupied.add(slot);
            copies.add(stack.copy());
            limits.add(handler.getSlotLimit(slot));
        }

        int[] occupiedSlots = new int[occupied.size()];
        int[] occupiedLimits = new int[occupied.size()];
        for (int i = 0; i < occupied.size(); i++) {
            occupiedSlots[i] = occupied.get(i);
            occupiedLimits[i] = limits.get(i);
        }

        int defaultLimit = slots > 0 ? handler.getSlotLimit(firstEmptySlot(handler, slots)) : 64;

        return new NetworkSnapshot.ItemEndpoint(
                slots,
                occupiedSlots,
                copies.toArray(ItemStack[]::new),
                defaultLimit,
                occupiedLimits);
    }

    private static int firstEmptySlot(IItemHandler handler, int slots) {
        for (int slot = 0; slot < slots; slot++) {
            if (handler.getStackInSlot(slot).isEmpty()) {
                return slot;
            }
        }
        return 0;
    }

    public static ItemStack[] copyFilters(ItemStack[] filters) {
        ItemStack[] copied = new ItemStack[filters.length];
        for (int i = 0; i < filters.length; i++) {
            copied[i] = filters[i].isEmpty() ? ItemStack.EMPTY : filters[i].copy();
        }
        return copied;
    }
}
