package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.NetworkRegistry;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// Dirty-only dispatch, no scan
@EventBusSubscriber
public class NetworkScheduler {

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().overworld();

        NetworkRegistry registry = NetworkRegistry.get(level);
        if (registry == null)
            return;

        registry.processDirtyNetworks(event.getServer());
    }
}
