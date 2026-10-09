package me.almana.logisticsnetworks.filter;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

public final class DurabilityFilterData {

    private static final String ROOT_KEY = "ln_durability_filter";
    private static final String KEY_VALUE = "value";
    private static final String KEY_OPERATOR = "operator";

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
        return false;
    }

    public static int getValue(ItemStack stack) {
        if (!isDurabilityFilterItem(stack))
            return DEFAULT_VALUE;

        CompoundTag root = getRootTag(stack);
        if (!root.contains(KEY_VALUE))
            return DEFAULT_VALUE;

        return clamp(root.getIntOr(KEY_VALUE, DEFAULT_VALUE));
    }

    public static Operator getOperator(ItemStack stack) {
        if (!isDurabilityFilterItem(stack))
            return DEFAULT_OPERATOR;

        CompoundTag root = getRootTag(stack);
        if (!root.contains(KEY_OPERATOR))
            return DEFAULT_OPERATOR;

        return Operator.fromId(root.getStringOr(KEY_OPERATOR, DEFAULT_OPERATOR.id()));
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

    private static CompoundTag getRootTag(ItemStack stack) {
        return getRootTag(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
    }

    private static CompoundTag getRootTag(CompoundTag customTag) {
        if (customTag.contains(ROOT_KEY)) {
            return customTag.getCompound(ROOT_KEY).map(CompoundTag::copy).orElseGet(CompoundTag::new);
        }
        return new CompoundTag();
    }
}
