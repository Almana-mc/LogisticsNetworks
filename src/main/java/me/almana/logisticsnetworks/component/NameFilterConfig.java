package me.almana.logisticsnetworks.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.almana.logisticsnetworks.filter.NameMatchScope;

public record NameFilterConfig(String expression, NameMatchScope scope) {

    private static final Codec<NameMatchScope> SCOPE_CODEC = Codec.STRING.xmap(
            ignored -> NameMatchScope.NAME,
            ignored -> "name");

    public static final Codec<NameFilterConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("expression").forGetter(NameFilterConfig::expression),
            SCOPE_CODEC.optionalFieldOf("scope", NameMatchScope.NAME).forGetter(NameFilterConfig::scope)
    ).apply(instance, NameFilterConfig::new));

    public NameFilterConfig {
        expression = expression == null ? "" : expression.trim();
        scope = scope == null ? NameMatchScope.NAME : scope;
    }
}
