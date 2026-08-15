package me.almana.logisticsnetworks.integration.sophisticated;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.p3pp3rf1y.sophisticatedcore.inventory.CachedFailedInsertInventoryHandler;
import net.p3pp3rf1y.sophisticatedcore.inventory.IItemHandlerSimpleInserter;
import org.jetbrains.annotations.Nullable;

final class SophisticatedCoreInsertion {
    private SophisticatedCoreInsertion() {
    }

    static boolean isSidedWrapper(IItemHandler handler) {
        return handler instanceof CachedFailedInsertInventoryHandler<?>;
    }

    static boolean isBulkHandler(@Nullable IItemHandler handler) {
        return handler instanceof IItemHandlerSimpleInserter;
    }

    static ItemStack insertItem(IItemHandler handler, ItemStack stack, boolean simulate) {
        return ((IItemHandlerSimpleInserter) handler).insertItem(stack, simulate);
    }
}
