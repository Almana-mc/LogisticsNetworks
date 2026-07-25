package me.almana.logisticsnetworks.menu;

import me.almana.logisticsnetworks.network.NetworkHandler;
import me.almana.logisticsnetworks.network.SyncMassPlacementChoicesPayload;
import net.minecraft.server.level.ServerPlayer;

final class MassPlacementNetworkAccess {
    private MassPlacementNetworkAccess() {
    }

    static void sendToPlayer(ServerPlayer player, SyncMassPlacementChoicesPayload payload) {
        NetworkHandler.sendToPlayer(player, payload);
    }
}
