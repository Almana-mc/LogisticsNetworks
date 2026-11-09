package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.FlowResource;

import java.util.HashMap;
import java.util.Map;

public class ChannelTelemetry {

    private final long[] flows = new long[ChannelType.values().length];
    private final Map<FlowResource, Long> resources = new HashMap<>();

    public void record(ChannelType type, long amount) {
        flows[type.ordinal()] += amount;
    }

    public void recordResource(FlowResource resource, long amount) {
        if (amount > 0) {
            resources.merge(resource, amount, Long::sum);
        }
    }

    public void drainFlows(long[] into) {
        for (int i = 0; i < flows.length; i++) {
            into[i] += flows[i];
            flows[i] = 0;
        }
    }

    public void drainResources(Map<FlowResource, Long> into) {
        resources.forEach((resource, amount) -> into.merge(resource, amount, Long::sum));
        resources.clear();
    }
}
