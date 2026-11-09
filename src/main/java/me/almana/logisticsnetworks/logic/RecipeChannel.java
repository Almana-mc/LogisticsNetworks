package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.DistributionMode;
import me.almana.logisticsnetworks.data.FlowResource;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.integration.storage.DirectStorageHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
        all.sort(BridgedImports.ORDER);
        return all;
    }

    static boolean hasLoose(ChannelData channel) {
        ItemStack[] filters = channel.getFilterItems();
        FilterItemData.ReadCache cache = channel.getReadCache();
        return switch (channel.getType()) {
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
        long[] own = new long[1];
        boolean robin = channel.getDistributionMode() == DistributionMode.PRIORITY_ROBIN;
        RecipeSets.Listener listener = (endpoint, resource, amount) -> {
            if (robin) {
                TransferEngine.ImportTarget ref = refs.get(endpoint);
                channel.setRecipeRobinCursor(new PriorityRobin.Cursor(ref.channel().getPriority(), ref.node().getUUID()));
            }
            if (telemetry) channel.getTelemetry().recordResource(resource, amount);
            if (ownType(channel.getType(), resource)) own[0] += amount;
        };
        int sets = RecipeSets.transfer(source, endpoints, cards, batch, tier, channel.getDistributionMode(),
                listener, level.registryAccess(), cache);
        // Flow total in channel units
        return sets < 0 ? sets : (int) Math.max(sets, Math.min(Integer.MAX_VALUE, own[0]));
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
                : TransferEngine.orderTargets(targets, channel, sourceNode);
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
        Direction side = channel.getIoDirection();
        boolean direct = !FilterLogic.hasConfiguredSlotMapping(channel.getFilterItems(), cache);
        // Enable when 26.1.2 is supported.
        /*
        boolean chemicals = kinds.contains(RecipeCardData.Kind.CHEMICAL) && !node.isMountedOnCreate() && MekanismCompat.isLoaded()
                && NodeUpgradeData.hasMekanismChemicalUpgrade(node);
        Third Source argument: chemicals ? caps.findChemicalHandler(level, node.getAttachedPos(), side) : null
        */
        return new RecipeSets.Source(
                kinds.contains(RecipeCardData.Kind.ITEM) ? node.capabilities().findItemExportHandler(side, direct)
                        : null,
                kinds.contains(RecipeCardData.Kind.FLUID) ? node.capabilities().findFluidExportHandler(side, true)
                        : null);
    }

    private static RecipeEndpoint endpoint(TransferEngine.ImportTarget target, RecipeSets.Source source,
            Set<RecipeCardData.Kind> kinds, FilterItemData.ReadCache cache) {
        // Enable when 26.1.2 is supported.
        /*
        Third RecipeEndpoint argument: source.chemicals() != null ? chemicals(target, source.chemicals(), caps) : null
        */
        return new RecipeEndpoint(
                kinds.contains(RecipeCardData.Kind.ITEM) ? items(target, source.items(), cache) : null,
                kinds.contains(RecipeCardData.Kind.FLUID) ? fluids(target, source.fluids()) : null);
    }

    @Nullable
    private static TransferEngine.ItemTransferTarget items(TransferEngine.ImportTarget target,
            @Nullable ResourceHandler<ItemResource> from, FilterItemData.ReadCache cache) {
        ItemStack[] filters = filtersFor(target.channel(), ChannelType.ITEM);
        boolean mapped = FilterLogic.hasConfiguredSlotMapping(filters, cache);
        ResourceHandler<ItemResource> handler = target.node().capabilities()
                .findItemImportHandler(target.channel().getIoDirection(), !mapped);
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
                .findFluidImportHandler(target.channel().getIoDirection(), true);
        if (handler == null || handler == from || from != null && DirectStorageHandlers.shareNetwork(from, handler))
            return null;
        return new TransferEngine.FluidTransferTarget(handler, filtersFor(target.channel(), ChannelType.FLUID),
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
                target.channel().getIoDirection());
        if (handler == null || handler == from)
            return null;
        return new RecipeEndpoint.Chemicals(handler, filtersFor(target.channel(), ChannelType.CHEMICAL),
                target.channel().getFilterMode());
    }
    */

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
