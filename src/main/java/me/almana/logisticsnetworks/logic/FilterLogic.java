package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.FilterMode;
import me.almana.logisticsnetworks.filter.*;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
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
            } else if (TagFilterData.isTagFilterItem(filter)) {
                TagFilterData.View view = TagFilterData.view(filter, filterReadCache);
                if (view.itemTag() != null && view.target() == FilterTargetType.ITEMS) {
                    isFilter = true;
                    matched = candidate.is(view.itemTag());
                    isBlacklist = view.blacklist();
                }
            } else if (ModFilterData.isModFilter(filter)) {
                ModFilterData.View view = ModFilterData.view(filter, filterReadCache);
                if (!view.mods().isEmpty() && view.target() == FilterTargetType.ITEMS) {
                    isFilter = true;
                    matched = view.matchesNamespace(BuiltInRegistries.ITEM.getKey(candidate.getItem()));
                    isBlacklist = view.blacklist();
                }
            } else if (NbtFilterData.isNbtFilter(filter)) {
                NbtFilterData.View view = NbtFilterData.view(filter, filterReadCache);
                if (view.target() == FilterTargetType.ITEMS && view.anyEnabled()) {
                    isFilter = true;
                    matched = NbtFilterData.matches(view.rules(), candidateNbt);
                    isBlacklist = view.blacklist();
                }
            } else if (NameFilterData.isNameFilter(filter)) {
                NameFilterData.View view = NameFilterData.view(filter, filterReadCache);
                if (!view.name().isEmpty() && view.target() == FilterTargetType.ITEMS) {
                    isFilter = true;
                    matched = NameFilterData.matches(view, candidate);
                    isBlacklist = view.blacklist();
                }
            } else if (DurabilityFilterData.isDurabilityFilterItem(filter)) {
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
            } else if (TagFilterData.isTagFilterItem(filter)) {
                TagFilterData.View view = TagFilterData.view(filter, filterReadCache);
                if (view.itemTag() != null && view.target() == FilterTargetType.ITEMS) {
                    isFilter = true;
                    matched = candidate.is(view.itemTag());
                    isBlacklist = view.blacklist();
                }
            } else if (ModFilterData.isModFilter(filter)) {
                ModFilterData.View view = ModFilterData.view(filter, filterReadCache);
                if (!view.mods().isEmpty() && view.target() == FilterTargetType.ITEMS) {
                    isFilter = true;
                    matched = view.matchesNamespace(BuiltInRegistries.ITEM.getKey(candidate.getItem()));
                    isBlacklist = view.blacklist();
                }
            } else if (NbtFilterData.isNbtFilter(filter)) {
                NbtFilterData.View view = NbtFilterData.view(filter, filterReadCache);
                if (view.target() == FilterTargetType.ITEMS && view.anyEnabled()) {
                    isFilter = true;
                    matched = NbtFilterData.matches(view.rules(), candidateNbt);
                    isBlacklist = view.blacklist();
                }
            } else if (NameFilterData.isNameFilter(filter)) {
                NameFilterData.View view = NameFilterData.view(filter, filterReadCache);
                if (!view.name().isEmpty() && view.target() == FilterTargetType.ITEMS) {
                    isFilter = true;
                    matched = NameFilterData.matches(view, candidate);
                    isBlacklist = view.blacklist();
                }
            } else if (DurabilityFilterData.isDurabilityFilterItem(filter)) {
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
            } else if (TagFilterData.isTagFilterItem(filter)) {
                TagFilterData.View view = TagFilterData.view(filter, filterReadCache);
                if (view.fluidTag() != null && view.target() == FilterTargetType.FLUIDS) {
                    isFilter = true;
                    matched = candidate.getFluid().builtInRegistryHolder().is(view.fluidTag());
                    isBlacklist = view.blacklist();
                }
            } else if (ModFilterData.isModFilter(filter)) {
                ModFilterData.View view = ModFilterData.view(filter, filterReadCache);
                if (!view.mods().isEmpty() && view.target() == FilterTargetType.FLUIDS) {
                    isFilter = true;
                    matched = view.matchesNamespace(BuiltInRegistries.FLUID.getKey(candidate.getFluid()));
                    isBlacklist = view.blacklist();
                }
            } else if (NbtFilterData.isNbtFilter(filter)) {
                NbtFilterData.View view = NbtFilterData.view(filter, filterReadCache);
                if (view.target() == FilterTargetType.FLUIDS && view.anyEnabled()) {
                    isFilter = true;
                    matched = provider != null && NbtFilterData.matches(view.rules(),
                            NbtFilterData.getSerializedComponents(candidate, provider));
                    isBlacklist = view.blacklist();
                }
            } else if (NameFilterData.isNameFilter(filter)) {
                NameFilterData.View view = NameFilterData.view(filter, filterReadCache);
                if (!view.name().isEmpty() && view.target() == FilterTargetType.FLUIDS) {
                    isFilter = true;
                    matched = NameFilterData.matches(view, candidate);
                    isBlacklist = view.blacklist();
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
            } else if (TagFilterData.isTagFilterItem(filter)) {
                TagFilterData.View view = TagFilterData.view(filter, filterReadCache);
                if (view.tag() != null && view.target() == FilterTargetType.CHEMICALS) {
                    isFilter = true;
                    matched = MekanismCompat.chemicalHasTag(chemicalId, view.tag());
                    isBlacklist = view.blacklist();
                }
            } else if (ModFilterData.isModFilter(filter)) {
                ModFilterData.View view = ModFilterData.view(filter, filterReadCache);
                if (!view.mods().isEmpty() && view.target() == FilterTargetType.CHEMICALS) {
                    isFilter = true;
                    matched = view.matchesNamespace(ResourceLocation.tryParse(chemicalId));
                    isBlacklist = view.blacklist();
                }
            } else if (NameFilterData.isNameFilter(filter)) {
                NameFilterData.View view = NameFilterData.view(filter, filterReadCache);
                if (!view.name().isEmpty() && view.target() == FilterTargetType.CHEMICALS) {
                    isFilter = true;
                    matched = NameFilterData.matches(view, chemicalId);
                    isBlacklist = view.blacklist();
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

    public static boolean hasConfiguredItemNbtFilter(ItemStack[] filters, @Nullable FilterItemData.ReadCache readCache) {
        if (filters == null)
            return false;
        for (ItemStack filter : filters) {
            if (NbtFilterData.isNbtFilter(filter)) {
                NbtFilterData.View view = NbtFilterData.view(filter, readCache);
                if (view.target() == FilterTargetType.ITEMS && view.anyEnabled()) {
                    return true;
                }
            }
            if (FilterItemData.isFilterItem(filter) && FilterItemData.hasAnyNbtEntries(filter, readCache)) {
                return true;
            }
        }
        return false;
    }
}
