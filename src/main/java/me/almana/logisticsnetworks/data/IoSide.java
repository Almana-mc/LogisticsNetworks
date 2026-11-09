package me.almana.logisticsnetworks.data;

import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public enum IoSide {
    DOWN, UP, NORTH, SOUTH, WEST, EAST, ALL, NONE;

    public static IoSide of(@Nullable Direction direction) {
        return direction == null ? ALL : values()[direction.ordinal()];
    }

    @Nullable
    public Direction direction() {
        return this.compareTo(ALL) < 0 ? Direction.values()[ordinal()] : null;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static IoSide byKey(String key) {
        for (IoSide side : values()) {
            if (side.key().equals(key)) return side;
        }
        return UP;
    }
}
