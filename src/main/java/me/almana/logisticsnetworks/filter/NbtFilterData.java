package me.almana.logisticsnetworks.filter;

import com.mojang.serialization.Codec;
import me.almana.logisticsnetworks.component.FilterSettingsData;
import me.almana.logisticsnetworks.component.LegacyComponentMigration;
import me.almana.logisticsnetworks.component.LogisticsDataComponents;
import me.almana.logisticsnetworks.component.NbtFilterConfig;
import me.almana.logisticsnetworks.item.NbtFilterItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class NbtFilterData {

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

        public static final Codec<Operator> CODEC = Codec.STRING.xmap(Operator::fromSymbol, Operator::symbol);

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

        private static Operator fromSymbol(String symbol) {
            return NOT_EQUALS.symbol.equals(symbol) ? NOT_EQUALS : EQUALS;
        }
    }

    public record NbtRule(@NotNull NbtPath path, @NotNull Operator operator, @NotNull Tag value, boolean enabled) {
        public NbtRule {
            Objects.requireNonNull(path);
            Objects.requireNonNull(operator);
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

    private NbtFilterData() {
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

    public static @Nullable Tag resolvePathValue(ItemStack stack, @Nullable NbtPath path, HolderLookup.Provider provider) {
        if (path == null) {
            return null;
        }

        if (isFluidPath(path)) {
            return FluidUtil.getFluidContained(stack)
                    .map(fluid -> {
                        CompoundTag tags = getSerializedComponents(fluid, provider);
                        return resolvePathValue(tags, path);
                    })
                    .orElse(null);
        }

        return resolvePathValue(getSerializedComponents(stack, provider), path);
    }

    public static @Nullable Tag resolvePathValue(@Nullable CompoundTag components, @Nullable NbtPath path) {
        if (components == null || path == null || path.isEmpty())
            return null;

        if (path.equals(COMPONENTS_PATH) || path.equals(FLUID_COMPONENTS_PATH))
            return components.copy();

        if (path.startsWith(FLUID_COMPONENTS_PATH))
            path = path.drop(2);
        else if (path.startsWith(COMPONENTS_PATH))
            path = path.drop(1);

        Tag found = path.getFrom(components);
        return found == null ? null : found.copy();
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

        Tag tag = stack.copyWithCount(1).save(provider);
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
        }
        int durability = stack.isDamageableItem() ? Math.max(0, stack.getMaxDamage() - stack.getDamageValue()) : 0;
        components.putInt("minecraft:durability", durability);
        components.put("minecraft:enchanted", ByteTag.valueOf(stack.isEnchanted()));

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

    public static boolean isFluidPath(@Nullable NbtPath path) {
        return path != null && path.startsWith(FLUID_COMPONENTS_PATH);
    }

    private static void collectLeaves(Tag tag, NbtPath currentPath, List<NbtEntry> out) {
        if (tag instanceof CompoundTag c) {
            if (c.isEmpty() && !currentPath.isEmpty()) {
                out.add(new NbtEntry(currentPath, "true"));
                return;
            }
            c.getAllKeys().stream().sorted().forEach(key -> {
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
}
