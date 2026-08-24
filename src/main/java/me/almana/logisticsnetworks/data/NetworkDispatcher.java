package me.almana.logisticsnetworks.data;

import com.mojang.logging.LogUtils;
import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.logic.TransferCapabilityCache;
import me.almana.logisticsnetworks.logic.TransferEngine;
import me.almana.logisticsnetworks.logic.async.AsyncTransferRuntime;
import me.almana.logisticsnetworks.logic.async.Snapshots;
import me.almana.logisticsnetworks.logic.async.TransferCommitter;
import me.almana.logisticsnetworks.logic.async.TransferPlan;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

final class NetworkDispatcher {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int WARNING_DISPATCH_COUNT = 50;

    private final NetworkDispatchState state;
    private final AsyncDispatchRuntime asyncRuntime = new AsyncDispatchRuntime();
    private final AsyncDispatchStats dispatchStats = new AsyncDispatchStats();

    NetworkDispatcher() {
        this(new NetworkDispatchState());
    }

    NetworkDispatcher(NetworkDispatchState state) {
        this.state = state;
    }

    boolean refreshAsyncMode(boolean enabled) {
        return asyncRuntime.refresh(enabled, state);
    }

    void processDirtyNetworks(Map<UUID, LogisticsNetwork> networks, MinecraftServer server) {
        long now = server.overworld().getGameTime();
        state.promoteDueWakes(now, networks::containsKey);
        Set<UUID> ids = state.takeAllPendingNetworks();
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
        AsyncTransferRuntime runtime = asyncRuntime.current();
        if (runtime == null) {
            return;
        }

        long gameTime = server.overworld().getGameTime();
        state.promoteDueWakes(gameTime, networks::containsKey);
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
                Snapshots.NetworkCapture capture = Snapshots.captureNetwork(
                        network, server, runtime.runtimeId(), capabilityCache);
                CaptureDisposition disposition = captureDisposition(capture);
                if (disposition == CaptureDisposition.DISABLE_ASYNC) {
                    dispatchStats.record(AsyncDispatchReason.OCCUPIED_SLOT_LIMIT, id);
                    if (state.disableForOccupiedSlots(id)) {
                        LOGGER.warn("Network {} exceeded the async occupied-slot limit of {}; "
                                + "falling back to synchronous transfers.",
                                id, Config.asyncMaxOccupiedSlots);
                    }
                    state.fallbackSynchronously(id);
                } else if (disposition == CaptureDisposition.DEFER) {
                    if (capture.status() == Snapshots.CaptureStatus.UNAVAILABLE) {
                        dispatchStats.record(AsyncDispatchReason.CAPTURE_UNAVAILABLE, id);
                        state.retryAt(id, gameTime + 20L);
                    } else {
                        dispatchStats.record(AsyncDispatchReason.NO_READY_ITEM_WORK, id);
                        state.retryAt(id, gameTime + 1L);
                    }
                } else if (!runtime.submit(capture.snapshot())) {
                    dispatchStats.record(AsyncDispatchReason.QUEUE_REJECTED, id);
                    state.retryAt(id, gameTime + 1L);
                }
            } catch (Exception exception) {
                dispatchStats.record(AsyncDispatchReason.WORKER_EXCEPTION, id);
                state.finishWorkerPlan(new TransferPlan(
                        id, network.getGeneration(), runtime.runtimeId(), true, List.of()),
                        network.getGeneration(), runtime.runtimeId());
                state.fallbackSynchronously(id);
                LOGGER.error("Error dispatching network {}", id, exception);
            }
        }
        logDispatchSummary(gameTime);
    }

    void commitCompleted(Map<UUID, LogisticsNetwork> networks, MinecraftServer server,
            TransferCapabilityCache capabilityCache, BooleanSupplier hasTime) {
        AsyncTransferRuntime runtime = asyncRuntime.current();
        if (runtime == null) {
            return;
        }

        drainCompleted(runtime::pollCompleted,
                plan -> commitOne(plan, networks, server, capabilityCache, runtime.runtimeId()),
                System::nanoTime, hasTime, Config.asyncCommitBudgetUs);
    }

    static void drainCompleted(Supplier<TransferPlan> pollCompleted,
            Consumer<TransferPlan> commit, LongSupplier nanoTime,
            BooleanSupplier hasTime, int budgetUs) {
        long startedAt = nanoTime.getAsLong();
        long budgetNanos = (long) budgetUs * 1_000L;
        TransferPlan plan;
        while ((plan = pollCompleted.get()) != null) {
            commit.accept(plan);
            if (nanoTime.getAsLong() - startedAt >= budgetNanos && !hasTime.getAsBoolean()) {
                return;
            }
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

    static CaptureDisposition captureDisposition(Snapshots.NetworkCapture capture) {
        if (capture.status() == Snapshots.CaptureStatus.OCCUPIED_SLOT_LIMIT_EXCEEDED) {
            return CaptureDisposition.DISABLE_ASYNC;
        }
        if (capture.snapshot() == null || capture.snapshot().units().isEmpty()) {
            return CaptureDisposition.DEFER;
        }
        return CaptureDisposition.ASYNC;
    }

    static AsyncDispatchReason rejectedPlanReason(
            TransferPlan plan, LogisticsNetwork network, long runtimeId) {
        if (plan.runtimeId() != runtimeId) {
            return AsyncDispatchReason.WRONG_RUNTIME;
        }
        if (plan.generation() != network.getGeneration()) {
            return AsyncDispatchReason.STALE_GENERATION;
        }
        return plan.failed() ? AsyncDispatchReason.WORKER_EXCEPTION : null;
    }

    private void commitOne(TransferPlan plan, Map<UUID, LogisticsNetwork> networks,
            MinecraftServer server, TransferCapabilityCache capabilityCache, long currentRuntimeId) {
        UUID id = plan.networkId();
        LogisticsNetwork network = networks.get(id);
        if (network == null) {
            state.finishDispatch(id);
            state.delete(id);
            return;
        }
        boolean newlyDisabled = state.finishWorkerPlan(
                plan, network.getGeneration(), currentRuntimeId);
        if (newlyDisabled) {
            LOGGER.warn("Network {} failed async planning 3 consecutive times; "
                    + "falling back to synchronous transfers.", id);
        }
        AsyncDispatchReason reason = rejectedPlanReason(plan, network, currentRuntimeId);
        if (reason == AsyncDispatchReason.STALE_GENERATION
                || reason == AsyncDispatchReason.WRONG_RUNTIME) {
            dispatchStats.record(reason, id);
            state.retryCurrent(id);
            return;
        }
        if (reason != null) {
            dispatchStats.record(reason, id);
            state.fallbackSynchronously(id);
            return;
        }
        commitCurrentPlan(plan, network, server, capabilityCache);
    }

    private void commitCurrentPlan(TransferPlan plan, LogisticsNetwork network,
            MinecraftServer server, TransferCapabilityCache capabilityCache) {
        UUID id = plan.networkId();
        long now = server.overworld().getGameTime();
        try {
            TransferCommitter.ItemCommitResult itemResult = TransferCommitter.commitItems(
                    plan, network, server, capabilityCache, asyncRuntime.runtimeId());
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

    private static void warnHighDispatch(int count) {
        if (count > WARNING_DISPATCH_COUNT) {
            LOGGER.warn("High load: Dispatching {} dirty networks in one tick.", count);
        }
    }

    private void logDispatchSummary(long gameTime) {
        if (!Config.debugMode) {
            return;
        }
        String summary = dispatchStats.summary(gameTime);
        if (summary != null) {
            LOGGER.debug("Async dispatch outcomes: {}", summary);
        }
    }

    enum CaptureDisposition {
        ASYNC,
        DEFER,
        DISABLE_ASYNC
    }
}
