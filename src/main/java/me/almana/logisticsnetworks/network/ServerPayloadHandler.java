package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.data.*;
import me.almana.logisticsnetworks.logic.LabelUpgradeSync;
import me.almana.logisticsnetworks.logic.NodeAccessPolicy;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.logic.TelemetryManager;
import me.almana.logisticsnetworks.filter.*;
import me.almana.logisticsnetworks.item.*;
import me.almana.logisticsnetworks.menu.ComputerMenu;
import me.almana.logisticsnetworks.menu.FilterMenu;
import me.almana.logisticsnetworks.menu.NodeMenu;
import me.almana.logisticsnetworks.menu.PatternSetterMenu;
import me.almana.logisticsnetworks.registration.ModTags;
import me.almana.logisticsnetworks.registration.Registration;
import me.almana.logisticsnetworks.upgrade.NodeUpgradeData;
import me.almana.logisticsnetworks.integration.mekanism.ChemicalTransferHelper;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.network.NetworkHooks;
import me.almana.logisticsnetworks.network.payload.IPayloadContext;
import me.almana.logisticsnetworks.network.SetFilterChemicalEntryPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ServerPayloadHandler {

    private static final Map<UUID, Boolean> DEFAULT_NODE_VISIBILITY = new HashMap<>();
    private static final Map<UUID, Integer> MODIFIER_KEYS = new HashMap<>();

    public static void handleUpdateChannel(UpdateChannelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LogisticsNodeEntity node = getAuthorizedNode(context, payload.entityId());
            if (node == null)
                return;

            ChannelData channel = node.getChannel(payload.channelIndex());
            if (channel == null)
                return;

            ChannelType oldType = channel.getType();
            updateChannelData(channel, payload, NodeUpgradeData.getUpgradeTier(node));
            if (oldType != channel.getType())
                autoDetectSide(node, channel);
            clampChannelToUpgradeLimits(node, channel);
            sendChannelSyncToViewers(node, payload.channelIndex(), channel);
            propagateToLabelGroup(node, payload.channelIndex());
            invalidateNetwork(node);
        });
    }

    private static void updateChannelData(ChannelData channel, UpdateChannelPayload payload, int tier) {
        channel.setEnabled(payload.enabled());

        if (isValidEnum(payload.modeOrdinal(), ChannelMode.values()))
            channel.setMode(ChannelMode.values()[payload.modeOrdinal()]);

        channel.setBatchSize(payload.batchSize());
        channel.setTickDelay(payload.tickDelay());

        if (isValidEnum(payload.typeOrdinal(), ChannelType.values()))
            NodeUpgradeData.applyTypeChange(channel, ChannelType.values()[payload.typeOrdinal()], tier);

        if (payload.directionOrdinal() == 6) {
            channel.setIoDirection(null);
        } else if (isValidEnum(payload.directionOrdinal(), Direction.values())) {
            channel.setIoDirection(Direction.values()[payload.directionOrdinal()]);
        }

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

    private static void autoDetectSide(LogisticsNodeEntity node, ChannelData channel) {
        if (!(node.level() instanceof ServerLevel level))
            return;
        BlockPos pos = node.getAttachedPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null)
            return;

        switch (channel.getType()) {
            case ITEM -> {
                for (Direction d : Direction.values()) {
                    if (be.getCapability(ForgeCapabilities.ITEM_HANDLER, d).isPresent()) {
                        channel.setIoDirection(d);
                        return;
                    }
                }
            }
            case FLUID -> {
                for (Direction d : Direction.values()) {
                    if (be.getCapability(ForgeCapabilities.FLUID_HANDLER, d).isPresent()) {
                        channel.setIoDirection(d);
                        return;
                    }
                }
            }
            case ENERGY -> {
                for (Direction d : Direction.values()) {
                    if (be.getCapability(ForgeCapabilities.ENERGY, d).isPresent()) {
                        channel.setIoDirection(d);
                        return;
                    }
                }
            }
            case CHEMICAL -> {
                if (MekanismCompat.isLoaded()) {
                    for (Direction d : Direction.values()) {
                        if (ChemicalTransferHelper.getHandler(level, pos, d) != null) {
                            channel.setIoDirection(d);
                            return;
                        }
                    }
                }
            }
            case SOURCE -> {} // non-directional
        }
    }

    public static void handleAssignNetwork(AssignNetworkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            LogisticsNodeEntity node = getAuthorizedNode(context, payload.entityId());
            if (node == null)
                return;

            NetworkRegistry registry = NetworkRegistry.get(player.serverLevel());

            LogisticsNetwork targetNetwork = resolveNetwork(registry, payload, player);
            if (targetNetwork == null)
                return;

            UUID oldNetworkId = node.getNetworkId();
            if (oldNetworkId != null && oldNetworkId.equals(targetNetwork.getId())) {
                node.setNetworkName(targetNetwork.getName());
                node.setNetworkColor(targetNetwork.getColor());
                if (player.containerMenu instanceof NodeMenu menu) {
                    menu.sendNetworkListToClient(player);
                }
                return;
            }

            if (targetNetwork.getOwnerUuid() == null) {
                targetNetwork.setOwnerUuid(player.getUUID());
            }
            NodeClipboardConfig.joinNetwork(node, registry, targetNetwork);

            if (NodeUpgradeData.needsDimensionalUpgradeWarning(node, targetNetwork, player.getServer())) {
                player.sendSystemMessage(Component.translatable("gui.logisticsnetworks.dimensional_upgrade_warning"));
            }

            if (player.containerMenu instanceof NodeMenu menu) {
                menu.sendNetworkListToClient(player);
            }
            if (oldNetworkId != null) GraphPayloadHandler.broadcast(player.getServer(), oldNetworkId);
            GraphPayloadHandler.broadcast(player.getServer(), targetNetwork.getId());
        });
    }

    private static LogisticsNetwork resolveNetwork(NetworkRegistry registry, AssignNetworkPayload payload,
            ServerPlayer player) {
        if (payload.networkId().isPresent()) {
            LogisticsNetwork network = registry.getNetwork(payload.networkId().get());
            if (network == null)
                return null;
            if (!NodeAccessPolicy.canAccess(network.getOwnerUuid(), player)) {
                return null;
            }
            return network;
        } else {
            String name = payload.newNetworkName().trim();
            return registry.createNetwork(name.isEmpty() ? "Unnamed" : name, player.getUUID());
        }
    }

    public static void handleRenameNetwork(RenameNetworkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;

            String newName = payload.newName().trim();
            if (newName.isEmpty() || newName.length() > 32)
                return;

            NetworkRegistry registry = NetworkRegistry.get(player.serverLevel());
            LogisticsNetwork network = registry.getNetwork(payload.networkId());
            if (network == null)
                return;

            if (!NodeAccessPolicy.canAccess(network.getOwnerUuid(), player)) {
                return;
            }

            network.setName(newName);
            registry.setDirty();

            for (java.util.UUID nodeId : network.getNodeUuids()) {
                for (ServerLevel level : player.getServer().getAllLevels()) {
                    Entity entity = level.getEntity(nodeId);
                    if (entity instanceof LogisticsNodeEntity node) {
                        node.setNetworkName(newName);
                        break;
                    }
                }
            }

            if (player.containerMenu instanceof NodeMenu menu) {
                menu.sendNetworkListToClient(player);
            }
            GraphPayloadHandler.broadcast(player.getServer(), network.getId());
        });
    }

    public static void handleDeleteNetwork(DeleteNetworkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof ComputerMenu)) {
                return;
            }
            player.displayClientMessage(deleteNetwork(player, payload.networkId()), true);
        });
    }

    private static Component deleteNetwork(ServerPlayer player, UUID networkId) {
        NetworkRegistry registry = NetworkRegistry.get(player.serverLevel());
        LogisticsNetwork network = registry.getNetwork(networkId);
        if (network == null) {
            return Component.translatable("message.logisticsnetworks.network_delete.missing");
        }
        if (!NodeAccessPolicy.canDelete(network.getOwnerUuid(), player)) {
            return Component.translatable("message.logisticsnetworks.network_delete.denied");
        }

        List<LogisticsNodeEntity> nodes = findNetworkNodes(player, network);
        int unavailable = network.getNodeUuids().size() - nodes.size();
        if (unavailable > 0) {
            return Component.translatable("message.logisticsnetworks.network_delete.unavailable", unavailable);
        }

        List<ItemStack> returnedItems = collectNetworkItems(nodes);
        removeNetworkNodes(nodes);
        registry.deleteNetwork(networkId);
        returnNetworkItems(player, returnedItems);
        refreshAllOpenComputerMenus(player.getServer());
        return Component.translatable("message.logisticsnetworks.network_delete.success", nodes.size());
    }

    private static List<LogisticsNodeEntity> findNetworkNodes(ServerPlayer player, LogisticsNetwork network) {
        List<LogisticsNodeEntity> nodes = new ArrayList<>(network.getNodeUuids().size());
        for (UUID nodeId : network.getNodeUuids()) {
            LogisticsNodeEntity node = findNode(player, nodeId);
            if (node != null && !node.isRemoved() && network.getId().equals(node.getNetworkId())) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    private static List<ItemStack> collectNetworkItems(List<LogisticsNodeEntity> nodes) {
        List<ItemStack> items = new ArrayList<>();
        for (LogisticsNodeEntity node : nodes) {
            items.add(new ItemStack(Registration.LOGISTICS_NODE_ITEM.get()));
            for (int slot = 0; slot < LogisticsNodeEntity.UPGRADE_SLOT_COUNT; slot++) {
                ItemStack upgrade = node.getUpgradeItem(slot);
                if (!upgrade.isEmpty()) {
                    items.add(upgrade.copy());
                }
            }
        }
        return items;
    }

    private static void removeNetworkNodes(List<LogisticsNodeEntity> nodes) {
        for (LogisticsNodeEntity node : nodes) {
            node.setNetworkId(null);
            node.discard();
        }
    }

    private static void returnNetworkItems(ServerPlayer player, List<ItemStack> items) {
        for (ItemStack stack : items) {
            player.getInventory().add(stack);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(player.serverLevel(), player.getX(), player.getY(), player.getZ(), stack);
            }
        }
    }

    private static void refreshAllOpenComputerMenus(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof ComputerMenu menu) {
                menu.requestNetworkList(player);
            }
        }
    }

    public static void handleSetNetworkColor(SetNetworkColorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            NetworkRegistry registry = NetworkRegistry.get(player.serverLevel());
            LogisticsNetwork network = registry.getNetwork(payload.networkId());
            if (network == null || !canAccessNetwork(player, network)) {
                return;
            }

            network.setColor(payload.color());
            registry.setDirty();

            for (UUID nodeId : network.getNodeUuids()) {
                LogisticsNodeEntity node = findNode(player, nodeId);
                if (node != null) {
                    node.setNetworkColor(network.getColor());
                }
            }

            GraphPayloadHandler.broadcast(player.getServer(), network.getId());
            if (player.containerMenu instanceof NodeMenu menu) {
                menu.sendNetworkListToClient(player);
            }
        });
    }

    public static void handleToggleVisibility(ToggleNodeVisibilityPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LogisticsNodeEntity node = getAuthorizedNode(context, payload.entityId());
            if (node == null)
                return;
            node.setRenderVisible(!node.isRenderVisible());
            propagateVisibilityToLabelGroup(node);
        });
    }

    private static void propagateVisibilityToLabelGroup(LogisticsNodeEntity sourceNode) {
        String label = sourceNode.getNodeLabel();
        if (label.isEmpty() || sourceNode.getNetworkId() == null
                || !(sourceNode.level() instanceof ServerLevel level))
            return;
        LogisticsNetwork network = NetworkRegistry.get(level).getNetwork(sourceNode.getNetworkId());
        if (network == null)
            return;
        for (UUID otherId : network.getNodeUuids()) {
            LogisticsNodeEntity other = findNode(level.getServer(), otherId);
            if (other != null && label.equals(other.getNodeLabel())) {
                other.setRenderVisible(sourceNode.isRenderVisible());
            }
        }
    }

    public static void handleSetDefaultNodeVisibility(SetDefaultNodeVisibilityPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                DEFAULT_NODE_VISIBILITY.put(player.getUUID(), payload.visible());
            }
        });
    }

    public static boolean getDefaultNodeVisibility(Player player) {
        return DEFAULT_NODE_VISIBILITY.getOrDefault(player.getUUID(), true);
    }

    public static void clearDefaultNodeVisibility(Player player) {
        DEFAULT_NODE_VISIBILITY.remove(player.getUUID());
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

    public static void handleMassSelectConnected(MassSelectConnectedPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            InteractionHand hand = payload.handOrdinal() == InteractionHand.OFF_HAND.ordinal()
                    ? InteractionHand.OFF_HAND
                    : InteractionHand.MAIN_HAND;

            if (WrenchItem.handleConnectedSelection(player, hand, payload.pos())) {
                player.getInventory().setChanged();
            }
        });
    }

    public static void handleCopyPasteConnected(CopyPasteConnectedPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            InteractionHand hand = payload.handOrdinal() == InteractionHand.OFF_HAND.ordinal()
                    ? InteractionHand.OFF_HAND
                    : InteractionHand.MAIN_HAND;

            if (WrenchItem.handleConnectedPaste(player, hand, payload.pos())) {
                player.getInventory().setChanged();
            }
        });
    }

    public static void handleSyncModifierKeys(SyncModifierKeysPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                MODIFIER_KEYS.put(player.getUUID(), payload.mask() & 0b1111);
            }
        });
    }

    public static boolean isModifierDown(Player player, int index) {
        Integer mask = MODIFIER_KEYS.get(player.getUUID());
        if (mask == null) {
            return index == 0 && player.isShiftKeyDown();
        }
        return index >= 0 && index < 4 && (mask & (1 << index)) != 0;
    }

    public static void clearModifierKeys(Player player) {
        MODIFIER_KEYS.remove(player.getUUID());
    }

    public static void handleSetChannelFilterItem(SetChannelFilterItemPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LogisticsNodeEntity node = getAuthorizedNode(context, payload.entityId());
            if (node == null)
                return;
            ChannelData channel = node.getChannel(payload.channelIndex());
            if (channel == null)
                return;

            channel.setFilterItem(payload.filterSlot(), ItemStack.EMPTY);
            propagateToLabelGroup(node, payload.channelIndex());
            invalidateNetwork(node);
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

    public static void handleSetFilterEntryAmount(SetFilterEntryAmountPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof FilterMenu menu && !menu.isAmountMode()) {
                menu.setEntryBatch((Player) context.player(), payload.entryIndex(), payload.batch());
                menu.setEntryStock((Player) context.player(), payload.entryIndex(), payload.stock());
            }
        });
    }

    public static void handleSetFilterEntryTag(SetFilterEntryTagPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof FilterMenu menu && !isSpecialMode(menu)) {
                String normalizedTag = FilterTagUtil.normalizeTag(payload.tagId());
                if (normalizedTag == null) {
                    menu.clearEntryTag(payload.entryIndex());
                } else {
                    menu.setEntryTag((Player) context.player(), payload.entryIndex(), normalizedTag);
                }
            }
        });
    }

    public static void handleSetFilterEntryNbt(SetFilterEntryNbtPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof FilterMenu menu && !isSpecialMode(menu)) {
                switch (payload.action()) {
                    case SetFilterEntryNbtPayload.ACTION_ADD ->
                        menu.addSlotNbtRule((Player) context.player(), payload.slot(),
                                payload.path(), payload.operator(), payload.value());
                    case SetFilterEntryNbtPayload.ACTION_REMOVE ->
                        menu.removeSlotNbtRule(payload.slot(), payload.ruleIndex());
                    case SetFilterEntryNbtPayload.ACTION_TOGGLE_MATCH ->
                        menu.toggleSlotNbtMatchMode(payload.slot());
                    case SetFilterEntryNbtPayload.ACTION_CLEAR ->
                        menu.clearSlotNbtRules(payload.slot());
                    case SetFilterEntryNbtPayload.ACTION_SET_VALUE ->
                        menu.setSlotNbtRuleValue(payload.slot(), payload.ruleIndex(), payload.value());
                    case SetFilterEntryNbtPayload.ACTION_SET_RAW ->
                        menu.setEntryNbtRaw((Player) context.player(), payload.slot(),
                                payload.path(), payload.value());
                }
            }
        });
    }

    public static void handleSetFilterEntryDurability(SetFilterEntryDurabilityPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof FilterMenu menu && !isSpecialMode(menu)) {
                if (payload.comparison() == null || payload.comparison().isEmpty()) {
                    menu.clearEntryDurability((Player) context.player(), payload.entryIndex());
                } else {
                    menu.setEntryDurability((Player) context.player(), payload.entryIndex(),
                            payload.comparison(), payload.value());
                }
            }
        });
    }

    public static void handleSetFilterEntrySlotMapping(SetFilterEntrySlotMappingPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof FilterMenu menu && !isSpecialMode(menu)) {
                menu.setEntrySlotMapping((Player) context.player(), payload.entryIndex(), payload.slotExpression());
            }
        });
    }

    public static void handleSetFilterEntryEnchanted(SetFilterEntryEnchantedPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof FilterMenu menu && !isSpecialMode(menu)) {
                menu.setEntryEnchanted((Player) context.player(), payload.entryIndex(),
                        payload.enabled() ? payload.value() : null);
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
                if (payload.itemStack().isEmpty()) {
                    menu.clearFilterEntryItem(payload.slot());
                } else {
                    menu.setItemFilterEntry((Player) context.player(), payload.slot(), payload.itemStack());
                }
            }
        });
    }

    private static LogisticsNodeEntity getNode(IPayloadContext context, int entityId) {
        Entity entity = context.player().level().getEntity(entityId);
        return (entity instanceof LogisticsNodeEntity node && node.isValidNode()) ? node : null;
    }

    private static LogisticsNodeEntity getAuthorizedNode(IPayloadContext context, int entityId) {
        LogisticsNodeEntity node = getNode(context, entityId);
        if (node == null) return null;
        return node.isOwnedBy(context.player()) ? node : null;
    }

    public static void invalidateNetwork(LogisticsNodeEntity node) {
        if (node.getNetworkId() != null && node.level() instanceof ServerLevel level) {
            NetworkRegistry.get(level).invalidateNetwork(node.getNetworkId());
            GraphPayloadHandler.broadcast(level.getServer(), node.getNetworkId());
        }
    }

    public static void handleSetNameFilter(SetNameFilterPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof FilterMenu menu && menu.isNameMode()) {
                menu.setNameExpression((Player) context.player(), payload.name());
            }
        });
    }

    public static void handleOpenFilterInSlot(OpenFilterInSlotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer))
                return;

            int slotIndex = payload.slotIndex();
            if (slotIndex < 0 || slotIndex >= serverPlayer.getInventory().getContainerSize())
                return;

            ItemStack stack = serverPlayer.getInventory().getItem(slotIndex);
            if (stack.isEmpty() || !stack.is(ModTags.FILTERS))
                return;

            boolean isMod = stack.getItem() instanceof ModFilterItem;
            boolean isName = stack.getItem() instanceof NameFilterItem;
            int slotCount = isMod || isName ? 0 : Math.max(1, FilterItemData.getCapacity(stack));

            NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
                    (id, inv, p) -> new FilterMenu(id, inv, slotIndex),
                    stack.getHoverName()), buf -> {
                        buf.writeVarInt(-1);
                        buf.writeVarInt(slotIndex);
                        buf.writeVarInt(slotCount);
                        writeFilterModes(buf, isMod, isName);
                    });
        });
    }

    private static void writeFilterModes(FriendlyByteBuf buf, boolean isMod, boolean isName) {
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        buf.writeBoolean(isMod);
        buf.writeBoolean(false);
        buf.writeBoolean(isName);
    }

    public static void handleOpenNodeFilter(OpenNodeFilterPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer))
                return;

            Entity entity = serverPlayer.level().getEntity(payload.entityId());
            if (!(entity instanceof LogisticsNodeEntity node) || !node.isOwnedBy(serverPlayer))
                return;

            int channel = payload.channel();
            int filterSlot = payload.filterSlot();
            if (channel < 0 || channel >= 9 || filterSlot < 0 || filterSlot >= 9)
                return;

            ItemStack stack = node.getChannel(channel).getFilterItem(filterSlot);
            if (stack.isEmpty() || !stack.is(ModTags.FILTERS))
                return;

            boolean isTag = stack.getItem() instanceof TagFilterItem;
            boolean isAmount = stack.getItem() instanceof AmountFilterItem;
            boolean isNbt = stack.getItem() instanceof NbtFilterItem;
            boolean isDurability = stack.getItem() instanceof DurabilityFilterItem;
            boolean isMod = stack.getItem() instanceof ModFilterItem;
            boolean isSlot = stack.getItem() instanceof SlotFilterItem;
            boolean isName = stack.getItem() instanceof NameFilterItem;
            boolean isSpecial = isTag || isAmount || isNbt || isDurability || isMod || isSlot || isName;
            int slotCount = isSpecial ? 0 : Math.max(1, FilterItemData.getCapacity(stack));

            NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
                    (id, inv, p) -> new FilterMenu(id, inv, node, channel, filterSlot),
                    stack.getHoverName()), buf -> {
                        buf.writeVarInt(-2);
                        buf.writeVarInt(node.getId());
                        buf.writeVarInt(channel);
                        buf.writeVarInt(filterSlot);
                        buf.writeVarInt(slotCount);
                        buf.writeBoolean(isTag);
                        buf.writeBoolean(isAmount);
                        buf.writeBoolean(isNbt);
                        buf.writeBoolean(isDurability);
                        buf.writeBoolean(isMod);
                        buf.writeBoolean(isSlot);
                        buf.writeBoolean(isName);
                    });
        });
    }

    public static void handleApplyPattern(ApplyPatternPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof PatternSetterMenu menu) {
                menu.applyPattern(payload.useOutputs(), payload.multiplier(),
                        context.player().level().registryAccess());
            }
        });
    }

    private static boolean isSpecialMode(FilterMenu menu) {
        return menu.isTagMode() || menu.isAmountMode() || menu.isNbtMode() || menu.isDurabilityMode()
                || menu.isModMode() || menu.isSlotMode() || menu.isNameMode();
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

    public static void clampChannelToUpgradeLimits(LogisticsNodeEntity node, ChannelData channel) {
        int tier = NodeUpgradeData.getUpgradeTier(node);
        int maxBatch = NodeUpgradeData.getOperationCap(channel.getType(), tier);

        if (channel.getType() == ChannelType.ENERGY) {
            channel.setBatchSize(maxBatch);
        } else {
            channel.setBatchSize(Math.max(1, Math.min(channel.getBatchSize(), maxBatch)));
        }

        int minDelay = NodeUpgradeData.getMinTickDelay(tier);
        if (channel.getTickDelay() < minDelay) {
            channel.setTickDelay(minDelay);
        }
    }

    public static void handleRequestNetworkNodes(RequestNetworkNodesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            if (!(player.containerMenu instanceof ComputerMenu))
                return;

            NetworkRegistry registry = NetworkRegistry.get(player.serverLevel());
            LogisticsNetwork network = registry.getNetwork(payload.networkId());
            if (network == null)
                return;

            if (!canAccessNetwork(player, network)) {
                return;
            }

            List<SyncNetworkNodesPayload.NodeInfo> nodeInfos = new ArrayList<>();
            for (UUID nodeId : network.getNodeUuids()) {
                for (ServerLevel level : player.getServer().getAllLevels()) {
                    Entity entity = level.getEntity(nodeId);
                    if (entity instanceof LogisticsNodeEntity node) {
                        BlockPos attachedPos = node.getAttachedPos();
                        String blockName = "unknown";
                        if (level.isLoaded(attachedPos)) {
                            BlockState state = level.getBlockState(attachedPos);
                            blockName = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                        }
                        nodeInfos.add(new SyncNetworkNodesPayload.NodeInfo(
                                nodeId, node.blockPosition(), attachedPos, blockName, node.getNodeLabel(),
                                level.dimension().location().toString(), node.isRenderVisible(), node.isHighlighted()));
                        break;
                    }
                }
            }

            NetworkHandler.sendToPlayer(player,
                    new SyncNetworkNodesPayload(payload.networkId(), nodeInfos));
        });
    }

    public static void handleSetNodeLabel(SetNodeLabelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            LogisticsNodeEntity node = getAuthorizedNode(context, payload.entityId());
            if (node == null)
                return;

            String label = payload.label().trim();
            if (label.length() > 48)
                label = label.substring(0, 48);
            node.setNodeLabel(label);

            if (!label.isEmpty() && node.getNetworkId() != null
                    && node.level() instanceof ServerLevel level) {
                LogisticsNetwork network = NetworkRegistry.get(level).getNetwork(node.getNetworkId());
                if (network != null) {
                    LogisticsNodeEntity authority = node;
                    for (UUID otherId : network.getNodeUuids()) {
                        if (otherId.equals(node.getUUID())) continue;
                        LogisticsNodeEntity other = findNode(player, otherId);
                        if (other != null && label.equals(other.getNodeLabel())) {
                            authority = other;
                            copyLabelPeer(other, node);
                            break;
                        }
                    }
                    LabelUpgradeSync.establishLabelTemplate(player, network, authority);
                }
            }
            invalidateNetwork(node);
            LabelUpgradeSync.synchronizeOnLoad(node);
        });
    }

    private static void copyLabelPeer(LogisticsNodeEntity source, LogisticsNodeEntity node) {
        node.setRenderVisible(source.isRenderVisible());
        for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
            ChannelData dst = node.getChannel(i);
            dst.copyFrom(source.getChannel(i));
            clampChannelToUpgradeLimits(node, dst);
            sendChannelSyncToViewers(node, i, dst);
        }
    }

    public static void handleSetChannelName(SetChannelNamePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LogisticsNodeEntity node = getAuthorizedNode(context, payload.entityId());
            if (node == null)
                return;

            ChannelData channel = node.getChannel(payload.channelIndex());
            if (channel == null)
                return;

            String name = payload.name().trim();
            if (name.length() > 24)
                name = name.substring(0, 24);

            UUID networkId = node.getNetworkId();
            if (networkId != null && node.level() instanceof ServerLevel level) {
                NetworkRegistry registry = NetworkRegistry.get(level);
                LogisticsNetwork network = registry.getNetwork(networkId);
                if (network != null) {
                    network.setChannelName(payload.channelIndex(), name);
                    registry.setDirty();

                    MinecraftServer server = level.getServer();
                    for (UUID nodeId : network.getNodeUuids()) {
                        for (ServerLevel sl : server.getAllLevels()) {
                            Entity entity = sl.getEntity(nodeId);
                            if (entity instanceof LogisticsNodeEntity otherNode) {
                                ChannelData otherCh = otherNode.getChannel(payload.channelIndex());
                                if (otherCh != null) {
                                    otherCh.setName(name);
                                    sendChannelSyncToViewers(otherNode, payload.channelIndex(), otherCh);
                                }
                                break;
                            }
                        }
                    }
                }
            } else {
                channel.setName(name);
            }

            invalidateNetwork(node);
        });
    }

    public static void handleSetNetworkNodesVisibility(SetNetworkNodesVisibilityPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            if (!(player.containerMenu instanceof ComputerMenu))
                return;

            NetworkRegistry registry = NetworkRegistry.get(player.serverLevel());
            LogisticsNetwork network = registry.getNetwork(payload.networkId());
            if (network == null)
                return;

            if (!canAccessNetwork(player, network)) {
                return;
            }

            for (UUID nodeId : network.getNodeUuids()) {
                for (ServerLevel level : player.getServer().getAllLevels()) {
                    Entity entity = level.getEntity(nodeId);
                    if (entity instanceof LogisticsNodeEntity node) {
                        node.setRenderVisible(payload.visible());
                        break;
                    }
                }
            }
        });
    }

    public static void handleToggleNetworkNodeHighlight(ToggleNetworkNodeHighlightPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            if (!(player.containerMenu instanceof ComputerMenu))
                return;

            NetworkRegistry registry = NetworkRegistry.get(player.serverLevel());
            LogisticsNetwork network = registry.getNetwork(payload.networkId());
            if (network == null || !canAccessNetwork(player, network)
                    || !network.getNodeUuids().contains(payload.nodeId())) {
                return;
            }

            LogisticsNodeEntity node = findNode(player, payload.nodeId());
            if (node != null) {
                node.setHighlighted(!node.isHighlighted());
            }
        });
    }

    public static void handleToggleNetworkLabelHighlight(ToggleNetworkLabelHighlightPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            if (!(player.containerMenu instanceof ComputerMenu))
                return;

            String label = payload.label().trim();
            if (label.isEmpty()) {
                return;
            }

            NetworkRegistry registry = NetworkRegistry.get(player.serverLevel());
            LogisticsNetwork network = registry.getNetwork(payload.networkId());
            if (network == null || !canAccessNetwork(player, network)) {
                return;
            }

            List<LogisticsNodeEntity> labeledNodes = new ArrayList<>();
            for (UUID nodeId : network.getNodeUuids()) {
                LogisticsNodeEntity node = findNode(player, nodeId);
                if (node != null && label.equals(node.getNodeLabel())) {
                    labeledNodes.add(node);
                }
            }

            if (labeledNodes.isEmpty()) {
                return;
            }

            boolean makeVisible = false;
            for (LogisticsNodeEntity node : labeledNodes) {
                if (!node.isHighlighted()) {
                    makeVisible = true;
                    break;
                }
            }

            for (LogisticsNodeEntity node : labeledNodes) {
                node.setHighlighted(makeVisible);
            }
        });
    }

    public static void handleRequestOpenNodeSettings(RequestOpenNodeSettingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            if (!(player.containerMenu instanceof ComputerMenu))
                return;

            NetworkRegistry registry = NetworkRegistry.get(player.serverLevel());
            LogisticsNetwork network = registry.getNetwork(payload.networkId());
            if (network == null || !canAccessNetwork(player, network))
                return;

            if (!network.getNodeUuids().contains(payload.nodeId()))
                return;

            LogisticsNodeEntity node = findNode(player, payload.nodeId());
            if (node == null || !node.isOwnedBy(player))
                return;

            NodeMenu.open(player, node, 0, true);
        });
    }

    public static void handleRequestNetworkLabels(RequestNetworkLabelsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;

            NetworkRegistry registry = NetworkRegistry.get(player.serverLevel());
            LogisticsNetwork network = registry.getNetwork(payload.networkId());
            if (network == null || !canAccessNetwork(player, network))
                return;

            sendNetworkLabels(player, network);
        });
    }

    public static void handleDeleteNetworkLabel(DeleteNetworkLabelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;

            NetworkRegistry registry = NetworkRegistry.get(player.serverLevel());
            LogisticsNetwork network = registry.getNetwork(payload.networkId());
            if (network == null || !canAccessNetwork(player, network))
                return;

            String label = payload.label();
            List<LogisticsNodeEntity> nodes = network.getNodeUuids().stream()
                    .map(id -> findNode(player, id))
                    .filter(node -> node != null && label.equals(node.getNodeLabel()))
                    .toList();
            if (nodes.isEmpty()
                    || LabelUpgradeSync.synchronizeLabels(player, network, nodes, player.getUUID(), "")) {
                network.removeLabel(label);
                registry.setDirty();
                GraphPayloadHandler.broadcast(player.getServer(), network.getId());
            }
            sendNetworkLabels(player, network);
        });
    }

    private static void sendNetworkLabels(ServerPlayer player, LogisticsNetwork network) {
        Map<String, Integer> labels = new LinkedHashMap<>();
        network.getLabelNames().forEach(label -> labels.put(label, 0));
        for (UUID nodeId : network.getNodeUuids()) {
            LogisticsNodeEntity node = findNode(player, nodeId);
            if (node != null && !node.getNodeLabel().isEmpty()) {
                labels.merge(node.getNodeLabel(), 1, Integer::sum);
            }
        }
        NetworkHandler.sendToPlayer(player, new SyncNetworkLabelsPayload(labels));
    }

    public static void propagateToLabelGroup(LogisticsNodeEntity sourceNode, int channelIndex) {
        String label = sourceNode.getNodeLabel();
        if (label.isEmpty() || sourceNode.getNetworkId() == null
                || !(sourceNode.level() instanceof ServerLevel level))
            return;

        ChannelData sourceChannel = sourceNode.getChannel(channelIndex);
        if (sourceChannel == null)
            return;

        NetworkRegistry registry = NetworkRegistry.get(level);
        LogisticsNetwork network = registry.getNetwork(sourceNode.getNetworkId());
        if (network == null)
            return;

        List<LogisticsNodeEntity> updated = new ArrayList<>();
        for (UUID otherId : network.getNodeUuids()) {
            if (otherId.equals(sourceNode.getUUID()))
                continue;
            LogisticsNodeEntity other = findNode(level.getServer(), otherId);
            if (other == null || !label.equals(other.getNodeLabel()))
                continue;
            ChannelData dst = other.getChannel(channelIndex);
            dst.copyFrom(sourceChannel);
            clampChannelToUpgradeLimits(other, dst);
            updated.add(other);
            sendChannelSyncToViewers(other, channelIndex, dst);
        }
        LabelUpgradeSync.refreshTemplateChannel(network, sourceNode, channelIndex, updated);
        registry.setDirty();
    }

    public static void handleSubscribeTelemetry(SubscribeTelemetryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            if (!(player.containerMenu instanceof ComputerMenu))
                return;

            NetworkRegistry registry = NetworkRegistry.get(player.serverLevel());
            TelemetryManager telemetry = registry.getTelemetryManager();

            if (payload.subscribe()) {
                LogisticsNetwork network = registry.getNetwork(payload.networkId());
                if (network == null || !canAccessNetwork(player, network))
                    return;
                telemetry.subscribe(payload.networkId(), payload.channelIndex(),
                        player, registry, player.getServer());
            } else {
                telemetry.unsubscribe(player);
            }
        });
    }

    public static void handleRequestChannelList(RequestChannelListPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            if (!(player.containerMenu instanceof ComputerMenu))
                return;

            NetworkRegistry registry = NetworkRegistry.get(player.serverLevel());
            LogisticsNetwork network = registry.getNetwork(payload.networkId());
            if (network == null || !canAccessNetwork(player, network))
                return;

            int[] nodeCounts = new int[LogisticsNodeEntity.CHANNEL_COUNT];
            int[] typeOrdinals = new int[LogisticsNodeEntity.CHANNEL_COUNT];
            boolean[] found = new boolean[LogisticsNodeEntity.CHANNEL_COUNT];

            for (UUID nodeId : network.getNodeUuids()) {
                LogisticsNodeEntity node = findNode(player, nodeId);
                if (node == null) continue;

                for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
                    ChannelData channel = node.getChannel(i);
                    if (channel == null) continue;
                    if (channel.isEnabled()) {
                        nodeCounts[i]++;
                        if (!found[i]) {
                            typeOrdinals[i] = channel.getType().ordinal();
                            found[i] = true;
                        }
                    }
                }
            }

            List<SyncChannelListPayload.ChannelEntry> entries = new ArrayList<>();
            for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
                if (nodeCounts[i] > 0) {
                    entries.add(new SyncChannelListPayload.ChannelEntry(i, 0, typeOrdinals[i], nodeCounts[i]));
                }
            }

            NetworkHandler.sendToPlayer(player,
                    new SyncChannelListPayload(payload.networkId(), entries));
        });
    }

    private static boolean canAccessNetwork(ServerPlayer player, LogisticsNetwork network) {
        return NodeAccessPolicy.canAccess(network.getOwnerUuid(), player);
    }

    private static LogisticsNodeEntity findNode(ServerPlayer player, UUID nodeId) {
        return findNode(player.getServer(), nodeId);
    }

    private static LogisticsNodeEntity findNode(MinecraftServer server, UUID nodeId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(nodeId);
            if (entity instanceof LogisticsNodeEntity node) {
                return node;
            }
        }
        return null;
    }

    public static void sendChannelSyncToViewers(LogisticsNodeEntity node, int channelIndex, ChannelData channel) {
        if (!(node.level() instanceof ServerLevel level))
            return;
        CompoundTag tag = channel.save(level.registryAccess());
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof NodeMenu menu
                    && menu.getNode() != null
                    && menu.getNode().getUUID().equals(node.getUUID())) {
                NetworkHandler.sendToPlayer(player,
                        new SyncChannelDataPayload(node.getId(), channelIndex, tag));
            }
        }
    }

    public static void refreshNodeViewers(MinecraftServer server, UUID networkId) {
        LogisticsNetwork network = NetworkRegistry.get(server.overworld()).getNetwork(networkId);
        if (network == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof NodeMenu menu && menu.getNode() != null
                    && networkId.equals(menu.getNode().getNetworkId()) && canAccessNetwork(player, network)) {
                sendNetworkLabels(player, network);
                NodeMenu.sendAvailableNetworkListToClient(player);
            }
        }
    }
}
