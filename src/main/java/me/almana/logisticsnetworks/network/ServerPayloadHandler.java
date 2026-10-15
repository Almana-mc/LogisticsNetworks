package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.data.*;
import me.almana.logisticsnetworks.logic.AttachedStorageFilterScanner;
import me.almana.logisticsnetworks.logic.LabelUpgradeSync;
import me.almana.logisticsnetworks.logic.NodeAccessPolicy;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.logic.TelemetryManager;
import me.almana.logisticsnetworks.filter.*;
import me.almana.logisticsnetworks.item.*;
import me.almana.logisticsnetworks.menu.ClipboardMenu;
import me.almana.logisticsnetworks.block.ComputerBlockEntity;
import me.almana.logisticsnetworks.menu.ComputerMenu;
import me.almana.logisticsnetworks.menu.FilterMenu;
import me.almana.logisticsnetworks.menu.GraphMenuContext;
import me.almana.logisticsnetworks.menu.NodeMenu;
import me.almana.logisticsnetworks.menu.NodeMenuSync;
import me.almana.logisticsnetworks.menu.PatternSetterMenu;
import me.almana.logisticsnetworks.registration.ModTags;
import me.almana.logisticsnetworks.registration.Registration;
import me.almana.logisticsnetworks.upgrade.NodeUpgradeData;
import me.almana.logisticsnetworks.integration.mekanism.ChemicalTransferHelper;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import me.almana.logisticsnetworks.integration.storage.LinkedStorage;
import me.almana.logisticsnetworks.integration.storage.StorageLink;

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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public class ServerPayloadHandler {

    private static final Map<UUID, Boolean> DEFAULT_NODE_VISIBILITY = new HashMap<>();
    private static final Map<UUID, Integer> MODIFIER_KEYS = new HashMap<>();
    private static final int SCAN_COOLDOWN_TICKS = 10;
    private static final Map<ServerPlayer, Integer> LAST_SCAN = new WeakHashMap<>();
    private static final Map<ServerPlayer, Integer> LAST_UPGRADE_CATALOG = new WeakHashMap<>();
    private static final Map<ServerPlayer, Integer> LAST_UPGRADE_INSTALL = new WeakHashMap<>();
    // Forge caps payloads at 1MiB
    private static final int EXPORT_BUDGET = 1_000_000;
    public static final int EXPORT_COOLDOWN_TICKS = 20;
    private static final Map<ServerPlayer, Integer> LAST_EXPORT = new WeakHashMap<>();

    public static void handleRequestStorageUpgradeCatalog(RequestStorageUpgradeCatalogPayload payload,
                                                          IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof NodeMenu menu)
                    || menu.containerId != payload.containerId()
                    || menu.getNodeId() != payload.entityId()
                    || !menu.canEditNode(player)
                    || payload.preferredSlot() < 0
                    || payload.preferredSlot() >= LogisticsNodeEntity.UPGRADE_SLOT_COUNT
                    || throttled(LAST_UPGRADE_CATALOG, player)) {
                return;
            }
            StorageLink link = menu.getAccessibleStorageLink(player);
            boolean available = link != null;
            List<SyncStorageUpgradeCatalogPayload.Entry> entries = new ArrayList<>();
            if (available) {
                for (LinkedStorage.UpgradeEntry entry : LinkedStorage.listUpgrades(
                        player, link, menu.getInstalledUpgrades())) {
                    entries.add(new SyncStorageUpgradeCatalogPayload.Entry(
                            entry.item(), entry.stored(), entry.craftable()));
                }
            }
            // Forge caps payloads at 1MiB
            entries = NetworkHandler.split(entries, SyncStorageUpgradeCatalogPayload::writeEntry).get(0);
            NetworkHandler.sendToPlayer(player, new SyncStorageUpgradeCatalogPayload(
                    payload.containerId(), payload.entityId(), payload.preferredSlot(),
                    link == null ? null : link.backend(), available, entries));
        });
    }

    public static void handleInstallStorageUpgrade(InstallStorageUpgradePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof NodeMenu menu)
                    || menu.containerId != payload.containerId()
                    || menu.getNodeId() != payload.entityId()
                    || !menu.canEditNode(player)
                    || payload.preferredSlot() < 0
                    || payload.preferredSlot() >= LogisticsNodeEntity.UPGRADE_SLOT_COUNT
                    || !BuiltInRegistries.ITEM.containsKey(payload.upgradeId())
                    || throttled(LAST_UPGRADE_INSTALL, player)) {
                return;
            }
            ItemStack upgrade = BuiltInRegistries.ITEM.get(payload.upgradeId()).getDefaultInstance();
            if (!upgrade.is(ModTags.UPGRADES)) return;
            StorageLink link = menu.getAccessibleStorageLink(player);
            if (link == null) {
                player.displayClientMessage(
                        Component.translatable("message.logisticsnetworks.storage.upgrade.unavailable"), true);
                return;
            }
            reportUpgradeInstall(player, upgrade, LinkedStorage.installUpgrade(
                    player, menu.getNode(), link, payload.preferredSlot(), upgrade.getItem()));
        });
    }

    public static boolean throttled(Map<ServerPlayer, Integer> last, ServerPlayer player) {
        int now = player.server.getTickCount();
        if (now - last.getOrDefault(player, -SCAN_COOLDOWN_TICKS) < SCAN_COOLDOWN_TICKS) return true;
        last.put(player, now);
        return false;
    }

    private static void reportUpgradeInstall(ServerPlayer player, ItemStack upgrade,
                                             LinkedStorage.UpgradeInstallResult result) {
        switch (result) {
            case CRAFTING -> player.displayClientMessage(
                    Component.translatable("message.logisticsnetworks.storage.upgrade.crafting",
                            upgrade.getHoverName()), true);
            case DUPLICATE -> player.displayClientMessage(
                    Component.translatable("message.logisticsnetworks.storage.upgrade.duplicate"), true);
            case PENDING -> player.displayClientMessage(
                    Component.translatable("message.logisticsnetworks.storage.upgrade.pending"), true);
            case NO_SLOT -> player.displayClientMessage(
                    Component.translatable("message.logisticsnetworks.storage.upgrade.no_slot"), true);
            case UNAVAILABLE -> player.displayClientMessage(
                    Component.translatable("message.logisticsnetworks.storage.upgrade.unavailable"), true);
            case INSTALLED -> {
            }
        }
    }

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
        channel.setResourceRoundRobin(payload.resourceRoundRobin());
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

    public static void handleToggleComputerPinnedNetwork(ToggleComputerPinnedNetworkPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.containerMenu instanceof ComputerMenu menu)) {
                return;
            }
            if (!menu.getComputerPos().equals(payload.computerPos())) {
                return;
            }
            if (!(player.serverLevel().getBlockEntity(payload.computerPos()) instanceof ComputerBlockEntity computer)) {
                return;
            }

            boolean adding = !computer.getStarredNetworks().contains(payload.networkId());
            if (adding && NetworkRegistry.get(player.serverLevel()).getVisibleNetworks(player).stream()
                    .noneMatch(net -> net.getId().equals(payload.networkId()))) {
                return;
            }

            computer.toggleNetworkStar(payload.networkId());
            refreshOpenComputerMenus(player, payload.computerPos());
        });
    }

    private static void refreshOpenComputerMenus(ServerPlayer sourcePlayer, BlockPos computerPos) {
        for (ServerPlayer player : sourcePlayer.getServer().getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof ComputerMenu menu
                    && player.serverLevel() == sourcePlayer.serverLevel()
                    && menu.getComputerPos().equals(computerPos)) {
                menu.requestNetworkList(player);
            }
        }
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

    public static void handleSetWrenchColors(SetWrenchColorsPayload payload, IPayloadContext context) {
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

            if (payload.reset()) {
                WrenchItem.clearColors(heldStack);
            } else {
                WrenchItem.setColors(heldStack, payload.caseRgb(), payload.screenRgb());
            }
            player.getInventory().setChanged();
        });
    }

    public static void handleSetWrenchFlow(SetWrenchFlowPayload payload, IPayloadContext context) {
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

            WrenchItem.setFlow(heldStack, payload.flow());
            player.getInventory().setChanged();
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

    public static void handleAddNodeFilterItem(AddNodeFilterItemPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LogisticsNodeEntity node = getAuthorizedNode(context, payload.entityId());
            if (node == null)
                return;
            ChannelData channel = node.getChannel(payload.channel());
            int fs = payload.filterSlot();
            ItemStack item = payload.item();
            if (channel == null || fs < 0 || fs >= ChannelData.FILTER_SIZE || item.isEmpty()
                    || item.is(ModTags.FILTERS))
                return;
            FilterTargetType desired = FilterTargetType.forChannel(channel.getType());
            if (desired == null)
                return;

            ItemStack filter = channel.getFilterItem(fs);
            if (filter.isEmpty()) {
                filter = VirtualFilterType.SMALL.createStack();
                FilterItemData.setTargetType(filter, desired);
            } else if (!FilterItemData.isFilterItem(filter)) {
                return;
            } else {
                filter = filter.copy();
            }
            FilterItemData.Edit result = FilterItemData.addItem(filter, item);
            if (result == FilterItemData.Edit.REFUSED)
                context.player().displayClientMessage(
                        Component.translatable("message.logisticsnetworks.filter.too_large"), true);
            if (result != FilterItemData.Edit.CHANGED)
                return;
            channel.setFilterItem(fs, filter);
            propagateToLabelGroup(node, payload.channel());
            invalidateNetwork(node);
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
            if (context.player().containerMenu instanceof FilterMenu menu && menu.stillValid(context.player())
                    && !menu.isAmountMode()) {
                menu.setEntryBatch((Player) context.player(), payload.entryIndex(), payload.batch());
                menu.setEntryStock((Player) context.player(), payload.entryIndex(), payload.stock());
            }
        });
    }

    public static void handleSetFilterEntryTag(SetFilterEntryTagPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof FilterMenu menu && menu.stillValid(context.player())
                    && !isSpecialMode(menu)) {
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
            if (context.player().containerMenu instanceof FilterMenu menu && menu.stillValid(context.player())
                    && !isSpecialMode(menu)) {
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
                    case SetFilterEntryNbtPayload.ACTION_SET_STRICT ->
                        menu.setEntryNbtStrict(payload.slot(), Boolean.parseBoolean(payload.value()));
                }
            }
        });
    }

    public static void handleSetFilterEntryDurability(SetFilterEntryDurabilityPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof FilterMenu menu && menu.stillValid(context.player())
                    && !isSpecialMode(menu)) {
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
            if (context.player().containerMenu instanceof FilterMenu menu && menu.stillValid(context.player())
                    && !isSpecialMode(menu)) {
                menu.setEntrySlotMapping((Player) context.player(), payload.entryIndex(), payload.slotExpression());
            }
        });
    }

    public static void handleSetFilterEntryEnchanted(SetFilterEntryEnchantedPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof FilterMenu menu && menu.stillValid(context.player())
                    && !isSpecialMode(menu)) {
                menu.setEntryEnchanted((Player) context.player(), payload.entryIndex(),
                        payload.enabled() ? payload.value() : null);
            }
        });
    }

    public static void handleSetFilterFluidEntry(SetFilterFluidEntryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof FilterMenu menu && menu.stillValid(context.player())
                    && !isSpecialMode(menu)) {
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
            if (context.player().containerMenu instanceof FilterMenu menu && menu.stillValid(context.player())
                    && !isSpecialMode(menu)) {
                if (payload.chemicalId() != null && !payload.chemicalId().isBlank()) {
                    menu.setChemicalFilterEntry((Player) context.player(), payload.slot(), payload.chemicalId());
                }
            }
        });
    }

    public static void handleSetFilterItemEntry(SetFilterItemEntryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof FilterMenu menu && menu.stillValid(context.player())
                    && !isSpecialMode(menu)) {
                switch (payload.action()) {
                    case SetFilterItemEntryPayload.ACTION_SET -> {
                        if (!payload.itemStack().isEmpty()) {
                            menu.setItemFilterEntry((Player) context.player(), payload.slot(), payload.itemStack());
                        }
                    }
                    case SetFilterItemEntryPayload.ACTION_CLEAR_ITEM ->
                        menu.clearFilterEntryItem((Player) context.player(), payload.slot());
                    case SetFilterItemEntryPayload.ACTION_CLEAR_ENTRY -> menu.clearFilterEntry(payload.slot());
                }
            }
        });
    }

    private static LogisticsNodeEntity getNode(IPayloadContext context, int entityId) {
        Entity entity = context.player().level().getEntity(entityId);
        return (entity instanceof LogisticsNodeEntity node && node.isValidNode()) ? node : null;
    }

    private static LogisticsNodeEntity getAuthorizedNode(IPayloadContext context, int entityId) {
        Player player = context.player();
        GraphMenuContext graph = GraphPayloadHandler.getContext(player.containerMenu);
        if (graph != null) {
            LogisticsNodeEntity selected = player.containerMenu instanceof NodeMenu menu
                    ? menu.getNode() : ((FilterMenu) player.containerMenu).getNodeSource();
            return graph.canEdit(player, selected) && selected.getId() == entityId ? selected : null;
        }
        LogisticsNodeEntity node = getNode(context, entityId);
        if (node == null) return null;
        return node.isOwnedBy(context.player()) ? node : null;
    }

    public static void invalidateNetwork(LogisticsNodeEntity node) {
        for (ChannelData channel : node.getChannels()) channel.resetResourceRotation();
        if (node.getNetworkId() != null && node.level() instanceof ServerLevel level) {
            NetworkRegistry.get(level).invalidateNetwork(node.getNetworkId());
            GraphPayloadHandler.broadcast(level.getServer(), node.getNetworkId());
        }
    }

    public static void handleSetNameFilter(SetNameFilterPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof FilterMenu menu && menu.stillValid(context.player())
                    && menu.isNameMode()) {
                NameFilterData.ValidationResult validation = NameFilterData.validateRegex(payload.name());
                if (!payload.name().isEmpty() && !validation.accepted()) {
                    String key = switch (validation.error()) {
                        case TOO_LONG -> "message.logisticsnetworks.filter.regex.too_long";
                        case UNSUPPORTED -> "message.logisticsnetworks.filter.regex.unsupported";
                        default -> "message.logisticsnetworks.filter.regex.invalid";
                    };
                    context.player().sendSystemMessage(Component.translatable(key));
                    return;
                }
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
            if (stack.getItem() instanceof NbtFilterItem)
                return;

            boolean isMod = stack.getItem() instanceof ModFilterItem;
            boolean isName = stack.getItem() instanceof NameFilterItem;
            int slotCount = isMod || isName ? 0 : Math.max(1, FilterItemData.getCapacity(stack));

            NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
                    (id, inv, p) -> new FilterMenu(id, inv, slotIndex),
                    stack.getHoverName()),
                    buf -> FilterMenu.writeMenuData(buf, slotIndex, slotCount, isMod, false, isName));
        });
    }

    public static void handleScanAttachedStorage(ScanAttachedStoragePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof FilterMenu menu)
                    || !menu.stillValid(player) || !menu.canScanAttachedStorage()) {
                return;
            }
            int now = player.server.getTickCount();
            if (now - LAST_SCAN.getOrDefault(player, -SCAN_COOLDOWN_TICKS) < SCAN_COOLDOWN_TICKS) {
                return;
            }
            LAST_SCAN.put(player, now);

            LogisticsNodeEntity node = menu.getNodeSource();
            ChannelData channel = node.getChannel(menu.getNodeChannel());
            if (!(node.level() instanceof ServerLevel level) || channel == null) {
                return;
            }

            ItemStack filter = menu.getOpenedStack();
            AttachedStorageFilterScanner.Result result = AttachedStorageFilterScanner.scan(
                    level, node, channel, filter);
            if (result.tooLarge()) {
                player.displayClientMessage(Component.translatable("message.logisticsnetworks.filter.too_large"), true);
                return;
            }
            if (result.added() > 0) {
                menu.refreshFilterEntries();
                invalidateNetwork(node);
            }
            NetworkHandler.sendToPlayer(player, new SyncFilterScanResultPayload(
                    filter.copyWithCount(1), result.added(), result.storageFound(), result.filterFull()));
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

            LogisticsNodeEntity node = getAuthorizedNode(context, payload.entityId());
            if (node == null)
                return;

            int ch = payload.channel();
            int fs = payload.filterSlot();
            if (ch < 0 || ch >= LogisticsNodeEntity.CHANNEL_COUNT || fs < 0 || fs >= ChannelData.FILTER_SIZE)
                return;
            FilterTargetType desired = FilterTargetType.forChannel(node.getChannel(ch).getType());
            if (desired == null)
                return;

            ItemStack stack = prepareNodeFilter(node, ch, fs, payload.requestedType(), desired);
            openNodeFilterMenu(serverPlayer, node, ch, fs, stack);
        });
    }

    // Conjure a filter when needed
    private static ItemStack prepareNodeFilter(LogisticsNodeEntity node, int ch, int fs,
            VirtualFilterType requested, FilterTargetType desired) {
        ChannelData channel = node.getChannel(ch);
        ItemStack stack = channel.getFilterItem(fs);
        boolean fresh = requested != VirtualFilterType.EXISTING || stack.isEmpty()
                || !stack.is(ModTags.FILTERS) || currentTarget(stack) != desired;
        if (!fresh)
            return stack;

        VirtualFilterType role = requested != VirtualFilterType.EXISTING
                ? requested
                : (stack.isEmpty() ? VirtualFilterType.SMALL : VirtualFilterType.fromStack(stack));
        ItemStack created = role.createStack();
        applyTarget(created, desired);
        channel.setFilterItem(fs, created);
        sendChannelSyncToViewers(node, ch, channel);
        propagateToLabelGroup(node, ch);
        invalidateNetwork(node);
        return channel.getFilterItem(fs);
    }

    private static void openNodeFilterMenu(ServerPlayer player, LogisticsNodeEntity node, int ch, int fs,
            ItemStack stack) {
        VirtualFilterType type = VirtualFilterType.fromStack(stack);
        boolean isMod = type == VirtualFilterType.MOD;
        boolean isName = type == VirtualFilterType.NAME;
        int slotCount = type.isSpecial() ? 0 : Math.max(1, FilterItemData.getCapacity(stack));
        ItemStack opened = stack.copyWithCount(1);
        boolean inline = NodeMenuSync.fitsOpenBuffer(buf -> buf.writeItem(opened));

        GraphMenuContext graphContext = GraphPayloadHandler.getContext(player.containerMenu);
        NetworkHooks.openScreen(player, new SimpleMenuProvider(
                (id, inv, p) -> {
                    FilterMenu menu = new FilterMenu(id, inv, node, ch, fs);
                    menu.setGraphContext(graphContext);
                    return menu;
                },
                opened.getHoverName()), buf -> {
                    buf.writeVarInt(-2);
                    buf.writeVarInt(node.getId());
                    buf.writeUUID(node.getUUID());
                    buf.writeResourceLocation(node.level().dimension().location());
                    buf.writeBoolean(graphContext != null);
                    if (graphContext != null) graphContext.write(buf);
                    buf.writeVarInt(ch);
                    buf.writeVarInt(fs);
                    FilterMenu.writeOpenedStack(buf, opened, inline);
                    buf.writeVarInt(slotCount);
                    writeFilterModes(buf, isMod, isName);
                });
        FilterMenu.sendOpenedStackIfDeferred(player, opened, inline);
    }

    private static FilterTargetType currentTarget(ItemStack stack) {
        if (FilterItemData.isFilterItem(stack)) {
            return FilterItemData.getTargetType(stack);
        }
        if (NameFilterData.isNameFilter(stack)) {
            return NameFilterData.getTargetType(stack);
        }
        if (ModFilterData.isModFilter(stack)) {
            return ModFilterData.getTargetType(stack);
        }
        return FilterTargetType.ITEMS;
    }

    private static void applyTarget(ItemStack stack, FilterTargetType target) {
        if (FilterItemData.isFilterItem(stack)) {
            FilterItemData.setTargetType(stack, target);
        } else if (NameFilterData.isNameFilter(stack)) {
            NameFilterData.setTargetType(stack, target);
        } else if (ModFilterData.isModFilter(stack)) {
            ModFilterData.setTargetType(stack, target);
        }
    }

    public static void handleOpenNodeMenu(OpenNodeMenuPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            LogisticsNodeEntity node = getAuthorizedNode(context, payload.entityId());
            if (node == null)
                return;
            int selectedChannel = Math.max(0,
                    Math.min(LogisticsNodeEntity.CHANNEL_COUNT - 1, payload.selectedChannel()));
            GraphMenuContext graphContext = GraphPayloadHandler.getContext(player.containerMenu);
            if (graphContext != null) {
                if (player.containerMenu.getCarried().isEmpty()) {
                    GraphPayloadHandler.open(player, graphContext, node, selectedChannel);
                }
                return;
            }
            StorageLink preferredStorageLink = player.containerMenu instanceof NodeMenu currentMenu
                    ? currentMenu.getAccessibleStorageLink(player) : null;
            NodeMenu.open(player, node, selectedChannel, false, null, preferredStorageLink);
        });
    }

    public static void handleApplyPattern(ApplyPatternPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof PatternSetterMenu menu) {
                if (!menu.applyPattern(payload.useOutputs(), payload.multiplier(), context.player().level()))
                    context.player().displayClientMessage(
                            Component.translatable("message.logisticsnetworks.filter.too_large"), true);
            }
        });
    }

    private static boolean isSpecialMode(FilterMenu menu) {
        return menu.isModMode() || menu.isNameMode();
    }

    private static ItemStack findOpenFilterStack(Player player, java.util.function.Predicate<ItemStack> matcher) {
        if (player.containerMenu instanceof FilterMenu menu) {
            if (!menu.stillValid(player)) return ItemStack.EMPTY;
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
                                level.dimension().location(), node.isRenderVisible(), node.isHighlighted()));
                        break;
                    }
                }
            }

            SyncNetworkNodesPayload.send(player, payload.networkId(), nodeInfos);
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
            GraphPayloadHandler.preserveLabelPosition(node, label);
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
                    LabelUpgradeSync.establishLabelTemplate(player, network, authority,
                            LinkedStorage.findAccessibleLink(player, null));
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
                    || LabelUpgradeSync.synchronizeLabels(player, network, nodes, player.getUUID(), "", null)) {
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
                telemetry.subscribe(payload.networkId(), player, registry, player.getServer());
            } else {
                telemetry.unsubscribe(player);
            }
        });
    }

    public static void handleRequestChannelList(RequestChannelListPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            if (!(player.containerMenu instanceof ComputerMenu)
                    && !(player.containerMenu instanceof ClipboardMenu))
                return;

            NetworkRegistry registry = NetworkRegistry.get(player.serverLevel());
            LogisticsNetwork network = registry.getNetwork(payload.networkId());
            if (network == null || !canAccessNetwork(player, network))
                return;

            List<String> channelNames = new ArrayList<>(LogisticsNodeEntity.CHANNEL_COUNT);
            for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
                channelNames.add(network.getChannelName(i));
            }

            NetworkHandler.sendToPlayer(player, new SyncChannelListPayload(payload.networkId(),
                    channelEntries(network, player.getServer()), channelNames));
        });
    }

    public static List<SyncChannelListPayload.ChannelEntry> channelEntries(LogisticsNetwork network,
            MinecraftServer server) {
        int[] nodeCounts = new int[LogisticsNodeEntity.CHANNEL_COUNT];
        int[] typeOrdinals = new int[LogisticsNodeEntity.CHANNEL_COUNT];
        boolean[] found = new boolean[LogisticsNodeEntity.CHANNEL_COUNT];

        for (UUID nodeId : network.getNodeUuids()) {
            LogisticsNodeEntity node = findNode(server, nodeId);
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
                entries.add(new SyncChannelListPayload.ChannelEntry(i, typeOrdinals[i], nodeCounts[i]));
            }
        }
        return entries;
    }

    public static void handleSetClipboardSettings(SetClipboardSettingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof ClipboardMenu menu))
                return;
            NodeClipboardConfig config = NodeClipboardConfig.loadSettings(payload.clipboardTag(),
                    player.level().registryAccess());
            if (config == null) {
                player.displayClientMessage(Component.translatable("message.logisticsnetworks.clipboard.invalid"), true);
                return;
            }
            config.stripUpgradeComponents();
            if (!menu.replaceSettings(config, player)) {
                player.displayClientMessage(Component.translatable("message.logisticsnetworks.clipboard.invalid"), true);
            }
        });
    }

    public static void handleAddClipboardFilterItem(AddClipboardFilterItemPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof ClipboardMenu menu && menu.stillValid(context.player())) {
                menu.addFilterItem(payload.filterSlot(), payload.item());
            }
        });
    }

    public static void handleRequestNetworkExport(RequestNetworkExportPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            if (!(player.containerMenu instanceof ComputerMenu))
                return;
            int now = player.server.getTickCount();
            if (now - LAST_EXPORT.getOrDefault(player, -EXPORT_COOLDOWN_TICKS) < EXPORT_COOLDOWN_TICKS)
                return;
            LAST_EXPORT.put(player, now);

            NetworkRegistry registry = NetworkRegistry.get(player.serverLevel());
            LogisticsNetwork network = registry.getNetwork(payload.networkId());
            if (network == null || !canAccessNetwork(player, network)) {
                sendNetworkExportError(player, payload.networkId(), "", "not_found");
                return;
            }

            List<SyncNetworkExportPayload.NodeExportInfo> entries = new ArrayList<>();
            Set<String> seenLabels = new HashSet<>();
            int missingLabels = 0;

            for (UUID nodeId : network.getNodeUuids()) {
                LogisticsNodeEntity node = findNode(player, nodeId);
                if (node == null) {
                    missingLabels++;
                    continue;
                }

                String label = node.getNodeLabel().trim();
                if (label.isEmpty()) {
                    missingLabels++;
                    continue;
                }

                if (!seenLabels.add(label)) {
                    continue;
                }
                CompoundTag clipboardTag = NodeClipboardConfig.fromNode(node).save(player.serverLevel().registryAccess());
                entries.add(new SyncNetworkExportPayload.NodeExportInfo(label, node.isRenderVisible(), clipboardTag));
            }

            if (missingLabels > 0) {
                sendNetworkExportError(player, network.getId(), network.getName(), "missing_labels|" + missingLabels);
                return;
            }

            SyncNetworkExportPayload export = new SyncNetworkExportPayload(network.getId(), network.getName(), entries, "");
            if (SyncNetworkExportPayload.encodedSize(export) > EXPORT_BUDGET) {
                sendNetworkExportError(player, network.getId(), network.getName(), "too_large");
                return;
            }
            NetworkHandler.sendToPlayer(player, export);
        });
    }

    private static void sendNetworkExportError(ServerPlayer player, UUID networkId, String networkName, String errorKey) {
        NetworkHandler.sendToPlayer(player,
                new SyncNetworkExportPayload(networkId, networkName, List.of(), trimExportError(errorKey)));
    }

    private static String trimExportError(String errorKey) {
        return errorKey.length() > 256 ? errorKey.substring(0, 256) : errorKey;
    }

    // Filters rebuilt server-side
    public static void handleSetComputerWrenchClipboard(SetComputerWrenchClipboardPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof ComputerMenu menu) || !menu.stillValid(player))
                return;
            NodeClipboardConfig config = NodeClipboardConfig.load(payload.clipboardTag(),
                    player.serverLevel().registryAccess());
            if (config == null || !config.isStructurallyValid()) {
                player.displayClientMessage(Component.translatable("message.logisticsnetworks.lnet.invalid_clipboard"), true);
                return;
            }
            if (config.sanitizeFilters() > 0)
                player.sendSystemMessage(Component.translatable("message.logisticsnetworks.filter.too_large"));
            config.stripUpgradeComponents();
            config.setNetworkTarget(lastPastedNetwork(player, menu, config.getNetworkName()), config.getNetworkName());

            if (!menu.setWrenchClipboard(config, player.serverLevel().registryAccess())) {
                player.displayClientMessage(Component.translatable("message.logisticsnetworks.lnet.no_wrench"), true);
                return;
            }

            player.displayClientMessage(Component.translatable("message.logisticsnetworks.lnet.copied_to_wrench"), true);
        });
    }

    // Rejoin wrench's earlier same-named network
    private static UUID lastPastedNetwork(ServerPlayer player, ComputerMenu menu, String name) {
        NodeClipboardConfig held = WrenchItem.getClipboard(menu.getSlot(0).getItem(),
                player.serverLevel().registryAccess());
        if (held == null || held.getNetworkId() == null)
            return null;
        LogisticsNetwork network = NetworkRegistry.get(player.serverLevel()).getNetwork(held.getNetworkId());
        return network != null && network.getName().equals(name) && canAccessNetwork(player, network)
                ? network.getId() : null;
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
                SyncChannelDataPayload.send(player, node.getId(), channelIndex, tag);
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
