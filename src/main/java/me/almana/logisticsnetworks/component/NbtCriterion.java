package me.almana.logisticsnetworks.component;

import me.almana.logisticsnetworks.filter.NbtPath;
import me.almana.logisticsnetworks.filter.NbtRuleMatcher;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public record NbtCriterion(NbtPath path, String operator, Tag value) {

    private static final String KEY_PATH = "p";
    private static final String KEY_OPERATOR = "o";
    private static final String KEY_VALUE = "v";

    public NbtCriterion {
        Objects.requireNonNull(path);
        operator = NbtRuleMatcher.normalizeOperator(Objects.requireNonNull(operator));
        value = Objects.requireNonNull(value).copy();
    }

    @Override
    public Tag value() {
        return value.copy();
    }

    public boolean matches(@Nullable Tag actual) {
        return NbtRuleMatcher.matchesValue(operator, value, actual);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_PATH, path.toString());
        tag.putString(KEY_OPERATOR, operator);
        tag.put(KEY_VALUE, value.copy());
        return tag;
    }

    @Nullable
    public static NbtCriterion load(CompoundTag tag) {
        String path = tag.getString(KEY_PATH);
        Tag value = tag.get(KEY_VALUE);
        if (path.isEmpty() || value == null)
            return null;
        String operator = tag.contains(KEY_OPERATOR, Tag.TAG_STRING) ? tag.getString(KEY_OPERATOR) : "=";
        return new NbtCriterion(NbtPath.parseLenient(path), operator, value);
    }
}
