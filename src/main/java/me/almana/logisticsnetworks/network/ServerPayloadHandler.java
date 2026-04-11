package me.almana.logisticsnetworks.network;

import me.almana.logisticsnetworks.util.ItemStackCompat;

import me.almana.logisticsnetworks.data.*;
import me.almana.logisticsnetworks.integration.ftbteams.FTBTeamsCompat;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.logic.TelemetryManager;
import me.almana.logisticsnetworks.filter.*;
import me.almana.logisticsnetworks.item.*;
import me.almana.logisticsnetworks.menu.ComputerMenu;
import me.almana.logisticsnetworks.menu.FilterMenu;
import me.almana.logisticsnetworks.menu.NodeMenu;
import me.almana.logisticsnetworks.menu.PatternSetterMenu;
import me.almana.logisticsnetworks.registration.ModTags;
import me.almana.logisticsnetworks.upgrade.NodeUpgradeData;
import me.almana.logisticsnetworks.integration.mekanism.ChemicalTransferHelper;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.network.NetworkHooks;
import me.almana.logisticsnetworks.network.payload.IPayloadContext;
import me.almana.logisticsnetworks.network.SetFilterChemicalEntryPayload;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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

            ChannelType oldType = channel.getType();
            updateChannelData(channel, payload);
            if (oldType != channel.getType())
                autoDetectSide(node, channel);
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
            LogisticsNodeEntity node = getNode(context, payload.entityId());
            if (node == null)
                return;

            NetworkRegistry registry = NetworkRegistry.get(player.serverLevel());

            LogisticsNetwork targetNetwork = resolveNetwork(registry, payload, player);
            if (targetNetwork == null)
                return;

            UUID oldNetworkId = node.getNetworkId();
            if (oldNetworkId != null && oldNetworkId.equals(targetNetwork.getId())) {
                node.setNetworkName(targetNetwork.getName());
                if (player.containerMenu instanceof NodeMenu menu) {
                    menu.sendNetworkListToClient(player);
                }
                return;
            }

            if (oldNetworkId != null) {
                registry.removeNodeFromNetwork(oldNetworkId, node.getUUID());
            }

            if (targetNetwork.getOwnerUuid() == null) {
                targetNetwork.setOwnerUuid(player.getUUID());
            }

            node.setNetworkId(targetNetwork.getId());
            node.setNetworkName(targetNetwork.getName());
            registry.addNodeToNetwork(targetNetwork.getId(), node.getUUID());

            for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
                ChannelData ch = node.getChannel(i);
                if (ch != null) {
                    ch.setName(targetNetwork.getChannelName(i));
                }
            }

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
            if (network.getOwnerUuid() != null
                    && !network.getOwnerUuid().equals(player.getUUID())
                    && !(FTBTeamsCompat.isLoaded()
                            && FTBTeamsCompat.arePlayersInSameTeam(network.getOwnerUuid(), player.getUUID()))
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

            if (network.getOwnerUuid() != null
                    && !network.getOwnerUuid().equals(player.getUUID())
                    && !(FTBTeamsCompat.isLoaded()
                            && FTBTeamsCompat.arePlayersInSameTeam(network.getOwnerUuid(), player.getUUID()))
                    && !player.hasPermissions(2)) {
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
                channel.setFilterItem(payload.filterSlot(), ItemStackCompat.copyWithCount(payload.filterItem(), 1));
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
                    payload.filterItem().is(ModTags.FILTERS) ? ItemStackCompat.copyWithCount(payload.filterItem(), 1) : ItemStack.EMPTY);
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
                String normalizedTag = FilterTagUtil.normalizeTag(payload.tag());
                boolean changed = normalizedTag != null
                        && (payload.remove() ? TagFilterData.removeTagFilter(filterStack, normalizedTag)
                                : TagFilterData.addTagFilter(filterStack, normalizedTag));
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

            NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
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

    public static void handleOpenNodeFilter(OpenNodeFilterPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer))
                return;

            Entity entity = serverPlayer.level().getEntity(payload.entityId());
            if (!(entity instanceof LogisticsNodeEntity node))
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

    private static void setChannelToUpgradeMax(LogisticsNodeEntity node, ChannelData channel) {
        channel.setBatchSize(getMaxBatch(node, channel.getType()));
        channel.setTickDelay(channel.getType() == ChannelType.ENERGY ? 1 : NodeUpgradeData.getMinTickDelay(node));
    }

    private static void clampChannelToUpgradeLimits(LogisticsNodeEntity node, ChannelData channel) {
        int maxBatch = getMaxBatch(node, channel.getType());

        channel.setBatchSize(Math.max(1, Math.min(channel.getBatchSize(), maxBatch)));

        if (channel.getType() == ChannelType.ENERGY) {
            channel.setTickDelay(1);
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
            LogisticsNodeEntity node = getNode(context, payload.entityId());
            if (node == null)
                return;

            String label = payload.label().trim();
            if (label.length() > 48)
                label = label.substring(0, 48);

            LOGGER.debug("[LabelSync] Setting label '{}' on node {} (networkId={})",
                    label, node.getUUID(), node.getNetworkId());
            node.setNodeLabel(label);

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

    public static void handleSetChannelName(SetChannelNamePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LogisticsNodeEntity node = getNode(context, payload.entityId());
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

            markNetworkDirty(node);
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
            if (node == null)
                return;

            NetworkHooks.openScreen(player, new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("gui.logisticsnetworks.node_config");
                }

                @Override
                public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player p) {
                    return new NodeMenu(containerId, playerInv, node);
                }
            }, buf -> {
                buf.writeVarInt(node.getId());
                for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
                    ChannelData ch = node.getChannel(i);
                    buf.writeNbt(ch != null ? ch.save(player.level().registryAccess()) : new CompoundTag());
                }
                for (int i = 0; i < LogisticsNodeEntity.UPGRADE_SLOT_COUNT; i++) {
                    buf.writeNbt(ItemStackCompat.saveOptional(node.getUpgradeItem(i), player.level().registryAccess()));
                }
            });

            if (player.containerMenu instanceof NodeMenu menu) {
                menu.setRemoteAccess(true);
                menu.sendNetworkListToClient(player);
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

            NetworkHandler.sendToPlayer(player,
                    new SyncNetworkLabelsPayload(new ArrayList<>(labels)));
        });
    }

    public static void propagateToLabelGroup(LogisticsNodeEntity sourceNode, int channelIndex) {
        String label = sourceNode.getNodeLabel();
        if (label.isEmpty() || sourceNode.getNetworkId() == null) {
            LOGGER.debug("[LabelSync] Skipping propagation: label='{}', networkId={}", label,
                    sourceNode.getNetworkId());
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
                        sendChannelSyncToViewers(other, channelIndex, dst);
                    }
                    break;
                }
            }
        }
        LOGGER.debug("[LabelSync] Propagation complete: {} nodes updated", updated);
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
                NetworkHandler.sendToPlayer(player,
                        new SyncChannelDataPayload(node.getId(), channelIndex, tag));
            }
        }
    }
}
