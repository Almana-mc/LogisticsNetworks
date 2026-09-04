package me.almana.logisticsnetworks.component;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public record StackSnapshot(Holder<Item> item, int count, DataComponentPatch components) {

    public static final Codec<StackSnapshot> CODEC = ItemStack.CODEC.xmap(StackSnapshot::of, StackSnapshot::toStack);

    public StackSnapshot {
        count = Math.max(1, Math.min(99, count));
        components = components == null ? DataComponentPatch.EMPTY : components;
    }

    public static StackSnapshot of(ItemStack stack) {
        return new StackSnapshot(stack.getItemHolder(), stack.getCount(), stack.getComponentsPatch());
    }

    public ItemStack toStack() {
        return new ItemStack(item, count, components);
    }
}
