package me.almana.logisticsnetworks.integration.storage;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface StorageAccess {

    StorageLink link();

    boolean isValid();

    boolean allows(ServerPlayer player, StorageAction action);

    long count(ItemStack pattern);

    long count(Item item);

    List<ItemStack> extract(ItemStack pattern, int amount, ServerPlayer player);

    ItemStack extractOne(Item item, ServerPlayer player);

    long insert(ItemStack stack, long amount, ServerPlayer player);

    Map<Item, Long> countTagged(TagKey<Item> tag);

    Set<Item> craftableTagged(TagKey<Item> tag);

    boolean isCraftable(ItemStack stack);

    @Nullable
    default CraftingBatch startCrafting(ServerPlayer player,
                                        List<LinkedStorage.ItemRequirement> requirements,
                                        CraftingBatch.Listener listener) {
        listener.failed(StorageFailure.of(StorageFailure.Reason.NO_PATTERN));
        return null;
    }
}
