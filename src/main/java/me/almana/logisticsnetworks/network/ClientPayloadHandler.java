package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.client.screen.ComputerScreen;
import me.almana.logisticsnetworks.client.screen.NodeScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ClientPayloadHandler {

    public static void handleSyncNetworkList(SyncNetworkListPayload payload, IPayloadContext context) {
        System.out.println("[ClientPayloadHandler] Received SyncNetworkListPayload with " + payload.networks().size() + " networks");
        context.enqueueWork(() -> {
            var screen = Minecraft.getInstance().screen;
            System.out.println("[ClientPayloadHandler] Current screen: " + (screen != null ? screen.getClass().getSimpleName() : "null"));
            if (screen instanceof NodeScreen nodeScreen) {
                System.out.println("[ClientPayloadHandler] Passing to NodeScreen");
                nodeScreen.receiveNetworkList(payload.networks());
            } else if (screen instanceof ComputerScreen computerScreen) {
                System.out.println("[ClientPayloadHandler] Passing to ComputerScreen");
                computerScreen.receiveNetworkList(payload.networks());
            } else {
                System.out.println("[ClientPayloadHandler] Screen is not NodeScreen or ComputerScreen, ignoring");
            }
        });
    }
}
