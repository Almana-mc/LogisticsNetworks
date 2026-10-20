package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.DistributionMode;
import me.almana.logisticsnetworks.data.FlowResource;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import me.almana.logisticsnetworks.integration.storage.DirectStorageHandlers;
import me.almana.logisticsnetworks.upgrade.NodeUpgradeData;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class RecipeChannel {

    private static final ItemStack[] NO_FILTERS = new ItemStack[0];

    private RecipeChannel() {
    }

    static List<TransferEngine.ImportTarget> receivers(List<TransferEngine.ImportTarget> items,
            List<TransferEngine.ImportTarget> fluids, List<TransferEngine.ImportTarget> chemicals) {
        List<TransferEngine.ImportTarget> all = new ArrayList<>(items.size() + fluids.size() + chemicals.size());
        all.addAll(items);
        all.addAll(fluids);
        all.addAll(chemicals);
        all.sort((a, b) -> Integer.compare(b.channel().getPriority(), a.channel().getPriority()));
        return all;
    }

    static boolean hasLoose(ChannelData channel) {
        ItemStack[] filters = channel.getFilterItems();
        FilterItemData.ReadCache cache = FilterItemData.createReadCache();
        return switch (channel.getType()) {
            case ITEM -> FilterLogic.hasConfiguredItemFilter(filters, cache);
            case FLUID -> FilterLogic.hasConfiguredFluidFilter(filters, cache);
            case CHEMICAL -> FilterLogic.hasConfiguredChemicalFilter(filters, cache);
            default -> false;
        };
    }

    static int transfer(LogisticsNodeEntity sourceNode, ServerLevel level, ChannelData channel,
            List<TransferEngine.ImportTarget> targets, int batch, int tier, Map<UUID, Boolean> dimCache,
            TransferCapabilityCache caps, boolean telemetry) {
        BlockPos pos = sourceNode.getAttachedPos();
        if (!sourceNode.isMountedOnCreate() && !level.isLoaded(pos))
            return -1;
        FilterItemData.ReadCache cache = FilterItemData.createReadCache();
        List<RecipeCardData> cards = RecipeSets.cards(channel.getFilterItems());
        Set<RecipeCardData.Kind> kinds = RecipeSets.kinds(cards);
        RecipeSets.Source source = source(sourceNode, level, channel, kinds, caps, cache);
        boolean sourceDim = dimCache.getOrDefault(sourceNode.getUUID(), false);
        List<RecipeEndpoint> endpoints = new ArrayList<>();
        List<TransferEngine.ImportTarget> refs = new ArrayList<>();
        boolean reachable = false;
        for (TransferEngine.ImportTarget target : TransferEngine.orderTargets(targets, channel, sourceNode)) {
            LogisticsNodeEntity node = target.node();
            if (node == sourceNode || !node.isValidNode()
                    || !TransferEngine.canReach(sourceNode, node, sourceDim, dimCache))
                continue;
            reachable = true;
            ServerLevel targetLevel = (ServerLevel) node.level();
            if (!node.isMountedOnCreate() && !targetLevel.isLoaded(node.getAttachedPos()))
                continue;
            if (!sourceNode.isMountedOnCreate() && !node.isMountedOnCreate()
                    && TransferEngine.isSameItemStorage(level, pos, targetLevel, node.getAttachedPos()))
                continue;
            endpoints.add(endpoint(target, source, kinds, caps, cache));
            refs.add(target);
        }
        if (!reachable)
            return -1;
        long[] own = new long[1];
        boolean robin = channel.getDistributionMode() == DistributionMode.PRIORITY_ROBIN;
        RecipeSets.Listener listener = (endpoint, resource, amount) -> {
            if (robin) TransferEngine.markServed(channel, refs.get(endpoint));
            if (telemetry) channel.getTelemetry().recordResource(resource, amount);
            if (ownType(channel.getType(), resource)) own[0] += amount;
        };
        int sets = RecipeSets.transfer(source, endpoints, cards, batch, tier, channel.getDistributionMode(),
                listener, level.registryAccess(), cache);
        // Flow total in channel units
        return sets <= 0 ? sets : (int) Math.max(sets, Math.min(Integer.MAX_VALUE, own[0]));
    }

    private static RecipeSets.Source source(LogisticsNodeEntity node, ServerLevel level, ChannelData channel,
            Set<RecipeCardData.Kind> kinds, TransferCapabilityCache caps, FilterItemData.ReadCache cache) {
        Direction side = channel.getIoDirection();
        boolean direct = !FilterLogic.hasConfiguredSlotMapping(channel.getFilterItems(), cache);
        boolean chemicals = kinds.contains(RecipeCardData.Kind.CHEMICAL) && MekanismCompat.isLoaded()
                && NodeUpgradeData.hasMekanismChemicalUpgrade(node);
        return new RecipeSets.Source(
                kinds.contains(RecipeCardData.Kind.ITEM) ? caps.findItemExportHandler(node, side, direct) : null,
                kinds.contains(RecipeCardData.Kind.FLUID) ? caps.findFluidExportHandler(node, side, true) : null,
                chemicals ? caps.findChemicalHandler(level, node.getAttachedPos(), side) : null);
    }

    private static RecipeEndpoint endpoint(TransferEngine.ImportTarget target, RecipeSets.Source source,
            Set<RecipeCardData.Kind> kinds, TransferCapabilityCache caps, FilterItemData.ReadCache cache) {
        return new RecipeEndpoint(
                kinds.contains(RecipeCardData.Kind.ITEM) ? items(target, source.items(), caps, cache) : null,
                kinds.contains(RecipeCardData.Kind.FLUID) ? fluids(target, source.fluids(), caps) : null,
                source.chemicals() != null ? chemicals(target, source.chemicals(), caps) : null);
    }

    @Nullable
    private static TransferEngine.ItemTransferTarget items(TransferEngine.ImportTarget target,
            @Nullable IItemHandler from, TransferCapabilityCache caps, FilterItemData.ReadCache cache) {
        ItemStack[] filters = filtersFor(target.channel(), ChannelType.ITEM);
        boolean mapped = FilterLogic.hasConfiguredSlotMapping(filters, cache);
        IItemHandler handler = caps.findItemImportHandler(target.node(), target.channel().getIoDirection(), !mapped);
        if (handler == null || handler == from || from != null && DirectStorageHandlers.shareNetwork(from, handler))
            return null;
        return new TransferEngine.ItemTransferTarget(handler,
                mapped ? null : caps.findBulkItemHandler(target.node(), handler), filters,
                target.channel().getFilterMode(), TransferAmountRules.collect(NO_FILTERS, filters, cache),
                FilterLogic.hasConfiguredItemNbtFilter(filters, cache), null, mapped);
    }

    @Nullable
    private static TransferEngine.FluidTransferTarget fluids(TransferEngine.ImportTarget target,
            @Nullable IFluidHandler from, TransferCapabilityCache caps) {
        IFluidHandler handler = caps.findFluidImportHandler(target.node(), target.channel().getIoDirection(), true);
        if (handler == null || handler == from || from != null && DirectStorageHandlers.shareNetwork(from, handler))
            return null;
        return new TransferEngine.FluidTransferTarget(handler, filtersFor(target.channel(), ChannelType.FLUID),
                target.channel().getFilterMode());
    }

    @Nullable
    private static RecipeEndpoint.Chemicals chemicals(TransferEngine.ImportTarget target, IChemicalHandler from,
            TransferCapabilityCache caps) {
        LogisticsNodeEntity node = target.node();
        IChemicalHandler handler = caps.findChemicalHandler((ServerLevel) node.level(), node.getAttachedPos(),
                target.channel().getIoDirection());
        if (handler == null || handler == from)
            return null;
        return new RecipeEndpoint.Chemicals(handler, filtersFor(target.channel(), ChannelType.CHEMICAL),
                target.channel().getFilterMode());
    }

    // Receiver filter, own type only
    private static ItemStack[] filtersFor(ChannelData channel, ChannelType type) {
        return channel.getType() == type ? channel.getFilterItems() : NO_FILTERS;
    }

    private static boolean ownType(ChannelType type, FlowResource resource) {
        return switch (resource) {
            case FlowResource.Item item -> type == ChannelType.ITEM;
            case FlowResource.Fluid fluid -> type == ChannelType.FLUID;
            case FlowResource.Chemical chemical -> type == ChannelType.CHEMICAL;
        };
    }
}
