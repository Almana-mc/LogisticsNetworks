package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.FilterMode;
import me.almana.logisticsnetworks.filter.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

public final class FilterLogic {

    private FilterLogic() {
    }

    public static boolean matchesItem(ItemStack[] filters, FilterMode filterMode, ItemStack candidate,
            HolderLookup.Provider provider, @Nullable CompoundTag candidateNbt) {
        return matchesItem(filters, filterMode, candidate, provider, candidateNbt, null);
    }

    public static boolean matchesItemInSlot(ItemStack[] filters, FilterMode filterMode, ItemStack candidate,
            HolderLookup.Provider provider, @Nullable CompoundTag candidateNbt,
            @Nullable FilterItemData.ReadCache filterReadCache, int inventorySlot) {
        if (inventorySlot < 0)
            return matchesItem(filters, filterMode, candidate, provider, candidateNbt, filterReadCache);

        if (filters == null || filters.length == 0)
            return true;
        if (candidate.isEmpty())
            return false;

        boolean matchAll = filterMode == FilterMode.MATCH_ALL;
        boolean hasConfiguredFilter = false;
        boolean anyWhitelistMatched = false;
        boolean allWhitelistsMatched = true;
        boolean hasWhitelist = false;

        for (ItemStack filter : filters) {
            if (filter.isEmpty())
                continue;

            boolean isFilter = false;
            boolean matched = false;
            boolean isBlacklist = false;

            if (FilterItemData.isFilterItem(filter)
                    && FilterItemData.hasAnyItemMatchEntries(filter, filterReadCache)) {
                isFilter = true;
                matched = FilterItemData.containsItemFullInSlot(filter, candidate, provider, candidateNbt,
                        filterReadCache, inventorySlot);
                isBlacklist = FilterItemData.isBlacklist(filter, filterReadCache);
            } else if (ModFilterData.isModFilter(filter) && ModFilterData.hasAnyMods(filter, filterReadCache)
                    && ModFilterData.getTargetType(filter, filterReadCache) == FilterTargetType.ITEMS) {
                isFilter = true;
                matched = ModFilterData.containsMod(filter, candidate, filterReadCache);
                isBlacklist = ModFilterData.isBlacklist(filter, filterReadCache);
            } else if (NameFilterData.isNameFilter(filter) && NameFilterData.hasNameFilter(filter, filterReadCache)
                    && NameFilterData.getTargetType(filter, filterReadCache) == FilterTargetType.ITEMS) {
                isFilter = true;
                matched = NameFilterData.containsName(filter, candidate, filterReadCache);
                isBlacklist = NameFilterData.isBlacklist(filter, filterReadCache);
            }

            if (isFilter) {
                hasConfiguredFilter = true;
                if (isBlacklist) {
                    if (matched)
                        return false;
                } else {
                    hasWhitelist = true;
                    if (matched) {
                        anyWhitelistMatched = true;
                    } else {
                        if (matchAll)
                            return false;
                        allWhitelistsMatched = false;
                    }
                }
            }
        }

        if (!hasConfiguredFilter)
            return true;
        if (!hasWhitelist)
            return true;

        return matchAll ? allWhitelistsMatched : anyWhitelistMatched;
    }

