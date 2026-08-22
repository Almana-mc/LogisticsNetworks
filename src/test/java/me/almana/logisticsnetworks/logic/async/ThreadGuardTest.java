package me.almana.logisticsnetworks.logic.async;

import org.junit.jupiter.api.Test;

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
        Thread other = new Thread(() -> assertThrows(IllegalStateException.class, ThreadGuard::requireServerThread));
        other.start();
        other.join();
    }

    @Test
    void serverThreadRejectedWhenWorkerRequired() {
        ThreadGuard.markServerThread();
        assertThrows(IllegalStateException.class, ThreadGuard::requireWorkerThread);
    }
}
