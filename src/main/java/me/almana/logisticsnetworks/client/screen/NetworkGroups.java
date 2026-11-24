package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.network.SyncNetworkListPayload.NetworkEntry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class NetworkGroups {
    private static final Set<String> COLLAPSED = new HashSet<>();

    private NetworkGroups() {
    }

    static void toggle(String group) {
        if (!COLLAPSED.remove(group)) COLLAPSED.add(group);
    }

    static List<Row> rows(List<NetworkEntry> sorted, boolean searching) {
        Map<String, List<NetworkEntry>> groups = new HashMap<>();
        for (NetworkEntry entry : sorted) {
            String group = group(entry.name());
            if (group != null) groups.computeIfAbsent(group, key -> new ArrayList<>()).add(entry);
        }
        List<Row> rows = new ArrayList<>();
        for (NetworkEntry entry : sorted) {
            String group = group(entry.name());
            if (group == null) {
                rows.add(new Row(entry, entry.name(), 0, true));
                continue;
            }
            List<NetworkEntry> members = groups.remove(group);
            if (members == null) continue;
            boolean open = searching || !COLLAPSED.contains(group);
            rows.add(new Row(null, group, members.size(), open));
            if (open) members.forEach(member -> rows.add(new Row(member, child(member.name()), 0, true)));
        }
        return rows;
    }

    @Nullable
    private static String group(String name) {
        int slash = name.indexOf('/');
        if (slash < 0 || child(name).isEmpty()) return null;
        String group = name.substring(0, slash).strip();
        return group.isEmpty() ? null : group;
    }

    private static String child(String name) {
        return name.substring(name.indexOf('/') + 1).strip();
    }

    record Row(@Nullable NetworkEntry entry, String label, int count, boolean open) {
        boolean header() {
            return entry == null;
        }
    }
}
