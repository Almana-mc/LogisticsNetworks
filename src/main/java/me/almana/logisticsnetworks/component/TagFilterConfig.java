package me.almana.logisticsnetworks.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.almana.logisticsnetworks.filter.FilterTagUtil;

public record TagFilterConfig(String tag) {

    public static final Codec<TagFilterConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("tag").forGetter(TagFilterConfig::tag)
    ).apply(instance, TagFilterConfig::new));

    public TagFilterConfig {
        String normalized = FilterTagUtil.normalizeTag(tag);
        tag = normalized == null ? "" : normalized;
    }
}
