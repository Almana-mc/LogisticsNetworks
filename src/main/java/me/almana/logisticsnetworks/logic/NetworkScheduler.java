package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.NetworkRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;

// Dirty-only dispatch, no scan
@EventBusSubscriber
public class NetworkScheduler {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ServerLevel level = event.getServer().overworld();

        NetworkRegistry registry = NetworkRegistry.get(level);
        if (registry == null)
            return;

        registry.processDirtyNetworks(event.getServer());
        registry.getTelemetryManager().tick(registry, event.getServer());
    }
}


