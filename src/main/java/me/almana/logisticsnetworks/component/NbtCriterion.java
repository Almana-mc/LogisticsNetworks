package me.almana.logisticsnetworks.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.almana.logisticsnetworks.filter.NbtPath;
import net.minecraft.nbt.Tag;

import java.util.Objects;

public record NbtCriterion(NbtPath path, String operator, Tag value) {

    public static final Codec<NbtCriterion> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            NbtPath.CODEC.fieldOf("path").forGetter(NbtCriterion::path),
            Codec.STRING.fieldOf("operator").forGetter(NbtCriterion::operator),
            ComponentCodecs.TAG.fieldOf("value").forGetter(criterion -> criterion.value)
    ).apply(instance, NbtCriterion::new));

    public NbtCriterion {
        Objects.requireNonNull(path);
        operator = Objects.requireNonNull(operator);
        value = Objects.requireNonNull(value).copy();
    }

    @Override
    public Tag value() {
        return value.copy();
    }
}
