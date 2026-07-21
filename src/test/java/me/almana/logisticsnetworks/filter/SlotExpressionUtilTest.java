package me.almana.logisticsnetworks.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class SlotExpressionUtilTest {

    @Test
    void parsesIncludesAndExcludes() {
        assertEquals(List.of(1, 3, 4), slots("1-4,!2"));
    }

    @Test
    void parsesExcludeOnlyMask() {
        assertEquals(List.of(52, 53), slots("!0-51"));
    }

    @Test
    void rejectsOutOfRangeSlots() {
        assertNull(SlotExpressionUtil.parseMask("54"));
    }

    @Test
    void formatsContiguousSlots() {
        assertEquals("1-3, 7", SlotExpressionUtil.formatSlots(List.of(1, 2, 3, 7)));
    }

    private static List<Integer> slots(String expression) {
        return SlotExpressionUtil.bitSetToList(SlotExpressionUtil.parseMask(expression));
    }
}
