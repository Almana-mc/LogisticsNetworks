package me.almana.logisticsnetworks.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record GeneralFilterConfig(List<GeneralFilterEntry> entries) {

    public static final Codec<GeneralFilterConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GeneralFilterEntry.CODEC.listOf().fieldOf("entries").forGetter(GeneralFilterConfig::entries)
    ).apply(instance, GeneralFilterConfig::new));

    public GeneralFilterConfig {
        entries = List.copyOf(entries);
    }
}
