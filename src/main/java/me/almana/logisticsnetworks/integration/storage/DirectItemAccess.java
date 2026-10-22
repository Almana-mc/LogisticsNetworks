package me.almana.logisticsnetworks.integration.storage;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.Set;

public interface DirectItemAccess extends IItemHandler {
    ItemStack insert(ItemStack stack, boolean simulate);

    ItemStack extract(ItemStack stack, int amount, boolean simulate);

    Object2IntMap<Item> countItems(Set<Item> items);
}
