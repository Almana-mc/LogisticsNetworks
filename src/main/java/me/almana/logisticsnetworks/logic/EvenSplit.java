package me.almana.logisticsnetworks.logic;

public final class EvenSplit {

    public interface Move {
        long apply(int target, long limit);
    }

    private EvenSplit() {
    }

    public static long share(long remaining, int openTargets) {
        return Math.max(1, remaining / openTargets);
    }

    public static long deal(int targets, long budget, Move move) {
        boolean[] closed = new boolean[targets];
        int open = targets;
        long remaining = budget;
        while (remaining > 0 && open > 0) {
            long share = share(remaining, open);
            for (int i = 0; i < targets && remaining > 0; i++) {
                if (closed[i]) continue;
                long moved = move.apply(i, share);
                remaining -= moved;
                // Short fill means target full
                if (moved < share) {
                    closed[i] = true;
                    open--;
                }
            }
        }
        return budget - remaining;
    }
}
