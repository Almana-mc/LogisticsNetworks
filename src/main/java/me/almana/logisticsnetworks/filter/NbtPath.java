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
import java.util.Arrays;
import java.util.List;

public record NbtPath(Component[] components) {
    public static final Codec<NbtPath> CODEC = Codec.STRING.xmap(NbtPath::parseLenient, NbtPath::toString);
    public static final StreamCodec<ByteBuf, NbtPath> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(NbtPath::parseLenient, NbtPath::toString);

    public static final NbtPath EMPTY = NbtPath.of();

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

        var list = new ArrayList<Component>();
        var pos = 0;
        while (pos < pathStr.length()) {
            pos = pathStr.charAt(pos) == '['
                    ? parseIndex(pathStr, pos + 1, list)
                    : parseKey(pathStr, pos, list);
            if (pos < 0) {
                return null;
            }

            if (pos < pathStr.length()) {
                var next = pathStr.charAt(pos);
                if (next == '.') {
                    pos++;
                } else if (next != '[') {
                    return null;
                }
            }
        }

        return new NbtPath(list.toArray(new Component[0]));
    }

    private static int parseKey(String str, int start, List<Component> out) {
        if (str.charAt(start) == '"') {
            return parseQuotedKey(str, start + 1, out);
        }

        var end = start;
        while (end < str.length() && str.charAt(end) != '.' && str.charAt(end) != '[') {
            end++;
        }
        if (end == start) {
            return -1;
        }

        out.add(new StringComponent(str.substring(start, end)));
        return end;
    }

    private static int parseQuotedKey(String str, int start, List<Component> out) {
        var key = new StringBuilder();
        var pos = start;
        while (pos < str.length()) {
            var c = str.charAt(pos++);
            if (c == '"') {
                out.add(new StringComponent(key.toString()));
                return pos;
            }
            if (c == '\\') {
                if (pos >= str.length() || (str.charAt(pos) != '"' && str.charAt(pos) != '\\')) {
                    return -1;
                }
                c = str.charAt(pos++);
            }
            key.append(c);
        }
        return -1;
    }

    private static boolean needsQuotes(String key) {
        return key.isEmpty() || key.charAt(0) == '"' || key.indexOf('.') >= 0 || key.indexOf('[') >= 0;
    }

    private static int parseIndex(String str, int start, List<Component> out) {
        var end = start;
        var value = 0;
        while (end < str.length() && str.charAt(end) >= '0' && str.charAt(end) <= '9') {
            var digit = str.charAt(end) - '0';
            if (value > (Integer.MAX_VALUE - digit) / 10) {
                return -1;
            }
            value = value * 10 + digit;
            end++;
        }
        if (end == start || end >= str.length() || str.charAt(end) != ']') {
            return -1;
        }

        out.add(new IndexComponent(value));
        return end + 1;
    }

    public static NbtPath parseLenient(String pathStr) {
        var parsed = parse(pathStr);
        // unparseable paths never resolve
        return parsed != null ? parsed : NbtPath.of(new StringComponent(pathStr));
    }

    @Override
    public @NotNull String toString() {
        if (this.components.length == 0) {
            return "";
        }

        // lenient parse restores it
        if (this.components.length == 1 && this.components[0] instanceof StringComponent(var value)
                && needsQuotes(value) && parse(value) == null) {
            return value;
        }

        var builder = new StringBuilder();
        for (var component : this.components) {
            switch (component) {
                case StringComponent p -> {
                    if (!builder.isEmpty()) {
                        builder.append('.');
                    }
                    if (needsQuotes(p.value)) {
                        builder.append('"').append(p.value.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
                    } else {
                        builder.append(p.value);
                    }
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

    public boolean startsWith(NbtPath prefix) {
        return this.size() >= prefix.size()
                && Arrays.equals(this.components, 0, prefix.size(), prefix.components, 0, prefix.size());
    }

    public NbtPath drop(int count) {
        return new NbtPath(Arrays.copyOfRange(this.components, count, this.components.length));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NbtPath other && Arrays.equals(this.components, other.components);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.components);
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

    record StringComponent(String value) implements Component {
        @Override
        public @Nullable Tag getFrom(@Nullable Tag parent) {
            if (!(parent instanceof CompoundTag compound) || compound.isEmpty()) {
                return null;
            }

            return compound.get(this.value);
        }
    }

    record IndexComponent(int value) implements Component {
        @Override
        public @Nullable Tag getFrom(@Nullable Tag parent) {
            if (!(parent instanceof CollectionTag<?> compound) || this.value < 0 || this.value >= compound.size()) {
                return null;
            }

            return compound.get(this.value);
        }
    }
}
