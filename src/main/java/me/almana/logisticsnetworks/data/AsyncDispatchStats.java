package me.almana.logisticsnetworks.data;

import java.util.EnumMap;
import java.util.StringJoiner;
import java.util.UUID;

final class AsyncDispatchStats {

    private static final long SUMMARY_INTERVAL_TICKS = 1_200L;

    private final EnumMap<AsyncDispatchReason, Long> counts =
            new EnumMap<>(AsyncDispatchReason.class);
    private final EnumMap<AsyncDispatchReason, UUID> latestNetworks =
            new EnumMap<>(AsyncDispatchReason.class);
    private long lastSummaryTick = -SUMMARY_INTERVAL_TICKS;

    void record(AsyncDispatchReason reason, UUID networkId) {
        counts.merge(reason, 1L, Long::sum);
        latestNetworks.put(reason, networkId);
    }

    long count(AsyncDispatchReason reason) {
        return counts.getOrDefault(reason, 0L);
    }

    UUID latestNetwork(AsyncDispatchReason reason) {
        return latestNetworks.get(reason);
    }

    String summary(long gameTime) {
        if (gameTime - lastSummaryTick < SUMMARY_INTERVAL_TICKS) {
            return null;
        }
        StringJoiner summary = new StringJoiner(", ");
        for (AsyncDispatchReason reason : AsyncDispatchReason.values()) {
            long count = count(reason);
            if (count != 0L) {
                summary.add(reason + "=" + count + " (" + latestNetwork(reason) + ")");
            }
        }
        if (summary.length() == 0) {
            return null;
        }
        lastSummaryTick = gameTime;
        return summary.toString();
    }
}
