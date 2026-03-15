package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.Logisticsnetworks;
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

import java.util.function.BiConsumer;

public final class NetworkHandler {

    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Logisticsnetworks.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static int nextId = 0;

    private NetworkHandler() {
    }

    public static void register() {
        registerServer(UpdateChannelPayload.class, UpdateChannelPayload.STREAM_CODEC, ServerPayloadHandler::handleUpdateChannel);
        registerServer(AssignNetworkPayload.class, AssignNetworkPayload.STREAM_CODEC, ServerPayloadHandler::handleAssignNetwork);
        registerServer(SetFilterPayload.class, SetFilterPayload.STREAM_CODEC, ServerPayloadHandler::handleSetFilter);
        registerServer(SetChannelFilterItemPayload.class, SetChannelFilterItemPayload.STREAM_CODEC, ServerPayloadHandler::handleSetChannelFilterItem);
        registerServer(SetNodeUpgradeItemPayload.class, SetNodeUpgradeItemPayload.STREAM_CODEC, ServerPayloadHandler::handleSetNodeUpgradeItem);
        registerServer(SelectNodeChannelPayload.class, SelectNodeChannelPayload.STREAM_CODEC, ServerPayloadHandler::handleSelectNodeChannel);
        registerServer(ModifyFilterTagPayload.class, ModifyFilterTagPayload.STREAM_CODEC, ServerPayloadHandler::handleModifyFilterTag);
        registerServer(ModifyFilterModPayload.class, ModifyFilterModPayload.STREAM_CODEC, ServerPayloadHandler::handleModifyFilterMod);
        registerServer(ModifyFilterNbtPayload.class, ModifyFilterNbtPayload.STREAM_CODEC, ServerPayloadHandler::handleModifyFilterNbt);
        registerServer(SetAmountFilterValuePayload.class, SetAmountFilterValuePayload.STREAM_CODEC, ServerPayloadHandler::handleSetAmountFilterValue);
        registerServer(SetFilterFluidEntryPayload.class, SetFilterFluidEntryPayload.STREAM_CODEC, ServerPayloadHandler::handleSetFilterFluidEntry);
        registerServer(SetFilterChemicalEntryPayload.class, SetFilterChemicalEntryPayload.STREAM_CODEC, ServerPayloadHandler::handleSetFilterChemicalEntry);
        registerServer(SetFilterItemEntryPayload.class, SetFilterItemEntryPayload.STREAM_CODEC, ServerPayloadHandler::handleSetFilterItemEntry);
        registerServer(SetDurabilityFilterValuePayload.class, SetDurabilityFilterValuePayload.STREAM_CODEC, ServerPayloadHandler::handleSetDurabilityFilterValue);
        registerServer(SetSlotFilterSlotsPayload.class, SetSlotFilterSlotsPayload.STREAM_CODEC, ServerPayloadHandler::handleSetSlotFilterSlots);
        registerServer(SetNameFilterPayload.class, SetNameFilterPayload.STREAM_CODEC, ServerPayloadHandler::handleSetNameFilter);
        registerServer(ToggleNodeVisibilityPayload.class, ToggleNodeVisibilityPayload.STREAM_CODEC, ServerPayloadHandler::handleToggleVisibility);
        registerServer(CycleWrenchModePayload.class, CycleWrenchModePayload.STREAM_CODEC, ServerPayloadHandler::handleCycleWrenchMode);
        registerServer(MassSelectConnectedPayload.class, MassSelectConnectedPayload.STREAM_CODEC, ServerPayloadHandler::handleMassSelectConnected);
        registerServer(CopyPasteConnectedPayload.class, CopyPasteConnectedPayload.STREAM_CODEC, ServerPayloadHandler::handleCopyPasteConnected);
        registerServer(OpenFilterInSlotPayload.class, OpenFilterInSlotPayload.STREAM_CODEC, ServerPayloadHandler::handleOpenFilterInSlot);
        registerServer(RenameNetworkPayload.class, RenameNetworkPayload.STREAM_CODEC, ServerPayloadHandler::handleRenameNetwork);
        registerServer(ApplyPatternPayload.class, ApplyPatternPayload.STREAM_CODEC, ServerPayloadHandler::handleApplyPattern);
        registerServer(RequestNetworkNodesPayload.class, RequestNetworkNodesPayload.STREAM_CODEC, ServerPayloadHandler::handleRequestNetworkNodes);
        registerServer(RequestNetworkLabelsPayload.class, RequestNetworkLabelsPayload.STREAM_CODEC, ServerPayloadHandler::handleRequestNetworkLabels);
        registerServer(RequestOpenNodeSettingsPayload.class, RequestOpenNodeSettingsPayload.STREAM_CODEC, ServerPayloadHandler::handleRequestOpenNodeSettings);
        registerServer(SetNodeLabelPayload.class, SetNodeLabelPayload.STREAM_CODEC, ServerPayloadHandler::handleSetNodeLabel);
        registerServer(SetNetworkNodesVisibilityPayload.class, SetNetworkNodesVisibilityPayload.STREAM_CODEC, ServerPayloadHandler::handleSetNetworkNodesVisibility);
        registerServer(ToggleNetworkNodeHighlightPayload.class, ToggleNetworkNodeHighlightPayload.STREAM_CODEC, ServerPayloadHandler::handleToggleNetworkNodeHighlight);
        registerServer(ToggleNetworkLabelHighlightPayload.class, ToggleNetworkLabelHighlightPayload.STREAM_CODEC, ServerPayloadHandler::handleToggleNetworkLabelHighlight);
        registerServer(SubscribeTelemetryPayload.class, SubscribeTelemetryPayload.STREAM_CODEC, ServerPayloadHandler::handleSubscribeTelemetry);
        registerServer(RequestChannelListPayload.class, RequestChannelListPayload.STREAM_CODEC, ServerPayloadHandler::handleRequestChannelList);
        registerServer(SetFilterEntryAmountPayload.class, SetFilterEntryAmountPayload.STREAM_CODEC, ServerPayloadHandler::handleSetFilterEntryAmount);
        registerServer(SetFilterEntryTagPayload.class, SetFilterEntryTagPayload.STREAM_CODEC, ServerPayloadHandler::handleSetFilterEntryTag);
        registerServer(SetFilterEntryNbtPayload.class, SetFilterEntryNbtPayload.STREAM_CODEC, ServerPayloadHandler::handleSetFilterEntryNbt);
        registerServer(SetFilterEntryDurabilityPayload.class, SetFilterEntryDurabilityPayload.STREAM_CODEC, ServerPayloadHandler::handleSetFilterEntryDurability);

        registerClient(SyncNetworkListPayload.class, SyncNetworkListPayload.STREAM_CODEC, ClientPayloadHandler::handleSyncNetworkList);
        registerClient(SyncNetworkNodesPayload.class, SyncNetworkNodesPayload.STREAM_CODEC, ClientPayloadHandler::handleSyncNetworkNodes);
        registerClient(SyncNetworkLabelsPayload.class, SyncNetworkLabelsPayload.STREAM_CODEC, ClientPayloadHandler::handleSyncNetworkLabels);
        registerClient(SyncChannelDataPayload.class, SyncChannelDataPayload.STREAM_CODEC, ClientPayloadHandler::handleSyncChannelData);
        registerClient(SyncTelemetryPayload.class, SyncTelemetryPayload.STREAM_CODEC, ClientPayloadHandler::handleSyncTelemetry);
        registerClient(SyncChannelListPayload.class, SyncChannelListPayload.STREAM_CODEC, ClientPayloadHandler::handleSyncChannelList);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        CHANNEL.sendToServer(payload);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    private static <T extends CustomPacketPayload> void registerServer(
            Class<T> type,
            StreamCodec<?, T> streamCodec,
            BiConsumer<T, IPayloadContext> consumer) {
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
            context.enqueueWork(work);
        }
    }
}
