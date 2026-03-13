package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.data.*;
import me.almana.logisticsnetworks.integration.ftbteams.FTBTeamsCompat;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.filter.*;
import me.almana.logisticsnetworks.item.*;
import me.almana.logisticsnetworks.menu.ComputerMenu;
import me.almana.logisticsnetworks.menu.FilterMenu;
import me.almana.logisticsnetworks.menu.NodeMenu;
import me.almana.logisticsnetworks.menu.PatternSetterMenu;
import me.almana.logisticsnetworks.registration.ModTags;
import me.almana.logisticsnetworks.upgrade.NodeUpgradeData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import me.almana.logisticsnetworks.network.SetFilterChemicalEntryPayload;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class ServerPayloadHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

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
            propagateToLabelGroup(node, payload.channelIndex());
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

            // Resolve target first, before removing from old network
            // (removing may delete the old network if it becomes empty)
            LogisticsNetwork targetNetwork = resolveNetwork(registry, payload, player);
            if (targetNetwork == null)
                return;

            UUID oldNetworkId = node.getNetworkId();
            // Skip remove+add if already on the same network
            if (oldNetworkId != null && oldNetworkId.equals(targetNetwork.getId())) {
                // Just update the name in case it changed
                node.setNetworkName(targetNetwork.getName());
                if (player.containerMenu instanceof NodeMenu menu) {
                    menu.sendNetworkListToClient(player);
                }
                return;
            }

            if (oldNetworkId != null) {
                registry.removeNodeFromNetwork(oldNetworkId, node.getUUID());
            }

            // Claim unowned networks on first selection
            if (targetNetwork.getOwnerUuid() == null) {
                targetNetwork.setOwnerUuid(player.getUUID());
            }

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

    private static LogisticsNetwork resolveNetwork(NetworkRegistry registry, AssignNetworkPayload payload,
            ServerPlayer player) {
        if (payload.networkId().isPresent()) {
            LogisticsNetwork network = registry.getNetwork(payload.networkId().get());
            if (network == null)
                return null;
            // Verify the player owns this network (or it's unowned, teammate, or op)
            if (network.getOwnerUuid() != null
                    && !network.getOwnerUuid().equals(player.getUUID())
                    && !(FTBTeamsCompat.isLoaded() && FTBTeamsCompat.arePlayersInSameTeam(network.getOwnerUuid(), player.getUUID()))
                    && !player.hasPermissions(2)) {
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

            // Ownership check: must own the network (or it's unowned, teammate, or op)
            if (network.getOwnerUuid() != null
                    && !network.getOwnerUuid().equals(player.getUUID())
                    && !(FTBTeamsCompat.isLoaded() && FTBTeamsCompat.arePlayersInSameTeam(network.getOwnerUuid(), player.getUUID()))
                    && !player.hasPermissions(2)) {
                return;
            }

            network.setName(newName);
            registry.setDirty();

            // Update network name on all nodes in this network
            for (java.util.UUID nodeId : network.getNodeUuids()) {
                for (ServerLevel level : player.getServer().getAllLevels()) {
                    Entity entity = level.getEntity(nodeId);
                    if (entity instanceof LogisticsNodeEntity node) {
                        node.setNetworkName(newName);
                        break;
                    }
                }
            }

            // Resend the network list to the player
            if (player.containerMenu instanceof NodeMenu menu) {
                menu.sendNetworkListToClient(player);
            }
        });
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

    public static void handleSetFilter(SetFilterPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LogisticsNodeEntity node = getNode(context, payload.entityId());
            if (node == null)
                return;
            ChannelData channel = node.getChannel(payload.channelIndex());
            if (channel != null) {
                channel.setFilterItem(payload.filterSlot(), payload.filterItem().copyWithCount(1));
                propagateToLabelGroup(node, payload.channelIndex());
                markNetworkDirty(node);
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
            propagateToLabelGroup(node, payload.channelIndex());
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

    public static void handleSetFilterEntryAmount(SetFilterEntryAmountPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof FilterMenu menu && !menu.isAmountMode()) {
                menu.setEntryAmount((Player) context.player(), payload.slot(), payload.amount());
            }
        });
    }

    public static void handleSetFilterEntryTag(SetFilterEntryTagPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof FilterMenu menu && !isSpecialMode(menu)) {
                if (payload.tag() == null || payload.tag().isEmpty()) {
                    menu.clearEntryTag(payload.slot());
                } else {
                    menu.setEntryTag((Player) context.player(), payload.slot(), payload.tag());
                }
            }
        });
    }

    public static void handleSetFilterEntryNbt(SetFilterEntryNbtPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof FilterMenu menu && !isSpecialMode(menu)) {
                if (payload.remove()) {
                    menu.clearEntryNbt((Player) context.player(), payload.slot());
                } else if (!payload.rawValue().isEmpty()) {
                    menu.setEntryNbtRaw((Player) context.player(), payload.slot(), payload.path(), payload.rawValue());
                } else {
                    menu.setEntryNbt((Player) context.player(), payload.slot(), payload.path());
                }
            }
        });
    }

    public static void handleSetFilterEntryDurability(SetFilterEntryDurabilityPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof FilterMenu menu && !isSpecialMode(menu)) {
                if (payload.operator() == null || payload.operator().isEmpty()) {
                    menu.clearEntryDurability((Player) context.player(), payload.slot());
                } else {
                    menu.setEntryDurability((Player) context.player(), payload.slot(),
                            payload.operator(), payload.value());
                }
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

            boolean isTag = stack.getItem() instanceof TagFilterItem;
            boolean isAmount = stack.getItem() instanceof AmountFilterItem;
            boolean isNbt = stack.getItem() instanceof NbtFilterItem;
            boolean isDurability = stack.getItem() instanceof DurabilityFilterItem;
            boolean isMod = stack.getItem() instanceof ModFilterItem;
            boolean isSlot = stack.getItem() instanceof SlotFilterItem;
            boolean isName = stack.getItem() instanceof NameFilterItem;
            boolean isSpecial = isTag || isAmount || isNbt || isDurability || isMod || isSlot || isName;
            int slotCount = isSpecial ? 0 : Math.max(1, FilterItemData.getCapacity(stack));

            serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new FilterMenu(id, inv, slotIndex),
                    stack.getHoverName()), buf -> {
                        buf.writeVarInt(-1);
                        buf.writeVarInt(slotIndex);
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
                menu.applyPattern(payload.useOutputs(), payload.multiplier(), context.player().level().registryAccess());
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
            case SOURCE -> NodeUpgradeData.getSourceOperationCap(node);
            default -> NodeUpgradeData.getItemOperationCap(node);
        };
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

            PacketDistributor.sendToPlayer(player,
                    new SyncNetworkNodesPayload(payload.networkId(), nodeInfos));
        });
    }

    public static void handleSetNodeLabel(SetNodeLabelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LogisticsNodeEntity node = getNode(context, payload.entityId());
            if (node == null)
                return;

            String label = payload.label().trim();
            if (label.length() > 48)
                label = label.substring(0, 48);

            LOGGER.debug("[LabelSync] Setting label '{}' on node {} (networkId={})",
                    label, node.getUUID(), node.getNetworkId());
            node.setNodeLabel(label);

            // If label is non-empty and other nodes in the network have this label,
            // copy their config onto this node
            if (!label.isEmpty() && node.getNetworkId() != null
                    && node.level() instanceof ServerLevel level) {
                NetworkRegistry registry = NetworkRegistry.get(level);
                LogisticsNetwork network = registry.getNetwork(node.getNetworkId());
                if (network != null) {
                    LOGGER.debug("[LabelSync] Searching {} nodes in network for label '{}'",
                            network.getNodeUuids().size(), label);
                    for (UUID otherId : network.getNodeUuids()) {
                        if (otherId.equals(node.getUUID()))
                            continue;
                        for (ServerLevel sl : level.getServer().getAllLevels()) {
                            Entity entity = sl.getEntity(otherId);
                            if (entity instanceof LogisticsNodeEntity other
                                    && label.equals(other.getNodeLabel())) {
                                LOGGER.debug("[LabelSync] Found matching node {}, copying all channels", otherId);
                                // Copy all channels from the existing labeled node
                                for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
                                    ChannelData src = other.getChannel(i);
                                    ChannelData dst = node.getChannel(i);
                                    if (src != null && dst != null) {
                                        dst.copyFrom(src);
                                        clampChannelToUpgradeLimits(node, dst);
                                        sendChannelSyncToViewers(node, i, dst);
                                    }
                                }
                                markNetworkDirty(node);
                                return;
                            }
                        }
                    }
                    LOGGER.debug("[LabelSync] No matching labeled node found in network");
                }
            }
        });
    }

    public static void handleSetNetworkNodesVisibility(SetNetworkNodesVisibilityPayload payload, IPayloadContext context) {
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

    public static void handleToggleNetworkNodeHighlight(ToggleNetworkNodeHighlightPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            if (!(player.containerMenu instanceof ComputerMenu))
                return;

            NetworkRegistry registry = NetworkRegistry.get(player.serverLevel());
            LogisticsNetwork network = registry.getNetwork(payload.networkId());
            if (network == null || !canAccessNetwork(player, network) || !network.getNodeUuids().contains(payload.nodeId())) {
                return;
            }

            LogisticsNodeEntity node = findNode(player, payload.nodeId());
            if (node != null) {
                node.setHighlighted(!node.isHighlighted());
            }
        });
    }

    public static void handleToggleNetworkLabelHighlight(ToggleNetworkLabelHighlightPayload payload, IPayloadContext context) {
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
            if (network == null)
                return;

            Set<String> labels = new LinkedHashSet<>();
            for (UUID nodeId : network.getNodeUuids()) {
                for (ServerLevel level : player.getServer().getAllLevels()) {
                    Entity entity = level.getEntity(nodeId);
                    if (entity instanceof LogisticsNodeEntity node) {
                        String label = node.getNodeLabel();
                        if (!label.isEmpty()) {
                            labels.add(label);
                        }
                        break;
                    }
                }
            }

            PacketDistributor.sendToPlayer(player,
                    new SyncNetworkLabelsPayload(new ArrayList<>(labels)));
        });
    }

    public static void propagateToLabelGroup(LogisticsNodeEntity sourceNode, int channelIndex) {
        String label = sourceNode.getNodeLabel();
        if (label.isEmpty() || sourceNode.getNetworkId() == null) {
            LOGGER.debug("[LabelSync] Skipping propagation: label='{}', networkId={}", label, sourceNode.getNetworkId());
            return;
        }
        if (!(sourceNode.level() instanceof ServerLevel level))
            return;

        ChannelData sourceChannel = sourceNode.getChannel(channelIndex);
        if (sourceChannel == null)
            return;

        NetworkRegistry registry = NetworkRegistry.get(level);
        LogisticsNetwork network = registry.getNetwork(sourceNode.getNetworkId());
        if (network == null) {
            LOGGER.debug("[LabelSync] Network not found for id={}", sourceNode.getNetworkId());
            return;
        }

        LOGGER.debug("[LabelSync] Propagating channel {} from node {} (label='{}') to {} network nodes",
                channelIndex, sourceNode.getUUID(), label, network.getNodeUuids().size());

        int updated = 0;
        for (UUID otherId : network.getNodeUuids()) {
            if (otherId.equals(sourceNode.getUUID()))
                continue;
            for (ServerLevel sl : level.getServer().getAllLevels()) {
                Entity entity = sl.getEntity(otherId);
                if (entity instanceof LogisticsNodeEntity other
                        && label.equals(other.getNodeLabel())) {
                    ChannelData dst = other.getChannel(channelIndex);
                    if (dst != null) {
                        dst.copyFrom(sourceChannel);
                        clampChannelToUpgradeLimits(other, dst);
                        updated++;
                        LOGGER.debug("[LabelSync] Updated node {} (label='{}')", otherId, other.getNodeLabel());
                        // Notify any player who has this node's menu open
                        sendChannelSyncToViewers(other, channelIndex, dst);
                    }
                    break;
                }
            }
        }
        LOGGER.debug("[LabelSync] Propagation complete: {} nodes updated", updated);
    }

    private static boolean canAccessNetwork(ServerPlayer player, LogisticsNetwork network) {
        return network.getOwnerUuid() == null
                || network.getOwnerUuid().equals(player.getUUID())
                || (FTBTeamsCompat.isLoaded()
                        && FTBTeamsCompat.arePlayersInSameTeam(network.getOwnerUuid(), player.getUUID()))
                || player.hasPermissions(2);
    }

    private static LogisticsNodeEntity findNode(ServerPlayer player, UUID nodeId) {
        for (ServerLevel level : player.getServer().getAllLevels()) {
            Entity entity = level.getEntity(nodeId);
            if (entity instanceof LogisticsNodeEntity node) {
                return node;
            }
        }
        return null;
    }

    private static void sendChannelSyncToViewers(LogisticsNodeEntity node, int channelIndex, ChannelData channel) {
        if (!(node.level() instanceof ServerLevel level))
            return;
        CompoundTag tag = channel.save(level.registryAccess());
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof NodeMenu menu
                    && menu.getNode() != null
                    && menu.getNode().getUUID().equals(node.getUUID())) {
                PacketDistributor.sendToPlayer(player,
                        new SyncChannelDataPayload(node.getId(), channelIndex, tag));
            }
        }
    }
}
