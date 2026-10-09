package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.data.FlowResource;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.network.SyncTelemetryPayload;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class FlowHistory {

    private static final int SIZE = 120;
    private static final float GROW_MS = 300f;
    private static final Sample EMPTY = new Sample(0, Map.of());

    record Sample(long total, Map<FlowResource, Long> resources) {
        long other() {
            long listed = 0;
            for (long amount : resources.values()) {
                listed += amount;
            }
            return Math.max(0, total - listed);
        }
    }

    private final Sample[][] samples = new Sample[LogisticsNodeEntity.CHANNEL_COUNT][SIZE];
    private final int[] types = new int[LogisticsNodeEntity.CHANNEL_COUNT];
    private final Map<Integer, FlowResource> dictionary = new HashMap<>();
    private int head;
    private int count;
    private long receivedAt;

    FlowHistory() {
        for (Sample[] row : samples) {
            Arrays.fill(row, EMPTY);
        }
        Arrays.fill(types, -1);
    }

    void accept(SyncTelemetryPayload payload) {
        if (payload.reset()) {
            dictionary.clear();
        }
        for (SyncTelemetryPayload.Definition definition : payload.added()) {
            dictionary.put(definition.id(), definition.resource());
        }
        for (int channel = 0; channel < types.length; channel++) {
            SyncTelemetryPayload.ChannelSample sample = payload.channels().get(channel);
            Map<FlowResource, Long> resources = new HashMap<>();
            for (SyncTelemetryPayload.Entry entry : sample.entries()) {
                FlowResource resource = dictionary.get(entry.id());
                if (resource != null) {
                    resources.merge(resource, entry.amount(), Long::sum);
                }
            }
            types[channel] = sample.typeOrdinal();
            samples[channel][head] = new Sample(sample.total(), resources);
        }
        head = (head + 1) % SIZE;
        count = Math.min(SIZE, count + 1);
        receivedAt = Util.getMillis();
    }

    boolean hasData() {
        return count > 0;
    }

    int type(int channel) {
        return types[channel];
    }

    long value(int channel, int ago, @Nullable FlowResource filter) {
        Sample sample = sample(channel, ago);
        return filter == null ? sample.total() : sample.resources().getOrDefault(filter, 0L);
    }

    long peak(int channel, int window, @Nullable FlowResource filter) {
        long peak = 0;
        for (int ago = 0; ago < window; ago++) {
            peak = Math.max(peak, value(channel, ago, filter));
        }
        return peak;
    }

    long sum(int channel, int window, @Nullable FlowResource filter) {
        long sum = 0;
        for (int ago = 0; ago < window; ago++) {
            sum += value(channel, ago, filter);
        }
        return sum;
    }

    int span(int window) {
        return Math.max(1, Math.min(window, count));
    }

    long average(int channel, int window, @Nullable FlowResource filter) {
        return sum(channel, window, filter) / span(window);
    }

    List<Map.Entry<FlowResource, Long>> totals(int channel, int window) {
        Map<FlowResource, Long> totals = new HashMap<>();
        for (int ago = 0; ago < window; ago++) {
            sample(channel, ago).resources().forEach((resource, amount) -> totals.merge(resource, amount, Long::sum));
        }
        return totals.entrySet().stream()
                .sorted(Map.Entry.<FlowResource, Long>comparingByValue().reversed())
                .toList();
    }

    long otherTotal(int channel, int window) {
        long other = 0;
        for (int ago = 0; ago < window; ago++) {
            other += sample(channel, ago).other();
        }
        return other;
    }

    float growProgress() {
        float progress = Math.min(1f, (Util.getMillis() - receivedAt) / GROW_MS);
        float rest = 1f - progress;
        return 1f - rest * rest * rest;
    }

    static long niceStep(long max) {
        double raw = Math.max(1, max) / 4.0;
        double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
        for (double factor : new double[] {1, 2, 2.5, 5, 10}) {
            if (factor * magnitude >= raw) {
                return Math.max(1, Math.round(factor * magnitude));
            }
        }
        return Math.max(1, Math.round(10 * magnitude));
    }

    static String format(long value) {
        if (value < 1000) return String.valueOf(value);
        if (value < 1_000_000) return compact(value / 1000.0) + "K";
        return compact(value / 1_000_000.0) + "M";
    }

    private static String compact(double value) {
        String text = String.format(Locale.ROOT, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    private Sample sample(int channel, int ago) {
        return samples[channel][Math.floorMod(head - 1 - ago, SIZE)];
    }
}
