package me.almana.logisticsnetworks.filter;

import me.almana.logisticsnetworks.component.DurabilityFilterConfig;
import me.almana.logisticsnetworks.component.FilterSettingsData;
import me.almana.logisticsnetworks.component.LegacyComponentMigration;
import me.almana.logisticsnetworks.component.LogisticsDataComponents;
import me.almana.logisticsnetworks.item.DurabilityFilterItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class DurabilityFilterData {

    private static final int DEFAULT_VALUE = 0;
    private static final int MIN_VALUE = 0;
    private static final int MAX_VALUE = 3000;
    private static final Operator DEFAULT_OPERATOR = Operator.GREATER_OR_EQUAL;

    public enum Operator {
        LESS_OR_EQUAL("le", "<="),
        EQUAL("eq", "="),
        GREATER_OR_EQUAL("ge", ">=");

        private final String id;
        private final String symbol;

        Operator(String id, String symbol) {
            this.id = id;
            this.symbol = symbol;
        }

        public String id() {
            return id;
        }

        public String symbol() {
            return symbol;
        }

        public Operator next() {
            return switch (this) {
                case LESS_OR_EQUAL -> EQUAL;
                case EQUAL -> GREATER_OR_EQUAL;
                case GREATER_OR_EQUAL -> LESS_OR_EQUAL;
            };
        }

        public static Operator fromId(@Nullable String id) {
            if (id == null)
                return DEFAULT_OPERATOR;
            for (Operator op : values()) {
                if (op.id.equals(id))
                    return op;
            }
            return DEFAULT_OPERATOR;
        }
    }

    private DurabilityFilterData() {
    }

    public static boolean isDurabilityFilterItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof DurabilityFilterItem;
    }

    public static boolean isBlacklist(ItemStack stack) {
        if (!isDurabilityFilterItem(stack))
            return false;
        LegacyComponentMigration.migrateDurabilityFilter(stack);
        return FilterSettingsData.get(stack).blacklist();
    }

    public static void setBlacklist(ItemStack stack, boolean isBlacklist) {
        if (!isDurabilityFilterItem(stack))
            return;
        LegacyComponentMigration.migrateDurabilityFilter(stack);
        FilterSettingsData.setBlacklist(stack, isBlacklist);
    }

    public static FilterTargetType getTargetType(ItemStack stack) {
        if (!isDurabilityFilterItem(stack))
            return FilterTargetType.ITEMS;
        LegacyComponentMigration.migrateDurabilityFilter(stack);
        return FilterSettingsData.get(stack).target();
    }

    public static void setTargetType(ItemStack stack, FilterTargetType type) {
        if (!isDurabilityFilterItem(stack))
            return;
        LegacyComponentMigration.migrateDurabilityFilter(stack);
        FilterSettingsData.setTarget(stack, type);
    }

    public static int getValue(ItemStack stack) {
        if (!isDurabilityFilterItem(stack))
            return DEFAULT_VALUE;

        LegacyComponentMigration.migrateDurabilityFilter(stack);
        return getConfig(stack).value();
    }

    public static void setValue(ItemStack stack, int value) {
        if (!isDurabilityFilterItem(stack))
            return;

        int clamped = clamp(value);
        LegacyComponentMigration.migrateDurabilityFilter(stack);
        setConfig(stack, new DurabilityFilterConfig(clamped, getConfig(stack).operator()));
    }

    public static Operator getOperator(ItemStack stack) {
        if (!isDurabilityFilterItem(stack))
            return DEFAULT_OPERATOR;

        LegacyComponentMigration.migrateDurabilityFilter(stack);
        return getConfig(stack).operator();
    }

    public static void setOperator(ItemStack stack, @Nullable Operator operator) {
        if (!isDurabilityFilterItem(stack))
            return;

        Operator normalized = operator == null ? DEFAULT_OPERATOR : operator;
        LegacyComponentMigration.migrateDurabilityFilter(stack);
        setConfig(stack, new DurabilityFilterConfig(getConfig(stack).value(), normalized));
    }

    public static int minValue() {
        return MIN_VALUE;
    }

    public static int maxValue() {
        return MAX_VALUE;
    }

    public static boolean matches(ItemStack filterStack, ItemStack candidate) {
        if (!isDurabilityFilterItem(filterStack) || candidate.isEmpty() || !candidate.isDamageableItem()) {
            return false;
        }

        int threshold = getValue(filterStack);
        int remaining = candidate.getMaxDamage() - candidate.getDamageValue();
        Operator operator = getOperator(filterStack);

        return switch (operator) {
            case LESS_OR_EQUAL -> remaining <= threshold;
            case EQUAL -> remaining == threshold;
            case GREATER_OR_EQUAL -> remaining >= threshold;
        };
    }

    private static int clamp(int value) {
        return Math.max(MIN_VALUE, Math.min(MAX_VALUE, value));
    }

    private static DurabilityFilterConfig getConfig(ItemStack stack) {
        return stack.getOrDefault(LogisticsDataComponents.DURABILITY_FILTER,
                new DurabilityFilterConfig(DEFAULT_VALUE, DEFAULT_OPERATOR));
    }

    private static void setConfig(ItemStack stack, DurabilityFilterConfig config) {
        if (config.value() == DEFAULT_VALUE && config.operator() == DEFAULT_OPERATOR) {
            stack.remove(LogisticsDataComponents.DURABILITY_FILTER);
        } else {
            stack.set(LogisticsDataComponents.DURABILITY_FILTER, config);
        }
    }
}
