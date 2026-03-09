package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.client.screen.ComputerScreen;
import me.almana.logisticsnetworks.client.screen.NodeScreen;
import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
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

    public static void handleSyncNetworkNodes(SyncNetworkNodesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var screen = Minecraft.getInstance().screen;
            if (screen instanceof ComputerScreen computerScreen) {
                computerScreen.receiveNetworkNodes(payload.networkId(), payload.nodes());
            }
        });
    }

    public static void handleSyncNetworkLabels(SyncNetworkLabelsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var screen = Minecraft.getInstance().screen;
            if (screen instanceof NodeScreen nodeScreen) {
                nodeScreen.receiveNetworkLabels(payload.labels());
            }
        });
    }

    public static void handleSyncChannelData(SyncChannelDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null || payload.channelData() == null)
                return;
            Entity entity = player.level().getEntity(payload.entityId());
            if (entity instanceof LogisticsNodeEntity node) {
                ChannelData channel = node.getChannel(payload.channelIndex());
                if (channel != null) {
                    channel.load(payload.channelData(), player.level().registryAccess());
                }
            }
        });
    }
}
