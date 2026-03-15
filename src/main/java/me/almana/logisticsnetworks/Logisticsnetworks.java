package me.almana.logisticsnetworks;

import me.almana.logisticsnetworks.network.AssignNetworkPayload;
import me.almana.logisticsnetworks.network.ClientPayloadHandler;
import me.almana.logisticsnetworks.network.CopyPasteConnectedPayload;
import me.almana.logisticsnetworks.network.CycleWrenchModePayload;
import me.almana.logisticsnetworks.network.ModifyFilterModPayload;
import me.almana.logisticsnetworks.network.ModifyFilterNbtPayload;
import me.almana.logisticsnetworks.network.ModifyFilterTagPayload;
import me.almana.logisticsnetworks.network.MassSelectConnectedPayload;
import me.almana.logisticsnetworks.network.SelectNodeChannelPayload;
import me.almana.logisticsnetworks.network.ServerPayloadHandler;
import me.almana.logisticsnetworks.network.SetAmountFilterValuePayload;
import me.almana.logisticsnetworks.network.SetChannelFilterItemPayload;
import me.almana.logisticsnetworks.network.SetDurabilityFilterValuePayload;
import me.almana.logisticsnetworks.network.SetFilterEntryAmountPayload;
import me.almana.logisticsnetworks.network.SetFilterEntryDurabilityPayload;
import me.almana.logisticsnetworks.network.SetFilterEntryNbtPayload;
import me.almana.logisticsnetworks.network.SetFilterEntryTagPayload;
import me.almana.logisticsnetworks.network.SetFilterFluidEntryPayload;
import me.almana.logisticsnetworks.network.SetFilterItemEntryPayload;
import me.almana.logisticsnetworks.network.SetFilterChemicalEntryPayload;
import me.almana.logisticsnetworks.network.SetFilterPayload;
import me.almana.logisticsnetworks.network.SetNodeUpgradeItemPayload;
import me.almana.logisticsnetworks.network.ApplyPatternPayload;
import me.almana.logisticsnetworks.network.RenameNetworkPayload;
import me.almana.logisticsnetworks.network.RequestChannelListPayload;
import me.almana.logisticsnetworks.network.RequestNetworkLabelsPayload;
import me.almana.logisticsnetworks.network.RequestNetworkNodesPayload;
import me.almana.logisticsnetworks.network.RequestOpenNodeSettingsPayload;
import me.almana.logisticsnetworks.network.SetNetworkNodesVisibilityPayload;
import me.almana.logisticsnetworks.network.SetNodeLabelPayload;
import me.almana.logisticsnetworks.network.OpenFilterInSlotPayload;
import me.almana.logisticsnetworks.network.SetNameFilterPayload;
import me.almana.logisticsnetworks.network.SetSlotFilterSlotsPayload;
import me.almana.logisticsnetworks.network.SubscribeTelemetryPayload;
import me.almana.logisticsnetworks.network.SyncNetworkListPayload;
import me.almana.logisticsnetworks.network.SyncChannelDataPayload;
import me.almana.logisticsnetworks.network.SyncChannelListPayload;
import me.almana.logisticsnetworks.network.SyncTelemetryPayload;
import me.almana.logisticsnetworks.network.SyncNetworkLabelsPayload;
import me.almana.logisticsnetworks.network.SyncNetworkNodesPayload;
import me.almana.logisticsnetworks.network.ToggleNodeVisibilityPayload;
import me.almana.logisticsnetworks.network.ToggleNetworkLabelHighlightPayload;
import me.almana.logisticsnetworks.network.ToggleNetworkNodeHighlightPayload;
import me.almana.logisticsnetworks.network.UpdateChannelPayload;
import me.almana.logisticsnetworks.registration.Registration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import me.almana.logisticsnetworks.upgrade.UpgradeLimitsConfig;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(Logisticsnetworks.MOD_ID)
public class Logisticsnetworks {

        public static final String MOD_ID = "logisticsnetworks";

