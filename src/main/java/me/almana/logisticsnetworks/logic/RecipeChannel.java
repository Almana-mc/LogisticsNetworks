package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.DistributionMode;
import me.almana.logisticsnetworks.data.IoSide;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.integration.storage.DirectStorageHandlers;
import me.almana.logisticsnetworks.upgrade.NodeUpgradeData;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.BlockPos;
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
import java.util.stream.Stream;

final class RecipeChannel {

    private static final ItemStack[] NO_FILTERS = new ItemStack[0];

    private RecipeChannel() {
    }

    static List<TransferEngine.ImportTarget> receivers(List<TransferEngine.ImportTarget> items,
            List<TransferEngine.ImportTarget> fluids, List<TransferEngine.ImportTarget> chemicals) {
        return Stream.of(items, fluids, chemicals).flatMap(List::stream).distinct().sorted(BridgedImports.ORDER)
                .toList();
    }

    // Card kinds only move loose entries
    static boolean runsLoose(ChannelData channel, ChannelType type) {
        ItemStack[] filters = channel.getFilterItems();
        boolean cardKind = RecipeSets.kinds(RecipeSets.cards(filters)).stream()
                .anyMatch(kind -> kind.channel == type);
        if (!cardKind)
            return true;
        FilterItemData.ReadCache cache = FilterItemData.createReadCache();
        return switch (type) {
            case ITEM -> FilterLogic.hasConfiguredItemFilter(filters, cache);
            case FLUID -> FilterLogic.hasConfiguredFluidFilter(filters, cache);
            case CHEMICAL -> FilterLogic.hasConfiguredChemicalFilter(filters, cache);
            default -> true;
        };
    }

    static int transfer(LogisticsNodeEntity sourceNode, ServerLevel level, ChannelData channel,
            List<TransferEngine.ImportTarget> targets, int batch, int tier, Map<UUID, Boolean> dimCache,
            TransferCapabilityCache caps, boolean telemetry) {
        if (!sourceNode.isMountedOnCreate() && !level.isLoaded(sourceNode.getAttachedPos()))
            return -1;
        FilterItemData.ReadCache cache = FilterItemData.createReadCache();
        List<RecipeCardData> cards = RecipeSets.cards(channel.getFilterItems());
        Set<RecipeCardData.Kind> kinds = RecipeSets.kinds(cards);
        RecipeSets.Source source = source(sourceNode, level, channel, kinds, caps, cache);
        List<RecipeEndpoint> endpoints = new ArrayList<>();
        List<TransferEngine.ImportTarget> refs = new ArrayList<>();
        if (!connect(sourceNode, level, channel, targets, dimCache, source, kinds, caps, cache, endpoints, refs))
            return -1;
        boolean robin = channel.getDistributionMode() == DistributionMode.PRIORITY_ROBIN;
        RecipeSets.Listener listener = (endpoint, resource, amount) -> {
            if (robin) {
                TransferEngine.ImportTarget ref = refs.get(endpoint);
                channel.setRecipeRobinCursor(new PriorityRobin.Cursor(ref.channel().getPriority(), ref.node().getUUID()));
            }
            if (telemetry) {
                channel.getTelemetry().recordResource(resource, amount);
                channel.getTelemetry().record(resource.type(), amount);
            }
        };
        return RecipeSets.transfer(source, endpoints, cards, batch, tier, channel.getDistributionMode(),
                listener, level.registryAccess(), cache);
    }

    // False when unreachable or paused
    private static boolean connect(LogisticsNodeEntity sourceNode, ServerLevel level, ChannelData channel,
            List<TransferEngine.ImportTarget> targets, Map<UUID, Boolean> dimCache, RecipeSets.Source source,
            Set<RecipeCardData.Kind> kinds, TransferCapabilityCache caps, FilterItemData.ReadCache cache,
            List<RecipeEndpoint> endpoints, List<TransferEngine.ImportTarget> refs) {
        BlockPos pos = sourceNode.getAttachedPos();
        boolean sourceDim = dimCache.getOrDefault(sourceNode.getUUID(), false);
        boolean reachable = false;
        boolean usable = false;
        boolean unavailableMounted = false;
        boolean stationary = false;
        List<TransferEngine.ImportTarget> ordered = channel.getDistributionMode() == DistributionMode.PRIORITY_ROBIN
                ? PriorityRobin.rotate(targets, channel.getRecipeRobinCursor(), t -> t.channel().getPriority(),
                        t -> t.node().getUUID())
                : TransferEngine.orderTargets(targets, channel, sourceNode, ChannelType.ITEM);
        for (TransferEngine.ImportTarget target : ordered) {
            LogisticsNodeEntity node = target.node();
            if (node == sourceNode || !node.isValidNode()
                    || !TransferEngine.canReach(sourceNode, node, sourceDim, dimCache))
                continue;
            reachable = true;
            stationary |= !node.isMountedOnCreate();
            ServerLevel targetLevel = (ServerLevel) node.level();
            if (!node.isMountedOnCreate() && !targetLevel.isLoaded(node.getAttachedPos()))
                continue;
            if (!sourceNode.isMountedOnCreate() && !node.isMountedOnCreate()
                    && TransferEngine.isSameItemStorage(level, pos, targetLevel, node.getAttachedPos()))
                continue;
            RecipeEndpoint endpoint = endpoint(target, source, kinds, caps, cache);
            if (endpoint.items() == null && endpoint.fluids() == null && endpoint.chemicals() == null) {
                unavailableMounted |= node.isMountedOnCreate();
                continue;
            }
            usable = true;
            endpoints.add(endpoint);
            refs.add(target);
        }
        return reachable
                && !TransferEngine.shouldPauseForUnavailableMountedTargets(usable, unavailableMounted, stationary);
    }

