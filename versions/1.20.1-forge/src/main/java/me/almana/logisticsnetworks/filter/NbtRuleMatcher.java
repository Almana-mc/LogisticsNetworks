package me.almana.logisticsnetworks.filter;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

final class NbtRuleMatcher {

    private static final String[] OPERATORS = { "=", "!=", ">", "<", ">=", "<=" };

    private NbtRuleMatcher() {
    }

    static String normalizeOperator(@Nullable String operator) {
        if (operator == null) return "=";
        return switch (operator) {
            case "!=", ">", "<", ">=", "<=" -> operator;
            default -> "=";
        };
    }

    static String nextOperator(String current) {
        for (int i = 0; i < OPERATORS.length; i++) {
            if (OPERATORS[i].equals(current)) return OPERATORS[(i + 1) % OPERATORS.length];
        }
        return OPERATORS[0];
    }

    static boolean matchesValue(@Nullable String operator, Tag expected, @Nullable Tag actual) {
        if (actual == null) return "!=".equals(operator);
        String normalized = normalizeOperator(operator);
        return switch (normalized) {
            case "!=" -> !expected.equals(actual);
            case ">", "<", ">=", "<=" -> compareNumeric(normalized, expected, actual);
            default -> expected.equals(actual);
        };
    }

    static boolean compoundContains(CompoundTag actual, CompoundTag expected) {
        for (String key : expected.getAllKeys()) {
            Tag expectedValue = expected.get(key);
            Tag actualValue = actual.get(key);
            if (actualValue == null || expectedValue == null) return false;
            if (expectedValue instanceof CompoundTag expectedCompound
                    && actualValue instanceof CompoundTag actualCompound) {
                if (!compoundContains(actualCompound, expectedCompound)) return false;
            } else if (!expectedValue.equals(actualValue)) {
                return false;
            }
        }
        return true;
    }

    private static boolean compareNumeric(String operator, Tag expected, Tag actual) {
        double expectedValue = tagToDouble(expected);
        double actualValue = tagToDouble(actual);
        if (Double.isNaN(expectedValue) || Double.isNaN(actualValue)) return false;
        return switch (operator) {
            case ">" -> actualValue > expectedValue;
            case "<" -> actualValue < expectedValue;
            case ">=" -> actualValue >= expectedValue;
            case "<=" -> actualValue <= expectedValue;
            default -> false;
        };
    }

    private static double tagToDouble(Tag tag) {
        if (tag instanceof NumericTag numeric) return numeric.getAsDouble();
        try {
            return Double.parseDouble(tag.getAsString());
        } catch (Exception ignored) {
            return Double.NaN;
        }
    }
}
