package me.almana.logisticsnetworks.data;

import com.mojang.logging.LogUtils;
import me.almana.logisticsnetworks.logic.TransferCapabilityCache;
import me.almana.logisticsnetworks.logic.TransferEngine;
import me.almana.logisticsnetworks.logic.async.AsyncTransferRuntime;
import me.almana.logisticsnetworks.logic.async.NetworkSnapshot;
import me.almana.logisticsnetworks.logic.async.Snapshots;
import me.almana.logisticsnetworks.logic.async.TransferCommitter;
import me.almana.logisticsnetworks.logic.async.TransferPlan;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class NetworkDispatcher {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int WARNING_DISPATCH_COUNT = 50;

    private final NetworkDispatchState state;
    private boolean modeKnown;
    private boolean asyncEnabled;
    private long runtimeId = -1L;

    NetworkDispatcher() {
        this(new NetworkDispatchState());
    }

    NetworkDispatcher(NetworkDispatchState state) {
        this.state = state;
    }

    boolean refreshAsyncMode(boolean enabled) {
        if (!modeKnown) {
            modeKnown = true;
            asyncEnabled = enabled;
            initializeRuntime(enabled);
        } else if (asyncEnabled != enabled) {
            state.resetAsyncState();
            asyncEnabled = enabled;
            replaceRuntime(enabled);
        } else if (enabled) {
            refreshPublishedRuntime();
        }
        return asyncEnabled;
    }

    void processDirtyNetworks(Map<UUID, LogisticsNetwork> networks, MinecraftServer server) {
        long now = server.overworld().getGameTime();
        state.promoteDueWakes(now, networks::containsKey);
        Set<UUID> ids = state.takeDirtyNetworks();
        warnHighDispatch(ids.size());

        for (UUID id : ids) {
            LogisticsNetwork network = networks.get(id);
            if (network != null) {
                processSynchronously(id, network, server, now);
            }
        }
    }

    void dispatchDirty(NetworkRegistry registry, Map<UUID, LogisticsNetwork> networks,
            MinecraftServer server, TransferCapabilityCache capabilityCache) {
        AsyncTransferRuntime runtime = AsyncTransferRuntime.get();
        if (!asyncEnabled || runtime == null || runtime.runtimeId() != runtimeId) {
            return;
        }

        state.promoteDueWakes(server.overworld().getGameTime(), networks::containsKey);
        Set<UUID> ids = state.dirtySnapshot();
        warnHighDispatch(ids.size());
        for (UUID id : ids) {
            LogisticsNetwork network = networks.get(id);
            if (network == null) {
                state.delete(id);
                continue;
            }
            if (!state.beginDispatch(id)) {
                continue;
            }
            try {
                if (network.isCacheDirty()) {
                    network.rebuildCache(registry);
                    network.clearCacheDirty();
                }
                NetworkSnapshot snapshot = Snapshots.captureNetwork(
                        network, server, runtime.runtimeId(), capabilityCache);
                if (!hasAsyncItemWork(snapshot) || !runtime.submit(snapshot)) {
                    state.fallbackSynchronously(id);
                }
            } catch (Exception exception) {
                state.fallbackSynchronously(id);
                LOGGER.error("Error dispatching network {}", id, exception);
            }
        }
    }

    void commitCompleted(Map<UUID, LogisticsNetwork> networks, MinecraftServer server,
            TransferCapabilityCache capabilityCache) {
        AsyncTransferRuntime runtime = AsyncTransferRuntime.get();
        if (!asyncEnabled || runtime == null || runtime.runtimeId() != runtimeId) {
            return;
        }

        TransferPlan plan;
        while ((plan = runtime.pollCompleted()) != null) {
            UUID id = plan.networkId();
            state.finishDispatch(id);
            LogisticsNetwork network = networks.get(id);
            if (network == null) {
                state.delete(id);
                continue;
            }
            if (requiresSynchronousFallback(plan, network, runtime.runtimeId())) {
                state.fallbackSynchronously(id);
                continue;
            }
            commitCurrentPlan(plan, network, server, capabilityCache);
        }
    }

    void processSynchronousFallbacks(Map<UUID, LogisticsNetwork> networks, MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (UUID id : state.takeSynchronousFallbacks()) {
            LogisticsNetwork network = networks.get(id);
            if (network != null) {
                processSynchronously(id, network, server, now);
            }
        }
    }

    void markDirty(UUID id) {
        state.markDirty(id);
    }

    void delete(UUID id) {
        state.delete(id);
    }

    static boolean hasAsyncItemWork(@Nullable NetworkSnapshot snapshot) {
        return snapshot != null && !snapshot.units().isEmpty();
    }

    static boolean requiresSynchronousFallback(
            TransferPlan plan, LogisticsNetwork network, long runtimeId) {
        return plan.failed() || plan.runtimeId() != runtimeId
                || plan.generation() != network.getGeneration();
    }

    private void commitCurrentPlan(TransferPlan plan, LogisticsNetwork network,
            MinecraftServer server, TransferCapabilityCache capabilityCache) {
        UUID id = plan.networkId();
        long now = server.overworld().getGameTime();
        try {
            TransferCommitter.ItemCommitResult itemResult = TransferCommitter.commitItems(
                    plan, network, server, capabilityCache, runtimeId);
            long synchronousDelta = TransferEngine.processNetworkWithoutItemTransfers(network, server);
            state.scheduleResult(id, now, Math.min(itemResult.wakeDelta(), synchronousDelta));
        } catch (Exception exception) {
            state.markDirty(id);
            LOGGER.error("Error committing plan for network {}", id, exception);
        }
    }

    private void processSynchronously(UUID id, LogisticsNetwork network,
            MinecraftServer server, long now) {
        try {
            state.scheduleResult(id, now, TransferEngine.processNetwork(network, server));
        } catch (Exception exception) {
            LOGGER.error("Error processing network {}: {}", id, exception.getMessage(), exception);
        }
    }

    private void initializeRuntime(boolean enabled) {
        if (!enabled) {
            AsyncTransferRuntime.stop();
            return;
        }
        AsyncTransferRuntime runtime = AsyncTransferRuntime.get();
        if (runtime == null) {
            AsyncTransferRuntime.start();
            runtime = AsyncTransferRuntime.get();
        }
        runtimeId = runtime.runtimeId();
    }

    private void replaceRuntime(boolean enabled) {
        AsyncTransferRuntime.stop();
        runtimeId = -1L;
        if (enabled) {
            AsyncTransferRuntime.start();
            runtimeId = AsyncTransferRuntime.get().runtimeId();
        }
    }

    private void refreshPublishedRuntime() {
        AsyncTransferRuntime runtime = AsyncTransferRuntime.get();
        if (runtime == null) {
            state.resetAsyncState();
            AsyncTransferRuntime.start();
            runtime = AsyncTransferRuntime.get();
        } else if (runtime.runtimeId() != runtimeId) {
            state.resetAsyncState();
        }
        runtimeId = runtime.runtimeId();
    }

    private static void warnHighDispatch(int count) {
        if (count > WARNING_DISPATCH_COUNT) {
            LOGGER.warn("High load: Dispatching {} dirty networks in one tick.", count);
        }
    }
}
