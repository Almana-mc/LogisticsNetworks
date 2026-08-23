package me.almana.logisticsnetworks.logic.async;

import me.almana.logisticsnetworks.Config;
import net.minecraft.core.RegistryAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.AbstractList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncTransferRuntimeTest {

    private int previousWorkerThreads;

    @BeforeEach
    void setUp() {
        previousWorkerThreads = Config.asyncWorkerThreads;
        AsyncTransferRuntime.stop();
        ThreadGuard.markServerThread();
    }

    @AfterEach
    void tearDown() {
        AsyncTransferRuntime.stop();
        ThreadGuard.clearServerThread();
        Config.asyncWorkerThreads = previousWorkerThreads;
    }

    @Test
    void startReplacesTheRuntimeWithAFreshId() {
        Config.asyncWorkerThreads = 1;
        AsyncTransferRuntime.start();
        AsyncTransferRuntime first = AsyncTransferRuntime.get();
        assertNotNull(first);

        AsyncTransferRuntime.start();
        AsyncTransferRuntime second = AsyncTransferRuntime.get();

        assertNotNull(second);
        assertNotSame(first, second);
        assertNotEquals(first.runtimeId(), second.runtimeId());
        assertFalse(first.submit(emptySnapshot(first.runtimeId())));

        AsyncTransferRuntime.stop();
        assertNull(AsyncTransferRuntime.get());
    }

    @Test
    void submitEnqueuesThePlannedSnapshot() throws Exception {
        Config.asyncWorkerThreads = 1;
        AsyncTransferRuntime.start();
        AsyncTransferRuntime runtime = AsyncTransferRuntime.get();
        assertNotNull(runtime);
        NetworkSnapshot snapshot = emptySnapshot(runtime.runtimeId());

        assertTrue(runtime.submit(snapshot));
        TransferPlan plan = awaitCompletion(runtime);

        assertEquals(snapshot.networkId(), plan.networkId());
        assertEquals(snapshot.generation(), plan.generation());
        assertEquals(snapshot.runtimeId(), plan.runtimeId());
        assertFalse(plan.failed());
        assertTrue(plan.channels().isEmpty());
    }

    @Test
    void plannerThrowableEnqueuesFailedPlanWithSnapshotIdentity() throws Exception {
        Config.asyncWorkerThreads = 1;
        AsyncTransferRuntime.start();
        AsyncTransferRuntime runtime = AsyncTransferRuntime.get();
        assertNotNull(runtime);
        NetworkSnapshot snapshot = new NetworkSnapshot(
                UUID.randomUUID(), 29L, runtime.runtimeId(), 41L, RegistryAccess.EMPTY, null);

        assertTrue(runtime.submit(snapshot));
        TransferPlan plan = awaitCompletion(runtime);

        assertEquals(snapshot.networkId(), plan.networkId());
        assertEquals(snapshot.generation(), plan.generation());
        assertEquals(snapshot.runtimeId(), plan.runtimeId());
        assertTrue(plan.failed());
        assertTrue(plan.channels().isEmpty());
    }

    @Test
    void fullPendingQueueReportsRejection() throws Exception {
        Config.asyncWorkerThreads = 1;
        AsyncTransferRuntime.start();
        AsyncTransferRuntime runtime = AsyncTransferRuntime.get();
        assertNotNull(runtime);
        BlockingUnits blockingUnits = new BlockingUnits(1);

        assertTrue(runtime.submit(snapshot(runtime.runtimeId(), blockingUnits)));
        blockingUnits.awaitStarted();
        for (int i = 0; i < 256; i++) {
            assertTrue(runtime.submit(emptySnapshot(runtime.runtimeId())));
        }

        assertFalse(runtime.submit(emptySnapshot(runtime.runtimeId())));
        blockingUnits.release();
    }

    @Test
    void configuredWorkersAreDaemonThreadsWithOwnedNames() throws Exception {
        Config.asyncWorkerThreads = 2;
        Set<Long> existingWorkerIds = workerThreads().stream()
                .map(Thread::threadId)
                .collect(Collectors.toSet());
        AsyncTransferRuntime.start();
        AsyncTransferRuntime runtime = AsyncTransferRuntime.get();
        assertNotNull(runtime);
        BlockingUnits blockingUnits = new BlockingUnits(2);
        NetworkSnapshot snapshot = snapshot(runtime.runtimeId(), blockingUnits);

        assertTrue(runtime.submit(snapshot));
        assertTrue(runtime.submit(snapshot));
        blockingUnits.awaitStarted();
        Set<Thread> workers = workerThreads().stream()
                .filter(thread -> !existingWorkerIds.contains(thread.threadId()))
                .collect(Collectors.toSet());

        assertEquals(2, workers.size());
        assertTrue(workers.stream().allMatch(Thread::isDaemon));
        assertTrue(workers.stream().allMatch(
                thread -> thread.getName().matches("LogisticsNetworks-Worker-\\d+")));

        blockingUnits.release();
        AsyncTransferRuntime.stop();
        for (Thread worker : workers) {
            worker.join(TimeUnit.SECONDS.toMillis(5));
            assertFalse(worker.isAlive());
        }
    }

    private static NetworkSnapshot emptySnapshot(long runtimeId) {
        return snapshot(runtimeId, List.of());
    }

    private static NetworkSnapshot snapshot(long runtimeId, List<NetworkSnapshot.ChannelUnit> units) {
        return new NetworkSnapshot(
                UUID.randomUUID(), 17L, runtimeId, 31L, RegistryAccess.EMPTY, units);
    }

    private static TransferPlan awaitCompletion(AsyncTransferRuntime runtime) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        TransferPlan plan;
        while ((plan = runtime.pollCompleted()) == null && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        assertNotNull(plan);
        return plan;
    }

    private static Set<Thread> workerThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(Thread::isAlive)
                .filter(thread -> thread.getName().startsWith("LogisticsNetworks-Worker-"))
                .collect(Collectors.toSet());
    }

    private static final class BlockingUnits extends AbstractList<NetworkSnapshot.ChannelUnit> {

        private final CountDownLatch started;
        private final CountDownLatch released = new CountDownLatch(1);

        private BlockingUnits(int workers) {
            started = new CountDownLatch(workers);
        }

        @Override
        public NetworkSnapshot.ChannelUnit get(int index) {
            throw new IndexOutOfBoundsException(index);
        }

        @Override
        public int size() {
            started.countDown();
            try {
                released.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return 0;
        }

        private void awaitStarted() throws InterruptedException {
            assertTrue(started.await(5, TimeUnit.SECONDS));
        }

        private void release() {
            released.countDown();
        }
    }
}
