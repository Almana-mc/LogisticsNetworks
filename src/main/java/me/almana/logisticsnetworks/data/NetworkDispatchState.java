package me.almana.logisticsnetworks.data;

import me.almana.logisticsnetworks.logic.async.TransferPlan;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;

final class NetworkDispatchState {

    private static final int MAX_ASYNC_FAILURES = 3;

    private final Set<UUID> dirtyNetworks = new HashSet<>();
    private final Set<UUID> inFlight = new HashSet<>();
    private final Set<UUID> dirtyAgain = new HashSet<>();
    private final LinkedHashSet<UUID> degradedNetworks = new LinkedHashSet<>();
    private final TreeMap<Long, Set<UUID>> wakeBuckets = new TreeMap<>();
    private final Map<UUID, Long> scheduledWake = new HashMap<>();
    private final Map<UUID, Integer> asyncFailures = new HashMap<>();
    private final Map<UUID, AsyncDisableReason> asyncDisabled = new HashMap<>();

    void markDirty(UUID id) {
        cancelWake(id);
        if (inFlight.contains(id)) {
            dirtyAgain.add(id);
        } else {
            queuePending(id);
        }
    }

    boolean beginDispatch(UUID id) {
        if (isAsyncDisabled(id)) {
            if (dirtyNetworks.remove(id)) {
                degradedNetworks.add(id);
            }
            return false;
        }
        if (inFlight.contains(id)) {
            if (dirtyNetworks.remove(id)) {
                dirtyAgain.add(id);
            }
            return false;
        }
        if (!dirtyNetworks.remove(id)) {
            return false;
        }
        inFlight.add(id);
        return true;
    }

    void finishDispatch(UUID id) {
        inFlight.remove(id);
        if (dirtyAgain.remove(id)) {
            queuePending(id);
        }
    }

    void retryCurrent(UUID id) {
        inFlight.remove(id);
        dirtyAgain.remove(id);
        queuePending(id);
    }

    void retryAt(UUID id, long tick) {
        inFlight.remove(id);
        if (dirtyAgain.remove(id)) {
            queuePending(id);
        } else {
            scheduleWake(id, tick);
        }
    }

    boolean finishWorkerPlan(
            TransferPlan plan, long currentGeneration, long currentRuntimeId) {
        UUID id = plan.networkId();
        finishDispatch(id);
        if (plan.generation() != currentGeneration
                || plan.runtimeId() != currentRuntimeId) {
            return false;
        }
        if (!plan.failed()) {
            asyncFailures.remove(id);
            return false;
        }
        if (isAsyncDisabled(id)) {
            return false;
        }
        int failures = Math.min(MAX_ASYNC_FAILURES,
                asyncFailures.getOrDefault(id, 0) + 1);
        asyncFailures.put(id, failures);
        if (failures != MAX_ASYNC_FAILURES) {
            return false;
        }
        dirtyNetworks.remove(id);
        dirtyAgain.remove(id);
        cancelWake(id);
        asyncDisabled.put(id, AsyncDisableReason.WORKER_FAILURES);
        degradedNetworks.add(id);
        return true;
    }

    boolean disableForOccupiedSlots(UUID id) {
        if (asyncDisabled.putIfAbsent(id, AsyncDisableReason.OCCUPIED_SLOT_LIMIT) != null) {
            return false;
        }
        inFlight.remove(id);
        dirtyAgain.remove(id);
        dirtyNetworks.remove(id);
        cancelWake(id);
        degradedNetworks.add(id);
        return true;
    }

    boolean isAsyncDisabled(UUID id) {
        return asyncDisabled.containsKey(id);
    }

    Set<UUID> dirtySnapshot() {
        return Set.copyOf(dirtyNetworks);
    }

    Set<UUID> takeAllPendingNetworks() {
        Set<UUID> ids = new HashSet<>(dirtyNetworks);
        ids.addAll(degradedNetworks);
        dirtyNetworks.clear();
        degradedNetworks.clear();
        return ids;
    }

    Optional<UUID> takeOneDegradedRecovery() {
        Iterator<UUID> ids = degradedNetworks.iterator();
        if (!ids.hasNext()) {
            return Optional.empty();
        }
        UUID id = ids.next();
        ids.remove();
        return Optional.of(id);
    }

    void resetAsyncState() {
        Set<UUID> pending = new LinkedHashSet<>(dirtyNetworks);
        pending.addAll(inFlight);
        pending.addAll(dirtyAgain);
        pending.addAll(degradedNetworks);
        dirtyNetworks.clear();
        inFlight.clear();
        dirtyAgain.clear();
        degradedNetworks.clear();
        pending.forEach(this::queuePending);
    }

    void scheduleResult(UUID id, long now, long delta) {
        cancelWake(id);
        if (dirtyNetworks.contains(id) || degradedNetworks.contains(id)) {
            return;
        }
        if (delta == 0L) {
            queuePending(id);
        } else if (delta != Long.MAX_VALUE) {
            scheduleWake(id, now + delta);
        }
    }

    void scheduleWake(UUID id, long tick) {
        Long existing = scheduledWake.get(id);
        if (existing != null) {
            if (existing <= tick) {
                return;
            }
            removeFromBucket(id, existing);
        }
        scheduledWake.put(id, tick);
        wakeBuckets.computeIfAbsent(tick, ignored -> new HashSet<>()).add(id);
    }

    void promoteDueWakes(long now, Predicate<UUID> present) {
        while (!wakeBuckets.isEmpty() && wakeBuckets.firstKey() <= now) {
            Map.Entry<Long, Set<UUID>> entry = wakeBuckets.pollFirstEntry();
            for (UUID id : entry.getValue()) {
                scheduledWake.remove(id);
                if (!present.test(id)) {
                    continue;
                }
                if (inFlight.contains(id)) {
                    dirtyAgain.add(id);
                } else {
                    queuePending(id);
                }
            }
        }
    }

    void clear() {
        dirtyNetworks.clear();
        inFlight.clear();
        dirtyAgain.clear();
        degradedNetworks.clear();
        wakeBuckets.clear();
        scheduledWake.clear();
        asyncFailures.clear();
        asyncDisabled.clear();
    }

    void delete(UUID id) {
        dirtyNetworks.remove(id);
        inFlight.remove(id);
        dirtyAgain.remove(id);
        degradedNetworks.remove(id);
        asyncFailures.remove(id);
        asyncDisabled.remove(id);
        cancelWake(id);
    }

    private void queuePending(UUID id) {
        if (isAsyncDisabled(id)) {
            degradedNetworks.add(id);
        } else if (!degradedNetworks.contains(id)) {
            dirtyNetworks.add(id);
        }
    }

    private void cancelWake(UUID id) {
        Long tick = scheduledWake.remove(id);
        if (tick != null) {
            removeFromBucket(id, tick);
        }
    }

    private void removeFromBucket(UUID id, long tick) {
        Set<UUID> bucket = wakeBuckets.get(tick);
        if (bucket == null) {
            return;
        }
        bucket.remove(id);
        if (bucket.isEmpty()) {
            wakeBuckets.remove(tick);
        }
    }

    private enum AsyncDisableReason {
        WORKER_FAILURES,
        OCCUPIED_SLOT_LIMIT
    }
}
