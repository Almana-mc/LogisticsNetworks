package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.util.ItemStackCompat;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

final class BulkInsertRejectionCache {
    private static final int MAX_REJECTIONS = 64;

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
        return simulate(handler, candidate, inserter);
    }

    ItemStack simulate(IItemHandler handler, ItemStack candidate, Inserter inserter) {
        List<ItemStack> rejectedCandidates = rejected.get(handler);
        if (rejectedCandidates != null) {
            for (ItemStack rejectedCandidate : rejectedCandidates) {
                if (rejectedCandidate.getCount() == candidate.getCount()
                        && ItemStackCompat.isSameItemSameComponents(rejectedCandidate, candidate)) {
                    return candidate;
                }
            }
        }

        ItemStack remainder = inserter.insert(handler, candidate);
        if (remainder.getCount() == candidate.getCount()
                && ItemStackCompat.isSameItemSameComponents(remainder, candidate)) {
            List<ItemStack> handlerRejections = rejected.computeIfAbsent(handler, key -> new ArrayList<>());
            if (handlerRejections.size() < MAX_REJECTIONS) {
                handlerRejections.add(candidate.copy());
            }
        }
        return remainder;
    }

    void clear(IItemHandler handler) {
        rejected.remove(handler);
    }
}
