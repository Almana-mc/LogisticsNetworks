package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.FlowResource;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.network.SyncTelemetryPayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class TelemetryDictionary {

    private static final int MAX_ENTRIES = 4096;
    private static final int RESET_AT = MAX_ENTRIES
            - LogisticsNodeEntity.CHANNEL_COUNT * TelemetryManager.TOP_RESOURCES;

    private final Map<FlowResource, Integer> ids = new HashMap<>();

    SyncTelemetryPayload encode(UUID networkId, List<TelemetryManager.ChannelDrain> channels) {
        boolean reset = ids.size() > RESET_AT;
        if (reset) {
            ids.clear();
        }
        List<SyncTelemetryPayload.Definition> added = new ArrayList<>();
        List<SyncTelemetryPayload.ChannelSample> samples = new ArrayList<>(channels.size());
        for (TelemetryManager.ChannelDrain channel : channels) {
            List<SyncTelemetryPayload.Entry> entries = new ArrayList<>(channel.top().size());
            for (Map.Entry<FlowResource, Long> entry : channel.top()) {
                Integer id = ids.get(entry.getKey());
                if (id == null) {
                    id = ids.size();
                    ids.put(entry.getKey(), id);
                    added.add(new SyncTelemetryPayload.Definition(id, entry.getKey()));
                }
                entries.add(new SyncTelemetryPayload.Entry(id, entry.getValue()));
            }
            samples.add(new SyncTelemetryPayload.ChannelSample(channel.typeOrdinal(), channel.total(), entries));
        }
        return new SyncTelemetryPayload(networkId, reset, added, samples);
    }
}
