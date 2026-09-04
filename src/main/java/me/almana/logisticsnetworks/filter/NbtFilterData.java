package me.almana.logisticsnetworks.filter;

import com.mojang.serialization.Codec;
import me.almana.logisticsnetworks.component.FilterSettings;
import me.almana.logisticsnetworks.component.FilterSettingsData;
import me.almana.logisticsnetworks.component.LegacyComponentMigration;
import me.almana.logisticsnetworks.component.LogisticsDataComponents;
import me.almana.logisticsnetworks.component.NbtFilterConfig;
import me.almana.logisticsnetworks.item.NbtFilterItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class NbtFilterData {

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

        public static final Codec<Operator> CODEC = Codec.STRING.xmap(Operator::fromSymbol, Operator::symbol);

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

        private static Operator fromSymbol(String symbol) {
            return NOT_EQUALS.symbol.equals(symbol) ? NOT_EQUALS : EQUALS;
        }
    }

    public record NbtRule(String path, Operator operator, Tag value, boolean enabled) {
        public NbtRule {
            path = Objects.requireNonNull(path);
            operator = Objects.requireNonNull(operator);
            value = Objects.requireNonNull(value).copy();
        }

        @Override
        public Tag value() {
            return value.copy();
        }

        public String valueDisplay() {
            return value.toString();
        }
    }

    public record View(List<NbtRule> rules, FilterTargetType target, boolean blacklist, boolean anyEnabled) {
    }

    private NbtFilterData() {
    }

    private static View buildView(ItemStack stack) {
        List<NbtRule> rules = getRules(stack);

        boolean anyEnabled = false;
        for (NbtRule rule : rules) {
            if (rule.enabled()) {
                anyEnabled = true;
                break;
            }
        }

        FilterSettings settings = FilterSettingsData.get(stack);
        return new View(rules, settings.target(), settings.blacklist(), anyEnabled);
    }

    public static boolean isNbtFilter(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof NbtFilterItem;
    }

    public static boolean isBlacklist(ItemStack stack) {
        if (!isNbtFilter(stack))
            return false;
        LegacyComponentMigration.migrateNbtFilter(stack);
        return FilterSettingsData.get(stack).blacklist();
    }

    public static void setBlacklist(ItemStack stack, boolean isBlacklist) {
        if (!isNbtFilter(stack))
            return;

        LegacyComponentMigration.migrateNbtFilter(stack);
        FilterSettingsData.setBlacklist(stack, isBlacklist);
    }

    public static FilterTargetType getTargetType(ItemStack stack) {
        if (!isNbtFilter(stack))
            return FilterTargetType.ITEMS;

        LegacyComponentMigration.migrateNbtFilter(stack);
        return FilterSettingsData.get(stack).target();
    }

    public static void setTargetType(ItemStack stack, FilterTargetType type) {
        if (!isNbtFilter(stack))
            return;

        LegacyComponentMigration.migrateNbtFilter(stack);
        FilterSettingsData.setTarget(stack, type);
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

        LegacyComponentMigration.migrateNbtFilter(stack);
        NbtFilterConfig config = stack.get(LogisticsDataComponents.NBT_FILTER);
        if (config == null) {
            return List.of();
        }
        return config.rules().stream()
                .map(rule -> new NbtRule(rule.path(), rule.operator(), rule.value(), rule.enabled()))
                .toList();
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
        List<NbtRule> rules = new ArrayList<>(getRules(stack));
        NbtRule updated = new NbtRule(path, resolvedOperator, value, true);
        int index = findRuleIndex(rules, path, resolvedOperator);
        if (index >= 0) {
            if (sameRule(rules.get(index), updated)) {
                return false;
            }
            rules.set(index, updated);
        } else {
            rules.add(updated);
        }
        FilterSettingsData.setTarget(stack, isFluidPath(path) ? FilterTargetType.FLUIDS : FilterTargetType.ITEMS);
        setRules(stack, rules);
        return true;
    }

    public static boolean removeRule(ItemStack stack, int index) {
        if (!isNbtFilter(stack))
            return false;

        List<NbtRule> rules = new ArrayList<>(getRules(stack));
        if (index < 0 || index >= rules.size()) {
            return false;
        }
        rules.remove(index);
        setRules(stack, rules);
        return true;
    }

    public static boolean toggleRuleEnabled(ItemStack stack, int index) {
        if (!isNbtFilter(stack))
            return false;

        List<NbtRule> rules = new ArrayList<>(getRules(stack));
        if (index < 0 || index >= rules.size()) {
            return false;
        }
        NbtRule current = rules.get(index);
        rules.set(index, new NbtRule(current.path(), current.operator(), current.value(), !current.enabled()));
        setRules(stack, rules);
        return true;
    }

    public static boolean cycleRuleOperator(ItemStack stack, int index) {
        if (!isNbtFilter(stack))
            return false;

        List<NbtRule> rules = new ArrayList<>(getRules(stack));
        if (index < 0 || index >= rules.size()) {
            return false;
        }
        NbtRule current = rules.get(index);
        rules.set(index, new NbtRule(current.path(), current.operator().next(), current.value(), current.enabled()));
        setRules(stack, rules);
        return true;
    }

    public static boolean setSelection(ItemStack stack, String rawPath, Tag value) {
        return addRule(stack, rawPath, Operator.EQUALS, value);
    }

    public static boolean clearSelection(ItemStack stack) {
        if (!isNbtFilter(stack))
            return false;

        LegacyComponentMigration.migrateNbtFilter(stack);
        return stack.remove(LogisticsDataComponents.NBT_FILTER) != null;
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

        Tag tag = stack.save(provider);
        CompoundTag components = new CompoundTag();
        if (tag instanceof CompoundTag c && c.contains("components", Tag.TAG_COMPOUND)) {
            components = c.getCompound("components").copy();
        }

        if (!components.contains("minecraft:max_stack_size"))
            components.putInt("minecraft:max_stack_size", stack.getMaxStackSize());
        if (!components.contains("minecraft:rarity"))
            components.putString("minecraft:rarity", stack.getRarity().getSerializedName());
        if (stack.isDamageableItem()) {
            if (!components.contains("minecraft:damage"))
                components.putInt("minecraft:damage", stack.getDamageValue());
            if (!components.contains("minecraft:max_damage"))
                components.putInt("minecraft:max_damage", stack.getMaxDamage());
            components.putInt("minecraft:durability", stack.getMaxDamage() - stack.getDamageValue());
        }

        if (stack.isEnchantable() || stack.isEnchanted()) {
            ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            components.put("minecraft:enchanted", ByteTag.valueOf(!enchants.isEmpty()));
        }

        return components.isEmpty() ? null : components;
    }

    public static @Nullable CompoundTag getSerializedComponents(FluidStack stack, HolderLookup.Provider provider) {
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

    private static void setRules(ItemStack stack, List<NbtRule> rules) {
        if (rules.isEmpty()) {
            stack.remove(LogisticsDataComponents.NBT_FILTER);
            return;
        }
        List<NbtFilterConfig.Rule> stored = rules.stream()
                .map(rule -> new NbtFilterConfig.Rule(rule.path(), rule.operator(), rule.value(), rule.enabled()))
                .toList();
        stack.set(LogisticsDataComponents.NBT_FILTER, new NbtFilterConfig(stored));
    }
}
