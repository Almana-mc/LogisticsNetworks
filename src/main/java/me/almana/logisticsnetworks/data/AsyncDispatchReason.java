package me.almana.logisticsnetworks.data;

enum AsyncDispatchReason {
    STALE_GENERATION,
    WRONG_RUNTIME,
    QUEUE_REJECTED,
    CAPTURE_UNAVAILABLE,
    NO_READY_ITEM_WORK,
    OCCUPIED_SLOT_LIMIT,
    COMMIT_REVALIDATION,
    WORKER_EXCEPTION
}
