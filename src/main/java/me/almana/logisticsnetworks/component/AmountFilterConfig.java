package me.almana.logisticsnetworks.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record AmountFilterConfig(int amount) {

    public static final int DEFAULT_AMOUNT = 64;
    public static final Codec<AmountFilterConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("amount").forGetter(AmountFilterConfig::amount)
    ).apply(instance, AmountFilterConfig::new));

    public AmountFilterConfig {
        amount = Math.max(0, Math.min(1_000_000, amount));
    }
}
