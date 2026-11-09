package me.almana.logisticsnetworks.integration.storage;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.Map;
import java.util.Set;

public interface DirectItemAccess extends ResourceHandler<ItemResource> {
    Object2IntMap<Item> countItems(Set<Item> items);
}
