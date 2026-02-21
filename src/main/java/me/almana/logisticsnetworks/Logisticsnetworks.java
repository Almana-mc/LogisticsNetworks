package me.almana.logisticsnetworks;

import me.almana.logisticsnetworks.network.AssignNetworkPayload;
import me.almana.logisticsnetworks.network.ClientPayloadHandler;
import me.almana.logisticsnetworks.network.CycleWrenchModePayload;
import me.almana.logisticsnetworks.network.ModifyFilterModPayload;
import me.almana.logisticsnetworks.network.ModifyFilterNbtPayload;
import me.almana.logisticsnetworks.network.ModifyFilterTagPayload;
import me.almana.logisticsnetworks.network.SelectNodeChannelPayload;
import me.almana.logisticsnetworks.network.ServerPayloadHandler;
import me.almana.logisticsnetworks.network.SetAmountFilterValuePayload;
import me.almana.logisticsnetworks.network.SetChannelFilterItemPayload;
import me.almana.logisticsnetworks.network.SetDurabilityFilterValuePayload;
import me.almana.logisticsnetworks.network.SetFilterFluidEntryPayload;
import me.almana.logisticsnetworks.network.SetFilterItemEntryPayload;
import me.almana.logisticsnetworks.network.SetFilterChemicalEntryPayload;
import me.almana.logisticsnetworks.network.SetFilterPayload;
import me.almana.logisticsnetworks.network.SetNodeUpgradeItemPayload;
import me.almana.logisticsnetworks.network.SetSlotFilterSlotsPayload;
import me.almana.logisticsnetworks.network.SyncNetworkListPayload;
import me.almana.logisticsnetworks.network.ToggleNodeVisibilityPayload;
import me.almana.logisticsnetworks.network.UpdateChannelPayload;
import me.almana.logisticsnetworks.registration.Registration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(Logisticsnetworks.MOD_ID)
public class Logisticsnetworks {

        public static final String MOD_ID = "logisticsnetworks";

        public Logisticsnetworks(IEventBus modBus) {
                Registration.init(modBus);
                modBus.addListener(this::registerPayloads);

                ModLoadingContext.get().getActiveContainer()
                                .registerConfig(ModConfig.Type.COMMON, Config.SPEC);
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
                registrar.playToServer(ToggleNodeVisibilityPayload.TYPE, ToggleNodeVisibilityPayload.STREAM_CODEC,
                                ServerPayloadHandler::handleToggleVisibility);
                registrar.playToServer(CycleWrenchModePayload.TYPE, CycleWrenchModePayload.STREAM_CODEC,
                                ServerPayloadHandler::handleCycleWrenchMode);

                // Server -> Client
                registrar.playToClient(SyncNetworkListPayload.TYPE, SyncNetworkListPayload.STREAM_CODEC,
                                ClientPayloadHandler::handleSyncNetworkList);
        }
}
