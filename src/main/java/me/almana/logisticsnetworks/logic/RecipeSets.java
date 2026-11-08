package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.DistributionMode;

public final class RecipeSets {

    public interface SetMove {
        int apply(int target, int sets);
    }

    private RecipeSets() {
    }

    static int sets(int available, int budget, int setSize, boolean first) {
        if (setSize <= 0) return 0;
        int byBudget = budget / setSize;
        return Math.min(available, byBudget == 0 && first ? 1 : byBudget);
    }

    static int deal(DistributionMode mode, int targets, int sets, SetMove move) {
        return switch (mode) {
            case ROUND_ROBIN -> (int) EvenSplit.deal(targets, sets, (target, limit) -> move.apply(target, (int) limit));
            case PRIORITY_ROBIN -> firstTaker(targets, sets, move);
            default -> inOrder(targets, sets, move);
        };
    }

    private static int firstTaker(int targets, int sets, SetMove move) {
        for (int target = 0; target < targets; target++) {
            int moved = move.apply(target, sets);
            if (moved > 0) return moved;
        }
        return 0;
    }

    private static int inOrder(int targets, int sets, SetMove move) {
        int left = sets;
        for (int target = 0; target < targets && left > 0; target++) {
            left -= move.apply(target, left);
        }
        return sets - left;
    }
}
