package me.almana.logisticsnetworks.integration.storage;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ItemResource {
    private final ItemStack stack;
    private final int hash;

    public ItemResource(ItemStack stack) {
        this.stack = stack.copyWithCount(1);
        this.hash = ItemStack.hashItemAndComponents(this.stack);
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
                && hash == resource.hash && ItemStack.isSameItemSameComponents(stack, resource.stack);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
