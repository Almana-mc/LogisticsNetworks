package me.almana.logisticsnetworks.logic.async;

import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NetworkPlanner {

    private NetworkPlanner() {
    }

    public static TransferPlan plan(NetworkSnapshot snapshot) {
        ThreadGuard.requireWorkerThread();

        List<IItemHandler> endpoints = createEndpoints(snapshot.endpoints());

        List<TransferPlan.ChannelMoves> channels = new ArrayList<>(snapshot.units().size());
        for (NetworkSnapshot.ChannelUnit unit : snapshot.units()) {
            channels.add(ItemPlanner.plan(unit, snapshot, endpoints));
        }

        return new TransferPlan(
                snapshot.networkId(),
                snapshot.generation(),
                snapshot.runtimeId(),
                false,
                snapshot.itemWakeDelta(),
                channels);
    }

    public static List<IItemHandler> createEndpoints(List<NetworkSnapshot.ItemEndpoint> snapshots) {
        Map<Integer, DirectItemStock> stocks = new HashMap<>();
        for (var endpoint : snapshots) {
            if (endpoint.direct() != null) {
                stocks.computeIfAbsent(endpoint.direct().network(), ignored -> new DirectItemStock()).include(endpoint.direct());
            }
        }
        List<IItemHandler> result = new ArrayList<>(snapshots.size());
        for (var endpoint : snapshots) {
            result.add(endpoint.direct() == null ? new SnapshotItemHandler(endpoint)
                    : new DirectSnapshotItemHandler(endpoint.direct(), stocks.get(endpoint.direct().network())));
        }
        return result;
    }
}
