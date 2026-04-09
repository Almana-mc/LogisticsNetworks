package me.almana.logisticsnetworks.filter;

import me.almana.logisticsnetworks.util.ItemDataUtil;

import me.almana.logisticsnetworks.item.NbtFilterItem;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public final class NbtFilterData {

    private static final String KEY_ROOT = "ln_nbt_filter";
    private static final String KEY_IS_BLACKLIST = "blacklist";
    private static final String KEY_PATH = "path";
    private static final String KEY_VALUE = "value";
    private static final String KEY_TARGET_TYPE = "target";
    private static final String KEY_RULES = "rules";
    private static final String KEY_RULE_OPERATOR = "operator";
    private static final String KEY_RULE_ENABLED = "enabled";

    public record NbtEntry(String path, String valueDisplay) {
    }

    private static final List<NbtEntry> DEFAULT_ENTRIES = List.of(
            new NbtEntry("minecraft:enchanted", "false"),
            new NbtEntry("minecraft:damage", "0"),
            new NbtEntry("minecraft:durability", "0"),
            new NbtEntry("minecraft:max_damage", "0"),
            new NbtEntry("minecraft:max_stack_size", "64"),
            new NbtEntry("minecraft:rarity", "\"common\"")
    );

    public static List<NbtEntry> getDefaultEntries() {
        return DEFAULT_ENTRIES;
    }

    public static @Nullable Tag getDefaultValue(String path) {
        return switch (path) {
            case "minecraft:enchanted" -> ByteTag.valueOf(false);
            case "minecraft:damage" -> IntTag.valueOf(0);
            case "minecraft:durability" -> IntTag.valueOf(0);
            case "minecraft:max_damage" -> IntTag.valueOf(0);
            case "minecraft:max_stack_size" -> IntTag.valueOf(64);
            case "minecraft:rarity" -> StringTag.valueOf("common");
            default -> null;
        };
    }

    public static @Nullable Tag parseValueString(String value) {
        if (value == null || value.isEmpty()) return null;
        if ("true".equals(value)) return ByteTag.valueOf(true);
        if ("false".equals(value)) return ByteTag.valueOf(false);
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2)
            return StringTag.valueOf(value.substring(1, value.length() - 1));
        try { return IntTag.valueOf(Integer.parseInt(value)); } catch (NumberFormatException ignored) {}
        return StringTag.valueOf(value);
    }

    public enum Operator {
        EQUALS("="),
        NOT_EQUALS("!=");

        private final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }

        public Operator next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public static Operator fromOrdinal(int ordinal) {
            Operator[] values = values();
            if (ordinal < 0 || ordinal >= values.length)
                return EQUALS;
            return values[ordinal];
        }
    }

    public record NbtRule(String path, Operator operator, Tag value, boolean enabled) {
        public String valueDisplay() {
            return value == null ? "" : value.toString();
        }
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
        if (path.isEmpty()) {
            List<NbtRule> rules = readRules(root);
            if (rules.isEmpty()) {
                NbtRule legacy = readLegacyRule(root);
                path = legacy == null ? "" : legacy.path();
            } else {
                path = rules.get(0).path();
            }
        }
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
        return hasAnyRules(stack);
    }

    public static @Nullable String getSelectedPath(ItemStack stack) {
        List<NbtRule> rules = getRules(stack);
        return rules.isEmpty() ? null : rules.get(0).path();
    }

    public static String getSelectedValueDisplay(ItemStack stack) {
        List<NbtRule> rules = getRules(stack);
        return rules.isEmpty() ? "" : rules.get(0).valueDisplay();
    }

    public static List<NbtRule> getRules(ItemStack stack) {
        if (!isNbtFilter(stack))
            return List.of();

        CompoundTag root = getRoot(stack);
        List<NbtRule> rules = readRules(root);
        if (!rules.isEmpty())
            return rules;

        NbtRule legacy = readLegacyRule(root);
        return legacy == null ? List.of() : List.of(legacy);
    }

    public static boolean hasAnyRules(ItemStack stack) {
        return !getRules(stack).isEmpty();
    }

    public static boolean hasEnabledRules(ItemStack stack) {
        for (NbtRule rule : getRules(stack)) {
            if (rule.enabled())
                return true;
        }
        return false;
    }

    public static boolean addRule(ItemStack stack, String rawPath, Operator operator, Tag value) {
        if (!isNbtFilter(stack) || value == null)
            return false;

        String path = normalizePath(rawPath);
        if (path == null)
            return false;

        Operator resolvedOperator = operator == null ? Operator.EQUALS : operator;
        boolean[] result = { false };

        updateRoot(stack, root -> {
            List<NbtRule> rules = new ArrayList<>(readRules(root));
            if (rules.isEmpty()) {
                NbtRule legacy = readLegacyRule(root);
                if (legacy != null)
                    rules.add(legacy);
            }

            NbtRule updated = new NbtRule(path, resolvedOperator, value.copy(), true);
            int index = findRuleIndex(rules, path, resolvedOperator);
            if (index >= 0) {
                NbtRule current = rules.get(index);
                if (sameRule(current, updated))
                    return;
                rules.set(index, updated);
            } else {
                rules.add(updated);
            }

            if (isFluidPath(path)) {
                root.putInt(KEY_TARGET_TYPE, FilterTargetType.FLUIDS.ordinal());
            } else {
                root.remove(KEY_TARGET_TYPE);
            }
            writeRules(root, rules);
            result[0] = true;
        });

        return result[0];
    }

    public static boolean removeRule(ItemStack stack, int index) {
        if (!isNbtFilter(stack))
            return false;

        boolean[] result = { false };

        updateRoot(stack, root -> {
            List<NbtRule> rules = new ArrayList<>(getRulesFromRoot(root));
            if (index < 0 || index >= rules.size())
                return;
            rules.remove(index);
            writeRules(root, rules);
            result[0] = true;
        });

        return result[0];
    }

    public static boolean toggleRuleEnabled(ItemStack stack, int index) {
        if (!isNbtFilter(stack))
            return false;

        boolean[] result = { false };

        updateRoot(stack, root -> {
            List<NbtRule> rules = new ArrayList<>(getRulesFromRoot(root));
            if (index < 0 || index >= rules.size())
                return;

            NbtRule current = rules.get(index);
            rules.set(index, new NbtRule(current.path(), current.operator(), current.value().copy(), !current.enabled()));
            writeRules(root, rules);
            result[0] = true;
        });

        return result[0];
    }

    public static boolean cycleRuleOperator(ItemStack stack, int index) {
        if (!isNbtFilter(stack))
            return false;

        boolean[] result = { false };

        updateRoot(stack, root -> {
            List<NbtRule> rules = new ArrayList<>(getRulesFromRoot(root));
            if (index < 0 || index >= rules.size())
                return;

            NbtRule current = rules.get(index);
            rules.set(index, new NbtRule(current.path(), current.operator().next(), current.value().copy(),
                    current.enabled()));
            writeRules(root, rules);
            result[0] = true;
        });

        return result[0];
    }

    public static boolean setSelection(ItemStack stack, String rawPath, Tag value) {
        return addRule(stack, rawPath, Operator.EQUALS, value);
    }

    public static boolean clearSelection(ItemStack stack) {
        if (!isNbtFilter(stack))
            return false;

        boolean[] result = { false };

        updateRoot(stack, root -> {
            if (root.contains(KEY_PATH) || root.contains(KEY_VALUE) || root.contains(KEY_RULES, Tag.TAG_LIST)) {
                root.remove(KEY_PATH);
                root.remove(KEY_VALUE);
                root.remove(KEY_RULES);
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

        CompoundTag components = getSerializedComponents(candidate, provider);
        return matches(filter, components);
    }

    public static boolean matchesSelection(ItemStack filter, FluidStack candidate, HolderLookup.Provider provider) {
        if (candidate == null || candidate.isEmpty() || provider == null)
            return false;
        if (getTargetType(filter) != FilterTargetType.FLUIDS)
            return false;

        CompoundTag components = getSerializedComponents(candidate, provider);
        return matches(filter, components);
    }

    public static boolean matches(ItemStack filter, @Nullable CompoundTag components) {
        if (!isNbtFilter(filter))
            return false;

        return matches(getRules(filter), components);
    }

    public static boolean matches(List<NbtRule> rules, @Nullable CompoundTag components) {
        if (components == null)
            return false;

        boolean hasEnabledRule = false;
        for (NbtRule rule : rules) {
            if (!rule.enabled())
                continue;

            hasEnabledRule = true;
            Tag actual = resolvePathValue(components, rule.path());
            if (!matchesRule(rule, actual))
                return false;
        }
        return hasEnabledRule;
    }

    public static boolean matchesSelection(ItemStack filter, String path, @Nullable CompoundTag components) {
        if (!isNbtFilter(filter))
            return false;
        String normalized = normalizePath(path);
        if (normalized == null)
            return false;

        Tag actual = resolvePathValue(components, normalized);
        Tag expected = resolveExpectedValue(filter, normalized);
        return expected != null && actual != null && expected.equals(actual);
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

        CompoundTag saved = stack.save(new CompoundTag());
        CompoundTag components = new CompoundTag();
        if (saved.contains("id", Tag.TAG_STRING)) {
            components.putString("id", saved.getString("id"));
        }
        components.putByte("count", saved.getByte("Count"));
        if (saved.contains("tag", Tag.TAG_COMPOUND)) {
            components.put("tag", saved.getCompound("tag").copy());
        }

        if (!components.contains("minecraft:max_stack_size"))
            components.putInt("minecraft:max_stack_size", stack.getMaxStackSize());
        if (!components.contains("minecraft:rarity"))
            components.putString("minecraft:rarity", stack.getRarity().name().toLowerCase(java.util.Locale.ROOT));
        if (stack.isDamageableItem()) {
            if (!components.contains("minecraft:damage"))
                components.putInt("minecraft:damage", stack.getDamageValue());
            if (!components.contains("minecraft:max_damage"))
                components.putInt("minecraft:max_damage", stack.getMaxDamage());
            components.putInt("minecraft:durability", stack.getMaxDamage() - stack.getDamageValue());
        }

        if (stack.isEnchantable() || stack.isEnchanted()) {
            components.put("minecraft:enchanted", ByteTag.valueOf(stack.isEnchanted()));
        }

        return components.isEmpty() ? null : components;
    }

    public static @Nullable CompoundTag getSerializedComponents(FluidStack stack, HolderLookup.Provider provider) {
        if (stack == null || stack.isEmpty() || provider == null)
            return null;

        CompoundTag saved = stack.writeToNBT(new CompoundTag());
        CompoundTag components = new CompoundTag();
        if (saved.contains("FluidName", Tag.TAG_STRING)) {
            components.putString("fluid", saved.getString("FluidName"));
        }
        components.putInt("amount", saved.getInt("Amount"));
        if (saved.contains("Tag", Tag.TAG_COMPOUND)) {
            components.put("tag", saved.getCompound("Tag").copy());
        }
        return components;
    }

    public static boolean isFluidPath(@Nullable String path) {
        String p = normalizePath(path);
        return p != null && (p.equals("fluid.components") || p.startsWith("fluid.components."));
    }

    private static void collectLeaves(Tag tag, String currentPath, List<NbtEntry> out) {
        if (tag instanceof CompoundTag c) {
            if (c.isEmpty() && !currentPath.isEmpty()) {
                out.add(new NbtEntry(currentPath, "true"));
                return;
            }
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
            if (l.isEmpty() && !currentPath.isEmpty()) {
                out.add(new NbtEntry(currentPath, "[]"));
                return;
            }
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

    private static boolean matchesRule(NbtRule rule, @Nullable Tag actual) {
        return switch (rule.operator()) {
            case EQUALS -> actual != null && rule.value().equals(actual);
            case NOT_EQUALS -> actual == null || !rule.value().equals(actual);
        };
    }

    private static @Nullable Tag resolveExpectedValue(ItemStack filter, String path) {
        for (NbtRule rule : getRules(filter)) {
            if (rule.path().equals(path))
                return rule.value();
        }
        return null;
    }

    private static List<NbtRule> getRulesFromRoot(CompoundTag root) {
        List<NbtRule> rules = readRules(root);
        if (!rules.isEmpty())
            return rules;

        NbtRule legacy = readLegacyRule(root);
        return legacy == null ? List.of() : List.of(legacy);
    }

    private static List<NbtRule> readRules(CompoundTag root) {
        if (!root.contains(KEY_RULES, Tag.TAG_LIST))
            return List.of();

        ListTag ruleList = root.getList(KEY_RULES, Tag.TAG_COMPOUND);
        List<NbtRule> rules = new ArrayList<>(ruleList.size());
        for (Tag tag : ruleList) {
            if (!(tag instanceof CompoundTag ruleTag))
                continue;

            String path = normalizePath(ruleTag.getString(KEY_PATH));
            Tag value = ruleTag.get(KEY_VALUE);
            if (path == null || value == null)
                continue;

            Operator operator = ruleTag.contains(KEY_RULE_OPERATOR, Tag.TAG_INT)
                    ? Operator.fromOrdinal(ruleTag.getInt(KEY_RULE_OPERATOR))
                    : Operator.EQUALS;
            boolean enabled = !ruleTag.contains(KEY_RULE_ENABLED, Tag.TAG_BYTE) || ruleTag.getBoolean(KEY_RULE_ENABLED);
            rules.add(new NbtRule(path, operator, value.copy(), enabled));
        }
        return rules;
    }

    private static @Nullable NbtRule readLegacyRule(CompoundTag root) {
        if (!root.contains(KEY_PATH, Tag.TAG_STRING) || !root.contains(KEY_VALUE))
            return null;

        String path = normalizePath(root.getString(KEY_PATH));
        Tag value = root.get(KEY_VALUE);
        if (path == null || value == null)
            return null;

        return new NbtRule(path, Operator.EQUALS, value.copy(), true);
    }

    private static void writeRules(CompoundTag root, List<NbtRule> rules) {
        root.remove(KEY_PATH);
        root.remove(KEY_VALUE);

        if (rules.isEmpty()) {
            root.remove(KEY_RULES);
            return;
        }

        ListTag ruleList = new ListTag();
        for (NbtRule rule : rules) {
            CompoundTag ruleTag = new CompoundTag();
            ruleTag.putString(KEY_PATH, rule.path());
            ruleTag.putInt(KEY_RULE_OPERATOR, rule.operator().ordinal());
            ruleTag.put(KEY_VALUE, rule.value().copy());
            if (!rule.enabled())
                ruleTag.putBoolean(KEY_RULE_ENABLED, false);
            ruleList.add(ruleTag);
        }
        root.put(KEY_RULES, ruleList);
    }

    private static int findRuleIndex(List<NbtRule> rules, String path, Operator operator) {
        for (int i = 0; i < rules.size(); i++) {
            NbtRule rule = rules.get(i);
            if (rule.path().equals(path) && rule.operator() == operator)
                return i;
        }
        return -1;
    }

    private static boolean sameRule(NbtRule left, NbtRule right) {
        return left.path().equals(right.path())
                && left.operator() == right.operator()
                && left.enabled() == right.enabled()
                && left.value().equals(right.value());
    }

    private static CompoundTag getRoot(ItemStack stack) {
        CompoundTag custom = ItemDataUtil.getCustomData(stack);
        return custom.contains(KEY_ROOT, Tag.TAG_COMPOUND) ? custom.getCompound(KEY_ROOT) : new CompoundTag();
    }

    private static void updateRoot(ItemStack stack, Consumer<CompoundTag> modifier) {
        ItemDataUtil.updateCustomData(stack, customTag -> {
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
