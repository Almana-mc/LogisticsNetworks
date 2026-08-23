package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.NetworkRegistry;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber
public class NetworkScheduler {

    @SubscribeEvent
    public static void onServerTickPre(ServerTickEvent.Pre event) {
        NetworkRegistry registry = NetworkRegistry.get(event.getServer().overworld());
        if (registry == null)
            return;

        if (registry.refreshAsyncPlanning()) {
            registry.dispatchDirty(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().overworld();
        NetworkRegistry registry = NetworkRegistry.get(level);
        if (registry == null)
            return;

        if (registry.refreshAsyncPlanning()) {
            registry.commitCompleted(event.getServer(), event::hasTime);
            registry.dispatchDirty(event.getServer());
            registry.processSynchronousFallbacks(event.getServer());
        } else {
            registry.processDirtyNetworks(event.getServer());
        }
        registry.getTelemetryManager().tick(registry, event.getServer());
    }
}
