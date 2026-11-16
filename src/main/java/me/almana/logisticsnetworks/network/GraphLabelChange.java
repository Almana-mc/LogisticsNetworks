package me.almana.logisticsnetworks.network;

import java.util.List;

public record GraphLabelChange(String label, int replacedLabels, Kind kind) {
    public enum Kind {
        DIRECT,
        REPLACE,
        JOIN,
        REMOVE,
        NONE
    }

    public static GraphLabelChange evaluate(List<String> selectedLabels, boolean targetExists,
                                            String requestedLabel) {
        String label = requestedLabel.trim();
        int replaced = (int) selectedLabels.stream()
                .filter(current -> !current.isEmpty() && !current.equals(label))
                .count();
        Kind kind = selectedLabels.stream().allMatch(label::equals) ? Kind.NONE
                : label.isEmpty() && replaced > 0 ? Kind.REMOVE
                : !label.isEmpty() && targetExists ? Kind.JOIN
                : replaced == 0 ? Kind.DIRECT : Kind.REPLACE;
        return new GraphLabelChange(label, replaced, kind);
    }

    public boolean requiresConfirmation() {
        return kind != Kind.DIRECT && kind != Kind.NONE;
    }
}
