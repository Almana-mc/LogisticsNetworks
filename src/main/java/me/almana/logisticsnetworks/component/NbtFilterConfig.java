package me.almana.logisticsnetworks.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.almana.logisticsnetworks.filter.NbtFilterData;
import net.minecraft.nbt.Tag;

import java.util.List;
import java.util.Objects;

public record NbtFilterConfig(List<Rule> rules) {

    public static final Codec<NbtFilterConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Rule.CODEC.listOf().fieldOf("rules").forGetter(NbtFilterConfig::rules)
    ).apply(instance, NbtFilterConfig::new));

    public NbtFilterConfig {
        rules = List.copyOf(rules);
    }

    public record Rule(String path, NbtFilterData.Operator operator, Tag value, boolean enabled) {

        public static final Codec<Rule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("path").forGetter(Rule::path),
                NbtFilterData.Operator.CODEC.fieldOf("operator").forGetter(Rule::operator),
                ComponentCodecs.TAG.fieldOf("value").forGetter(rule -> rule.value),
                Codec.BOOL.optionalFieldOf("enabled", true).forGetter(Rule::enabled)
        ).apply(instance, Rule::new));

        public Rule {
            path = Objects.requireNonNull(path);
            operator = Objects.requireNonNull(operator);
            value = Objects.requireNonNull(value).copy();
        }

        @Override
        public Tag value() {
            return value.copy();
        }
    }
}
