package me.almana.logisticsnetworks.menu;

import me.almana.logisticsnetworks.network.SyncMassPlacementChoicesPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

final class MassPlacementNetworkAccess {
    private MassPlacementNetworkAccess() {
    }

    static void sendToPlayer(ServerPlayer player, SyncMassPlacementChoicesPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
