package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.logic.FilterLogic;
import me.almana.logisticsnetworks.logic.TransferAmountRules;
import me.almana.logisticsnetworks.logic.TransferEngine;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.ArrayList;
import java.util.List;

public final class ItemPlanner {

    private ItemPlanner() {
    }

    public static TransferPlan.ChannelMoves plan(NetworkSnapshot.ChannelUnit unit,
            NetworkSnapshot snapshot, List<SnapshotItemHandler> endpoints) {
        ThreadGuard.requireWorkerThread();

        List<TransferPlan.TargetRef> targetRefs = new ArrayList<>(unit.targets().size());
        List<TransferEngine.ItemTransferTarget> engineTargets = new ArrayList<>(unit.targets().size());
        FilterItemData.ReadCache readCache = FilterItemData.createReadCache();

        var exportFilters = unit.exportFilters();
        for (NetworkSnapshot.TargetUnit target : unit.targets()) {
            targetRefs.add(new TransferPlan.TargetRef(target.nodeId(), target.channelIndex(), target.bulk()));
            engineTargets.add(engineTarget(target, exportFilters, endpoints, readCache));
        }

        List<TransferPlan.MoveIntent> moves = new ArrayList<>();
        ResourceHandler<ItemResource> sourceHandler = endpoints.get(unit.sourceEndpoint());

        TransferEngine.executeMove(sourceHandler, engineTargets, unit.batchLimit(), exportFilters,
                unit.exportFilterMode(), null, snapshot.registries(), unit.roundRobin(), readCache,
                (sourceSlot, targetIndex, moved, mask) -> moves.add(new TransferPlan.MoveIntent(
                        sourceSlot, targetIndex, ItemResource.of(moved), moved.getCount(), mask)));

        return new TransferPlan.ChannelMoves(unit.sourceNodeId(), unit.channelIndex(), targetRefs, moves);
    }

    private static TransferEngine.ItemTransferTarget engineTarget(NetworkSnapshot.TargetUnit target,
            ItemStack[] exportFilters, List<SnapshotItemHandler> endpoints,
            FilterItemData.ReadCache readCache) {
        var importFilters = target.importFilters();
        return new TransferEngine.ItemTransferTarget(endpoints.get(target.endpoint()), importFilters,
                target.importFilterMode(), TransferAmountRules.collect(exportFilters, importFilters, readCache),
                FilterLogic.hasConfiguredItemNbtFilter(importFilters, readCache), null, target.hasImportSlotMapping());
    }
}
