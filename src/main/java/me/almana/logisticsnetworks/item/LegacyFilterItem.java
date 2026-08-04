package me.almana.logisticsnetworks.item;

public final class LegacyFilterItem extends BaseFilterItem {
    public enum Kind {
        AMOUNT("amount_filter"),
        DURABILITY("durability_filter"),
        NBT("nbt_filter"),
        SLOT("slot_filter"),
        TAG("tag_filter");

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    private final Kind kind;

    public LegacyFilterItem(Properties properties, Kind kind) {
        super(properties, 45);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