    public static boolean matchesItem(ItemStack[] filters, FilterMode filterMode, ItemStack candidate,
            HolderLookup.Provider provider, @Nullable CompoundTag candidateNbt,
            @Nullable FilterItemData.ReadCache filterReadCache) {
        if (filters == null || filters.length == 0)
            return true;
        if (candidate.isEmpty())
            return false;

        boolean matchAll = filterMode == FilterMode.MATCH_ALL;
        boolean hasConfiguredFilter = false;

        boolean anyWhitelistMatched = false;
        boolean allWhitelistsMatched = true;
        boolean hasWhitelist = false;

        for (ItemStack filter : filters) {
            if (filter.isEmpty())
                continue;

            boolean isFilter = false;
            boolean matched = false;
            boolean isBlacklist = false;

            if (FilterItemData.isFilterItem(filter)
                    && FilterItemData.hasAnyItemMatchEntries(filter, filterReadCache)) {
                isFilter = true;
                matched = FilterItemData.containsItemFull(filter, candidate, provider, candidateNbt, filterReadCache);
                isBlacklist = FilterItemData.isBlacklist(filter, filterReadCache);
            } else if (ModFilterData.isModFilter(filter) && ModFilterData.hasAnyMods(filter, filterReadCache)
                    && ModFilterData.getTargetType(filter, filterReadCache) == FilterTargetType.ITEMS) {
                isFilter = true;
                matched = ModFilterData.containsMod(filter, candidate, filterReadCache);
                isBlacklist = ModFilterData.isBlacklist(filter, filterReadCache);
            } else if (NameFilterData.isNameFilter(filter) && NameFilterData.hasNameFilter(filter, filterReadCache)
                    && NameFilterData.getTargetType(filter, filterReadCache) == FilterTargetType.ITEMS) {
                isFilter = true;
                matched = NameFilterData.containsName(filter, candidate, filterReadCache);
                isBlacklist = NameFilterData.isBlacklist(filter, filterReadCache);
            }

            if (isFilter) {
                hasConfiguredFilter = true;
                if (isBlacklist) {
                    if (matched)
                        return false;
                } else {
                    hasWhitelist = true;
                    if (matched) {
                        anyWhitelistMatched = true;
                    } else {
                        if (matchAll)
                            return false;
                        allWhitelistsMatched = false;
                    }
                }
            }
        }

        if (!hasConfiguredFilter)
            return true;
        if (!hasWhitelist)
            return true;

        return matchAll ? allWhitelistsMatched : anyWhitelistMatched;
    }

    public static boolean matchesFluid(ItemStack[] filters, FilterMode filterMode, FluidStack candidate,
            HolderLookup.Provider provider) {
        return matchesFluid(filters, filterMode, candidate, provider, null);
    }

    public static boolean matchesFluid(ItemStack[] filters, FilterMode filterMode, FluidStack candidate,
            HolderLookup.Provider provider, @Nullable FilterItemData.ReadCache filterReadCache) {
        if (filters == null || filters.length == 0)
            return true;
        if (candidate.isEmpty())
            return false;

        boolean matchAll = filterMode == FilterMode.MATCH_ALL;
        boolean hasConfiguredFilter = false;

        boolean anyWhitelistMatched = false;
        boolean allWhitelistsMatched = true;
        boolean hasWhitelist = false;

        for (ItemStack filter : filters) {
            if (filter.isEmpty())
                continue;

            boolean isFilter = false;
            boolean matched = false;
            boolean isBlacklist = false;

            if (FilterItemData.isFilterItem(filter)
                    && (FilterItemData.hasAnyFluidEntries(filter, filterReadCache)
                            || FilterItemData.hasAnyTagEntries(filter, filterReadCache))) {
                isFilter = true;
                matched = FilterItemData.containsFluidFull(filter, candidate, provider, filterReadCache);
                isBlacklist = FilterItemData.isBlacklist(filter, filterReadCache);
            } else if (ModFilterData.isModFilter(filter) && ModFilterData.hasAnyMods(filter, filterReadCache)
                    && ModFilterData.getTargetType(filter, filterReadCache) == FilterTargetType.FLUIDS) {
                isFilter = true;
                matched = ModFilterData.containsMod(filter, candidate, filterReadCache);
                isBlacklist = ModFilterData.isBlacklist(filter, filterReadCache);
            } else if (NameFilterData.isNameFilter(filter) && NameFilterData.hasNameFilter(filter, filterReadCache)
                    && NameFilterData.getTargetType(filter, filterReadCache) == FilterTargetType.FLUIDS) {
                isFilter = true;
                matched = NameFilterData.containsName(filter, candidate, filterReadCache);
                isBlacklist = NameFilterData.isBlacklist(filter, filterReadCache);
            }

            if (isFilter) {
                hasConfiguredFilter = true;
                if (isBlacklist) {
                    if (matched)
                        return false;
                } else {
                    hasWhitelist = true;
                    if (matched) {
                        anyWhitelistMatched = true;
                    } else {
                        if (matchAll)
                            return false;
                        allWhitelistsMatched = false;
                    }
                }
            }
        }

        if (!hasConfiguredFilter)
            return true;
        if (!hasWhitelist)
            return true;

        return matchAll ? allWhitelistsMatched : anyWhitelistMatched;
    }

