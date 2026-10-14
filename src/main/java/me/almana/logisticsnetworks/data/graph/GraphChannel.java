package me.almana.logisticsnetworks.data.graph;

import me.almana.logisticsnetworks.data.ChannelMode;
import me.almana.logisticsnetworks.data.ChannelType;

public record GraphChannel(int index, ChannelType type, ChannelMode mode) {

    public GraphChannel {
        if (index < 0 || index >= 9) {
            throw new IllegalArgumentException("Channel index out of range: " + index);
        }
    }
}
