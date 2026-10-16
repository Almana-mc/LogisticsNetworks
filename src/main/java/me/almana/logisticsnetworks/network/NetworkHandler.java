package me.almana.logisticsnetworks.network;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.RegistryFriendlyByteBuf;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.network.payload.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import me.almana.logisticsnetworks.network.payload.IPayloadContext;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public final class NetworkHandler {

    // Clientbound cap: 1MiB
    private static final int CLIENTBOUND_BUDGET = 900_000;
    // Serverbound payloads cap at 32767
    private static final int SERVERBOUND_BUDGET = 32_000;
    private static final Map<Class<?>, StreamCodec<?, ?>> SERVERBOUND_CODECS = new HashMap<>();
    private static final String PROTOCOL_VERSION = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static int nextId = 0;

    private NetworkHandler() {
    }

    public static void register() {
        registerServer(UpdateChannelPayload.class, UpdateChannelPayload.STREAM_CODEC, ServerPayloadHandler::handleUpdateChannel);
        registerServer(AssignNetworkPayload.class, AssignNetworkPayload.STREAM_CODEC, ServerPayloadHandler::handleAssignNetwork);
        registerServer(SetChannelFilterItemPayload.class, SetChannelFilterItemPayload.STREAM_CODEC, ServerPayloadHandler::handleSetChannelFilterItem);
        registerServer(SelectNodeChannelPayload.class, SelectNodeChannelPayload.STREAM_CODEC, ServerPayloadHandler::handleSelectNodeChannel);
        registerServer(ModifyFilterModPayload.class, ModifyFilterModPayload.STREAM_CODEC, ServerPayloadHandler::handleModifyFilterMod);
        registerServer(SetFilterFluidEntryPayload.class, SetFilterFluidEntryPayload.STREAM_CODEC, ServerPayloadHandler::handleSetFilterFluidEntry);
        registerServer(SetFilterChemicalEntryPayload.class, SetFilterChemicalEntryPayload.STREAM_CODEC, ServerPayloadHandler::handleSetFilterChemicalEntry);
        registerServer(SetFilterItemEntryPayload.class, SetFilterItemEntryPayload.STREAM_CODEC, ServerPayloadHandler::handleSetFilterItemEntry);
        registerServer(SetNameFilterPayload.class, SetNameFilterPayload.STREAM_CODEC, ServerPayloadHandler::handleSetNameFilter);
        registerServer(ToggleNodeVisibilityPayload.class, ToggleNodeVisibilityPayload.STREAM_CODEC, ServerPayloadHandler::handleToggleVisibility);
        registerServer(CycleWrenchModePayload.class, CycleWrenchModePayload.STREAM_CODEC, ServerPayloadHandler::handleCycleWrenchMode);
        registerServer(MassSelectConnectedPayload.class, MassSelectConnectedPayload.STREAM_CODEC, ServerPayloadHandler::handleMassSelectConnected);
        registerServer(CopyPasteConnectedPayload.class, CopyPasteConnectedPayload.STREAM_CODEC, ServerPayloadHandler::handleCopyPasteConnected);
        registerServer(OpenFilterInSlotPayload.class, OpenFilterInSlotPayload.STREAM_CODEC, ServerPayloadHandler::handleOpenFilterInSlot);
        registerServer(OpenNodeFilterPayload.class, OpenNodeFilterPayload.STREAM_CODEC, ServerPayloadHandler::handleOpenNodeFilter);
        registerServer(RenameNetworkPayload.class, RenameNetworkPayload.STREAM_CODEC, ServerPayloadHandler::handleRenameNetwork);
        registerServer(ApplyPatternPayload.class, ApplyPatternPayload.STREAM_CODEC, ServerPayloadHandler::handleApplyPattern);
        registerServer(RequestNetworkNodesPayload.class, RequestNetworkNodesPayload.STREAM_CODEC, ServerPayloadHandler::handleRequestNetworkNodes);
        registerServer(RequestNetworkLabelsPayload.class, RequestNetworkLabelsPayload.STREAM_CODEC, ServerPayloadHandler::handleRequestNetworkLabels);
        registerServer(RequestOpenNodeSettingsPayload.class, RequestOpenNodeSettingsPayload.STREAM_CODEC, GraphPayloadHandler::handleOpenNodeSettings);
        registerServer(SetNodeLabelPayload.class, SetNodeLabelPayload.STREAM_CODEC, ServerPayloadHandler::handleSetNodeLabel);
        registerServer(SetChannelNamePayload.class, SetChannelNamePayload.STREAM_CODEC, ServerPayloadHandler::handleSetChannelName);
        registerServer(SetNetworkNodesVisibilityPayload.class, SetNetworkNodesVisibilityPayload.STREAM_CODEC, ServerPayloadHandler::handleSetNetworkNodesVisibility);
        registerServer(ToggleNetworkNodeHighlightPayload.class, ToggleNetworkNodeHighlightPayload.STREAM_CODEC, ServerPayloadHandler::handleToggleNetworkNodeHighlight);
        registerServer(ToggleNetworkLabelHighlightPayload.class, ToggleNetworkLabelHighlightPayload.STREAM_CODEC, ServerPayloadHandler::handleToggleNetworkLabelHighlight);
        registerServer(SubscribeTelemetryPayload.class, SubscribeTelemetryPayload.STREAM_CODEC, ServerPayloadHandler::handleSubscribeTelemetry);
        registerServer(RequestChannelListPayload.class, RequestChannelListPayload.STREAM_CODEC, ServerPayloadHandler::handleRequestChannelList);
        registerServer(SetFilterEntryAmountPayload.class, SetFilterEntryAmountPayload.STREAM_CODEC, ServerPayloadHandler::handleSetFilterEntryAmount);
        registerServer(SetFilterEntryTagPayload.class, SetFilterEntryTagPayload.STREAM_CODEC, ServerPayloadHandler::handleSetFilterEntryTag);
        registerServer(SetFilterEntryNbtPayload.class, SetFilterEntryNbtPayload.STREAM_CODEC, ServerPayloadHandler::handleSetFilterEntryNbt);
        registerServer(SetFilterEntryDurabilityPayload.class, SetFilterEntryDurabilityPayload.STREAM_CODEC, ServerPayloadHandler::handleSetFilterEntryDurability);
        registerServer(SetFilterEntrySlotMappingPayload.class, SetFilterEntrySlotMappingPayload.STREAM_CODEC, ServerPayloadHandler::handleSetFilterEntrySlotMapping);
        registerServer(SetFilterEntryEnchantedPayload.class, SetFilterEntryEnchantedPayload.STREAM_CODEC, ServerPayloadHandler::handleSetFilterEntryEnchanted);

        registerClient(SyncNetworkListPayload.class, SyncNetworkListPayload.STREAM_CODEC, ClientPayloadHandler::handleSyncNetworkList);
        registerClient(SyncNetworkNodesPayload.class, SyncNetworkNodesPayload.STREAM_CODEC, ClientPayloadHandler::handleSyncNetworkNodes);
        registerClient(SyncNetworkLabelsPayload.class, SyncNetworkLabelsPayload.STREAM_CODEC, ClientPayloadHandler::handleSyncNetworkLabels);
        registerClient(SyncChannelDataPayload.class, SyncChannelDataPayload.STREAM_CODEC, ClientPayloadHandler::handleSyncChannelData);
        registerClient(SyncTelemetryPayload.class, SyncTelemetryPayload.STREAM_CODEC, ClientPayloadHandler::handleSyncTelemetry);
        registerClient(SyncChannelListPayload.class, SyncChannelListPayload.STREAM_CODEC, ClientPayloadHandler::handleSyncChannelList);

        // Last keeps earlier IDs stable
        registerServer(SyncModifierKeysPayload.class, SyncModifierKeysPayload.STREAM_CODEC, ServerPayloadHandler::handleSyncModifierKeys);
        registerServer(SetNetworkColorPayload.class, SetNetworkColorPayload.STREAM_CODEC, ServerPayloadHandler::handleSetNetworkColor);
        registerServer(DeleteNetworkPayload.class, DeleteNetworkPayload.STREAM_CODEC, ServerPayloadHandler::handleDeleteNetwork);
        registerServer(SetDefaultNodeVisibilityPayload.class, SetDefaultNodeVisibilityPayload.STREAM_CODEC, ServerPayloadHandler::handleSetDefaultNodeVisibility);
        registerServer(DeleteNetworkLabelPayload.class, DeleteNetworkLabelPayload.STREAM_CODEC, ServerPayloadHandler::handleDeleteNetworkLabel);
        registerServer(AddNodeFilterItemPayload.class, AddNodeFilterItemPayload.STREAM_CODEC, ServerPayloadHandler::handleAddNodeFilterItem);
        registerServer(OpenNodeMenuPayload.class, OpenNodeMenuPayload.STREAM_CODEC, ServerPayloadHandler::handleOpenNodeMenu);
        registerServer(ToggleComputerPinnedNetworkPayload.class, ToggleComputerPinnedNetworkPayload.STREAM_CODEC,
                ServerPayloadHandler::handleToggleComputerPinnedNetwork);
        registerServer(RequestOpenGraphPayload.class, RequestOpenGraphPayload.STREAM_CODEC, GraphPayloadHandler::handleOpen);
        registerServer(RequestNetworkGraphPayload.class, RequestNetworkGraphPayload.STREAM_CODEC, GraphPayloadHandler::handleRequest);
        registerServer(MoveGraphVertexPayload.class, MoveGraphVertexPayload.STREAM_CODEC, GraphPayloadHandler::handleMove);
        registerServer(MoveGraphVerticesPayload.class, MoveGraphVerticesPayload.STREAM_CODEC, GraphPayloadHandler::handleMoveVertices);
        registerServer(SetNodeLabelsPayload.class, SetNodeLabelsPayload.STREAM_CODEC, GraphPayloadHandler::handleSetLabels);
        registerServer(PreviewGraphLabelsPayload.class, PreviewGraphLabelsPayload.STREAM_CODEC, GraphPayloadHandler::handlePreviewLabels);
        registerServer(ConfirmGraphLabelsPayload.class, ConfirmGraphLabelsPayload.STREAM_CODEC, GraphPayloadHandler::handleConfirmLabels);
        registerServer(ResetGraphLayoutPayload.class, ResetGraphLayoutPayload.STREAM_CODEC, GraphPayloadHandler::handleReset);
        registerServer(ReturnToComputerPayload.class, ReturnToComputerPayload.STREAM_CODEC, GraphPayloadHandler::handleReturn);
        registerClient(SyncNetworkGraphPayload.class, SyncNetworkGraphPayload.STREAM_CODEC, ClientPayloadHandler::handleSyncNetworkGraph);
        registerClient(GraphLabelPreviewPayload.class, GraphLabelPreviewPayload.STREAM_CODEC, ClientPayloadHandler::handleGraphLabelPreview);
        registerServer(ScanAttachedStoragePayload.class, ScanAttachedStoragePayload.STREAM_CODEC, ServerPayloadHandler::handleScanAttachedStorage);
        registerClient(SyncFilterScanResultPayload.class, SyncFilterScanResultPayload.STREAM_CODEC, ClientPayloadHandler::handleSyncFilterScanResult);
        registerClient(SyncOpenFilterPayload.class, SyncOpenFilterPayload.STREAM_CODEC, ClientPayloadHandler::handleSyncOpenFilter);
        registerServer(SetClipboardSettingsPayload.class, SetClipboardSettingsPayload.STREAM_CODEC, ServerPayloadHandler::handleSetClipboardSettings);
        registerServer(AddClipboardFilterItemPayload.class, AddClipboardFilterItemPayload.STREAM_CODEC, ServerPayloadHandler::handleAddClipboardFilterItem);
        registerClient(SyncClipboardPayload.class, SyncClipboardPayload.STREAM_CODEC, ClientPayloadHandler::handleSyncClipboard);
        registerServer(RequestNetworkExportPayload.class, RequestNetworkExportPayload.STREAM_CODEC, ServerPayloadHandler::handleRequestNetworkExport);
        registerServer(SetComputerWrenchClipboardPayload.class, SetComputerWrenchClipboardPayload.STREAM_CODEC,
                ServerPayloadHandler::handleSetComputerWrenchClipboard);
        registerClient(SyncNetworkExportPayload.class, SyncNetworkExportPayload.STREAM_CODEC, ClientPayloadHandler::handleSyncNetworkExport);
        registerServer(SetWrenchModePayload.class, SetWrenchModePayload.STREAM_CODEC, SetWrenchModePayload::handle);
        registerServer(SetWrenchColorsPayload.class, SetWrenchColorsPayload.STREAM_CODEC, ServerPayloadHandler::handleSetWrenchColors);
        registerServer(SetWrenchFlowPayload.class, SetWrenchFlowPayload.STREAM_CODEC, ServerPayloadHandler::handleSetWrenchFlow);
        registerClient(SyncMassPlacementChoicesPayload.class, SyncMassPlacementChoicesPayload.STREAM_CODEC,
                ClientPayloadHandler::handleSyncMassPlacementChoices);
        registerClient(SyncMassPlacementRequirementsPayload.class, SyncMassPlacementRequirementsPayload.STREAM_CODEC,
                ClientPayloadHandler::handleSyncMassPlacementRequirements);
        registerServer(RequestStorageUpgradeCatalogPayload.class, RequestStorageUpgradeCatalogPayload.STREAM_CODEC,
                ServerPayloadHandler::handleRequestStorageUpgradeCatalog);
        registerServer(InstallStorageUpgradePayload.class, InstallStorageUpgradePayload.STREAM_CODEC,
                ServerPayloadHandler::handleInstallStorageUpgrade);
        registerClient(SyncStorageUpgradeCatalogPayload.class, SyncStorageUpgradeCatalogPayload.STREAM_CODEC,
                ClientPayloadHandler::handleSyncStorageUpgradeCatalog);
        registerClient(SyncQueuedNodePlacementPayload.class, SyncQueuedNodePlacementPayload.STREAM_CODEC,
                ClientPayloadHandler::handleSyncQueuedNodePlacement);
        registerServer(UpdateServerRackPayload.class, UpdateServerRackPayload.STREAM_CODEC, ServerPayloadHandler::handleUpdateServerRack);
        registerClient(SyncServerRackPayload.class, SyncServerRackPayload.STREAM_CODEC, ClientPayloadHandler::handleSyncServerRack);
        registerServer(SetRecipeCardPayload.class, SetRecipeCardPayload.STREAM_CODEC, ServerPayloadHandler::handleSetRecipeCard);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        if (serverboundSize(payload) > SERVERBOUND_BUDGET) {
            ClientPayloadHandler.warnTooLarge();
            return;
        }
        CHANNEL.sendToServer(payload);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    public static <T> List<List<T>> split(List<T> items, BiConsumer<FriendlyByteBuf, T> writer) {
        List<List<T>> parts = new ArrayList<>();
        List<T> part = new ArrayList<>();
        int used = 0;
        FriendlyByteBuf probe = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (T item : items) {
                probe.clear();
                writer.accept(probe, item);
                int size = probe.readableBytes();
                if (used + size > CLIENTBOUND_BUDGET && !part.isEmpty()) {
                    parts.add(part);
                    part = new ArrayList<>();
                    used = 0;
                }
                part.add(item);
                used += size;
            }
        } finally {
            probe.release();
        }
        parts.add(part);
        return parts;
    }

    public static List<byte[]> split(CompoundTag tag) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        byte[] bytes;
        try {
            buf.writeNbt(tag);
            bytes = ByteBufUtil.getBytes(buf);
        } finally {
            buf.release();
        }
        List<byte[]> parts = new ArrayList<>();
        for (int start = 0; start < bytes.length; start += CLIENTBOUND_BUDGET) {
            parts.add(Arrays.copyOfRange(bytes, start, Math.min(bytes.length, start + CLIENTBOUND_BUDGET)));
        }
        return parts;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static int serverboundSize(CustomPacketPayload payload) {
        FriendlyByteBuf probe = new FriendlyByteBuf(Unpooled.buffer());
        try {
            encode(probe, payload, (StreamCodec) SERVERBOUND_CODECS.get(payload.getClass()));
            return probe.readableBytes();
        } finally {
            probe.release();
        }
    }

    private static <T extends CustomPacketPayload> void registerServer(
            Class<T> type,
            StreamCodec<?, T> streamCodec,
            BiConsumer<T, IPayloadContext> consumer) {
        SERVERBOUND_CODECS.put(type, streamCodec);
        CHANNEL.messageBuilder(type, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder((message, buffer) -> encode(buffer, message, streamCodec))
                .decoder(buffer -> decode(buffer, streamCodec))
                .consumerMainThread((message, contextSupplier) -> consumer.accept(message, new ContextBridge(contextSupplier.get())))
                .add();
    }

    private static <T extends CustomPacketPayload> void registerClient(
            Class<T> type,
            StreamCodec<?, T> streamCodec,
            BiConsumer<T, IPayloadContext> consumer) {
        CHANNEL.messageBuilder(type, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, buffer) -> encode(buffer, message, streamCodec))
                .decoder(buffer -> decode(buffer, streamCodec))
                .consumerMainThread((message, contextSupplier) -> consumer.accept(message, new ContextBridge(contextSupplier.get())))
                .add();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static <T> void encode(FriendlyByteBuf buffer, T message, StreamCodec<?, T> streamCodec) {
        StreamCodec codec = streamCodec;
        if (buffer instanceof RegistryFriendlyByteBuf registryBuffer) {
            codec.encode(registryBuffer, message);
        } else {
            codec.encode(new RegistryFriendlyByteBuf(buffer), message);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static <T> T decode(FriendlyByteBuf buffer, StreamCodec<?, T> streamCodec) {
        StreamCodec codec = streamCodec;
        if (buffer instanceof RegistryFriendlyByteBuf registryBuffer) {
            return (T) codec.decode(registryBuffer);
        }
        return (T) codec.decode(new RegistryFriendlyByteBuf(buffer));
    }

    private record ContextBridge(NetworkEvent.Context context) implements IPayloadContext {
        @Override
        public Player player() {
            return context.getSender();
        }

        @Override
        public void enqueueWork(Runnable work) {
            context.enqueueWork(() -> {
                // Inventory already saved on disconnect
                ServerPlayer sender = context.getSender();
                if (sender != null && sender.hasDisconnected()) return;
                work.run();
            });
        }
    }
}