    public static boolean matchesChemical(ItemStack[] filters, FilterMode filterMode, String chemicalId) {
        return matchesChemical(filters, filterMode, chemicalId, null);
    }

    public static boolean matchesChemical(ItemStack[] filters, FilterMode filterMode, String chemicalId,
            @Nullable FilterItemData.ReadCache filterReadCache) {
        if (filters == null || filters.length == 0)
            return true;
        if (chemicalId == null || chemicalId.isEmpty())
            return false;

        boolean matchAll = filterMode == FilterMode.MATCH_ALL;
        boolean hasConfiguredFilter = false;

        boolean anyWhitelistMatched = false;
        boolean allWhitelistsMatched = true;
        boolean hasWhitelist = false;

        for (ItemStack filter : filters) {
            if (filter.isEmpty())
                continue;

            boolean isFilter = false;
            boolean matched = false;
            boolean isBlacklist = false;

            if (FilterItemData.isFilterItem(filter)
                    && (FilterItemData.hasAnyChemicalEntries(filter, filterReadCache)
                            || FilterItemData.hasAnyTagEntries(filter, filterReadCache))) {
                isFilter = true;
                matched = FilterItemData.containsChemicalFull(filter, chemicalId, filterReadCache);
                isBlacklist = FilterItemData.isBlacklist(filter, filterReadCache);
            } else if (ModFilterData.isModFilter(filter) && ModFilterData.hasAnyMods(filter, filterReadCache)
                    && ModFilterData.getTargetType(filter, filterReadCache) == FilterTargetType.CHEMICALS) {
                isFilter = true;
                matched = ModFilterData.containsMod(filter, chemicalId, filterReadCache);
                isBlacklist = ModFilterData.isBlacklist(filter, filterReadCache);
            } else if (NameFilterData.isNameFilter(filter) && NameFilterData.hasNameFilter(filter, filterReadCache)
                    && NameFilterData.getTargetType(filter, filterReadCache) == FilterTargetType.CHEMICALS) {
                isFilter = true;
                matched = NameFilterData.containsName(filter, chemicalId, filterReadCache);
                isBlacklist = NameFilterData.isBlacklist(filter, filterReadCache);
            }

            if (isFilter) {
                hasConfiguredFilter = true;
                if (isBlacklist) {
                    if (matched)
                        return false;
                } else {
                    hasWhitelist = true;
                    if (matched) {
                        anyWhitelistMatched = true;
                    } else {
                        if (matchAll)
                            return false;
                        allWhitelistsMatched = false;
                    }
                }
            }
        }

        if (!hasConfiguredFilter)
            return true;
        if (!hasWhitelist)
            return true;

        return matchAll ? allWhitelistsMatched : anyWhitelistMatched;
    }

    public static boolean hasConfiguredItemNbtFilter(ItemStack[] filters) {
        return hasConfiguredItemNbtFilter(filters, null);
    }

    public static boolean hasConfiguredItemNbtFilter(ItemStack[] filters,
            @Nullable FilterItemData.ReadCache readCache) {
        if (filters == null)
            return false;
        for (ItemStack filter : filters) {
            if (FilterItemData.isFilterItem(filter) && FilterItemData.hasAnyNbtEntries(filter, readCache)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasConfiguredSlotMapping(ItemStack[] filters,
            @Nullable FilterItemData.ReadCache readCache) {
        if (filters == null)
            return false;
        for (ItemStack filter : filters) {
            if (FilterItemData.isFilterItem(filter) && FilterItemData.hasAnySlotMappings(filter, readCache)) {
                return true;
            }
        }
        return false;
    }
}
