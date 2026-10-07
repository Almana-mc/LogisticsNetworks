package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.FlowResource;

import java.util.HashMap;
import java.util.Map;

public class ChannelTelemetry {

    private long currentFlow;
    private final Map<FlowResource, Long> resources = new HashMap<>();

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
