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

            // Check each filter type
            if (FilterItemData.isFilterItem(filter)
                    && FilterItemData.hasAnyItemMatchEntries(filter, filterReadCache)) {
                isFilter = true;
                matched = FilterItemData.containsItemFull(filter, candidate, provider, candidateNbt, filterReadCache);
                isBlacklist = FilterItemData.isBlacklist(filter, filterReadCache);
            } else if (ModFilterData.isModFilter(filter) && ModFilterData.hasAnyMods(filter)
                    && ModFilterData.getTargetType(filter) == FilterTargetType.ITEMS) {
                isFilter = true;
                matched = ModFilterData.containsMod(filter, candidate);
                isBlacklist = ModFilterData.isBlacklist(filter);
            } else if (NameFilterData.isNameFilter(filter) && NameFilterData.hasNameFilter(filter)
                    && NameFilterData.getTargetType(filter) == FilterTargetType.ITEMS) {
                isFilter = true;
                matched = NameFilterData.containsName(filter, candidate);
                isBlacklist = NameFilterData.isBlacklist(filter);
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
                    && (FilterItemData.hasAnyFluidEntries(filter) || FilterItemData.hasAnyTagEntries(filter))) {
                isFilter = true;
                matched = FilterItemData.containsFluidFull(filter, candidate, provider);
                isBlacklist = FilterItemData.isBlacklist(filter);
            } else if (ModFilterData.isModFilter(filter) && ModFilterData.hasAnyMods(filter)
                    && ModFilterData.getTargetType(filter) == FilterTargetType.FLUIDS) {
                isFilter = true;
                matched = ModFilterData.containsMod(filter, candidate);
                isBlacklist = ModFilterData.isBlacklist(filter);
            } else if (NameFilterData.isNameFilter(filter) && NameFilterData.hasNameFilter(filter)
                    && NameFilterData.getTargetType(filter) == FilterTargetType.FLUIDS) {
                isFilter = true;
                matched = NameFilterData.containsName(filter, candidate);
                isBlacklist = NameFilterData.isBlacklist(filter);
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
                    && (FilterItemData.hasAnyChemicalEntries(filter) || FilterItemData.hasAnyTagEntries(filter))) {
                isFilter = true;
                matched = FilterItemData.containsChemicalFull(filter, chemicalId);
                isBlacklist = FilterItemData.isBlacklist(filter);
            } else if (ModFilterData.isModFilter(filter) && ModFilterData.hasAnyMods(filter)
                    && ModFilterData.getTargetType(filter) == FilterTargetType.CHEMICALS) {
                isFilter = true;
                matched = ModFilterData.containsMod(filter, chemicalId);
                isBlacklist = ModFilterData.isBlacklist(filter);
            } else if (NameFilterData.isNameFilter(filter) && NameFilterData.hasNameFilter(filter)
                    && NameFilterData.getTargetType(filter) == FilterTargetType.CHEMICALS) {
                isFilter = true;
                matched = NameFilterData.containsName(filter, chemicalId);
                isBlacklist = NameFilterData.isBlacklist(filter);
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
        if (filters == null)
            return false;
        for (ItemStack filter : filters) {
            if (FilterItemData.isFilterItem(filter) && FilterItemData.hasAnyNbtEntries(filter)) {
                return true;
            }
        }
        return false;
    }
}
