package me.almana.logisticsnetworks.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.almana.logisticsnetworks.filter.NbtFilterData;
import me.almana.logisticsnetworks.filter.NbtPath;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public record NbtFilterConfig(List<Rule> rules) {

    public static final Codec<NbtFilterConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Rule.CODEC.listOf().fieldOf("rules").forGetter(NbtFilterConfig::rules)
    ).apply(instance, NbtFilterConfig::new));

    public NbtFilterConfig {
        rules = List.copyOf(rules);
    }

    public record Rule(@NotNull NbtPath path, @NotNull NbtFilterData.Operator operator, @NotNull Tag value, boolean enabled) {

        public static final Codec<Rule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                NbtPath.CODEC.fieldOf("path").forGetter(Rule::path),
                NbtFilterData.Operator.CODEC.fieldOf("operator").forGetter(Rule::operator),
                ComponentCodecs.TAG.fieldOf("value").forGetter(rule -> rule.value),
                Codec.BOOL.optionalFieldOf("enabled", true).forGetter(Rule::enabled)
        ).apply(instance, Rule::new));

        public Rule {
            Objects.requireNonNull(path);
            Objects.requireNonNull(operator);
            value = Objects.requireNonNull(value).copy();
        }

        @Override
        public Tag value() {
            return value.copy();
        }
    }
}
