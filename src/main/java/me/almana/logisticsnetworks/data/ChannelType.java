package me.almana.logisticsnetworks.data;

import com.mojang.serialization.Codec;
import me.almana.logisticsnetworks.component.ComponentCodecs;

import java.util.ArrayList;
import java.util.List;

public enum ChannelType {
    ITEM,
    FLUID,
    ENERGY,
    CHEMICAL,
    SOURCE;

    public static final Codec<ChannelType> CODEC = ComponentCodecs.enumCodec(values(), ITEM);
    public static final int ALL = (1 << values().length) - 1;
    public static final int FILTERABLE = ITEM.bit() | FLUID.bit() | CHEMICAL.bit();
    // Engine walk order
    public static final List<ChannelType> ORDER = List.of(ITEM, FLUID, CHEMICAL, SOURCE, ENERGY);

    public int bit() {
        return 1 << ordinal();
    }

    public boolean in(int mask) {
        return (mask & bit()) != 0;
    }

    public static ChannelType first(int mask) {
        return mask == 0 ? ITEM : values()[Integer.numberOfTrailingZeros(mask)];
    }

    public static int maskOf(List<ChannelType> types) {
        int mask = 0;
        for (ChannelType type : types) {
            mask |= type.bit();
        }
        return mask;
    }

    public static List<ChannelType> listOf(int mask) {
        List<ChannelType> types = new ArrayList<>();
        for (ChannelType type : values()) {
            if (type.in(mask)) types.add(type);
        }
        return types;
    }
}
