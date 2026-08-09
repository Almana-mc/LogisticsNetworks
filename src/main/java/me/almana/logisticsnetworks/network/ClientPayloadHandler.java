package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.client.screen.ComputerScreen;
import me.almana.logisticsnetworks.client.screen.MassPlacementScreen;
import me.almana.logisticsnetworks.client.screen.NodeScreen;
import me.almana.logisticsnetworks.client.TransferVisuals;
import me.almana.logisticsnetworks.menu.NodeMenu;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ClientPayloadHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static void handleTransferVisual(TransferVisualPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> TransferVisuals.accept(payload));
    }

    public static void handleSyncNetworkList(SyncNetworkListPayload payload, IPayloadContext context) {
        if (Config.debugMode) LOGGER.debug("Received SyncNetworkListPayload with {} networks", payload.networks().size());
        context.enqueueWork(() -> {
            var screen = Minecraft.getInstance().screen;
            if (Config.debugMode) LOGGER.debug("Current screen: {}", screen != null ? screen.getClass().getSimpleName() : "null");
            if (screen instanceof NodeScreen nodeScreen) {
                if (Config.debugMode) LOGGER.debug("Passing to NodeScreen");
                nodeScreen.receiveNetworkList(payload.networks());
            } else if (screen instanceof ComputerScreen computerScreen) {
                if (Config.debugMode) LOGGER.debug("Passing to ComputerScreen");
                computerScreen.receiveNetworkList(payload.networks());
            } else {
                if (Config.debugMode) LOGGER.debug("Screen is not NodeScreen or ComputerScreen, ignoring");
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

    public static void handleSyncNetworkExport(SyncNetworkExportPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var screen = Minecraft.getInstance().screen;
            if (screen instanceof ComputerScreen computerScreen) {
                computerScreen.receiveNetworkExport(payload);
            }
        });
    }

    public static void handleSyncMassPlacementChoices(SyncMassPlacementChoicesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var screen = Minecraft.getInstance().screen;
            if (screen instanceof MassPlacementScreen massPlacementScreen
                    && massPlacementScreen.hasContainerId(payload.containerId())) {
                massPlacementScreen.receiveBlockChoices(payload.choices(), payload.maxNodes());
            }
        });
    }

    public static void handleSyncChannelData(SyncChannelDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null || payload.channelData() == null)
                return;
            if (player.containerMenu instanceof NodeMenu menu && menu.getNodeId() == payload.entityId()) {
                ChannelData channel = menu.getNode().getChannel(payload.channelIndex());
                if (channel != null) {
                    channel.load(payload.channelData(), player.level().registryAccess());
                }
            }
        });
    }
}
