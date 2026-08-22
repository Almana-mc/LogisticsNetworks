package me.almana.logisticsnetworks.logic.async;

import org.junit.jupiter.api.Test;

import java.util.concurrent.FutureTask;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ThreadGuardTest {

    @Test
    void workerThreadRejectedWhenServerThreadRequired() {
        ThreadGuard.markServerThread();
        assertDoesNotThrow(ThreadGuard::requireServerThread);
    }

    @Test
    void unmarkedThreadFailsServerCheck() throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            assertThrows(IllegalStateException.class, ThreadGuard::requireServerThread);
            return null;
        });
        Thread other = new Thread(task);
        other.start();
        other.join();
        task.get();
    }

    @Test
    void serverThreadRejectedWhenWorkerRequired() {
        ThreadGuard.markServerThread();
        assertThrows(IllegalStateException.class, ThreadGuard::requireWorkerThread);
    }
}
