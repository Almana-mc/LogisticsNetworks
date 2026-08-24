package me.almana.logisticsnetworks.logic.async;

import java.util.ArrayList;
import java.util.List;

public final class NetworkPlanner {

    private NetworkPlanner() {
    }

    public static TransferPlan plan(NetworkSnapshot snapshot) {
        ThreadGuard.requireWorkerThread();

        List<TransferPlan.ChannelMoves> channels = new ArrayList<>(snapshot.units().size());
        for (NetworkSnapshot.ChannelUnit unit : snapshot.units()) {
            channels.add(ItemPlanner.plan(unit, snapshot));
        }

        return new TransferPlan(
                snapshot.networkId(),
                snapshot.generation(),
                snapshot.runtimeId(),
                false,
                snapshot.itemWakeDelta(),
                channels);
    }
}
