package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.logic.FilterLogic;
import me.almana.logisticsnetworks.logic.TransferAmountRules;
import me.almana.logisticsnetworks.logic.TransferEngine;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

public final class ItemPlanner {

    private ItemPlanner() {
    }

    public static TransferPlan.ChannelMoves plan(NetworkSnapshot.ChannelUnit unit,
            NetworkSnapshot snapshot) {
        ThreadGuard.requireWorkerThread();

        List<TransferPlan.TargetRef> targetRefs = new ArrayList<>(unit.targets().size());
        List<TransferEngine.ItemTransferTarget> engineTargets = new ArrayList<>(unit.targets().size());
        FilterItemData.ReadCache readCache = FilterItemData.createReadCache();

        for (NetworkSnapshot.TargetUnit target : unit.targets()) {
            IItemHandler targetHandler = new SnapshotItemHandler(target.endpoint());
            targetRefs.add(new TransferPlan.TargetRef(target.nodeId(), target.channelIndex(), target.bulk()));
            engineTargets.add(new TransferEngine.ItemTransferTarget(
                    targetHandler,
                    null,
                    target.importFilters(),
                    target.importFilterMode(),
                    TransferAmountRules.collect(unit.exportFilters(), target.importFilters(), readCache),
                    FilterLogic.hasConfiguredItemNbtFilter(target.importFilters(), readCache),
                    null,
                    target.hasImportSlotMapping()));
        }

        List<TransferPlan.ItemMove> moves = new ArrayList<>();
        IItemHandler sourceHandler = new SnapshotItemHandler(unit.source());

        TransferEngine.executeMove(
                sourceHandler,
                engineTargets,
                unit.batchLimit(),
                unit.exportFilters(),
                unit.exportFilterMode(),
                null,
                snapshot.registries(),
                null,
                null,
                readCache,
                (sourceSlot, targetIndex, moved, mask) -> moves.add(new TransferPlan.ItemMove(
                        sourceSlot,
                        targetIndex,
                        moved.getItem(),
                        moved.getComponents(),
                        moved.getCount(),
                        mask)));

        return new TransferPlan.ChannelMoves(unit.sourceNodeId(), unit.channelIndex(), targetRefs, moves);
    }
}
