package me.almana.logisticsnetworks.filter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NameFilterValidationTest {

    @Test
    void acceptsBoundedExpressions() {
        assertTrue(NameFilterData.validateRegex("iron_[a-z]").accepted());
    }

    @Test
    void rejectsInvalidAndOversizedExpressions() {
        assertEquals(NameFilterData.ValidationError.INVALID,
                NameFilterData.validateRegex("[").error());
        assertEquals(NameFilterData.ValidationError.TOO_LONG,
                NameFilterData.validateRegex("x".repeat(NameFilterData.MAX_EXPRESSION_LENGTH + 1)).error());
    }
}
