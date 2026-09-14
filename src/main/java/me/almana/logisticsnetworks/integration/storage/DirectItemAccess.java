package me.almana.logisticsnetworks.integration.storage;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.Map;
import java.util.Set;

public interface DirectItemAccess extends ResourceHandler<ItemResource> {
    Map<Item, Integer> countItems(Set<Item> items);
}
