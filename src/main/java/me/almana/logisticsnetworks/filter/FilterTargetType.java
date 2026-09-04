package me.almana.logisticsnetworks.filter;

import com.mojang.serialization.Codec;
import me.almana.logisticsnetworks.data.ChannelType;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

public enum FilterTargetType implements StringRepresentable {
    ITEMS,
    FLUIDS,
    CHEMICALS;

    public static final Codec<FilterTargetType> CODEC = StringRepresentable.fromEnum(FilterTargetType::values);

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
    public @NotNull String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
