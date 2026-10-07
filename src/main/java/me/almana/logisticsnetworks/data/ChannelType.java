package me.almana.logisticsnetworks.data;

import com.mojang.serialization.Codec;
import me.almana.logisticsnetworks.component.ComponentCodecs;

public enum ChannelType {
    ITEM,
    FLUID,
    ENERGY,
    CHEMICAL,
    SOURCE;

    public static final Codec<ChannelType> CODEC = ComponentCodecs.enumCodec(values(), ITEM);
}
