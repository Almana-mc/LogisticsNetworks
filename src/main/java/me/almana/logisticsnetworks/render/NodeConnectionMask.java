package me.almana.logisticsnetworks.render;

import net.minecraft.core.Direction;

public final class NodeConnectionMask {

    public static final int NONE = 0;

    private NodeConnectionMask() {
    }

    public static int add(int mask, Direction direction) {
        return mask | bit(direction);
    }

    public static boolean has(int mask, Direction direction) {
        return (mask & bit(direction)) != 0;
    }

    static int remove(int mask, Direction direction) {
        return mask & ~bit(direction);
    }

    static int addCorner(int mask, Direction first, Direction second) {
        return mask | cornerBit(first, second);
    }

    static boolean hasCorner(int mask, Direction first, Direction second) {
        return (mask & cornerBit(first, second)) != 0;
    }

    static boolean isCornerPair(Direction first, Direction second) {
        return first.getAxis() != second.getAxis();
    }

    private static int bit(Direction direction) {
        return 1 << direction.ordinal();
    }

    private static int cornerBit(Direction first, Direction second) {
        if (!isCornerPair(first, second)) {
            throw new IllegalArgumentException("Directions must be orthogonal");
        }

        Direction low = first.ordinal() < second.ordinal() ? first : second;
        Direction high = first.ordinal() < second.ordinal() ? second : first;
        Direction[] directions = Direction.values();
        int index = 0;
        for (int i = 0; i < directions.length; i++) {
            for (int j = i + 1; j < directions.length; j++) {
                if (!isCornerPair(directions[i], directions[j])) {
                    continue;
                }
                if (directions[i] == low && directions[j] == high) {
                    return 1 << (6 + index);
                }
                index++;
            }
        }
        throw new IllegalArgumentException("Unsupported corner pair");
    }
}
