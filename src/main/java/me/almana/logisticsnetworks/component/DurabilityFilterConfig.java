package me.almana.logisticsnetworks.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.almana.logisticsnetworks.filter.DurabilityFilterData;

public record DurabilityFilterConfig(int value, DurabilityFilterData.Operator operator) {

    private static final Codec<DurabilityFilterData.Operator> OPERATOR_CODEC = Codec.STRING.xmap(
            DurabilityFilterData.Operator::fromId,
            DurabilityFilterData.Operator::id);

    public static final Codec<DurabilityFilterConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("value").forGetter(DurabilityFilterConfig::value),
            OPERATOR_CODEC.optionalFieldOf("operator", DurabilityFilterData.Operator.GREATER_OR_EQUAL)
                    .forGetter(DurabilityFilterConfig::operator)
    ).apply(instance, DurabilityFilterConfig::new));

    public DurabilityFilterConfig {
        value = Math.max(0, Math.min(3000, value));
        operator = operator == null ? DurabilityFilterData.Operator.GREATER_OR_EQUAL : operator;
    }
}