    private static RecipeSets.Source source(LogisticsNodeEntity node, ServerLevel level, ChannelData channel,
            Set<RecipeCardData.Kind> kinds, TransferCapabilityCache caps, FilterItemData.ReadCache cache) {
        IoSide side = channel.getIoSide();
        boolean direct = !FilterLogic.hasConfiguredSlotMapping(channel.getFilterItems(), cache);
        int types = NodeUpgradeData.effectiveTypes(node, channel);
        boolean chemicals = kinds.contains(RecipeCardData.Kind.CHEMICAL) && ChannelType.CHEMICAL.in(types);
        return new RecipeSets.Source(
                kinds.contains(RecipeCardData.Kind.ITEM) && ChannelType.ITEM.in(types)
                        ? caps.findItemExportHandler(node, side, direct) : null,
                kinds.contains(RecipeCardData.Kind.FLUID) && ChannelType.FLUID.in(types)
                        ? caps.findFluidExportHandler(node, side, true) : null,
                chemicals ? caps.findChemicalHandler(level, node.getAttachedPos(), side) : null);
    }

    private static RecipeEndpoint endpoint(TransferEngine.ImportTarget target, RecipeSets.Source source,
            Set<RecipeCardData.Kind> kinds, TransferCapabilityCache caps, FilterItemData.ReadCache cache) {
        int accepts = NodeUpgradeData.effectiveTypes(target.node(), target.channel());
        return new RecipeEndpoint(
                source.items() != null && kinds.contains(RecipeCardData.Kind.ITEM) && ChannelType.ITEM.in(accepts)
                        ? items(target, source.items(), caps, cache) : null,
                source.fluids() != null && kinds.contains(RecipeCardData.Kind.FLUID) && ChannelType.FLUID.in(accepts)
                        ? fluids(target, source.fluids(), caps) : null,
                source.chemicals() != null && ChannelType.CHEMICAL.in(accepts)
                        ? chemicals(target, source.chemicals(), caps) : null);
    }

    @Nullable
    private static TransferEngine.ItemTransferTarget items(TransferEngine.ImportTarget target,
            @Nullable IItemHandler from, TransferCapabilityCache caps, FilterItemData.ReadCache cache) {
        ItemStack[] filters = target.channel().getFilterItems();
        boolean mapped = FilterLogic.hasConfiguredSlotMapping(filters, cache);
        IItemHandler handler = caps.findItemImportHandler(target.node(), target.channel().getIoSide(), !mapped);
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
        IFluidHandler handler = caps.findFluidImportHandler(target.node(), target.channel().getIoSide(), true);
        if (handler == null || handler == from || from != null && DirectStorageHandlers.shareNetwork(from, handler))
            return null;
        return new TransferEngine.FluidTransferTarget(handler, target.channel().getFilterItems(),
                target.channel().getFilterMode());
    }

    @Nullable
    private static RecipeEndpoint.Chemicals chemicals(TransferEngine.ImportTarget target, IChemicalHandler from,
            TransferCapabilityCache caps) {
        LogisticsNodeEntity node = target.node();
        if (node.isMountedOnCreate())
            return null;
        IChemicalHandler handler = caps.findChemicalHandler((ServerLevel) node.level(), node.getAttachedPos(),
                target.channel().getIoSide());
        if (handler == null || handler == from)
            return null;
        return new RecipeEndpoint.Chemicals(handler, target.channel().getFilterItems(),
                target.channel().getFilterMode());
    }
}
