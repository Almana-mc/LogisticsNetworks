package me.almana.logisticsnetworks;

import me.almana.logisticsnetworks.network.AssignNetworkPayload;
import me.almana.logisticsnetworks.network.ClientPayloadHandler;
import me.almana.logisticsnetworks.network.CopyPasteConnectedPayload;
import me.almana.logisticsnetworks.network.CycleWrenchModePayload;
import me.almana.logisticsnetworks.network.ModifyFilterModPayload;
import me.almana.logisticsnetworks.network.MassSelectConnectedPayload;
import me.almana.logisticsnetworks.network.SelectNodeChannelPayload;
import me.almana.logisticsnetworks.network.ServerPayloadHandler;
import me.almana.logisticsnetworks.network.SetChannelFilterItemPayload;
import me.almana.logisticsnetworks.network.AddNodeFilterItemPayload;
import me.almana.logisticsnetworks.network.SetFilterEntryAmountPayload;
import me.almana.logisticsnetworks.network.SetFilterEntryDurabilityPayload;
import me.almana.logisticsnetworks.network.SetFilterEntryEnchantedPayload;
import me.almana.logisticsnetworks.network.SetFilterEntryNbtPayload;
import me.almana.logisticsnetworks.network.SetFilterEntrySlotMappingPayload;
import me.almana.logisticsnetworks.network.SetFilterEntryTagPayload;
import me.almana.logisticsnetworks.network.SetDefaultNodeVisibilityPayload;
import me.almana.logisticsnetworks.network.SetChannelNamePayload;
import me.almana.logisticsnetworks.network.OpenNodeMenuPayload;
import me.almana.logisticsnetworks.network.OpenNodeFilterPayload;
import me.almana.logisticsnetworks.network.SetFilterFluidEntryPayload;
import me.almana.logisticsnetworks.network.SetFilterItemEntryPayload;
import me.almana.logisticsnetworks.network.SetFilterChemicalEntryPayload;
import me.almana.logisticsnetworks.network.SetFilterPayload;
import me.almana.logisticsnetworks.network.SetNodeUpgradeItemPayload;
import me.almana.logisticsnetworks.network.SetWrenchColorsPayload;
import me.almana.logisticsnetworks.network.ApplyPatternPayload;
import me.almana.logisticsnetworks.network.RenameNetworkPayload;
import me.almana.logisticsnetworks.network.SetNetworkColorPayload;
import me.almana.logisticsnetworks.network.RequestChannelListPayload;
import me.almana.logisticsnetworks.network.RequestNetworkExportPayload;
import me.almana.logisticsnetworks.network.RequestNetworkLabelsPayload;
import me.almana.logisticsnetworks.network.RequestNetworkNodesPayload;
import me.almana.logisticsnetworks.network.RequestOpenNodeSettingsPayload;
import me.almana.logisticsnetworks.network.SetComputerWrenchClipboardPayload;
import me.almana.logisticsnetworks.network.SetNetworkNodesVisibilityPayload;
import me.almana.logisticsnetworks.network.SetNodeLabelPayload;
import me.almana.logisticsnetworks.network.OpenFilterInSlotPayload;
import me.almana.logisticsnetworks.network.SetNameFilterPayload;
import me.almana.logisticsnetworks.network.SubscribeTelemetryPayload;
import me.almana.logisticsnetworks.network.SyncNetworkListPayload;
import me.almana.logisticsnetworks.network.SyncChannelDataPayload;
import me.almana.logisticsnetworks.network.SyncChannelListPayload;
import me.almana.logisticsnetworks.network.SyncMassPlacementChoicesPayload;
import me.almana.logisticsnetworks.network.SyncNetworkExportPayload;
import me.almana.logisticsnetworks.network.SyncTelemetryPayload;
import me.almana.logisticsnetworks.network.SyncNetworkLabelsPayload;
import me.almana.logisticsnetworks.network.SyncNetworkNodesPayload;
import me.almana.logisticsnetworks.network.ToggleNodeVisibilityPayload;
import me.almana.logisticsnetworks.network.ToggleComputerPinnedNetworkPayload;
import me.almana.logisticsnetworks.network.ToggleNetworkLabelHighlightPayload;
import me.almana.logisticsnetworks.network.ToggleNetworkNodeHighlightPayload;
import me.almana.logisticsnetworks.network.TransferVisualPayload;
import me.almana.logisticsnetworks.network.UpdateChannelPayload;
import me.almana.logisticsnetworks.client.ConfigScreenRegistrar;
import me.almana.logisticsnetworks.datagen.ModDataGenerators;
import me.almana.logisticsnetworks.integration.ae2.AE2Compat;
import me.almana.logisticsnetworks.registration.Registration;
import me.almana.logisticsnetworks.upgrade.UpgradeLimitsConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(LogisticsNetworks.MOD_ID)
public class LogisticsNetworks {

        public static final String MOD_ID = "logisticsnetworks";

