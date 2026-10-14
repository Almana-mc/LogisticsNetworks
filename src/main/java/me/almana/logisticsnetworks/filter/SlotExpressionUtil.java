package me.almana.logisticsnetworks.filter;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public final class SlotExpressionUtil {

    public static final int MIN_SLOT = 0;
    public static final int MAX_SLOT = 53;

    private SlotExpressionUtil() {
    }

    public static String formatSlots(List<Integer> slots) {
        if (slots == null || slots.isEmpty()) {
            return "";
        }

        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < slots.size()) {
            int start = slots.get(i);
            int end = start;
            while (i + 1 < slots.size() && slots.get(i + 1) == end + 1) {
                i++;
                end = slots.get(i);
            }

            if (!out.isEmpty()) {
                out.append(", ");
            }
            if (start == end) {
                out.append(start);
            } else {
                out.append(start).append('-').append(end);
            }
            i++;
        }

        return out.toString();
    }

    @Nullable
    public static BitSet parseSlots(String expression) {
        BitSet bits = new BitSet(MAX_SLOT + 1);
        String[] tokens = expression.split("[,;\\s]+");

        for (String rawToken : tokens) {
            if (rawToken == null) {
                continue;
            }
            String token = rawToken.trim();
            if (token.isEmpty()) {
                continue;
            }

            int dash = token.indexOf('-');
            if (dash >= 0) {
                String left = token.substring(0, dash).trim();
                String right = token.substring(dash + 1).trim();
                Integer a = parseSlot(left);
                Integer b = parseSlot(right);
                if (a == null || b == null) {
                    return null;
                }

                int from = Math.min(a, b);
                int to = Math.max(a, b);
                bits.set(from, to + 1);
            } else {
                Integer slot = parseSlot(token);
                if (slot == null) {
                    return null;
                }
                bits.set(slot);
            }
        }
        return bits;
    }

    public static List<Integer> bitSetToList(BitSet bits) {
        List<Integer> slots = new ArrayList<>();
        for (int slot = bits.nextSetBit(MIN_SLOT); slot >= 0; slot = bits.nextSetBit(slot + 1)) {
            slots.add(slot);
        }
        return slots;
    }

    @Nullable
    public static BitSet parseMask(String expression) {
        if (expression == null) {
            return null;
        }

        BitSet include = new BitSet(MAX_SLOT + 1);
        BitSet exclude = new BitSet(MAX_SLOT + 1);
        boolean anyInclude = false;
        boolean anyToken = false;

        for (String rawToken : expression.split("[,;\\s]+")) {
            if (rawToken == null) {
                continue;
            }
            String token = rawToken.trim();
            boolean negate = token.startsWith("!");
            if (negate) {
                token = token.substring(1).trim();
            }
            if (token.isEmpty()) {
                continue;
            }
            anyToken = true;
            BitSet target = negate ? exclude : include;
            if (!negate) {
                anyInclude = true;
            }

            int dash = token.indexOf('-');
            if (dash >= 0) {
                Integer a = parseSlot(token.substring(0, dash).trim());
                Integer b = parseSlot(token.substring(dash + 1).trim());
                if (a == null || b == null) {
                    return null;
                }
                target.set(Math.min(a, b), Math.max(a, b) + 1);
            } else {
                Integer slot = parseSlot(token);
                if (slot == null) {
                    return null;
                }
                target.set(slot);
            }
        }

        if (!anyToken) {
            return new BitSet(0);
        }

        BitSet result;
        if (anyInclude) {
            result = include;
        } else {
            result = new BitSet(MAX_SLOT + 1);
            result.set(MIN_SLOT, MAX_SLOT + 1);
        }
        result.andNot(exclude);
        return result;
    }

    @Nullable
    private static Integer parseSlot(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw);
            if (value < MIN_SLOT || value > MAX_SLOT) {
                return null;
            }
            return value;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
