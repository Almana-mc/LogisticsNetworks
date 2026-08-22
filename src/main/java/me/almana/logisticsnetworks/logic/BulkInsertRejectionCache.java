package me.almana.logisticsnetworks.logic;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

final class BulkInsertRejectionCache {
    @FunctionalInterface
    interface Inserter {
        ItemStack insert(IItemHandler handler, ItemStack stack);
    }

    private final Inserter inserter;
    private final IdentityHashMap<IItemHandler, List<ItemStack>> rejected = new IdentityHashMap<>();

    BulkInsertRejectionCache(Inserter inserter) {
        this.inserter = inserter;
    }

    ItemStack simulate(IItemHandler handler, ItemStack candidate) {
        List<ItemStack> rejectedCandidates = rejected.get(handler);
        if (rejectedCandidates != null) {
            for (ItemStack rejectedCandidate : rejectedCandidates) {
                if (rejectedCandidate.getCount() == candidate.getCount()
                        && ItemStack.isSameItemSameComponents(rejectedCandidate, candidate)) {
                    return candidate;
                }
            }
        }

        ItemStack remainder = inserter.insert(handler, candidate);
        if (remainder.getCount() == candidate.getCount()
                && ItemStack.isSameItemSameComponents(remainder, candidate)) {
            rejected.computeIfAbsent(handler, key -> new ArrayList<>()).add(candidate.copy());
        }
        return remainder;
    }

    void clear(IItemHandler handler) {
        rejected.remove(handler);
    }
}
