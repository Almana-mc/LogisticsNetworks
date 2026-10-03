package me.almana.logisticsnetworks.filter;

import me.almana.logisticsnetworks.component.DurabilityFilterConfig;
import me.almana.logisticsnetworks.component.LegacyComponentMigration;
import me.almana.logisticsnetworks.component.LogisticsDataComponents;
import me.almana.logisticsnetworks.item.DurabilityFilterItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class DurabilityFilterData {

    private static final int DEFAULT_VALUE = 0;
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

    public static int getValue(ItemStack stack) {
        if (!isDurabilityFilterItem(stack))
            return DEFAULT_VALUE;

        LegacyComponentMigration.migrateDurabilityFilter(stack);
        return getConfig(stack).value();
    }

    public static Operator getOperator(ItemStack stack) {
        if (!isDurabilityFilterItem(stack))
            return DEFAULT_OPERATOR;

        LegacyComponentMigration.migrateDurabilityFilter(stack);
        return getConfig(stack).operator();
    }

    private static DurabilityFilterConfig getConfig(ItemStack stack) {
        return stack.getOrDefault(LogisticsDataComponents.DURABILITY_FILTER,
                new DurabilityFilterConfig(DEFAULT_VALUE, DEFAULT_OPERATOR));
    }
}
