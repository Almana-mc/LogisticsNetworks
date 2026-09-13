package me.almana.logisticsnetworks.integration.storage;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.Map;
import java.util.Set;

public interface DirectItemAccess extends IItemHandler {
    ItemStack insert(ItemStack stack, boolean simulate);

    ItemStack extract(ItemStack stack, int amount, boolean simulate);

    Map<Item, Integer> countItems(Set<Item> items);
}
