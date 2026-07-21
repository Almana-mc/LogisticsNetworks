package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.filter.SlotFilterData;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.Arrays;
import java.util.List;

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
            if (!SlotFilterData.isSlotFilterItem(filter) || !SlotFilterData.hasAnySlots(filter)) {
                continue;
            }

            hasConfiguredSlotFilter = true;
            List<Integer> slots = SlotFilterData.getSlots(filter);
            if (slots.isEmpty()) {
                continue;
            }

            if (SlotFilterData.isBlacklist(filter)) {
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
