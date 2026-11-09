package me.almana.logisticsnetworks.integration.refinedstorage;

import com.refinedmods.refinedstorage.api.network.impl.node.iface.InterfaceExportState;
import com.refinedmods.refinedstorage.api.network.node.NetworkNode;
import com.ultramega.cabletiers.common.iface.TieredInterfaceNetworkNode;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

final class CableTiersInterfaces {

    private CableTiersInterfaces() {
    }

    @Nullable
    static Supplier<InterfaceExportState> exportState(NetworkNode node) {
        if (!ModList.get().isLoaded("cabletiers")) return null;
        return node instanceof TieredInterfaceNetworkNode tiered ? tiered::getExportState : null;
    }
}
