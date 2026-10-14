package me.almana.logisticsnetworks;

public enum NodeAccessMode {
    TEAMS("Teams"),
    ALL("All"),
    ALLIES("Allies");

    private final String serializedName;

    NodeAccessMode(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public boolean allows(boolean teammates, boolean allies) {
        return switch (this) {
            case TEAMS -> teammates;
            case ALL -> true;
            case ALLIES -> teammates || allies;
        };
    }

    public static NodeAccessMode fromSerializedName(String name) {
        for (NodeAccessMode mode : values()) {
            if (mode.serializedName.equals(name)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown node access mode: " + name);
    }
}
