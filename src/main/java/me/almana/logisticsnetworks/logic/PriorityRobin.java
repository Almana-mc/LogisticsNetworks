package me.almana.logisticsnetworks.logic;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToIntFunction;

public final class PriorityRobin {

    private PriorityRobin() {
    }

    public static <T> List<T> rotate(List<T> sorted, @Nullable Cursor cursor,
            ToIntFunction<T> priority, Function<T, UUID> nodeId) {
        if (cursor == null || sorted.size() <= 1) return sorted;
        int start = 0;
        while (start < sorted.size()
                && !cursor.precedes(priority.applyAsInt(sorted.get(start)), nodeId.apply(sorted.get(start)))) {
            start++;
        }
        if (start == 0 || start == sorted.size()) return sorted;
        List<T> rotated = new ArrayList<>(sorted);
        Collections.rotate(rotated, -start);
        return rotated;
    }

    // Matches priority-desc, uuid-asc sort
    public record Cursor(int priority, UUID nodeId) {
        boolean precedes(int otherPriority, UUID otherId) {
            return otherPriority < priority || otherPriority == priority && otherId.compareTo(nodeId) > 0;
        }
    }
}
