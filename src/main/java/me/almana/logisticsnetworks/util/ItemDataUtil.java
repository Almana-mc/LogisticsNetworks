package me.almana.logisticsnetworks.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
//? if forge {
/*import net.minecraft.nbt.Tag;
*///?} else {
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
//?}

import java.util.function.Consumer;

/**
 * 1.20.1 has no data components, so it keeps the mod's per-stack data in a
 * nested tag that stands in for CUSTOM_DATA. The stored shape differs between
 * the two, but every caller sees the same CompoundTag either way.
 */
public final class ItemDataUtil {

    //? if forge
    /*private static final String ROOT_KEY = "ln_custom_data";*/

    private ItemDataUtil() {
    }

    public static CompoundTag getCustomData(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new CompoundTag();
        }

        //? if forge {
        /*CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(ROOT_KEY, Tag.TAG_COMPOUND)) {
            return tag.getCompound(ROOT_KEY).copy();
        }
        return new CompoundTag();
        *///?} else {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        //?}
    }

    public static void updateCustomData(ItemStack stack, Consumer<CompoundTag> updater) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        //? if forge {
        /*CompoundTag itemTag = stack.getOrCreateTag();
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
        *///?} else {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, updater::accept);
        //?}
    }
}
