package me.almana.logisticsnetworks.filter;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public record NbtPath(Component[] components) {
    public static final Codec<NbtPath> CODEC = Codec.STRING.xmap(NbtPath::parse, NbtPath::toString);
    public static final StreamCodec<ByteBuf, NbtPath> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(NbtPath::parse, NbtPath::toString);

    public static final NbtPath EMPTY = NbtPath.of();
    public static final NbtPath IDENTITY = NbtPath.of(new StringComponent("."));

    public static NbtPath of(Component... components) {
        return new NbtPath(components);
    }

    @Nullable
    public static NbtPath parse(@Nullable String pathStr) {
        if (pathStr == null) {
            return null;
        }

        if (pathStr.isEmpty()) {
            return NbtPath.EMPTY;
        }

        if (pathStr.equals(".")) {
            return NbtPath.IDENTITY;
        }

        var list = new ArrayList<Component>();

        var remaining = pathStr;
        while (!remaining.isEmpty()) {
            var start = remaining.charAt(0);

            if (start == '[') {
                remaining = remaining.substring(1);
                var res = IndexComponent.parse(remaining);
                if (res.result() == null) {
                    return null;
                }
                list.add(res.result());
                remaining = remaining.substring(res.usage());
                if (remaining.charAt(0) != ']') {
                    return null;
                }
                remaining = remaining.substring(1);
            } else {
                var res = StringComponent.parse(remaining);
                if (res.result() == null) {
                    return null;
                }
                list.add(res.result());
                remaining = remaining.substring(res.usage());
            }

            if (!remaining.isEmpty()) {
                var next = remaining.charAt(0);
                if (next == '.') {
                    remaining = remaining.substring(1);
                } else if (next != '[') {
                    return null;
                }
            }
        }

        return new NbtPath(list.toArray(new Component[0]));
    }

    @Override
    public @NotNull String toString() {
        if (this.components.length == 0) {
            return "";
        }

        var builder = new StringBuilder();
        for (var component : this.components) {
            switch (component) {
                case StringComponent p -> {
                    if (!builder.isEmpty()) {
                        builder.append('.');
                    }
                    builder.append(p.value);
                }
                case IndexComponent p -> {
                    builder.append('[');
                    builder.append(p.value);
                    builder.append(']');
                }
                case null -> throw new RuntimeException("Encountered null path while stringifying");
                default ->
                        throw new RuntimeException("Path stringification is unimplemented for " + component.getClass().getCanonicalName());
            }
        }

        return builder.toString();
    }

    @Nullable
    public Tag getFrom(Tag root) {
        var current = root;

        for (var component : this.components) {
            if (current == null) {
                return null;
            }

            current = component.getFrom(current);
        }

        return current;
    }

    public NbtPath then(Component... components) {
        var newPathComponents = new NbtPath.Component[this.size() + components.length];
        System.arraycopy(this.components(), 0, newPathComponents, 0, this.size());
        System.arraycopy(components, 0, newPathComponents, this.size(), components.length);

        return new NbtPath(newPathComponents);
    }

    public boolean isEmpty() {
        return this.components.length == 0;
    }

    public int size() {
        return this.components.length;
    }

    public interface Component {
        @Nullable
        Tag getFrom(@Nullable Tag parent);

        static Component of(String str) {
            return new StringComponent(str);
        }

        static Component of(int idx) {
            return new IndexComponent(idx);
        }
    }

    record UsageAndResult<T>(int usage, T result) {
        public static <T> UsageAndResult<T> of(int usage, T result) {
            return new UsageAndResult<>(usage, result);
        }
    }

    record StringComponent(String value) implements Component {
        @Override
        public @Nullable Tag getFrom(@Nullable Tag parent) {
            if (!(parent instanceof CompoundTag compound) || compound.isEmpty()) {
                return null;
            }

            return compound.get(this.value);
        }

        public static @NotNull UsageAndResult<@Nullable Component> parse(String string) {
            if (string.isEmpty()) {
                return UsageAndResult.of(0, null);
            }

            int periodIdx = string.indexOf('.');
            if (periodIdx == -1) {
                periodIdx = string.length();
            }

            int bracketIdx = string.indexOf('[');
            if (bracketIdx == -1) {
                bracketIdx = string.length();
            }

            String value = string.substring(0, Math.min(periodIdx, bracketIdx));
            return UsageAndResult.of(value.length(), new StringComponent(value));
        }
    }

    record IndexComponent(int value) implements Component {
        @Override
        public @Nullable Tag getFrom(@Nullable Tag parent) {
            if (!(parent instanceof CollectionTag<?> compound) || compound.isEmpty()) {
                return null;
            }

            return compound.get(this.value);
        }

        public static @NotNull UsageAndResult<@Nullable Component> parse(String string) {
            if (string.isEmpty()) {
                return UsageAndResult.of(0, null);
            }

            char[] chars = string.toCharArray();
            if (chars[0] < '0' || chars[0] > '9') {
                return UsageAndResult.of(0, null);
            }

            int value = 0;
            int i = 0;

            for (; i < chars.length; i++) {
                char c = chars[i];
                if (c < '0' || c > '9') {
                    break;
                }

                var oldValue = value;
                value = value * 10 + c - '0';

                if (oldValue > value) {
                    return UsageAndResult.of(0, null);
                }
            }

            return UsageAndResult.of(i, new IndexComponent(value));
        }
    }
}
