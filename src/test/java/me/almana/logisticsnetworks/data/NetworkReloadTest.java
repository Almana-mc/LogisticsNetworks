package me.almana.logisticsnetworks.data;

import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.logic.async.AsyncTransferRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkReloadTest {

    private boolean previousAsyncPlanning;
    private int previousWorkerThreads;

    @BeforeEach
    void setUp() {
        previousAsyncPlanning = Config.asyncPlanning;
        previousWorkerThreads = Config.asyncWorkerThreads;
        Config.asyncWorkerThreads = 1;
        AsyncTransferRuntime.stop();
    }

    @AfterEach
    void tearDown() {
        AsyncTransferRuntime.stop();
        Config.asyncPlanning = previousAsyncPlanning;
        Config.asyncWorkerThreads = previousWorkerThreads;
    }

    @Test
    void enabledReloadReplacesRuntimeAndNextTickRequeuesInFlightWork() {
        Config.asyncPlanning = true;
        NetworkDispatchState state = new NetworkDispatchState();
        NetworkDispatcher dispatcher = new NetworkDispatcher(state);
        dispatcher.refreshAsyncMode(true);
        AsyncTransferRuntime first = AsyncTransferRuntime.get();
        assertNotNull(first);
        UUID id = beginInFlight(state);

        LogisticsNetworks.onDatapackReload(null);
        AsyncTransferRuntime replacement = AsyncTransferRuntime.get();
        assertNotNull(replacement);
        assertNotEquals(first.runtimeId(), replacement.runtimeId());

        dispatcher.refreshAsyncMode(true);
        assertTrue(state.beginDispatch(id));
    }

    @Test
    void disabledReloadStopsRuntimeAndNextTickRequeuesInFlightWork() {
        Config.asyncPlanning = true;
        NetworkDispatchState state = new NetworkDispatchState();
        NetworkDispatcher dispatcher = new NetworkDispatcher(state);
        dispatcher.refreshAsyncMode(true);
        UUID id = beginInFlight(state);

        Config.asyncPlanning = false;
        LogisticsNetworks.onDatapackReload(null);

        assertNull(AsyncTransferRuntime.get());
        dispatcher.refreshAsyncMode(false);
        assertTrue(state.beginDispatch(id));
    }

    private static UUID beginInFlight(NetworkDispatchState state) {
        UUID id = UUID.randomUUID();
        state.markDirty(id);
        assertTrue(state.beginDispatch(id));
        state.markDirty(id);
        return id;
    }
}
