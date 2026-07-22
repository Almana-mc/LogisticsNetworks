package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.filter.FilterItemData;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.Arrays;

final class TransferSlotAccess {
    private TransferSlotAccess() {
    }

    static boolean[] build(IItemHandler handler, ItemStack[] filters) {
        if (handler == null || filters == null || filters.length == 0) {
            return null;
        }

        int slotCount = handler.getSlots();
        if (slotCount <= 0) {
            return null;
        }

        boolean[] allowed = new boolean[slotCount];
        boolean[] blacklistMask = new boolean[slotCount];
        boolean hasConfiguredSlotFilter = false;
        boolean hasWhitelist = false;

        for (ItemStack filter : filters) {
            if (!FilterItemData.isFilterItem(filter)) {
                continue;
            }

            boolean blacklist = FilterItemData.isBlacklist(filter);
            int capacity = FilterItemData.getCapacity(filter);
            for (int entry = 0; entry < capacity; entry++) {
                int[] slots = FilterItemData.getEntrySlotMapping(filter, entry);
                if (slots == null || slots.length == 0) {
                    continue;
                }

                hasConfiguredSlotFilter = true;
                if (blacklist) {
                    for (int slot : slots) {
                        if (slot >= 0 && slot < slotCount) {
                            blacklistMask[slot] = true;
                        }
                    }
                } else {
                    hasWhitelist = true;
                    for (int slot : slots) {
                        if (slot >= 0 && slot < slotCount) {
                            allowed[slot] = true;
                        }
                    }
                }
            }
        }

        if (!hasConfiguredSlotFilter) {
            return null;
        }

        if (!hasWhitelist) {
            Arrays.fill(allowed, true);
        }

        for (int i = 0; i < slotCount; i++) {
            if (blacklistMask[i]) {
                allowed[i] = false;
            }
        }

        return allowed;
    }

    static boolean hasAny(boolean[] allowedSlots) {
        if (allowedSlots == null) {
            return true;
        }
        for (boolean allowed : allowedSlots) {
            if (allowed) return true;
        }
        return false;
    }
}
