package me.almana.logisticsnetworks.integration.storage;

import net.minecraft.world.item.ItemStack;

import java.util.List;

public interface CraftingBatch {

    interface Listener {
        boolean complete(CraftingBatch batch);

        void failed(StorageFailure failure);
    }

    void tick();

    boolean isFinished();

    long held(ItemStack pattern);

    List<ItemStack> take(ItemStack pattern, int amount);

    void cancel();
}
