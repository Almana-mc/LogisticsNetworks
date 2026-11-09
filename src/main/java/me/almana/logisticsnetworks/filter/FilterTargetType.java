package me.almana.logisticsnetworks.filter;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

public enum FilterTargetType implements StringRepresentable {
    ITEMS,
    FLUIDS,
    CHEMICALS;

    public static final Codec<FilterTargetType> CODEC = StringRepresentable.fromEnum(FilterTargetType::values);

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
