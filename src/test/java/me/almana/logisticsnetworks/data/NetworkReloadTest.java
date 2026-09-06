package me.almana.logisticsnetworks.data;

import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.logic.async.AsyncTransferRuntime;
import me.almana.logisticsnetworks.logic.async.ThreadGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class NetworkReloadTest {
    private boolean async;
    private boolean ticking;
    private int workers;

    @BeforeEach
    void setup() {
        async = Config.asyncPlanning;
        ticking = Config.networkTickingEnabled;
        workers = Config.asyncWorkerThreads;
        Config.asyncPlanning = true;
        Config.networkTickingEnabled = true;
        Config.asyncWorkerThreads = 1;
        ThreadGuard.markServerThread();
        AsyncTransferRuntime.stop();
    }

    @AfterEach
    void cleanup() {
        AsyncTransferRuntime.stop();
        ThreadGuard.clearServerThread();
        Config.asyncPlanning = async;
        Config.networkTickingEnabled = ticking;
        Config.asyncWorkerThreads = workers;
    }

    @Test
    void reloadMarshalsInvalidationAndRuntimeReplacementToRegistryRefresh() throws Exception {
        var registry = new NetworkRegistry();
        var network = registry.createNetwork();
        registry.refreshAsyncPlanning();
        var first = AsyncTransferRuntime.get();
        var state = state(registry);
        registry.wakeNetwork(network.getId());
        assertTrue(state.beginDispatch(network.getId()));
        long generation = network.getGeneration();
        Thread worker = new Thread(() -> LogisticsNetworks.onDatapackReload(null));
        worker.start();
        worker.join();
        assertSame(first, AsyncTransferRuntime.get());
        assertEquals(generation, network.getGeneration());
        registry.refreshAsyncPlanning();
        assertNotEquals(first.runtimeId(), AsyncTransferRuntime.get().runtimeId());
        assertEquals(generation + 1, network.getGeneration());
        assertTrue(state.beginDispatch(network.getId()));
        assertDoesNotThrow(ThreadGuard::requireServerThread);
    }

    @Test
    void disabledReloadRetainsPendingIdsAndServerThread() throws Exception {
        var registry = new NetworkRegistry();
        var network = registry.createNetwork();
        registry.refreshAsyncPlanning();
        var state = state(registry);
        registry.wakeNetwork(network.getId());
        assertTrue(state.beginDispatch(network.getId()));
        Config.asyncPlanning = false;
        LogisticsNetworks.onDatapackReload(null);
        assertFalse(registry.refreshAsyncPlanning());
        assertNull(AsyncTransferRuntime.get());
        assertTrue(state.beginDispatch(network.getId()));
        assertDoesNotThrow(ThreadGuard::requireServerThread);
    }

    @Test
    void reloadPreservesOccupiedAndFailurePins() throws Exception {
        var registry = new NetworkRegistry();
        var network = registry.createNetwork();
        registry.refreshAsyncPlanning();
        var state = state(registry);
        state.disableForOccupiedSlots(network.getId());
        LogisticsNetworks.onDatapackReload(null);
        registry.refreshAsyncPlanning();
        assertTrue(state.isAsyncDisabled(network.getId()));
        assertEquals(network.getId(), state.takeOneDegradedRecovery().orElseThrow());
    }

    static NetworkDispatchState state(NetworkRegistry registry) throws Exception {
        Field dispatcher = NetworkRegistry.class.getDeclaredField("dispatcher");
        dispatcher.setAccessible(true);
        Field state = NetworkDispatcher.class.getDeclaredField("state");
        state.setAccessible(true);
        return (NetworkDispatchState) state.get(dispatcher.get(registry));
    }
}
