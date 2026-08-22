package me.almana.logisticsnetworks.logic.async;

public final class ThreadGuard {

    private static Thread serverThread;

    private ThreadGuard() {
    }

    public static void markServerThread() {
        serverThread = Thread.currentThread();
    }

    public static void clearServerThread() {
        serverThread = null;
    }

    public static void requireServerThread() {
        if (Thread.currentThread() != serverThread) {
            throw new IllegalStateException(
                    "Minecraft state accessed off the server thread: " + Thread.currentThread().getName());
        }
    }

    public static void requireWorkerThread() {
        if (Thread.currentThread() == serverThread) {
            throw new IllegalStateException("Planner ran on the server thread");
        }
    }
}
