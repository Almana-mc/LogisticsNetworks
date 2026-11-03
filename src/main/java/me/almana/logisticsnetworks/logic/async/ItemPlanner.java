package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.logic.EntryFaces;
import me.almana.logisticsnetworks.logic.FilterLogic;
import me.almana.logisticsnetworks.logic.TransferAmountRules;
import me.almana.logisticsnetworks.logic.TransferEngine;
import me.almana.logisticsnetworks.integration.storage.ItemResource;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ItemPlanner {

    private ItemPlanner() {
    }

    public static TransferPlan.ChannelMoves plan(NetworkSnapshot.ChannelUnit unit,
            NetworkSnapshot snapshot, List<IItemHandler> endpoints) {
        ThreadGuard.requireWorkerThread();

        List<TransferPlan.TargetRef> targetRefs = new ArrayList<>(unit.targets().size());
        List<TransferEngine.ItemTransferTarget> engineTargets = new ArrayList<>(unit.targets().size());
        FilterItemData.ReadCache readCache = FilterItemData.createReadCache();

        for (NetworkSnapshot.TargetUnit target : unit.targets()) {
            IItemHandler targetHandler = endpoints.get(target.endpoint());
            var direct = snapshot.endpoints().get(target.endpoint()).direct();
            targetRefs.add(new TransferPlan.TargetRef(target.nodeId(), target.channelIndex(), target.bulk(),
                    direct == null ? -1 : direct.binding(), target.faces() != null));
            var constraints = TransferAmountRules.collect(unit.exportFilters(), target.importFilters(), readCache);
            boolean nbt = FilterLogic.hasConfiguredItemNbtFilter(target.importFilters(), readCache);
            engineTargets.add(new TransferEngine.ItemTransferTarget(
                    targetHandler,
                    target.bulk() ? targetHandler : null,
                    target.importFilters(),
                    target.importFilterMode(),
                    constraints,
                    nbt,
                    null,
                    target.hasImportSlotMapping(),
                    target.faces() == null ? null : new EntryFaces<>(target.side(),
                            FilterLogic.hasConfiguredEntrySides(target.importFilters(), readCache),
                            FilterLogic.hasConfiguredEntrySides(unit.exportFilters(), readCache),
                            side -> face(target.faces()[side.ordinal()], endpoints, constraints, nbt))));
        }

        List<TransferPlan.ItemMove> moves = new ArrayList<>();
        IItemHandler sourceHandler = endpoints.get(unit.sourceEndpoint());

        TransferEngine.executeItemOperation(
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
                unit.distribution(),
                (sourceSlot, targetIndex, moved, mask, face) -> moves.add(new TransferPlan.ItemMove(
                        sourceSlot,
                        targetIndex,
                        new ItemResource(moved),
                        moved.getCount(),
                        mask,
                        face)), java.util.Map.of(), List.of(),
                unit.resourceRoundRobin(), unit.resourceCursor(), null);

        var directSource = snapshot.endpoints().get(unit.sourceEndpoint()).direct();
        return new TransferPlan.ChannelMoves(unit.sourceNodeId(), unit.channelIndex(),
                directSource == null ? -1 : directSource.binding(), targetRefs, moves);
    }

    @Nullable
    private static TransferEngine.ItemTransferTarget face(@Nullable NetworkSnapshot.TargetUnit face,
            List<IItemHandler> endpoints, TransferAmountRules.Constraints constraints, boolean nbt) {
        if (face == null)
            return null;
        IItemHandler handler = endpoints.get(face.endpoint());
        return new TransferEngine.ItemTransferTarget(handler, face.bulk() ? handler : null, face.importFilters(),
                face.importFilterMode(), constraints, nbt, null, face.hasImportSlotMapping());
    }
}
