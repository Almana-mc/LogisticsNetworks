package me.almana.logisticsnetworks.network.dist;

import me.almana.logisticsnetworks.network.NetworkHandler;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public final class PacketDistributor {

    private PacketDistributor() {
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        NetworkHandler.sendToPlayer(player, payload);
    }
}
