package me.almana.logisticsnetworks.filter;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

public final class NbtRuleMatcher {
    private static final String OP_EQUALS = "=";
    private static final String OP_NOT_EQUALS = "!=";
    private static final String[] OPS = { "=", "!=", ">", "<", ">=", "<=" };

    private NbtRuleMatcher() {
    }

    public static String normalizeOperator(@Nullable String operator) {
        if (operator == null) {
            return OP_EQUALS;
        }
        return switch (operator) {
            case "!=", ">", "<", ">=", "<=" -> operator;
            default -> OP_EQUALS;
        };
    }

    static String nextOperator(String current) {
        for (int i = 0; i < OPS.length; i++) {
            if (OPS[i].equals(current)) {
                return OPS[(i + 1) % OPS.length];
            }
        }
        return OPS[0];
    }

    static boolean matchesValue(@Nullable String operator, Tag expected, @Nullable Tag actual) {
        if (actual == null) {
            return OP_NOT_EQUALS.equals(operator);
        }
        String op = operator != null ? operator : OP_EQUALS;
        return switch (op) {
            case "!=" -> !expected.equals(actual);
            case ">", "<", ">=", "<=" -> compareNumeric(op, expected, actual);
            default -> expected.equals(actual);
        };
    }

    static boolean compoundContains(CompoundTag actual, CompoundTag expected) {
        for (String key : expected.keySet()) {
            Tag expectedVal = expected.get(key);
            Tag actualVal = actual.get(key);
            if (actualVal == null || expectedVal == null) {
                return false;
            }
            if (expectedVal instanceof CompoundTag expectedCompound && actualVal instanceof CompoundTag actualCompound) {
                if (!compoundContains(actualCompound, expectedCompound)) {
                    return false;
                }
            } else if (!expectedVal.equals(actualVal)) {
                return false;
            }
        }
        return true;
    }

    private static boolean compareNumeric(String op, Tag expected, Tag actual) {
        double exp = tagToDouble(expected);
        double act = tagToDouble(actual);
        if (Double.isNaN(exp) || Double.isNaN(act)) {
            return false;
        }
        return switch (op) {
            case ">" -> act > exp;
            case "<" -> act < exp;
            case ">=" -> act >= exp;
            case "<=" -> act <= exp;
            default -> false;
        };
    }

    private static double tagToDouble(Tag tag) {
        if (tag instanceof NumericTag numeric) {
            return numeric.doubleValue();
        }
        if (tag instanceof StringTag string) {
            try {
                return Double.parseDouble(string.value());
            } catch (Exception e) {
                return Double.NaN;
            }
        }
        try {
            return Double.parseDouble(tag.toString());
        } catch (Exception e) {
            return Double.NaN;
        }
    }
}
