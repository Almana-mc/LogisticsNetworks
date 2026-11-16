package me.almana.logisticsnetworks.client.screen;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

final class NodeTableSession {
    record State(UUID networkId, String networkName, int scroll, Set<String> collapsedGroups,
                 Set<UUID> selectedNodes, UUID settingsSource, String label) {
        State {
            collapsedGroups = Set.copyOf(collapsedGroups);
            selectedNodes = Set.copyOf(selectedNodes);
        }
    }

    private static State returning;

    private NodeTableSession() {
    }

    static void begin(UUID networkId, String networkName, int scroll, Set<String> collapsedGroups,
                      Set<UUID> selectedNodes, UUID settingsSource, String label) {
        returning = new State(networkId, networkName, scroll, new LinkedHashSet<>(collapsedGroups),
                new LinkedHashSet<>(selectedNodes), settingsSource, label);
    }

    static State take() {
        State state = returning;
        returning = null;
        return state;
    }
}
