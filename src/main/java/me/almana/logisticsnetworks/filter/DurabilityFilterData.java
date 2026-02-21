package me.almana.logisticsnetworks.filter;

import me.almana.logisticsnetworks.item.DurabilityFilterItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

public final class DurabilityFilterData {

    private static final String ROOT_KEY = "ln_durability_filter";
    private static final String KEY_VALUE = "value";
    private static final String KEY_OPERATOR = "operator";
    private static final String KEY_IS_BLACKLIST = "blacklist";
    private static final String KEY_TARGET_TYPE = "target";

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
        return getRootTag(stack).getBoolean(KEY_IS_BLACKLIST);
    }

    public static void setBlacklist(ItemStack stack, boolean isBlacklist) {
        if (!isDurabilityFilterItem(stack))
            return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, customTag -> {
            CompoundTag root = getRootTag(customTag);
            if (isBlacklist) {
                root.putBoolean(KEY_IS_BLACKLIST, true);
            } else {
                root.remove(KEY_IS_BLACKLIST);
            }
            writeRoot(customTag, root);
        });
    }

    public static FilterTargetType getTargetType(ItemStack stack) {
        if (!isDurabilityFilterItem(stack))
            return FilterTargetType.ITEMS;
        CompoundTag root = getRootTag(stack);
        return FilterTargetType.fromOrdinal(root.getInt(KEY_TARGET_TYPE));
    }

    public static void setTargetType(ItemStack stack, FilterTargetType type) {
        if (!isDurabilityFilterItem(stack))
            return;
        FilterTargetType target = type == null ? FilterTargetType.ITEMS : type;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, customTag -> {
            CompoundTag root = getRootTag(customTag);
            if (target == FilterTargetType.ITEMS) {
                root.remove(KEY_TARGET_TYPE);
            } else {
                root.putInt(KEY_TARGET_TYPE, target.ordinal());
            }
            writeRoot(customTag, root);
        });
    }

    public static int getValue(ItemStack stack) {
        if (!isDurabilityFilterItem(stack))
            return DEFAULT_VALUE;

        CompoundTag root = getRootTag(stack);
        if (!root.contains(KEY_VALUE, Tag.TAG_INT))
            return DEFAULT_VALUE;

        return clamp(root.getInt(KEY_VALUE));
    }

    public static void setValue(ItemStack stack, int value) {
        if (!isDurabilityFilterItem(stack))
            return;

        int clamped = clamp(value);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, customTag -> {
            CompoundTag root = getRootTag(customTag);
            if (clamped == DEFAULT_VALUE) {
                root.remove(KEY_VALUE);
            } else {
                root.putInt(KEY_VALUE, clamped);
            }
            writeRoot(customTag, root);
        });
    }

    public static Operator getOperator(ItemStack stack) {
        if (!isDurabilityFilterItem(stack))
            return DEFAULT_OPERATOR;

        CompoundTag root = getRootTag(stack);
        if (!root.contains(KEY_OPERATOR, Tag.TAG_STRING))
            return DEFAULT_OPERATOR;

        return Operator.fromId(root.getString(KEY_OPERATOR));
    }

    public static void setOperator(ItemStack stack, @Nullable Operator operator) {
        if (!isDurabilityFilterItem(stack))
            return;

        Operator normalized = operator == null ? DEFAULT_OPERATOR : operator;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, customTag -> {
            CompoundTag root = getRootTag(customTag);
            if (normalized == DEFAULT_OPERATOR) {
                root.remove(KEY_OPERATOR);
            } else {
                root.putString(KEY_OPERATOR, normalized.id());
            }
            writeRoot(customTag, root);
        });
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
