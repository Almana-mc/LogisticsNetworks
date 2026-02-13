package me.almana.logisticsnetworks.filter;

import me.almana.logisticsnetworks.item.NbtFilterItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class NbtFilterData {

    private static final String KEY_ROOT = "ln_nbt_filter";
    private static final String KEY_IS_BLACKLIST = "blacklist";
    private static final String KEY_PATH = "path";
    private static final String KEY_VALUE = "value";
    private static final String KEY_TARGET_TYPE = "target";

    public record NbtEntry(String path, String valueDisplay) {
    }

    private NbtFilterData() {
    }

    public static boolean isNbtFilter(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof NbtFilterItem;
    }

    public static boolean isBlacklist(ItemStack stack) {
        if (!isNbtFilter(stack))
            return false;
        return getRoot(stack).getBoolean(KEY_IS_BLACKLIST);
    }

    public static void setBlacklist(ItemStack stack, boolean isBlacklist) {
        if (!isNbtFilter(stack))
            return;

        updateRoot(stack, root -> {
            if (isBlacklist) {
                root.putBoolean(KEY_IS_BLACKLIST, true);
            } else {
                root.remove(KEY_IS_BLACKLIST);
            }
        });
    }

    public static FilterTargetType getTargetType(ItemStack stack) {
        if (!isNbtFilter(stack))
            return FilterTargetType.ITEMS;

        CompoundTag root = getRoot(stack);
        if (root.contains(KEY_TARGET_TYPE, Tag.TAG_INT)) {
            return FilterTargetType.fromOrdinal(root.getInt(KEY_TARGET_TYPE));
        }

        String path = root.getString(KEY_PATH);
        return isFluidPath(path) ? FilterTargetType.FLUIDS : FilterTargetType.ITEMS;
    }

    public static void setTargetType(ItemStack stack, FilterTargetType type) {
        if (!isNbtFilter(stack))
            return;

        FilterTargetType target = type == null ? FilterTargetType.ITEMS : type;
        updateRoot(stack, root -> {
            if (target == FilterTargetType.ITEMS) {
                root.remove(KEY_TARGET_TYPE);
            } else {
                root.putInt(KEY_TARGET_TYPE, target.ordinal());
            }
        });
    }

    public static boolean hasSelection(ItemStack stack) {
        if (!isNbtFilter(stack))
            return false;
        CompoundTag root = getRoot(stack);
        return root.contains(KEY_PATH, Tag.TAG_STRING) && root.contains(KEY_VALUE);
    }

    public static @Nullable String getSelectedPath(ItemStack stack) {
        if (!hasSelection(stack))
            return null;
        String path = getRoot(stack).getString(KEY_PATH).trim();
        return path.isEmpty() ? null : path;
    }

    public static String getSelectedValueDisplay(ItemStack stack) {
        if (!hasSelection(stack))
            return "";
        Tag val = getRoot(stack).get(KEY_VALUE);
        return val == null ? "" : val.toString();
    }

    public static boolean setSelection(ItemStack stack, String rawPath, Tag value) {
        if (!isNbtFilter(stack) || value == null)
            return false;

        String path = normalizePath(rawPath);
        if (path == null)
            return false;

        boolean[] result = { false };

        updateRoot(stack, root -> {
            String currentPath = root.getString(KEY_PATH);
            Tag currentValue = root.get(KEY_VALUE);

            if (path.equals(currentPath) && value.equals(currentValue)) {
                return;
            }

            root.putString(KEY_PATH, path);
            root.put(KEY_VALUE, value.copy());
            result[0] = true;
        });

        return result[0];
    }

    public static boolean clearSelection(ItemStack stack) {
        if (!isNbtFilter(stack))
            return false;

        boolean[] result = { false };

        updateRoot(stack, root -> {
            if (root.contains(KEY_PATH) || root.contains(KEY_VALUE)) {
                root.remove(KEY_PATH);
                root.remove(KEY_VALUE);
                result[0] = true;
            }
        });

        return result[0];
    }

    public static boolean matchesSelection(ItemStack filter, ItemStack candidate, HolderLookup.Provider provider) {
        if (candidate.isEmpty() || provider == null)
            return false;
        if (getTargetType(filter) != FilterTargetType.ITEMS)
            return false;

        String path = getSelectedPath(filter);
        if (path == null)
            return false;

        CompoundTag components = getSerializedComponents(candidate, provider);
        return checkMatch(filter, path, components);
    }

    public static boolean matchesSelection(ItemStack filter, FluidStack candidate, HolderLookup.Provider provider) {
        if (candidate == null || candidate.isEmpty() || provider == null)
            return false;
        if (getTargetType(filter) != FilterTargetType.FLUIDS)
            return false;

        String path = getSelectedPath(filter);
        if (path == null || !isFluidPath(path))
            return false;

        CompoundTag components = getSerializedComponents(candidate, provider);
        return checkMatch(filter, path, components);
    }

    private static boolean checkMatch(ItemStack filter, String path, @Nullable CompoundTag components) {
        if (components == null)
            return false;

        Tag expected = getRoot(filter).get(KEY_VALUE);
        if (expected == null)
            return false;

        Tag actual = resolvePathValue(components, path);
        return actual != null && expected.equals(actual);
    }

    public static boolean matchesSelection(ItemStack filter, String path, @Nullable CompoundTag components) {
        if (!isNbtFilter(filter))
            return false;
        String normalized = normalizePath(path);
        if (normalized == null)
            return false;

        return checkMatch(filter, normalized, components);
    }

    public static @Nullable Tag resolvePathValue(ItemStack stack, String path, HolderLookup.Provider provider) {
        String normalized = normalizePath(path);
        if (normalized == null)
            return null;

        if (isFluidPath(normalized)) {
            return FluidUtil.getFluidContained(stack)
                    .map(fluid -> {
                        CompoundTag tags = getSerializedComponents(fluid, provider);
                        return resolvePathValue(tags, normalized);
                    })
                    .orElse(null);
        }

        return resolvePathValue(getSerializedComponents(stack, provider), normalized);
    }

    public static @Nullable Tag resolvePathValue(@Nullable CompoundTag components, String path) {
        if (components == null)
            return null;

        String p = normalizePath(path);
        if (p == null)
            return null;

        if (p.equals("components") || p.equals("fluid.components")) {
            return components.copy();
        }

        p = stripPrefix(p, "components.");
        p = stripPrefix(p, "fluid.components.");

        Tag found = traverseTag(components, p);
        return found == null ? null : found.copy();
    }

    private static String stripPrefix(String s, String prefix) {
        return s.startsWith(prefix) ? s.substring(prefix.length()) : s;
    }

    public static List<NbtEntry> extractEntries(ItemStack stack, HolderLookup.Provider provider) {
        return extractEntriesInternal(getSerializedComponents(stack, provider), "");
    }

    public static List<NbtEntry> extractEntries(FluidStack stack, HolderLookup.Provider provider) {
        return extractEntriesInternal(getSerializedComponents(stack, provider), "fluid.components");
    }

    private static List<NbtEntry> extractEntriesInternal(@Nullable CompoundTag root, String rootPath) {
        if (root == null)
            return List.of();

        List<NbtEntry> entries = new ArrayList<>();
        collectLeaves(root, rootPath, entries);
        entries.sort(Comparator.comparing(NbtEntry::path));
        return entries;
    }

    public static boolean isNbtFilterItem(ItemStack stack) {
        return isNbtFilter(stack);
    }

    public static @Nullable CompoundTag getSerializedComponents(ItemStack stack, HolderLookup.Provider provider) {
        if (stack.isEmpty() || provider == null)
            return null;

        Tag tag = stack.save(provider);
        if (tag instanceof CompoundTag c && c.contains("components", Tag.TAG_COMPOUND)) {
            return c.getCompound("components");
        }
        return null;
    }

    private static @Nullable CompoundTag getSerializedComponents(FluidStack stack, HolderLookup.Provider provider) {
        if (stack == null || stack.isEmpty() || provider == null)
            return null;

        Tag tag = stack.saveOptional(provider);
        if (tag instanceof CompoundTag c && c.contains("components", Tag.TAG_COMPOUND)) {
            return c.getCompound("components");
        }
        return null;
    }

    public static boolean isFluidPath(@Nullable String path) {
        String p = normalizePath(path);
        return p != null && (p.equals("fluid.components") || p.startsWith("fluid.components."));
    }

    private static void collectLeaves(Tag tag, String currentPath, List<NbtEntry> out) {
        if (tag instanceof CompoundTag c) {
            c.getAllKeys().stream().sorted().forEach(key -> {
                Tag child = c.get(key);
                if (child != null) {
                    String nextPath = currentPath.isEmpty() ? key : currentPath + "." + key;
                    collectLeaves(child, nextPath, out);
                }
            });
            return;
        }

        if (tag instanceof ListTag l) {
            for (int i = 0; i < l.size(); i++) {
                collectLeaves(l.get(i), currentPath + "[" + i + "]", out);
            }
            return;
        }

        if (!currentPath.isEmpty()) {
            out.add(new NbtEntry(currentPath, tag.toString()));
        }
    }

    private static @Nullable Tag traverseTag(Tag root, String path) {
        if (root == null || path.isEmpty())
            return null;

        Tag current = root;
        int len = path.length();
        int i = 0;

        while (i < len) {
            int start = i;
            while (i < len && path.charAt(i) != '.' && path.charAt(i) != '[') {
                i++;
            }
            String key = path.substring(start, i);

            if (!key.isEmpty()) {
                if (!(current instanceof CompoundTag c) || !c.contains(key)) {
                    return null;
                }
                current = c.get(key);
            }

            while (i < len && path.charAt(i) == '[') {
                i++;
                int numStart = i;
                while (i < len && Character.isDigit(path.charAt(i))) {
                    i++;
                }

                if (i >= len || path.charAt(i) != ']')
                    return null;

                String numStr = path.substring(numStart, i);
                i++;

                if (!(current instanceof ListTag list) || numStr.isEmpty())
                    return null;

                try {
                    int idx = Integer.parseInt(numStr);
                    if (idx < 0 || idx >= list.size())
                        return null;
                    current = list.get(idx);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            if (i < len && path.charAt(i) == '.') {
                i++;
            }
        }

        return current;
    }

    private static @Nullable String normalizePath(String path) {
        if (path == null)
            return null;
        String trimmed = path.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static CompoundTag getRoot(ItemStack stack) {

        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return custom.contains(KEY_ROOT, Tag.TAG_COMPOUND) ? custom.getCompound(KEY_ROOT) : new CompoundTag();
    }

    private static void updateRoot(ItemStack stack, java.util.function.Consumer<CompoundTag> modifier) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, customTag -> {
            CompoundTag root = customTag.getCompound(KEY_ROOT);
            CompoundTag workingRoot = customTag.contains(KEY_ROOT, Tag.TAG_COMPOUND)
                    ? customTag.getCompound(KEY_ROOT)
                    : new CompoundTag();

            modifier.accept(workingRoot);

            if (workingRoot.isEmpty()) {
                customTag.remove(KEY_ROOT);
            } else {
                customTag.put(KEY_ROOT, workingRoot);
            }
        });
    }
}
