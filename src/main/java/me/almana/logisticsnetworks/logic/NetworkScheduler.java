package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.filter.TooltipLines;
import me.almana.logisticsnetworks.integration.storage.LinkedStorage;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;

import java.util.function.BooleanSupplier;

// Dirty-only dispatch, no scan
@EventBusSubscriber
public class NetworkScheduler {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            planTick(event.getServer());
        } else {
            commitTick(event.getServer(), event::haveTime);
        }
    }

    private static void planTick(MinecraftServer server) {
        TooltipLines.drainPending();
        NetworkRegistry registry = NetworkRegistry.get(server.overworld());
        if (registry == null)
            return;

        registry.refreshRackLinks(server);
        if (registry.refreshAsyncPlanning()) {
            registry.dispatchDirty(server);
        }
    }

    private static void commitTick(MinecraftServer server, BooleanSupplier hasTime) {
        LinkedStorage.tickCraftingRequests(server);
        NetworkRegistry registry = NetworkRegistry.get(server.overworld());
        if (registry == null)
            return;

        // Racks may unload mid-tick
        registry.refreshRackLinks(server);
        if (registry.refreshAsyncPlanning()) {
            registry.commitCompleted(server, hasTime);
            registry.dispatchDirty(server);
            registry.processDegradedRecovery(server);
        } else {
            registry.processDirtyNetworks(server);
        }
        registry.getTelemetryManager().tick(registry, server);
    }
}
