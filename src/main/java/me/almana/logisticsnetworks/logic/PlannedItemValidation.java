package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.filter.NbtFilterData;
import me.almana.logisticsnetworks.integration.storage.DirectItemAccess;
import me.almana.logisticsnetworks.integration.storage.DirectStorageReads;
import me.almana.logisticsnetworks.logic.async.TransferPlan;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PlannedItemValidation {
    private final ResourceHandler<ItemResource> source;
    private final ChannelData channel;
    private final HolderLookup.Provider registries;
    private final FilterItemData.ReadCache filters = FilterItemData.createReadCache();
    private final Set<Item> candidates = new HashSet<>();
    private final Map<ResourceHandler<ItemResource>, Map<Item, Integer>> targetCounts = new IdentityHashMap<>();
    private Map<Item, Integer> sourceCounts;

    public PlannedItemValidation(ResourceHandler<ItemResource> source, ChannelData channel,
            HolderLookup.Provider registries, List<TransferPlan.MoveIntent> moves) {
        this.source = source;
        this.channel = channel;
        this.registries = registries;
        for (TransferPlan.MoveIntent move : moves) candidates.add(move.getItem());
    }

    public TransferPlan.MoveIntent validate(TransferPlan.MoveIntent move,
            ResourceHandler<ItemResource> target, ChannelData targetChannel,
            Map<Item, Integer> batchMoved, int remaining) {
        ItemStack candidate = move.resource().toStack(Math.min(move.amount(), remaining));
        if (candidate.isEmpty()) return move.withAmount(0, null);
        ItemStack[] exports = channel.getFilterItems();
        ItemStack[] imports = targetChannel.getFilterItems();
        CompoundTag components = FilterLogic.hasConfiguredItemNbtFilter(exports, filters)
                || FilterLogic.hasConfiguredItemNbtFilter(imports, filters)
                ? NbtFilterData.getSerializedComponents(candidate, registries) : null;
        int sourceSlot = source instanceof DirectItemAccess ? -1 : move.sourceSlot();
        if (!FilterLogic.matchesItemInSlot(exports, channel.getFilterMode(), candidate, registries,
                components, filters, sourceSlot)) return move.withAmount(0, null);
        boolean[] mask = null;
        if (FilterLogic.hasConfiguredSlotMapping(imports, filters)) {
            mask = TransferEngine.computeImportAllowedSlots(target, imports, targetChannel.getFilterMode(),
                    candidate, registries, components, filters);
            if (mask == null) return move.withAmount(0, null);
        } else if (!FilterLogic.matchesItemInSlot(imports, targetChannel.getFilterMode(), candidate,
                registries, components, filters, -1)) {
            return move.withAmount(0, null);
        }
        int allowed = allowed(candidate, target, imports, components, batchMoved);
        return move.withAmount(Math.min(candidate.getCount(), allowed), mask);
    }

    private int allowed(ItemStack candidate, ResourceHandler<ItemResource> target, ItemStack[] imports,
            CompoundTag components, Map<Item, Integer> batchMoved) {
        TransferAmountRules.Constraints constraints = TransferAmountRules.collect(
                channel.getFilterItems(), imports, filters);
        if (!constraints.hasPerEntryAmounts() && !constraints.hasImportThreshold()
                && !constraints.hasExportThreshold()) return candidate.getCount();
        if (sourceCounts == null && (constraints.hasExportThreshold()
                || TransferAmountRules.hasStockFilter(channel.getFilterItems(), filters))) {
            sourceCounts = TransferAmountRules.countItems(source, candidates);
        }
        Map<Item, Integer> counts = null;
        if (constraints.hasImportThreshold() || TransferAmountRules.hasStockFilter(imports, filters)) {
            counts = target instanceof DirectItemAccess direct ? direct.countItems(candidates)
                    : targetCounts.computeIfAbsent(target,
                            ignored -> TransferAmountRules.countItems(target, candidates));
        }
        int allowed = TransferAmountRules.allowedItems(candidate, constraints, sourceCounts, counts);
        int perEntry = TransferAmountRules.perEntryItemAmount(candidate, channel.getFilterItems(), imports,
                sourceCounts, counts, registries, components, filters);
        if (perEntry >= 0) allowed = Math.min(allowed, perEntry);
        int batch = TransferAmountRules.perEntryItemBatch(
                candidate, channel.getFilterItems(), registries, components, filters);
        if (batch > 0) {
            allowed = Math.min(allowed,
                    Math.max(0, batch - batchMoved.getOrDefault(candidate.getItem(), 0)));
        }
        return allowed;
    }

    public void moved(Item item, int amount, ResourceHandler<ItemResource> target) {
        if (sourceCounts != null) {
            sourceCounts.computeIfPresent(item, (key, count) -> Math.max(0, count - amount));
        }
        Map<Item, Integer> counts = targetCounts.get(target);
        if (counts != null) {
            counts.merge(item, amount, (left, right) -> DirectStorageReads.clamp((long) left + right));
        }
    }
}
