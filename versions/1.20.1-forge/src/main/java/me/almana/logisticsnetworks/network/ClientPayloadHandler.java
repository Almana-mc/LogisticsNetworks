package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.client.screen.ComputerScreen;
import me.almana.logisticsnetworks.client.screen.NodeScreen;
import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import me.almana.logisticsnetworks.network.payload.IPayloadContext;

public class ClientPayloadHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static void handleSyncNetworkList(SyncNetworkListPayload payload, IPayloadContext context) {
        LOGGER.debug("Received SyncNetworkListPayload with {} networks", payload.networks().size());
        context.enqueueWork(() -> {
            var screen = Minecraft.getInstance().screen;
            LOGGER.debug("Current screen: {}", screen != null ? screen.getClass().getSimpleName() : "null");
            if (screen instanceof NodeScreen nodeScreen) {
                LOGGER.debug("Passing to NodeScreen");
                nodeScreen.receiveNetworkList(payload.networks());
            } else if (screen instanceof ComputerScreen computerScreen) {
                LOGGER.debug("Passing to ComputerScreen");
                computerScreen.receiveNetworkList(payload.networks());
            } else {
                LOGGER.debug("Screen is not NodeScreen or ComputerScreen, ignoring");
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

    public static void handleSyncTelemetry(SyncTelemetryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var screen = Minecraft.getInstance().screen;
            if (screen instanceof ComputerScreen computerScreen) {
                computerScreen.receiveTelemetry(payload);
            }
        });
    }

    public static void handleSyncChannelList(SyncChannelListPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var screen = Minecraft.getInstance().screen;
            if (screen instanceof ComputerScreen computerScreen) {
                computerScreen.receiveChannelList(payload.networkId(), payload.channels());
            }
        });
    }

    public static void handleSyncChannelData(SyncChannelDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null || payload.channelTag() == null)
                return;
            Entity entity = player.level().getEntity(payload.entityId());
            if (entity instanceof LogisticsNodeEntity node) {
                ChannelData channel = node.getChannel(payload.channelIndex());
                if (channel != null) {
                    channel.load(payload.channelTag(), player.level().registryAccess());
                }
            }
        });
    }
}
