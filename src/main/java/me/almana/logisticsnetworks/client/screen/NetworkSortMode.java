package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.network.SyncNetworkListPayload.NetworkEntry;

import java.util.Comparator;

enum NetworkSortMode {
    NAME_ASC("gui.logisticsnetworks.node.sort.az"),
    NAME_DESC("gui.logisticsnetworks.node.sort.za"),
    OLD_NEW("gui.logisticsnetworks.node.sort.old_new"),
    NEW_OLD("gui.logisticsnetworks.node.sort.new_old");

    private static final Comparator<NetworkEntry> BY_NAME =
            Comparator.comparing(NetworkEntry::name, String.CASE_INSENSITIVE_ORDER);
    private static final Comparator<NetworkEntry> BY_AGE = Comparator.comparingLong(NetworkEntry::createdAt);

    private final String labelKey;

    NetworkSortMode(String labelKey) {
        this.labelKey = labelKey;
    }

    NetworkSortMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    String labelKey() {
        return labelKey;
    }

    Comparator<NetworkEntry> comparator() {
        return switch (this) {
            case NAME_ASC -> BY_NAME;
            case NAME_DESC -> BY_NAME.reversed();
            case OLD_NEW -> BY_AGE.thenComparing(BY_NAME);
            case NEW_OLD -> BY_AGE.reversed().thenComparing(BY_NAME);
        };
    }
}
