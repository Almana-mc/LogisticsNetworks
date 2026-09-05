package me.almana.logisticsnetworks.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record SlotFilterConfig(List<Integer> slots) {

    public static final Codec<SlotFilterConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.listOf().fieldOf("slots").forGetter(SlotFilterConfig::slots)
    ).apply(instance, SlotFilterConfig::new));

    public SlotFilterConfig {
        slots = slots.stream().filter(slot -> slot >= 0 && slot <= 53).distinct().sorted().toList();
    }
}
