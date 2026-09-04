package me.almana.logisticsnetworks.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.LinkedHashSet;
import java.util.List;

public record ModFilterConfig(List<String> namespaces) {

    public static final Codec<ModFilterConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().fieldOf("namespaces").forGetter(ModFilterConfig::namespaces)
    ).apply(instance, ModFilterConfig::new));

    public ModFilterConfig {
        namespaces = List.copyOf(new LinkedHashSet<>(namespaces));
    }
}
