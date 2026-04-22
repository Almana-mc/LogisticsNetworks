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
            } else if (TagFilterData.isTagFilterItem(filter) && TagFilterData.hasAnyTags(filter)
                    && TagFilterData.getTargetType(filter) == FilterTargetType.ITEMS) {
                isFilter = true;
                matched = TagFilterData.containsTag(filter, candidate);
                isBlacklist = TagFilterData.isBlacklist(filter);
            } else if (ModFilterData.isModFilter(filter) && ModFilterData.hasAnyMods(filter)
                    && ModFilterData.getTargetType(filter) == FilterTargetType.ITEMS) {
                isFilter = true;
                matched = ModFilterData.containsMod(filter, candidate);
                isBlacklist = ModFilterData.isBlacklist(filter);
            } else if (NbtFilterData.isNbtFilter(filter)
                    && NbtFilterData.getTargetType(filter) == FilterTargetType.ITEMS) {
                if (NbtFilterData.hasEnabledRules(filter)) {
                    isFilter = true;
                    matched = NbtFilterData.matches(filter, candidateNbt);
                    isBlacklist = NbtFilterData.isBlacklist(filter);
                }
            } else if (NameFilterData.isNameFilter(filter) && NameFilterData.hasNameFilter(filter)
                    && NameFilterData.getTargetType(filter) == FilterTargetType.ITEMS) {
                isFilter = true;
                matched = NameFilterData.containsName(filter, candidate);
                isBlacklist = NameFilterData.isBlacklist(filter);
            } else if (DurabilityFilterData.isDurabilityFilterItem(filter)) {
                isFilter = true;
                if (!DurabilityFilterData.matches(filter, candidate))
                    return false;
                hasConfiguredFilter = true;
                continue;
            }

            if (isFilter) {
                hasConfiguredFilter = true;
                if (isBlacklist) {
                    if (matched)
                        return false;
                } else {
                    hasWhitelist = true;
                    if (matched)
                        anyWhitelistMatched = true;
                    else
                        allWhitelistsMatched = false;
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

            // Check each filter type
            if (FilterItemData.isFilterItem(filter)
                    && FilterItemData.hasAnyItemMatchEntries(filter, filterReadCache)) {
                isFilter = true;
                matched = FilterItemData.containsItemFull(filter, candidate, provider, candidateNbt, filterReadCache);
                isBlacklist = FilterItemData.isBlacklist(filter, filterReadCache);
            } else if (TagFilterData.isTagFilterItem(filter) && TagFilterData.hasAnyTags(filter)
                    && TagFilterData.getTargetType(filter) == FilterTargetType.ITEMS) {
                isFilter = true;
                matched = TagFilterData.containsTag(filter, candidate);
                isBlacklist = TagFilterData.isBlacklist(filter);
            } else if (ModFilterData.isModFilter(filter) && ModFilterData.hasAnyMods(filter)
                    && ModFilterData.getTargetType(filter) == FilterTargetType.ITEMS) {
                isFilter = true;
                matched = ModFilterData.containsMod(filter, candidate);
                isBlacklist = ModFilterData.isBlacklist(filter);
            } else if (NbtFilterData.isNbtFilter(filter)
                    && NbtFilterData.getTargetType(filter) == FilterTargetType.ITEMS) {
                if (NbtFilterData.hasEnabledRules(filter)) {
                    isFilter = true;
                    matched = NbtFilterData.matches(filter, candidateNbt);
                    isBlacklist = NbtFilterData.isBlacklist(filter);
                }
            } else if (NameFilterData.isNameFilter(filter) && NameFilterData.hasNameFilter(filter)
                    && NameFilterData.getTargetType(filter) == FilterTargetType.ITEMS) {
                isFilter = true;
                matched = NameFilterData.containsName(filter, candidate);
                isBlacklist = NameFilterData.isBlacklist(filter);
            } else if (DurabilityFilterData.isDurabilityFilterItem(filter)) {
                isFilter = true;
                if (!DurabilityFilterData.matches(filter, candidate)) {
                    return false;
                }
                hasConfiguredFilter = true;
                continue;
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
            } else if (TagFilterData.isTagFilterItem(filter) && TagFilterData.hasAnyTags(filter)
                    && TagFilterData.getTargetType(filter) == FilterTargetType.FLUIDS) {
                isFilter = true;
                matched = TagFilterData.containsTag(filter, candidate);
                isBlacklist = TagFilterData.isBlacklist(filter);
            } else if (ModFilterData.isModFilter(filter) && ModFilterData.hasAnyMods(filter)
                    && ModFilterData.getTargetType(filter) == FilterTargetType.FLUIDS) {
                isFilter = true;
                matched = ModFilterData.containsMod(filter, candidate);
                isBlacklist = ModFilterData.isBlacklist(filter);
            } else if (NbtFilterData.isNbtFilter(filter)
                    && NbtFilterData.getTargetType(filter) == FilterTargetType.FLUIDS) {
                if (NbtFilterData.hasEnabledRules(filter)) {
                    isFilter = true;
                    matched = NbtFilterData.matchesSelection(filter, candidate, provider);
                    isBlacklist = NbtFilterData.isBlacklist(filter);
                }
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
            } else if (TagFilterData.isTagFilterItem(filter) && TagFilterData.hasAnyTags(filter)
                    && TagFilterData.getTargetType(filter) == FilterTargetType.CHEMICALS) {
                isFilter = true;
                matched = TagFilterData.containsTag(filter, chemicalId);
                isBlacklist = TagFilterData.isBlacklist(filter);
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
            if (NbtFilterData.isNbtFilter(filter)
                    && NbtFilterData.getTargetType(filter) == FilterTargetType.ITEMS
                    && NbtFilterData.hasEnabledRules(filter)) {
                return true;
            }
            if (FilterItemData.isFilterItem(filter) && FilterItemData.hasAnyNbtEntries(filter)) {
                return true;
            }
        }
        return false;
    }
}
