package me.almana.logisticsnetworks.network.dist;

import me.almana.logisticsnetworks.network.NetworkHandler;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;

public final class ClientPacketDistributor {

    private ClientPacketDistributor() {
    }

    public static void sendToServer(CustomPacketPayload payload) {
        NetworkHandler.sendToServer(payload);
    }
}
