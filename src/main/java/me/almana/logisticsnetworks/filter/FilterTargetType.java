package me.almana.logisticsnetworks.filter;

import me.almana.logisticsnetworks.data.ChannelType;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

public enum FilterTargetType implements StringRepresentable {
    ITEMS,
    FLUIDS,
    CHEMICALS;

    public static FilterTargetType forChannel(ChannelType type) {
        return switch (type) {
            case ITEM -> ITEMS;
            case FLUID -> FLUIDS;
            case CHEMICAL -> CHEMICALS;
            case ENERGY, SOURCE -> null;
        };
    }

    public FilterTargetType next() {
        FilterTargetType[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static FilterTargetType fromOrdinal(int ordinal) {
        FilterTargetType[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return ITEMS;
        }
        return values[ordinal];
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
