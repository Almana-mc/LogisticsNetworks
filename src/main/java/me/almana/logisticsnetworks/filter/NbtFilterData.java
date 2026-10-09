package me.almana.logisticsnetworks.filter;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class NbtFilterData {

    private static final String KEY_ROOT = "ln_nbt_filter";
    private static final String KEY_IS_BLACKLIST = "blacklist";
    private static final String KEY_PATH = "path";
    private static final String KEY_VALUE = "value";
    private static final String KEY_RULES = "rules";
    private static final String KEY_RULE_OPERATOR = "operator";
    private static final String KEY_RULE_ENABLED = "enabled";

    public record NbtEntry(NbtPath path, String valueDisplay) {
    }

    private static final List<NbtEntry> DEFAULT_ENTRIES = List.of(
            new NbtEntry(NbtPath.of(NbtPath.Component.of("minecraft:enchanted")), "false"),
            new NbtEntry(NbtPath.of(NbtPath.Component.of("minecraft:damage")), "0"),
            new NbtEntry(NbtPath.of(NbtPath.Component.of("minecraft:durability")), "0"),
            new NbtEntry(NbtPath.of(NbtPath.Component.of("minecraft:max_damage")), "0"),
            new NbtEntry(NbtPath.of(NbtPath.Component.of("minecraft:max_stack_size")), "64"),
            new NbtEntry(NbtPath.of(NbtPath.Component.of("minecraft:rarity")), "\"common\"")
    );

    static final NbtPath COMPONENTS_PATH = NbtPath.of(NbtPath.Component.of("components"));
    static final NbtPath FLUID_COMPONENTS_PATH = NbtPath.of(NbtPath.Component.of("fluid"),
            NbtPath.Component.of("components"));

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

        public static final com.mojang.serialization.Codec<Operator> CODEC = com.mojang.serialization.Codec.STRING.xmap(
                symbol -> "!=".equals(symbol) ? NOT_EQUALS : EQUALS, Operator::symbol);

        private final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }

        public static Operator fromOrdinal(int ordinal) {
            Operator[] values = values();
            if (ordinal < 0 || ordinal >= values.length)
                return EQUALS;
            return values[ordinal];
        }
    }

    public record NbtRule(NbtPath path, Operator operator, Tag value, boolean enabled) {
        public String valueDisplay() {
            return value == null ? "" : value.toString();
        }
    }

    private NbtFilterData() {
    }

    public static boolean isNbtFilter(ItemStack stack) {
        return false;
    }

    public static boolean isBlacklist(ItemStack stack) {
        if (!isNbtFilter(stack))
            return false;
        return getRoot(stack).getBooleanOr(KEY_IS_BLACKLIST, false);
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

    public static @Nullable Tag resolvePathValue(ItemStack stack, @Nullable NbtPath path, HolderLookup.Provider provider) {
        if (path == null || stack.isEmpty()) {
            return null;
        }

        if (isFluidPath(path)) {
            CandidateComponents components = CandidateComponents.of(FluidUtil.getFirstStackContained(stack), provider);
            return components == null ? null : components.resolve(path);
        }

        return new CandidateComponents(stack, provider).resolve(path);
    }

    public static List<NbtEntry> extractEntries(ItemStack stack, HolderLookup.Provider provider) {
        return extractEntriesInternal(getSerializedComponents(stack, provider), NbtPath.EMPTY);
    }

    public static List<NbtEntry> extractEntries(FluidStack stack, HolderLookup.Provider provider) {
        return extractEntriesInternal(getSerializedComponents(stack, provider), FLUID_COMPONENTS_PATH);
    }

    private static List<NbtEntry> extractEntriesInternal(@Nullable CompoundTag root, NbtPath rootPath) {
        if (root == null)
            return List.of();

        List<NbtEntry> entries = new ArrayList<>();
        collectLeaves(root, rootPath, entries);
        entries.sort(Comparator.comparing(e -> e.path().toString()));
        return entries;
    }

    public static boolean isNbtFilterItem(ItemStack stack) {
        return isNbtFilter(stack);
    }

    public static @Nullable CompoundTag getSerializedComponents(ItemStack stack, HolderLookup.Provider provider) {
        if (stack.isEmpty() || provider == null)
            return null;
        return new CandidateComponents(stack, provider).full();
    }

    public static @Nullable CompoundTag getSerializedComponents(FluidStack stack, HolderLookup.Provider provider) {
        if (stack == null || provider == null)
            return null;
        CandidateComponents components = CandidateComponents.of(stack, provider);
        return components == null ? null : components.full();
    }

    public static boolean isFluidPath(@Nullable NbtPath path) {
        return path != null && path.startsWith(FLUID_COMPONENTS_PATH);
    }

    private static void collectLeaves(Tag tag, NbtPath currentPath, List<NbtEntry> out) {
        if (tag instanceof CompoundTag c) {
            if (c.isEmpty() && !currentPath.isEmpty()) {
                out.add(new NbtEntry(currentPath, "true"));
                return;
            }
            c.keySet().stream().sorted().forEach(key -> {
                Tag child = c.get(key);
                if (child != null) {
                    var nextPath = currentPath.then(new NbtPath.StringComponent(key));
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
                collectLeaves(l.get(i), currentPath.then(new NbtPath.IndexComponent(i)), out);
            }
            return;
        }

        if (!currentPath.isEmpty()) {
            out.add(new NbtEntry(currentPath, tag.toString()));
        }
    }

    private static @Nullable String normalizePath(String path) {
        if (path == null)
            return null;
        String trimmed = path.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static List<NbtRule> readRules(CompoundTag root) {
        if (!root.contains(KEY_RULES))
            return List.of();

        ListTag ruleList = root.getListOrEmpty(KEY_RULES);
        List<NbtRule> rules = new ArrayList<>(ruleList.size());
        for (Tag tag : ruleList) {
            if (!(tag instanceof CompoundTag ruleTag))
                continue;

            String path = normalizePath(ruleTag.getStringOr(KEY_PATH, ""));
            Tag value = ruleTag.get(KEY_VALUE);
            if (path == null || value == null)
                continue;

            Operator operator = ruleTag.contains(KEY_RULE_OPERATOR)
                    ? Operator.fromOrdinal(ruleTag.getIntOr(KEY_RULE_OPERATOR, Operator.EQUALS.ordinal()))
                    : Operator.EQUALS;
            boolean enabled = !ruleTag.contains(KEY_RULE_ENABLED) || ruleTag.getBooleanOr(KEY_RULE_ENABLED, true);
            rules.add(new NbtRule(NbtPath.parseLenient(path), operator, value.copy(), enabled));
        }
        return rules;
    }

    private static @Nullable NbtRule readLegacyRule(CompoundTag root) {
        if (!root.contains(KEY_PATH) || !root.contains(KEY_VALUE))
            return null;

        String path = normalizePath(root.getStringOr(KEY_PATH, ""));
        Tag value = root.get(KEY_VALUE);
        if (path == null || value == null)
            return null;

        return new NbtRule(NbtPath.parseLenient(path), Operator.EQUALS, value.copy(), true);
    }

    private static CompoundTag getRoot(ItemStack stack) {

        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return custom.contains(KEY_ROOT) ? custom.getCompound(KEY_ROOT).orElseGet(CompoundTag::new) : new CompoundTag();
    }
}
