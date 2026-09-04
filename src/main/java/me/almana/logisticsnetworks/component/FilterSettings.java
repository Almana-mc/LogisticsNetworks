package me.almana.logisticsnetworks.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.almana.logisticsnetworks.filter.FilterTargetType;

public record FilterSettings(FilterTargetType target, boolean blacklist) {

    public static final FilterSettings DEFAULT = new FilterSettings(FilterTargetType.ITEMS, false);
    public static final Codec<FilterSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            FilterTargetType.CODEC.optionalFieldOf("target", FilterTargetType.ITEMS).forGetter(FilterSettings::target),
            Codec.BOOL.optionalFieldOf("blacklist", false).forGetter(FilterSettings::blacklist)
    ).apply(instance, FilterSettings::new));

    public FilterSettings {
        target = target == null ? FilterTargetType.ITEMS : target;
    }

    public boolean isDefault() {
        return target == FilterTargetType.ITEMS && !blacklist;
    }
}
