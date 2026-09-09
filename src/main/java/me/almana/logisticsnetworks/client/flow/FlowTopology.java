package me.almana.logisticsnetworks.client.flow;

import me.almana.logisticsnetworks.data.ChannelMode;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.NodeRouteChannels;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

final class FlowTopology {
    private static final ChannelType[] TYPES = ChannelType.values();
    private static final Comparator<Key> ORDER = Comparator.comparing(Key::network)
            .thenComparingInt(Key::channel).thenComparing(Key::type);

    private FlowTopology() {
    }

    static List<Bundle> build(List<Node> nodes) {
        Map<Key, Ends> groups = new TreeMap<>(ORDER);
        for (Node node : nodes) {
            for (int channel = 0; channel < 9; channel++) {
                for (ChannelType type : TYPES) {
                    boolean source = NodeRouteChannels.matches(node.channels(), channel, type, ChannelMode.EXPORT);
                    boolean target = NodeRouteChannels.matches(node.channels(), channel, type, ChannelMode.IMPORT);
                    if (!source && !target) continue;
                    Ends ends = groups.computeIfAbsent(new Key(node.network(), channel, type), ignored -> new Ends());
                    (source ? ends.sources : ends.targets).add(node.id());
                }
            }
        }
        List<Bundle> result = new ArrayList<>();
        groups.forEach((key, ends) -> {
            if (!ends.sources.isEmpty() && !ends.targets.isEmpty()) {
                ends.sources.sort(UUID::compareTo);
                ends.targets.sort(UUID::compareTo);
                result.add(new Bundle(key, List.copyOf(ends.sources), List.copyOf(ends.targets)));
            }
        });
        return List.copyOf(result);
    }

    record Node(UUID id, UUID network, long channels) {
    }

    record Key(UUID network, int channel, ChannelType type) {
    }

    record Bundle(Key key, List<UUID> sources, List<UUID> targets) {
    }

    private static final class Ends {
        private final List<UUID> sources = new ArrayList<>();
        private final List<UUID> targets = new ArrayList<>();
    }
}
