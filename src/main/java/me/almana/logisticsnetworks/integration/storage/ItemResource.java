package me.almana.logisticsnetworks.integration.storage;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public final class ItemResource {
    private final ItemStack stack;
    private final int hash;

    public ItemResource(ItemStack stack) {
        this.stack = stack.copyWithCount(1);
        this.hash = Objects.hash(this.stack.getItem(), this.stack.getTag());
    }

    public Item item() {
        return stack.getItem();
    }

    public ItemStack toStack(int amount) {
        return stack.copyWithCount(amount);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ItemResource resource
                && hash == resource.hash && ItemStack.isSameItemSameTags(stack, resource.stack);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
