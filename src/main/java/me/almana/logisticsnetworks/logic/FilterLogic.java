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
            if (FilterItemData.isFilterItem(filter) && FilterItemData.hasAnyItemEntries(filter)) {
                isFilter = true;
                matched = FilterItemData.containsItem(filter, candidate, provider);
                isBlacklist = FilterItemData.isBlacklist(filter);
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
                String path = NbtFilterData.getSelectedPath(filter);
                if (path != null && !NbtFilterData.isFluidPath(path)) {
                    isFilter = true;
                    matched = NbtFilterData.matchesSelection(filter, path, candidateNbt);
                    isBlacklist = NbtFilterData.isBlacklist(filter);
                }
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

            if (FilterItemData.isFilterItem(filter) && FilterItemData.hasAnyFluidEntries(filter)) {
                isFilter = true;
                matched = FilterItemData.containsFluid(filter, candidate);
                isBlacklist = FilterItemData.isBlacklist(filter);
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
                String path = NbtFilterData.getSelectedPath(filter);
                if (path != null && NbtFilterData.isFluidPath(path)) {
                    isFilter = true;
                    matched = NbtFilterData.matchesSelection(filter, candidate, provider);
                    isBlacklist = NbtFilterData.isBlacklist(filter);
                }
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

            if (FilterItemData.isFilterItem(filter) && FilterItemData.hasAnyChemicalEntries(filter)) {
                isFilter = true;
                matched = FilterItemData.containsChemical(filter, chemicalId);
                isBlacklist = FilterItemData.isBlacklist(filter);
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
                    && NbtFilterData.getSelectedPath(filter) != null) {
                return true;
            }
        }
        return false;
    }
}
