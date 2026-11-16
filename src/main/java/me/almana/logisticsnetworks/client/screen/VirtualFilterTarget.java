package me.almana.logisticsnetworks.client.screen;

import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

public interface VirtualFilterTarget {
    int getFilterSlotCount();

    boolean isFilterSlotItemAddable(int slot);

    Rect2i getFilterSlotArea(int slot);

    void addItemToFilterSlot(int slot, ItemStack item);
}
