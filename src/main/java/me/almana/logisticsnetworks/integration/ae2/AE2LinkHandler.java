package me.almana.logisticsnetworks.integration.ae2;

import appeng.api.features.IGridLinkableHandler;
import me.almana.logisticsnetworks.integration.storage.StorageBackend;
import me.almana.logisticsnetworks.integration.storage.StorageLink;
import me.almana.logisticsnetworks.item.WrenchItem;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.item.ItemStack;

final class AE2LinkHandler implements IGridLinkableHandler {

    static final AE2LinkHandler INSTANCE = new AE2LinkHandler();

    @Override
    public boolean canLink(ItemStack stack) {
        return stack.getItem() instanceof WrenchItem;
    }

    @Override
    public void link(ItemStack itemStack, GlobalPos pos) {
        StorageLink current = WrenchItem.getStorageLink(itemStack);
        StorageLink link = new StorageLink(StorageBackend.AE2, pos);
        if (current == null || current.equals(link)) WrenchItem.setStorageLink(itemStack, link);
    }

    @Override
    public void unlink(ItemStack itemStack) {
        StorageLink current = WrenchItem.getStorageLink(itemStack);
        if (current != null && current.backend() == StorageBackend.AE2) WrenchItem.clearStorageLink(itemStack);
    }
}
