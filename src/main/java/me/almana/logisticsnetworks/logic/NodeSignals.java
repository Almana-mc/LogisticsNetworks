package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.RedstoneMode;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;

import java.util.IdentityHashMap;
import java.util.Map;

// Per-pass redstone reads, on demand
public final class NodeSignals {
    private final Map<LogisticsNodeEntity, Integer> known = new IdentityHashMap<>();

    public boolean active(LogisticsNodeEntity node, RedstoneMode mode) {
        return mode == RedstoneMode.IGNORED || TransferEngine.isRedstoneActive(mode, signal(node));
    }

    private int signal(LogisticsNodeEntity node) {
        return known.computeIfAbsent(node, n -> n.level().getBestNeighborSignal(n.getAttachedPos()));
    }
}
