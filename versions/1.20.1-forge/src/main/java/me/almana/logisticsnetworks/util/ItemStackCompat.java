package me.almana.logisticsnetworks.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class ItemStackCompat {

    private ItemStackCompat() {
    }

    public static ItemStack copyWithCount(ItemStack stack, int count) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack copy = stack.copy();
        copy.setCount(Math.max(0, count));
        return copy;
    }

    public static boolean isSameItemSameComponents(ItemStack left, ItemStack right) {
        return ItemStack.isSameItemSameTags(left, right);
    }

    public static ItemStack parseOptional(@Nullable HolderLookup.Provider provider, CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return ItemStack.of(tag);
    }

    public static CompoundTag save(ItemStack stack, @Nullable HolderLookup.Provider provider) {
        return stack == null ? new CompoundTag() : stack.save(new CompoundTag());
    }

    public static CompoundTag saveOptional(ItemStack stack, @Nullable HolderLookup.Provider provider) {
        if (stack == null || stack.isEmpty()) {
            return new CompoundTag();
        }
        return stack.save(new CompoundTag());
    }
}
