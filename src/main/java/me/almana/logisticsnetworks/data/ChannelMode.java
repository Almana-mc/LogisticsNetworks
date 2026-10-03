package me.almana.logisticsnetworks.data;

import com.mojang.serialization.Codec;
import me.almana.logisticsnetworks.component.ComponentCodecs;

public enum ChannelMode {
    IMPORT,
    EXPORT;

    public static final Codec<ChannelMode> CODEC = ComponentCodecs.enumCodec(values(), IMPORT);
}
