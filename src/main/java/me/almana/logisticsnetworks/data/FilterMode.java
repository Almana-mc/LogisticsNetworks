package me.almana.logisticsnetworks.data;

import com.mojang.serialization.Codec;
import me.almana.logisticsnetworks.component.ComponentCodecs;

public enum FilterMode {
    MATCH_ANY,
    MATCH_ALL;

    public static final Codec<FilterMode> CODEC = ComponentCodecs.enumCodec(values(), MATCH_ANY);
}
