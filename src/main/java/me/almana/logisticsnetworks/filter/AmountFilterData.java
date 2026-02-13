package me.almana.logisticsnetworks.filter;

import me.almana.logisticsnetworks.item.AmountFilterItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class AmountFilterData {

    private static final String ROOT_KEY = "ln_amount_filter";
    private static final String KEY_AMOUNT = "amount";

    private static final int DEFAULT_AMOUNT = 64;
    private static final int MIN_AMOUNT = 0;
    private static final int MAX_AMOUNT = 9_999;

    private AmountFilterData() {
    }

    public static boolean isAmountFilterItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof AmountFilterItem;
    }

    public static int getAmount(ItemStack stack) {
        if (!isAmountFilterItem(stack))
            return DEFAULT_AMOUNT;

        CompoundTag root = getRootTag(stack);
        if (!root.contains(KEY_AMOUNT, Tag.TAG_INT))
            return DEFAULT_AMOUNT;

        return clamp(root.getInt(KEY_AMOUNT));
    }

    public static void setAmount(ItemStack stack, int amount) {
        if (!isAmountFilterItem(stack))
            return;

        int clamped = clamp(amount);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, customTag -> {
            CompoundTag root = getRootTag(customTag);
            if (clamped == DEFAULT_AMOUNT) {
                root.remove(KEY_AMOUNT);
            } else {
                root.putInt(KEY_AMOUNT, clamped);
            }
            writeRoot(customTag, root);
        });
    }

    private static int clamp(int amount) {
        return Math.max(MIN_AMOUNT, Math.min(MAX_AMOUNT, amount));
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
