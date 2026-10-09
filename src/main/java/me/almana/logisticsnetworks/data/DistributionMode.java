package me.almana.logisticsnetworks.data;

import com.mojang.serialization.Codec;
import me.almana.logisticsnetworks.component.ComponentCodecs;

public enum DistributionMode {
    PRIORITY,
    NEAREST_FIRST,
    FARTHEST_FIRST,
    // Displayed as Equal Distribution
    ROUND_ROBIN,
    PRIORITY_ROBIN;

    public static final Codec<DistributionMode> CODEC = ComponentCodecs.enumCodec(values(), PRIORITY);
}
