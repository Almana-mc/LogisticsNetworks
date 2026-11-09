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
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
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

    // Cards allow loose entries only
    static boolean runsLoose(ChannelData channel, ChannelType type) {
        ItemStack[] filters = channel.getFilterItems();
        FilterItemData.ReadCache cache = channel.getReadCache();
        return switch (type) {
            case ITEM -> FilterLogic.hasConfiguredItemFilter(filters, cache);
            case FLUID -> FilterLogic.hasConfiguredFluidFilter(filters, cache);
            case CHEMICAL -> FilterLogic.hasConfiguredChemicalFilter(filters, cache);
            default -> false;
        };
    }

    static int transfer(LogisticsNodeEntity sourceNode, ServerLevel level, ChannelData channel,
            List<TransferEngine.ImportTarget> targets, int batch, int tier, Map<UUID, Boolean> dimCache,
            boolean telemetry) {
        if (!level.isLoaded(sourceNode.getAttachedPos()))
            return -1;
        FilterItemData.ReadCache cache = channel.getReadCache();
        List<RecipeCardData> cards = RecipeSets.cards(channel.getFilterItems());
        Set<RecipeCardData.Kind> kinds = RecipeSets.kinds(cards);
        RecipeSets.Source source = source(sourceNode, channel, kinds, cache);
        List<RecipeEndpoint> endpoints = new ArrayList<>();
        List<TransferEngine.ImportTarget> refs = new ArrayList<>();
        if (!connect(sourceNode, level, channel, targets, dimCache, source, kinds, cache, endpoints, refs))
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

    // False when unreachable
    private static boolean connect(LogisticsNodeEntity sourceNode, ServerLevel level, ChannelData channel,
            List<TransferEngine.ImportTarget> targets, Map<UUID, Boolean> dimCache, RecipeSets.Source source,
            Set<RecipeCardData.Kind> kinds, FilterItemData.ReadCache cache,
            List<RecipeEndpoint> endpoints, List<TransferEngine.ImportTarget> refs) {
        BlockPos pos = sourceNode.getAttachedPos();
        boolean sourceDim = dimCache.getOrDefault(sourceNode.getUUID(), false);
        boolean reachable = false;
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
            ServerLevel targetLevel = (ServerLevel) node.level();
            if (!targetLevel.isLoaded(node.getAttachedPos()))
                continue;
            if (TransferEngine.isSameItemStorage(level, pos, targetLevel, node.getAttachedPos()))
                continue;
            RecipeEndpoint endpoint = endpoint(target, source, kinds, cache);
            if (endpoint.items() == null && endpoint.fluids() == null)
                continue;
            endpoints.add(endpoint);
            refs.add(target);
        }
        return reachable;
    }

    private static RecipeSets.Source source(LogisticsNodeEntity node, ChannelData channel,
            Set<RecipeCardData.Kind> kinds, FilterItemData.ReadCache cache) {
        IoSide side = channel.getIoSide();
        boolean direct = !FilterLogic.hasConfiguredSlotMapping(channel.getFilterItems(), cache);
        int types = NodeUpgradeData.effectiveTypes(node, channel);
        // Enable when 26.1.2 is supported.
        /*
        boolean chemicals = kinds.contains(RecipeCardData.Kind.CHEMICAL) && ChannelType.CHEMICAL.in(types);
        Third Source argument: chemicals ? caps.findChemicalHandler(level, node.getAttachedPos(), side) : null
        */
        return new RecipeSets.Source(
                kinds.contains(RecipeCardData.Kind.ITEM) && ChannelType.ITEM.in(types)
                        ? node.capabilities().findItemExportHandler(side, direct) : null,
                kinds.contains(RecipeCardData.Kind.FLUID) && ChannelType.FLUID.in(types)
                        ? node.capabilities().findFluidExportHandler(side, true) : null);
    }

    private static RecipeEndpoint endpoint(TransferEngine.ImportTarget target, RecipeSets.Source source,
            Set<RecipeCardData.Kind> kinds, FilterItemData.ReadCache cache) {
        int accepts = NodeUpgradeData.effectiveTypes(target.node(), target.channel());
        // Enable when 26.1.2 is supported.
        /*
        Third RecipeEndpoint argument: source.chemicals() != null && ChannelType.CHEMICAL.in(accepts)
                ? chemicals(target, source.chemicals(), caps) : null
        */
        return new RecipeEndpoint(
                source.items() != null && kinds.contains(RecipeCardData.Kind.ITEM) && ChannelType.ITEM.in(accepts)
                        ? items(target, source.items(), cache) : null,
                source.fluids() != null && kinds.contains(RecipeCardData.Kind.FLUID) && ChannelType.FLUID.in(accepts)
                        ? fluids(target, source.fluids()) : null);
    }

    @Nullable
    private static TransferEngine.ItemTransferTarget items(TransferEngine.ImportTarget target,
            @Nullable ResourceHandler<ItemResource> from, FilterItemData.ReadCache cache) {
        ItemStack[] filters = target.channel().getFilterItems();
        boolean mapped = FilterLogic.hasConfiguredSlotMapping(filters, cache);
        ResourceHandler<ItemResource> handler = target.node().capabilities()
                .findItemImportHandler(target.channel().getIoSide(), !mapped);
        if (handler == null || handler == from || from != null && DirectStorageHandlers.shareNetwork(from, handler))
            return null;
        return new TransferEngine.ItemTransferTarget(handler, filters, target.channel().getFilterMode(),
                TransferAmountRules.collect(NO_FILTERS, filters, cache),
                FilterLogic.hasConfiguredItemNbtFilter(filters, cache), null, mapped);
    }

    @Nullable
    private static TransferEngine.FluidTransferTarget fluids(TransferEngine.ImportTarget target,
            @Nullable ResourceHandler<FluidResource> from) {
        ResourceHandler<FluidResource> handler = target.node().capabilities()
                .findFluidImportHandler(target.channel().getIoSide(), true);
        if (handler == null || handler == from || from != null && DirectStorageHandlers.shareNetwork(from, handler))
            return null;
        return new TransferEngine.FluidTransferTarget(handler, target.channel().getFilterItems(),
                target.channel().getFilterMode());
    }

    // Enable when 26.1.2 is supported.
    /*
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
    */
}
