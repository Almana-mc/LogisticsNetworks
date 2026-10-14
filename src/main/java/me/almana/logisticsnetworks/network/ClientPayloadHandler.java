package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.client.screen.ClipboardScreen;
import me.almana.logisticsnetworks.client.screen.ComputerScreen;
import me.almana.logisticsnetworks.client.screen.FilterScreen;
import me.almana.logisticsnetworks.client.screen.NodeEditorScreen;
import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.NodeClipboardConfig;
import me.almana.logisticsnetworks.menu.ClipboardMenu;
import me.almana.logisticsnetworks.menu.FilterMenu;
import me.almana.logisticsnetworks.menu.NodeMenu;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.minecraft.client.Minecraft;
import me.almana.logisticsnetworks.network.payload.IPayloadContext;

public class ClientPayloadHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static void handleSyncNetworkList(SyncNetworkListPayload payload, IPayloadContext context) {
        LOGGER.debug("Received SyncNetworkListPayload with {} networks", payload.networks().size());
        context.enqueueWork(() -> {
            var screen = Minecraft.getInstance().screen;
            LOGGER.debug("Current screen: {}", screen != null ? screen.getClass().getSimpleName() : "null");
            if (screen instanceof NodeEditorScreen<?> nodeScreen) {
                LOGGER.debug("Passing to NodeScreen");
                nodeScreen.receiveNetworkList(payload.networks());
            } else if (screen instanceof ComputerScreen computerScreen) {
                LOGGER.debug("Passing to ComputerScreen");
                computerScreen.receiveNetworkList(payload.networks());
            } else if (screen instanceof ClipboardScreen clipboardScreen) {
                clipboardScreen.receiveNetworkList(payload.networks());
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
            if (screen instanceof NodeEditorScreen<?> nodeScreen) {
                nodeScreen.receiveNetworkLabels(payload.labels());
            } else if (screen instanceof ClipboardScreen clipboardScreen) {
                clipboardScreen.receiveNetworkLabels(payload.labels());
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
                computerScreen.receiveChannelList(payload);
            } else if (screen instanceof ClipboardScreen clipboardScreen) {
                clipboardScreen.receiveChannelList(payload.networkId(), payload.channelNames());
            }
        });
    }

    public static void handleSyncChannelData(SyncChannelDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null || payload.channelTag() == null)
                return;
            if (player.containerMenu instanceof NodeMenu menu && menu.getNodeId() == payload.entityId()) {
                ChannelData channel = menu.getNode().getChannel(payload.channelIndex());
                if (channel != null) {
                    channel.load(payload.channelTag(), player.level().registryAccess());
                }
            }
        });
    }

    public static void handleSyncFilterScanResult(SyncFilterScanResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var minecraft = Minecraft.getInstance();
            if (minecraft.player != null
                    && minecraft.player.containerMenu instanceof FilterMenu menu
                    && minecraft.screen instanceof FilterScreen screen
                    && menu.canScanAttachedStorage()) {
                menu.applySyncedFilter(payload.filter());
                screen.showScanResult(payload.added(), payload.storageFound(), payload.filterFull());
            }
        });
    }

    public static void handleSyncClipboard(SyncClipboardPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            NodeClipboardConfig clipboard = NodeClipboardConfig.load(payload.clipboardTag(), null);
            if (player != null && clipboard != null && player.containerMenu instanceof ClipboardMenu menu) {
                menu.setClipboard(clipboard);
                if (Minecraft.getInstance().screen instanceof ClipboardScreen screen) screen.requestChannelNames();
            }
        });
    }

    public static void handleSyncOpenFilter(SyncOpenFilterPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof FilterMenu menu
                    && menu.containerId == payload.containerId() && menu.isBoundFilter()) {
                menu.applySyncedFilter(payload.filter());
            }
        });
    }
}
