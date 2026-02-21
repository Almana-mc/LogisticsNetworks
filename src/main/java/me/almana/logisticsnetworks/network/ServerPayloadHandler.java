package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.data.*;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.filter.*;
import me.almana.logisticsnetworks.item.WrenchItem;
import me.almana.logisticsnetworks.menu.FilterMenu;
import me.almana.logisticsnetworks.menu.NodeMenu;
import me.almana.logisticsnetworks.registration.ModTags;
import me.almana.logisticsnetworks.upgrade.NodeUpgradeData;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import me.almana.logisticsnetworks.network.SetFilterChemicalEntryPayload;

public class ServerPayloadHandler {

    public static void handleUpdateChannel(UpdateChannelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LogisticsNodeEntity node = getNode(context, payload.entityId());
            if (node == null)
                return;

            ChannelData channel = node.getChannel(payload.channelIndex());
            if (channel == null)
                return;

            updateChannelData(channel, payload);
            clampChannelToUpgradeLimits(node, channel);
            markNetworkDirty(node);
        });
    }

    private static void updateChannelData(ChannelData channel, UpdateChannelPayload payload) {
        channel.setEnabled(payload.enabled());

        if (isValidEnum(payload.modeOrdinal(), ChannelMode.values()))
            channel.setMode(ChannelMode.values()[payload.modeOrdinal()]);

        if (isValidEnum(payload.typeOrdinal(), ChannelType.values()))
            channel.setType(ChannelType.values()[payload.typeOrdinal()]);

        channel.setBatchSize(payload.batchSize());
        channel.setTickDelay(payload.tickDelay());

        if (isValidEnum(payload.directionOrdinal(), Direction.values()))
            channel.setIoDirection(Direction.values()[payload.directionOrdinal()]);

        if (isValidEnum(payload.redstoneModeOrdinal(), RedstoneMode.values()))
            channel.setRedstoneMode(RedstoneMode.values()[payload.redstoneModeOrdinal()]);

        if (isValidEnum(payload.distributionModeOrdinal(), DistributionMode.values()))
            channel.setDistributionMode(DistributionMode.values()[payload.distributionModeOrdinal()]);

        if (isValidEnum(payload.filterModeOrdinal(), FilterMode.values()))
            channel.setFilterMode(FilterMode.values()[payload.filterModeOrdinal()]);

        channel.setPriority(payload.priority());
    }

    private static <T extends Enum<T>> boolean isValidEnum(int ordinal, T[] values) {
        return ordinal >= 0 && ordinal < values.length;
    }

    public static void handleAssignNetwork(AssignNetworkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            LogisticsNodeEntity node = getNode(context, payload.entityId());
            if (node == null)
                return;

            NetworkRegistry registry = NetworkRegistry.get(player.serverLevel());
            if (node.getNetworkId() != null) {
                registry.removeNodeFromNetwork(node.getNetworkId(), node.getUUID());
            }

            LogisticsNetwork targetNetwork = resolveNetwork(registry, payload);
            if (targetNetwork == null)
                return;

            node.setNetworkId(targetNetwork.getId());
            node.setNetworkName(targetNetwork.getName());
            registry.addNodeToNetwork(targetNetwork.getId(), node.getUUID());

            if (NodeUpgradeData.needsDimensionalUpgradeWarning(node, targetNetwork, player.getServer())) {
                player.sendSystemMessage(Component.translatable("gui.logisticsnetworks.dimensional_upgrade_warning"));
            }

            if (player.containerMenu instanceof NodeMenu menu) {
                menu.sendNetworkListToClient(player);
            }
        });
    }

    private static LogisticsNetwork resolveNetwork(NetworkRegistry registry, AssignNetworkPayload payload) {
        if (payload.networkId().isPresent()) {
            return registry.getNetwork(payload.networkId().get());
        } else {
            String name = payload.newNetworkName().trim();
            return registry.createNetwork(name.isEmpty() ? "Unnamed" : name);
        }
    }

    public static void handleToggleVisibility(ToggleNodeVisibilityPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LogisticsNodeEntity node = getNode(context, payload.entityId());
            if (node != null)
                node.setRenderVisible(!node.isRenderVisible());
        });
    }

    public static void handleCycleWrenchMode(CycleWrenchModePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            InteractionHand hand = payload.handOrdinal() == InteractionHand.OFF_HAND.ordinal()
                    ? InteractionHand.OFF_HAND
                    : InteractionHand.MAIN_HAND;

            ItemStack heldStack = player.getItemInHand(hand);
            if (!(heldStack.getItem() instanceof WrenchItem)) {
                return;
            }

            WrenchItem.Mode mode = WrenchItem.cycleMode(heldStack, payload.forward());
            player.getInventory().setChanged();
            player.displayClientMessage(WrenchItem.getModeChangedMessage(mode), true);
        });
    }

    public static void handleSetFilter(SetFilterPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LogisticsNodeEntity node = getNode(context, payload.entityId());
            if (node == null)
                return;
            ChannelData channel = node.getChannel(payload.channelIndex());
            if (channel != null) {
                channel.setFilterItem(payload.filterSlot(), payload.filterItem().copyWithCount(1));
            }
        });
    }

    public static void handleSetChannelFilterItem(SetChannelFilterItemPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LogisticsNodeEntity node = getNode(context, payload.entityId());
            if (node == null)
                return;
            ChannelData channel = node.getChannel(payload.channelIndex());
            if (channel == null)
                return;

            channel.setFilterItem(payload.filterSlot(),
                    payload.filterItem().is(ModTags.FILTERS) ? payload.filterItem().copyWithCount(1) : ItemStack.EMPTY);
            markNetworkDirty(node);
        });
    }

    public static void handleSetNodeUpgradeItem(SetNodeUpgradeItemPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LogisticsNodeEntity node = getNode(context, payload.entityId());
            if (node == null)
                return;

            node.setUpgradeItem(payload.upgradeSlot(), payload.upgradeItem());

            for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
                ChannelData channel = node.getChannel(i);
                if (channel != null)
                    setChannelToUpgradeMax(node, channel);
            }
            markNetworkDirty(node);
        });
    }

    public static void handleSelectNodeChannel(SelectNodeChannelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof NodeMenu menu
                    && menu.getNode() != null
                    && menu.getNode().getId() == payload.entityId()) {
                menu.setSelectedChannel(payload.channelIndex());
            }
        });
    }

    public static void handleModifyFilterTag(ModifyFilterTagPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = (Player) context.player();
            ItemStack filterStack = findOpenFilterStack(player, TagFilterData::isTagFilterItem);
            if (TagFilterData.isTagFilterItem(filterStack)) {
                boolean changed = payload.remove() ? TagFilterData.removeTagFilter(filterStack, payload.tag())
                        : TagFilterData.addTagFilter(filterStack, payload.tag());
                if (changed) {
                    player.getInventory().setChanged();
                    if (player.containerMenu instanceof FilterMenu menu && menu.isTagMode()) {
                        menu.broadcastChanges();
                    }
                }
            }
        });
    }

    public static void handleModifyFilterMod(ModifyFilterModPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = (Player) context.player();
            ItemStack filterStack = findOpenFilterStack(player, ModFilterData::isModFilter);
            if (ModFilterData.isModFilter(filterStack)) {
                boolean changed = payload.remove() ? ModFilterData.removeModFilter(filterStack, payload.modId())
                        : ModFilterData.setSingleModFilter(filterStack, payload.modId());
                if (changed) {
                    player.getInventory().setChanged();
                    if (player.containerMenu instanceof FilterMenu menu && menu.isModMode()) {
                        menu.broadcastChanges();
                    }
                }
            }
        });
    }

    public static void handleModifyFilterNbt(ModifyFilterNbtPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player.containerMenu instanceof FilterMenu menu && menu.isNbtMode()) {
                ItemStack filterStack = menu.getOpenedFilterStack(player);
                if (NbtFilterData.isNbtFilter(filterStack)) {
                    boolean changed;
                    if (payload.remove()) {
                        changed = NbtFilterData.clearSelection(filterStack);
                    } else {
                        ItemStack extractor = menu.getExtractorItem();
                        Tag selectedValue = NbtFilterData.resolvePathValue(extractor, payload.path(),
                                player.level().registryAccess());
                        changed = selectedValue != null
                                && NbtFilterData.setSelection(filterStack, payload.path(), selectedValue);
                    }
                    if (changed)
                        menu.broadcastChanges();
                }
            }
        });
    }

    public static void handleSetAmountFilterValue(SetAmountFilterValuePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof FilterMenu menu && menu.isAmountMode()) {
                menu.setAmountValue((Player) context.player(), payload.amount());
            }
        });
    }

    public static void handleSetDurabilityFilterValue(SetDurabilityFilterValuePayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof FilterMenu menu && menu.isDurabilityMode()) {
                menu.setDurabilityValue((Player) context.player(), payload.value());
            }
        });
    }

    public static void handleSetSlotFilterSlots(SetSlotFilterSlotsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof FilterMenu menu && menu.isSlotMode()) {
                boolean ok = menu.setSlotExpression((Player) context.player(), payload.expression());
                if (!ok && context.player() instanceof ServerPlayer player) {
                    player.displayClientMessage(
                            Component.translatable("message.logisticsnetworks.filter.slot.invalid"), true);
                }
            }
        });
    }

    public static void handleSetFilterFluidEntry(SetFilterFluidEntryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof FilterMenu menu && !isSpecialMode(menu)) {
                ResourceLocation fluidId = ResourceLocation.tryParse(payload.fluidId());
                if (fluidId != null) {
                    BuiltInRegistries.FLUID.getOptional(fluidId)
                            .ifPresent(fluid -> menu.setFluidFilterEntry((Player) context.player(), payload.slot(),
                                    new FluidStack(fluid, 1000)));
                }
            }
        });
    }

    public static void handleSetFilterChemicalEntry(SetFilterChemicalEntryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof FilterMenu menu && !isSpecialMode(menu)) {
                if (payload.chemicalId() != null && !payload.chemicalId().isBlank()) {
                    menu.setChemicalFilterEntry((Player) context.player(), payload.slot(), payload.chemicalId());
                }
            }
        });
    }

    public static void handleSetFilterItemEntry(SetFilterItemEntryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof FilterMenu menu && !isSpecialMode(menu)) {
                if (!payload.itemStack().isEmpty()) {
                    menu.setItemFilterEntry((Player) context.player(), payload.slot(), payload.itemStack());
                }
            }
        });
    }

    private static LogisticsNodeEntity getNode(IPayloadContext context, int entityId) {
        Entity entity = context.player().level().getEntity(entityId);
        return (entity instanceof LogisticsNodeEntity node && node.isValidNode()) ? node : null;
    }

    private static void markNetworkDirty(LogisticsNodeEntity node) {
        if (node.getNetworkId() != null && node.level() instanceof ServerLevel level) {
            NetworkRegistry.get(level).markNetworkDirty(node.getNetworkId());
        }
    }

    private static boolean isSpecialMode(FilterMenu menu) {
        return menu.isTagMode() || menu.isAmountMode() || menu.isNbtMode() || menu.isDurabilityMode()
                || menu.isModMode() || menu.isSlotMode();
    }

    private static ItemStack findOpenFilterStack(Player player, java.util.function.Predicate<ItemStack> matcher) {
        if (player.containerMenu instanceof FilterMenu menu) {
            ItemStack menuStack = menu.getOpenedFilterStack(player);
            if (matcher.test(menuStack)) {
                return menuStack;
            }
        }

        ItemStack main = player.getMainHandItem();
        if (matcher.test(main)) {
            return main;
        }

        ItemStack off = player.getOffhandItem();
        if (matcher.test(off)) {
            return off;
        }

        return ItemStack.EMPTY;
    }

    private static void setChannelToUpgradeMax(LogisticsNodeEntity node, ChannelData channel) {
        channel.setBatchSize(getMaxBatch(node, channel.getType()));
        channel.setTickDelay(channel.getType() == ChannelType.ENERGY ? 1 : NodeUpgradeData.getMinTickDelay(node));
    }

    private static void clampChannelToUpgradeLimits(LogisticsNodeEntity node, ChannelData channel) {
        int maxBatch = getMaxBatch(node, channel.getType());

        if (channel.getType() == ChannelType.ENERGY) {
            channel.setBatchSize(maxBatch);
            channel.setTickDelay(1);
        } else {
            channel.setBatchSize(Math.max(1, Math.min(channel.getBatchSize(), maxBatch)));
        }

        int minDelay = NodeUpgradeData.getMinTickDelay(node);
        if (channel.getTickDelay() < minDelay) {
            channel.setTickDelay(minDelay);
        }
    }

    private static int getMaxBatch(LogisticsNodeEntity node, ChannelType type) {
        return switch (type) {
            case FLUID -> NodeUpgradeData.getFluidOperationCapMb(node);
            case ENERGY -> NodeUpgradeData.getEnergyOperationCap(node);
            case CHEMICAL -> NodeUpgradeData.getChemicalOperationCap(node);
            default -> NodeUpgradeData.getItemOperationCap(node);
        };
    }
}
