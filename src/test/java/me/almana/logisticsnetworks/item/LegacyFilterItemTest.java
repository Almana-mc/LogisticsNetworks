package me.almana.logisticsnetworks.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegacyFilterItemTest {
    @Test
    void preservesHistoricalIds() {
        assertEquals(List.of("amount_filter", "durability_filter", "nbt_filter", "slot_filter", "tag_filter"),
                Arrays.stream(LegacyFilterItem.Kind.values()).map(LegacyFilterItem.Kind::id).toList());
    }
}