        public LogisticsNetworks(IEventBus modBus) {
                Registration.init(modBus);
                modBus.addListener(ModDataGenerators::gather);
                modBus.addListener(ModDataGenerators::gatherServer);
                modBus.addListener(this::registerPayloads);
                modBus.addListener(this::commonSetup);
                if (FMLEnvironment.getDist() == Dist.CLIENT) {
                        modBus.addListener(LogisticsClientEvents::registerRenderers);
                        modBus.addListener(LogisticsClientEvents::registerScreens);
                        modBus.addListener(LogisticsClientEvents::clientSetup);
                        modBus.addListener(LogisticsClientEvents::registerKeyMappings);
                        ConfigScreenRegistrar.register();
                }

                ModLoadingContext.get().getActiveContainer()
                                .registerConfig(ModConfig.Type.COMMON, Config.SPEC, "logistics-network/common.toml");
                ModLoadingContext.get().getActiveContainer()
                                .registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC,
                                                "logistics-network/client.toml");
                UpgradeLimitsConfig.load();
        }

        private void commonSetup(FMLCommonSetupEvent event) {
                event.enqueueWork(AE2Compat::registerLinkable);
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
                registrar.playToServer(AddNodeFilterItemPayload.TYPE, AddNodeFilterItemPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleAddNodeFilterItem);
                registrar.playToServer(SetNodeUpgradeItemPayload.TYPE, SetNodeUpgradeItemPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleSetNodeUpgradeItem);
                registrar.playToServer(SelectNodeChannelPayload.TYPE, SelectNodeChannelPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleSelectNodeChannel);
                registrar.playToServer(ModifyFilterModPayload.TYPE, ModifyFilterModPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleModifyFilterMod);
                registrar.playToServer(SetFilterEntryAmountPayload.TYPE, SetFilterEntryAmountPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleSetFilterEntryAmount);
                registrar.playToServer(SetFilterEntryEnchantedPayload.TYPE, SetFilterEntryEnchantedPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleSetFilterEntryEnchanted);
                registrar.playToServer(SetFilterEntrySlotMappingPayload.TYPE,
                                SetFilterEntrySlotMappingPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleSetFilterEntrySlotMapping);
                registrar.playToServer(SetChannelNamePayload.TYPE, SetChannelNamePayload.STREAM_CODEC,
                                ServerPayloadHandler::handleSetChannelName);
                registrar.playToServer(OpenNodeFilterPayload.TYPE, OpenNodeFilterPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleOpenNodeFilter);
                registrar.playToServer(OpenNodeMenuPayload.TYPE, OpenNodeMenuPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleOpenNodeMenu);
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
                                ServerPayloadHandler::handleSetFilterChemicalEntry);
                registrar.playToServer(SetFilterItemEntryPayload.TYPE, SetFilterItemEntryPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleSetFilterItemEntry);
                registrar.playToServer(SetNameFilterPayload.TYPE, SetNameFilterPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleSetNameFilter);
                registrar.playToServer(OpenFilterInSlotPayload.TYPE, OpenFilterInSlotPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleOpenFilterInSlot);
                registrar.playToServer(ToggleNodeVisibilityPayload.TYPE, ToggleNodeVisibilityPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleToggleVisibility);
                registrar.playToServer(SetDefaultNodeVisibilityPayload.TYPE,
                                SetDefaultNodeVisibilityPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleSetDefaultNodeVisibility);
                registrar.playToServer(CycleWrenchModePayload.TYPE, CycleWrenchModePayload.STREAM_CODEC,
                                ServerPayloadHandler::handleCycleWrenchMode);
                registrar.playToServer(SetWrenchColorsPayload.TYPE, SetWrenchColorsPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleSetWrenchColors);
                registrar.playToServer(MassSelectConnectedPayload.TYPE, MassSelectConnectedPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleMassSelectConnected);
                registrar.playToServer(CopyPasteConnectedPayload.TYPE, CopyPasteConnectedPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleCopyPasteConnected);
                registrar.playToServer(RenameNetworkPayload.TYPE, RenameNetworkPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleRenameNetwork);
                registrar.playToServer(SetNetworkColorPayload.TYPE, SetNetworkColorPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleSetNetworkColor);
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
                registrar.playToServer(ToggleComputerPinnedNetworkPayload.TYPE,
                                ToggleComputerPinnedNetworkPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleToggleComputerPinnedNetwork);
                registrar.playToServer(RequestNetworkExportPayload.TYPE,
                                RequestNetworkExportPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleRequestNetworkExport);
                registrar.playToServer(SetComputerWrenchClipboardPayload.TYPE,
                                SetComputerWrenchClipboardPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleSetComputerWrenchClipboard);

                // Server -> Client
                registrar.playToClient(SyncMassPlacementChoicesPayload.TYPE,
                                SyncMassPlacementChoicesPayload.STREAM_CODEC,
                                ClientPayloadHandler::handleSyncMassPlacementChoices);
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
                registrar.playToClient(SyncNetworkExportPayload.TYPE, SyncNetworkExportPayload.STREAM_CODEC,
                                ClientPayloadHandler::handleSyncNetworkExport);
                registrar.playToClient(TransferVisualPayload.TYPE, TransferVisualPayload.STREAM_CODEC,
                                ClientPayloadHandler::handleTransferVisual);
        }
}
