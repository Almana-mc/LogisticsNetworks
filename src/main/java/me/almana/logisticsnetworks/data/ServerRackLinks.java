package me.almana.logisticsnetworks.data;

import net.minecraft.core.GlobalPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ServerRackLinks {

    public record Link(int channel, UUID peer, int peerChannel) {
    }

    private final Map<GlobalPos, ServerRackConfig> racks = new HashMap<>();
    private final Map<UUID, List<Link>> byNetwork = new HashMap<>();

    public List<Link> linksFor(UUID networkId) {
        return byNetwork.getOrDefault(networkId, List.of());
    }

    Set<UUID> put(GlobalPos pos, ServerRackConfig config) {
        Set<UUID> affected = networksOf(racks.put(pos, config));
        affected.addAll(networksOf(config));
        reindex();
        return affected;
    }

    Set<UUID> remove(GlobalPos pos) {
        Set<UUID> affected = networksOf(racks.remove(pos));
        reindex();
        return affected;
    }

    // Both directions, channels swapped
    private void reindex() {
        byNetwork.clear();
        for (ServerRackConfig config : racks.values()) {
            if (!config.bridges()) {
                continue;
            }
            UUID left = config.left().get();
            UUID right = config.right().get();
            for (ServerRackConfig.Row row : config.rows()) {
                if (!row.linked()) {
                    continue;
                }
                addLink(left, new Link(row.left(), right, row.right()));
                addLink(right, new Link(row.right(), left, row.left()));
            }
        }
    }

    private void addLink(UUID network, Link link) {
        List<Link> list = byNetwork.computeIfAbsent(network, id -> new ArrayList<>());
        if (!list.contains(link)) {
            list.add(link);
        }
    }

    private static Set<UUID> networksOf(@Nullable ServerRackConfig config) {
        Set<UUID> ids = new HashSet<>();
        if (config != null) {
            config.left().ifPresent(ids::add);
            config.right().ifPresent(ids::add);
        }
        return ids;
    }
}
