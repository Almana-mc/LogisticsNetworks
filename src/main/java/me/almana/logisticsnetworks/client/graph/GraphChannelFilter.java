package me.almana.logisticsnetworks.client.graph;

import me.almana.logisticsnetworks.data.ChannelMode;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.graph.GraphChannel;
import me.almana.logisticsnetworks.data.graph.GraphNode;
import me.almana.logisticsnetworks.data.graph.NetworkGraph;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;

import java.util.List;
import java.util.Set;

final class GraphChannelFilter {
    static final int ALL = -1;
    static final int SENDER = 1;
    static final int RECEIVER = 2;

    private GraphChannelFilter() {
    }

    static int next(int channel) {
        return channel + 1 < LogisticsNodeEntity.CHANNEL_COUNT ? channel + 1 : ALL;
    }

    static boolean carries(NetworkGraph.Edge edge, int channel) {
        return channel == ALL || (edge.channels() & 1 << channel) != 0;
    }

    static int roles(List<GraphNode> members, int channel, Set<ChannelType> types) {
        int roles = 0;
        for (GraphNode member : members) {
            for (GraphChannel route : member.channels()) {
                if ((channel == ALL || route.index() == channel) && types.contains(route.type())) {
                    roles |= route.mode() == ChannelMode.EXPORT ? SENDER : RECEIVER;
                }
            }
        }
        return roles;
    }
}
