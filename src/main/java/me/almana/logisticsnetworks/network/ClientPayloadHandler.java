package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.client.screen.ClipboardScreen;
import me.almana.logisticsnetworks.client.screen.ComputerScreen;
import me.almana.logisticsnetworks.client.screen.FilterScreen;
import me.almana.logisticsnetworks.client.screen.MassPlacementScreen;
import me.almana.logisticsnetworks.client.screen.NodeEditorScreen;
import me.almana.logisticsnetworks.client.screen.NodeGraphScreen;
import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.NodeClipboardConfig;
import me.almana.logisticsnetworks.menu.ClipboardMenu;
import me.almana.logisticsnetworks.menu.FilterMenu;
import me.almana.logisticsnetworks.menu.NodeMenu;
import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import me.almana.logisticsnetworks.network.payload.IPayloadContext;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public class ClientPayloadHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<SyncNetworkListPayload.NetworkEntry> NETWORK_PARTS = new ArrayList<>();
    private static final List<SyncNetworkNodesPayload.NodeInfo> NODE_PARTS = new ArrayList<>();
    private static final ByteArrayOutputStream CHANNEL_PARTS = new ByteArrayOutputStream();
    private static final ByteArrayOutputStream CLIPBOARD_PARTS = new ByteArrayOutputStream();

    public static void clearParts() {
        NETWORK_PARTS.clear();
        NODE_PARTS.clear();
        CHANNEL_PARTS.reset();
        CLIPBOARD_PARTS.reset();
    }

    public static void warnTooLarge() {
        Minecraft.getInstance().player.displayClientMessage(
                Component.translatable("message.logisticsnetworks.payload_too_large"), false);
    }

    @Nullable
    private static <T> List<T> join(List<T> parts, List<T> part, boolean first, boolean more) {
        if (first) parts.clear();
        parts.addAll(part);
        if (more) return null;
        List<T> all = new ArrayList<>(parts);
        parts.clear();
        return all;
    }

    @Nullable
    private static CompoundTag join(ByteArrayOutputStream parts, byte[] part, boolean first, boolean more) {
        if (first) parts.reset();
        parts.writeBytes(part);
        if (more) return null;
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(parts.toByteArray()));
        parts.reset();
        return buf.readAnySizeNbt();
    }

    public static void handleSyncNetworkList(SyncNetworkListPayload payload, IPayloadContext context) {
        LOGGER.debug("Received SyncNetworkListPayload with {} networks", payload.networks().size());
        context.enqueueWork(() -> {
            var networks = join(NETWORK_PARTS, payload.networks(), payload.first(), payload.more());
            if (networks == null) return;
            var screen = Minecraft.getInstance().screen;
            LOGGER.debug("Current screen: {}", screen != null ? screen.getClass().getSimpleName() : "null");
            if (screen instanceof NodeEditorScreen<?> nodeScreen) {
                LOGGER.debug("Passing to NodeScreen");
                nodeScreen.receiveNetworkList(networks);
            } else if (screen instanceof ComputerScreen computerScreen) {
                LOGGER.debug("Passing to ComputerScreen");
                computerScreen.receiveNetworkList(networks);
            } else if (screen instanceof ClipboardScreen clipboardScreen) {
                clipboardScreen.receiveNetworkList(networks);
            } else {
                LOGGER.debug("Screen is not NodeScreen or ComputerScreen, ignoring");
            }
        });
    }

    public static void handleSyncNetworkGraph(SyncNetworkGraphPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof NodeGraphScreen screen) {
                screen.receiveGraph(payload);
            }
        });
    }

    public static void handleGraphLabelPreview(GraphLabelPreviewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof NodeGraphScreen screen) {
                screen.receiveLabelPreview(payload);
            }
        });
    }

    public static void handleSyncNetworkNodes(SyncNetworkNodesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var nodes = join(NODE_PARTS, payload.nodes(), payload.first(), payload.more());
            if (nodes != null && Minecraft.getInstance().screen instanceof ComputerScreen computerScreen) {
                computerScreen.receiveNetworkNodes(payload.networkId(), nodes);
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

    public static void handleSyncMassPlacementChoices(SyncMassPlacementChoicesPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> {
            var screen = Minecraft.getInstance().screen;
            if (screen instanceof MassPlacementScreen massPlacementScreen
                    && massPlacementScreen.hasContainerId(payload.containerId())) {
                massPlacementScreen.receiveBlockChoices(payload.choices(), payload.maxNodes());
            }
        });
    }

    public static void handleSyncMassPlacementRequirements(SyncMassPlacementRequirementsPayload payload,
                                                            IPayloadContext context) {
        context.enqueueWork(() -> {
            var screen = Minecraft.getInstance().screen;
            if (screen instanceof MassPlacementScreen massPlacementScreen
                    && massPlacementScreen.hasContainerId(payload.containerId())) {
                massPlacementScreen.receiveRequirements(payload.requirements(), payload.pending());
            }
        });
    }

    public static void handleSyncNetworkExport(SyncNetworkExportPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof ComputerScreen computerScreen) {
                computerScreen.receiveNetworkExport(payload);
            }
        });
    }

    public static void handleSyncChannelData(SyncChannelDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            CompoundTag channelTag = join(CHANNEL_PARTS, payload.channelBytes(), payload.first(), payload.more());
            var player = Minecraft.getInstance().player;
            if (player == null || channelTag == null)
                return;
            if (player.containerMenu instanceof NodeMenu menu && menu.getNodeId() == payload.entityId()) {
                ChannelData channel = menu.getNode().getChannel(payload.channelIndex());
                if (channel != null) {
                    channel.load(channelTag, player.level().registryAccess());
                }
            }
        });
    }

    public static void handleSyncStorageUpgradeCatalog(SyncStorageUpgradeCatalogPayload payload,
                                                       IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof NodeEditorScreen<?> screen) {
                screen.receiveStorageUpgradeCatalog(payload);
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
            CompoundTag clipboardTag = join(CLIPBOARD_PARTS, payload.clipboardBytes(), payload.first(), payload.more());
            if (clipboardTag == null) return;
            var player = Minecraft.getInstance().player;
            NodeClipboardConfig clipboard = NodeClipboardConfig.load(clipboardTag, null);
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
