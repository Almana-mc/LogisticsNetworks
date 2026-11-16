package me.almana.logisticsnetworks.data;

import me.almana.logisticsnetworks.logic.async.AsyncTransferRuntime;
import org.jetbrains.annotations.Nullable;

final class AsyncDispatchRuntime {

    private boolean modeKnown;
    private boolean enabled;
    private long runtimeId = -1L;

    boolean refresh(boolean requested, NetworkDispatchState state) {
        if (!modeKnown) {
            modeKnown = true;
            enabled = requested;
            initialize(requested);
        } else if (enabled != requested) {
            state.resetAsyncState();
            enabled = requested;
            replace(requested);
        } else if (requested) {
            adoptPublished(state);
        } else if (AsyncTransferRuntime.get() != null) {
            AsyncTransferRuntime.stop();
        }
        return enabled;
    }

    @Nullable
    AsyncTransferRuntime current() {
        AsyncTransferRuntime runtime = AsyncTransferRuntime.get();
        if (!enabled || runtime == null || runtime.runtimeId() != runtimeId) {
            return null;
        }
        return runtime;
    }

    long runtimeId() {
        return runtimeId;
    }

    private void initialize(boolean requested) {
        if (!requested) {
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

    private void replace(boolean requested) {
        AsyncTransferRuntime.stop();
        runtimeId = -1L;
        if (requested) {
            AsyncTransferRuntime.start();
            runtimeId = AsyncTransferRuntime.get().runtimeId();
        }
    }

    private void adoptPublished(NetworkDispatchState state) {
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
}