        public Logisticsnetworks(IEventBus modBus) {
                Registration.init(modBus);
                modBus.addListener(this::registerPayloads);

                ModLoadingContext.get().getActiveContainer()
                                .registerConfig(ModConfig.Type.COMMON, Config.SPEC, "logistics-network/common.toml");
                ModLoadingContext.get().getActiveContainer()
                                .registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC,
                                                "logistics-network/client.toml");
                UpgradeLimitsConfig.load();
        }

        private void registerPayloads(final RegisterPayloadHandlersEvent event) {
                final var registrar = event.registrar(MOD_ID).versioned("1");

                // Client -> Server
                registrar.playToServer(UpdateChannelPayload.TYPE, UpdateChannelPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleUpdateChannel);
                registrar.playToServer(AssignNetworkPayload.TYPE, AssignNetworkPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleAssignNetwork);
                registrar.playToServer(SetFilterPayload.TYPE, SetFilterPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleSetFilter);
                registrar.playToServer(SetChannelFilterItemPayload.TYPE, SetChannelFilterItemPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleSetChannelFilterItem);
                registrar.playToServer(SetNodeUpgradeItemPayload.TYPE, SetNodeUpgradeItemPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleSetNodeUpgradeItem);
                registrar.playToServer(SelectNodeChannelPayload.TYPE, SelectNodeChannelPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleSelectNodeChannel);
                registrar.playToServer(ModifyFilterTagPayload.TYPE, ModifyFilterTagPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleModifyFilterTag);
                registrar.playToServer(ModifyFilterModPayload.TYPE, ModifyFilterModPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleModifyFilterMod);
                registrar.playToServer(ModifyFilterNbtPayload.TYPE, ModifyFilterNbtPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleModifyFilterNbt);
                registrar.playToServer(SetAmountFilterValuePayload.TYPE, SetAmountFilterValuePayload.STREAM_CODEC,
                                ServerPayloadHandler::handleSetAmountFilterValue);
                registrar.playToServer(SetFilterEntryAmountPayload.TYPE, SetFilterEntryAmountPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleSetFilterEntryAmount);
                registrar.playToServer(SetFilterEntryTagPayload.TYPE, SetFilterEntryTagPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleSetFilterEntryTag);
                registrar.playToServer(SetFilterEntryNbtPayload.TYPE, SetFilterEntryNbtPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleSetFilterEntryNbt);
                registrar.playToServer(SetFilterEntryDurabilityPayload.TYPE,
                                SetFilterEntryDurabilityPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleSetFilterEntryDurability);
                registrar.playToServer(SetFilterFluidEntryPayload.TYPE, SetFilterFluidEntryPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleSetFilterFluidEntry);
                registrar.playToServer(SetFilterChemicalEntryPayload.TYPE, SetFilterChemicalEntryPayload.STREAM_CODEC,
                                (payload, context) -> ServerPayloadHandler.handleSetFilterChemicalEntry(payload,
                                                context));
                registrar.playToServer(SetFilterItemEntryPayload.TYPE, SetFilterItemEntryPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleSetFilterItemEntry);
                registrar.playToServer(SetDurabilityFilterValuePayload.TYPE,
                                SetDurabilityFilterValuePayload.STREAM_CODEC,
                                ServerPayloadHandler::handleSetDurabilityFilterValue);
                registrar.playToServer(SetSlotFilterSlotsPayload.TYPE, SetSlotFilterSlotsPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleSetSlotFilterSlots);
                registrar.playToServer(SetNameFilterPayload.TYPE, SetNameFilterPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleSetNameFilter);
                registrar.playToServer(OpenFilterInSlotPayload.TYPE, OpenFilterInSlotPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleOpenFilterInSlot);
                registrar.playToServer(ToggleNodeVisibilityPayload.TYPE, ToggleNodeVisibilityPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleToggleVisibility);
                registrar.playToServer(CycleWrenchModePayload.TYPE, CycleWrenchModePayload.STREAM_CODEC,
                                ServerPayloadHandler::handleCycleWrenchMode);
                registrar.playToServer(MassSelectConnectedPayload.TYPE, MassSelectConnectedPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleMassSelectConnected);
                registrar.playToServer(CopyPasteConnectedPayload.TYPE, CopyPasteConnectedPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleCopyPasteConnected);
                registrar.playToServer(RenameNetworkPayload.TYPE, RenameNetworkPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleRenameNetwork);
                registrar.playToServer(ApplyPatternPayload.TYPE, ApplyPatternPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleApplyPattern);
                registrar.playToServer(RequestNetworkNodesPayload.TYPE, RequestNetworkNodesPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleRequestNetworkNodes);
                registrar.playToServer(SetNodeLabelPayload.TYPE, SetNodeLabelPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleSetNodeLabel);
                registrar.playToServer(RequestNetworkLabelsPayload.TYPE, RequestNetworkLabelsPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleRequestNetworkLabels);
                registrar.playToServer(SetNetworkNodesVisibilityPayload.TYPE,
                                SetNetworkNodesVisibilityPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleSetNetworkNodesVisibility);
                registrar.playToServer(ToggleNetworkNodeHighlightPayload.TYPE,
                                ToggleNetworkNodeHighlightPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleToggleNetworkNodeHighlight);
                registrar.playToServer(ToggleNetworkLabelHighlightPayload.TYPE,
                                ToggleNetworkLabelHighlightPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleToggleNetworkLabelHighlight);
                registrar.playToServer(RequestOpenNodeSettingsPayload.TYPE,
                                RequestOpenNodeSettingsPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleRequestOpenNodeSettings);
                registrar.playToServer(SubscribeTelemetryPayload.TYPE,
                                SubscribeTelemetryPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleSubscribeTelemetry);
                registrar.playToServer(RequestChannelListPayload.TYPE,
                                RequestChannelListPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleRequestChannelList);

                // Server -> Client
                registrar.playToClient(SyncNetworkListPayload.TYPE, SyncNetworkListPayload.STREAM_CODEC,
                                ClientPayloadHandler::handleSyncNetworkList);
                registrar.playToClient(SyncNetworkNodesPayload.TYPE, SyncNetworkNodesPayload.STREAM_CODEC,
                                ClientPayloadHandler::handleSyncNetworkNodes);
                registrar.playToClient(SyncNetworkLabelsPayload.TYPE, SyncNetworkLabelsPayload.STREAM_CODEC,
                                ClientPayloadHandler::handleSyncNetworkLabels);
                registrar.playToClient(SyncChannelDataPayload.TYPE, SyncChannelDataPayload.STREAM_CODEC,
                                ClientPayloadHandler::handleSyncChannelData);
                registrar.playToClient(SyncTelemetryPayload.TYPE, SyncTelemetryPayload.STREAM_CODEC,
                                ClientPayloadHandler::handleSyncTelemetry);
                registrar.playToClient(SyncChannelListPayload.TYPE, SyncChannelListPayload.STREAM_CODEC,
                                ClientPayloadHandler::handleSyncChannelList);
        }
}
