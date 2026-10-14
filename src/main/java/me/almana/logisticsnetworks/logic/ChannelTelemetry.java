package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.FlowResource;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class ChannelTelemetry {

    // Server thread only
    @Nullable
    private static ChannelTelemetry recording;

    private long currentFlow;
    private final Map<FlowResource, Long> resources = new HashMap<>();

    public static void startRecording(@Nullable ChannelTelemetry telemetry) {
        recording = telemetry;
    }

    public static void recordMoved(FlowResource resource, long amount) {
        if (recording != null) {
            recording.recordResource(resource, amount);
        }
    }

    public static boolean isRecording() {
        return recording != null;
    }

    public void record(long amount) {
        currentFlow += amount;
    }

    public void recordResource(FlowResource resource, long amount) {
        if (amount > 0) {
            resources.merge(resource, amount, Long::sum);
        }
    }

    public long drainFlow() {
        long flow = currentFlow;
        currentFlow = 0;
        return flow;
    }

    public void drainResources(Map<FlowResource, Long> into) {
        resources.forEach((resource, amount) -> into.merge(resource, amount, Long::sum));
        resources.clear();
    }
}
