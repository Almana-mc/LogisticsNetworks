package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.client.screen.NodeScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ClientPayloadHandler {

    public static void handleSyncNetworkList(SyncNetworkListPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof NodeScreen screen) {
                screen.receiveNetworkList(payload.networks());
            }
        });
    }
}
