package me.almana.logisticsnetworks.filter;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NbtRuleMatcherTest {

    @Test
    void comparesNumericRules() {
        assertTrue(NbtRuleMatcher.matchesValue(">=", IntTag.valueOf(4), IntTag.valueOf(5)));
        assertFalse(NbtRuleMatcher.matchesValue("<", IntTag.valueOf(4), IntTag.valueOf(5)));
    }

    @Test
    void matchesNestedSubsets() {
        CompoundTag expected = new CompoundTag();
        CompoundTag expectedDisplay = new CompoundTag();
        expectedDisplay.putString("Name", "crate");
        expected.put("display", expectedDisplay);

        CompoundTag actual = expected.copy();
        actual.putInt("Count", 3);
        assertTrue(NbtRuleMatcher.compoundContains(actual, expected));
    }
}
