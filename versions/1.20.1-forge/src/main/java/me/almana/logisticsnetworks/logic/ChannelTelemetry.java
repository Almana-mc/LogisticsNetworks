package me.almana.logisticsnetworks.logic;

public class ChannelTelemetry {

    private long currentFlow;

    public void record(long amount) {
        currentFlow += amount;
    }

    public long drainFlow() {
        long flow = currentFlow;
        currentFlow = 0;
        return flow;
    }
}
