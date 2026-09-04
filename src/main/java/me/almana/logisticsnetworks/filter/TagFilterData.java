package me.almana.logisticsnetworks.filter;

import me.almana.logisticsnetworks.component.FilterSettings;
import me.almana.logisticsnetworks.component.FilterSettingsData;
import me.almana.logisticsnetworks.component.LegacyComponentMigration;
import me.almana.logisticsnetworks.component.LogisticsDataComponents;
import me.almana.logisticsnetworks.component.TagFilterConfig;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import me.almana.logisticsnetworks.item.TagFilterItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TagFilterData {

    public record View(@Nullable String tag, @Nullable TagKey<Item> itemTag, @Nullable TagKey<Fluid> fluidTag,
            FilterTargetType target, boolean blacklist) {
    }

    private TagFilterData() {
    }

    public static boolean isTagFilterItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof TagFilterItem;
    }

    private static View buildView(ItemStack stack) {
        LegacyComponentMigration.migrateTagFilter(stack);
        TagFilterConfig config = stack.get(LogisticsDataComponents.TAG_FILTER);
        String tag = config == null || config.tag().isEmpty() ? null : config.tag();
        TagKey<Item> itemTag = null;
        TagKey<Fluid> fluidTag = null;
        if (tag != null) {
            ResourceLocation id = ResourceLocation.tryParse(tag);
            if (id != null) {
                itemTag = TagKey.create(Registries.ITEM, id);
                fluidTag = TagKey.create(Registries.FLUID, id);
            }
        }
        FilterSettings settings = FilterSettingsData.get(stack);
        return new View(tag, itemTag, fluidTag, settings.target(), settings.blacklist());
    }

    public static boolean isBlacklist(ItemStack stack) {
        if (!isTagFilterItem(stack)) {
            return false;
        }
        LegacyComponentMigration.migrateTagFilter(stack);
        return FilterSettingsData.get(stack).blacklist();
    }

    public static void setBlacklist(ItemStack stack, boolean blacklist) {
        if (!isTagFilterItem(stack)) {
            return;
        }
        LegacyComponentMigration.migrateTagFilter(stack);
        FilterSettingsData.setBlacklist(stack, blacklist);
    }

    public static FilterTargetType getTargetType(ItemStack stack) {
        if (!isTagFilterItem(stack)) {
            return FilterTargetType.ITEMS;
        }
        LegacyComponentMigration.migrateTagFilter(stack);
        return FilterSettingsData.get(stack).target();
    }

    public static void setTargetType(ItemStack stack, FilterTargetType targetType) {
        if (!isTagFilterItem(stack)) {
            return;
        }
        LegacyComponentMigration.migrateTagFilter(stack);
        FilterSettingsData.setTarget(stack, targetType);
    }

    public static List<String> getTagFilters(ItemStack filterStack) {
        if (!isTagFilterItem(filterStack)) {
            return List.of();
        }

        LegacyComponentMigration.migrateTagFilter(filterStack);
        TagFilterConfig config = filterStack.get(LogisticsDataComponents.TAG_FILTER);
        if (config == null || config.tag().isEmpty()) {
            return List.of();
        }
        return List.of(config.tag());
    }

    public static int getTagFilterCount(ItemStack filterStack) {
        return getTagFilters(filterStack).size();
    }

    public static boolean hasAnyTags(ItemStack filterStack) {
        return getTagFilterCount(filterStack) > 0;
    }

    public static boolean addTagFilter(ItemStack filterStack, String tagValue) {
        if (!isTagFilterItem(filterStack)) {
            return false;
        }

        String normalized = normalizeTag(tagValue);
        if (normalized == null) {
            return false;
        }

        LegacyComponentMigration.migrateTagFilter(filterStack);
        TagFilterConfig current = filterStack.get(LogisticsDataComponents.TAG_FILTER);
        if (current != null && normalized.equals(current.tag())) {
            return false;
        }
        filterStack.set(LogisticsDataComponents.TAG_FILTER, new TagFilterConfig(normalized));
        return true;
    }

    public static boolean removeTagFilter(ItemStack filterStack, String tagValue) {
        if (!isTagFilterItem(filterStack)) {
            return false;
        }

        String normalized = normalizeTag(tagValue);
        if (normalized == null) {
            return false;
        }

        LegacyComponentMigration.migrateTagFilter(filterStack);
        TagFilterConfig current = filterStack.get(LogisticsDataComponents.TAG_FILTER);
        if (current == null || !normalized.equals(current.tag())) {
            return false;
        }
        filterStack.remove(LogisticsDataComponents.TAG_FILTER);
        return true;
    }

    public static boolean containsTag(ItemStack filterStack, ItemStack candidate) {
        if (!isTagFilterItem(filterStack) || candidate.isEmpty()) {
            return false;
        }
        if (getTargetType(filterStack) != FilterTargetType.ITEMS) {
            return false;
        }

        List<String> filterTags = getTagFilters(filterStack);
        if (filterTags.isEmpty()) {
            return false;
        }

        Set<String> tagSet = new HashSet<>(filterTags);
        return candidate.getTags().map(tag -> tag.location().toString()).anyMatch(tagSet::contains);
    }

    public static boolean containsTag(ItemStack filterStack, FluidStack candidate) {
        if (!isTagFilterItem(filterStack) || candidate == null || candidate.isEmpty()) {
            return false;
        }
        if (getTargetType(filterStack) != FilterTargetType.FLUIDS) {
            return false;
        }

        List<String> filterTags = getTagFilters(filterStack);
        if (filterTags.isEmpty()) {
            return false;
        }

        Set<String> tagSet = new HashSet<>(filterTags);
        return candidate.getTags().map(tag -> tag.location().toString()).anyMatch(tagSet::contains);
    }

    public static boolean containsTag(ItemStack filterStack, String chemicalId) {
        if (!isTagFilterItem(filterStack) || chemicalId == null || chemicalId.isEmpty()) {
            return false;
        }
        if (getTargetType(filterStack) != FilterTargetType.CHEMICALS) {
            return false;
        }

        List<String> filterTags = getTagFilters(filterStack);
        if (filterTags.isEmpty()) {
            return false;
        }

        for (String tag : filterTags) {
            if (MekanismCompat.chemicalHasTag(chemicalId, tag)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeTag(String tagValue) {
        return FilterTagUtil.normalizeTag(tagValue);
    }

}
