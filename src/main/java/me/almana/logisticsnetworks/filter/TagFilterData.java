package me.almana.logisticsnetworks.filter;

import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import me.almana.logisticsnetworks.item.TagFilterItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TagFilterData {

    private static final String ROOT_KEY = "ln_tag_filter";
    private static final String MODE_KEY = "blacklist";
    private static final String TAGS_KEY = "tags";
    private static final String TARGET_KEY = "target";

    private TagFilterData() {
    }

    public static boolean isTagFilterItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof TagFilterItem;
    }

    public static boolean isBlacklist(ItemStack stack) {
        if (!isTagFilterItem(stack)) {
            return false;
        }
        return getRootTag(stack).getBoolean(MODE_KEY);
    }

    public static void setBlacklist(ItemStack stack, boolean blacklist) {
        if (!isTagFilterItem(stack)) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, customTag -> {
            CompoundTag root = getRootTag(customTag);
            if (blacklist) {
                root.putBoolean(MODE_KEY, true);
            } else {
                root.remove(MODE_KEY);
            }
            writeRoot(customTag, root);
        });
    }

    public static FilterTargetType getTargetType(ItemStack stack) {
        if (!isTagFilterItem(stack)) {
            return FilterTargetType.ITEMS;
        }
        CompoundTag root = getRootTag(stack);
        return FilterTargetType.fromOrdinal(root.getInt(TARGET_KEY));
    }

    public static void setTargetType(ItemStack stack, FilterTargetType targetType) {
        if (!isTagFilterItem(stack)) {
            return;
        }
        FilterTargetType normalized = targetType == null ? FilterTargetType.ITEMS : targetType;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, customTag -> {
            CompoundTag root = getRootTag(customTag);
            if (normalized == FilterTargetType.ITEMS) {
                root.remove(TARGET_KEY);
            } else {
                root.putInt(TARGET_KEY, normalized.ordinal());
            }
            writeRoot(customTag, root);
        });
    }

    public static List<String> getTagFilters(ItemStack filterStack) {
        if (!isTagFilterItem(filterStack)) {
            return List.of();
        }

        CompoundTag root = getRootTag(filterStack);
        if (!root.contains(TAGS_KEY, Tag.TAG_LIST)) {
            return List.of();
        }

        ListTag list = root.getList(TAGS_KEY, Tag.TAG_STRING);
        List<String> tags = new ArrayList<>(1);
        for (int i = 0; i < list.size(); i++) {
            String normalized = normalizeTag(list.getString(i));
            if (normalized != null) {
                tags.add(normalized);
                break;
            }
        }
        return tags;
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

        final boolean[] changed = { false };
        CustomData.update(DataComponents.CUSTOM_DATA, filterStack, customTag -> {
            CompoundTag root = getRootTag(customTag);
            ListTag current = root.getList(TAGS_KEY, Tag.TAG_STRING);
            boolean alreadySingle = current.size() == 1 && normalized.equals(current.getString(0));
            if (!alreadySingle) {
                ListTag single = new ListTag();
                single.add(StringTag.valueOf(normalized));
                root.put(TAGS_KEY, single);
                changed[0] = true;
            }
            writeRoot(customTag, root);
        });
        return changed[0];
    }

    public static boolean removeTagFilter(ItemStack filterStack, String tagValue) {
        if (!isTagFilterItem(filterStack)) {
            return false;
        }

        String normalized = normalizeTag(tagValue);
        if (normalized == null) {
            return false;
        }

        final boolean[] changed = { false };
        CustomData.update(DataComponents.CUSTOM_DATA, filterStack, customTag -> {
            CompoundTag root = getRootTag(customTag);
            ListTag list = root.getList(TAGS_KEY, Tag.TAG_STRING);
            String current = null;
            for (int i = 0; i < list.size(); i++) {
                String entry = normalizeTag(list.getString(i));
                if (entry != null) {
                    current = entry;
                    break;
                }
            }

            if (current == null || !normalized.equals(current)) {
                writeRoot(customTag, root);
                return;
            }

            changed[0] = true;
            root.remove(TAGS_KEY);
            writeRoot(customTag, root);
        });

        return changed[0];
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
        if (tagValue == null) {
            return null;
        }

        String cleaned = tagValue.trim();
        if (cleaned.startsWith("#")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.isEmpty()) {
            return null;
        }

        ResourceLocation id = ResourceLocation.tryParse(cleaned);
        return id == null ? null : id.toString();
    }

    private static CompoundTag getRootTag(ItemStack stack) {
        return getRootTag(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
    }

    private static CompoundTag getRootTag(CompoundTag customTag) {
        if (customTag.contains(ROOT_KEY, Tag.TAG_COMPOUND)) {
            return customTag.getCompound(ROOT_KEY).copy();
        }
        return new CompoundTag();
    }

    private static void writeRoot(CompoundTag customTag, CompoundTag root) {
        if (root.isEmpty()) {
            customTag.remove(ROOT_KEY);
        } else {
            customTag.put(ROOT_KEY, root);
        }
    }
}
