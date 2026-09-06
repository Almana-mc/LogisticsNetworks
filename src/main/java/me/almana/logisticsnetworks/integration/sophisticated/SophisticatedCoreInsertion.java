package me.almana.logisticsnetworks.integration.sophisticated;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.p3pp3rf1y.sophisticatedcore.controller.ControllerBlockEntityBase;
import net.p3pp3rf1y.sophisticatedcore.inventory.FilteredItemHandler;
import net.p3pp3rf1y.sophisticatedcore.inventory.ITrackedContentsItemResourceHandler;

final class SophisticatedCoreInsertion {
    private SophisticatedCoreInsertion() {
    }

    static boolean isBulkHandler(ResourceHandler<ItemResource> handler) {
        return handler instanceof ITrackedContentsItemResourceHandler
                || handler instanceof FilteredItemHandler<?>
                || handler instanceof ControllerBlockEntityBase;
    }
}
