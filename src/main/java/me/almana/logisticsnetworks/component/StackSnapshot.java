package me.almana.logisticsnetworks.component;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public record StackSnapshot(CompoundTag tag) {

    public static StackSnapshot of(ItemStack stack) {
        return new StackSnapshot(stack.save(new CompoundTag()));
    }

    public ItemStack toStack() {
        return ItemStack.of(tag);
    }
}
