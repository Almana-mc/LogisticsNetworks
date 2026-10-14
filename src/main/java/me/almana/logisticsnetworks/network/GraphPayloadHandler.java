package me.almana.logisticsnetworks.network;

import net.minecraft.server.MinecraftServer;

import java.util.UUID;

public final class GraphPayloadHandler {

    private GraphPayloadHandler() {
    }

    // Graph snapshots arrive Phase 6
    public static void broadcast(MinecraftServer server, UUID networkId) {
        ServerPayloadHandler.refreshNodeViewers(server, networkId);
    }
}
