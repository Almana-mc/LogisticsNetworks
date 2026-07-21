package me.almana.logisticsnetworks.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

public final class ItemDataUtil {

    private static final String ROOT_KEY = "ln_custom_data";

    private ItemDataUtil() {
    }

    public static CompoundTag getCustomData(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new CompoundTag();
        }

        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(ROOT_KEY, Tag.TAG_COMPOUND)) {
            return tag.getCompound(ROOT_KEY).copy();
        }

        return new CompoundTag();
    }

    public static void updateCustomData(ItemStack stack, Consumer<CompoundTag> updater) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        CompoundTag itemTag = stack.getOrCreateTag();
        CompoundTag customData = itemTag.contains(ROOT_KEY, Tag.TAG_COMPOUND)
                ? itemTag.getCompound(ROOT_KEY).copy()
                : new CompoundTag();

        updater.accept(customData);

        if (customData.isEmpty()) {
            itemTag.remove(ROOT_KEY);
            if (itemTag.isEmpty()) {
                stack.setTag(null);
            }
        } else {
            itemTag.put(ROOT_KEY, customData);
        }
    }
}
