package me.almana.logisticsnetworks.component;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.NeoForgeExtraCodecs;
import org.slf4j.Logger;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

public final class ComponentCodecs {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Codec<Tag> TAG = Codec.PASSTHROUGH.xmap(
            dynamic -> dynamic.convert(NbtOps.INSTANCE).getValue().copy(),
            tag -> new Dynamic<>(NbtOps.INSTANCE, tag.copy()));

    public static final Codec<ItemStack> STACK = lenient(ItemStack.OPTIONAL_CODEC, () -> ItemStack.EMPTY);
    public static final Codec<ItemStack> QUIET_STACK = quietLenient(ItemStack.OPTIONAL_CODEC, () -> ItemStack.EMPTY);

    private ComponentCodecs() {
    }

    public static <E extends Enum<E>> Codec<E> enumCodec(E[] values, E fallback) {
        return Codec.STRING.xmap(value -> {
            for (E candidate : values) {
                if (candidate.name().equalsIgnoreCase(value)) {
                    return candidate;
                }
            }
            return fallback;
        }, value -> value.name().toLowerCase(Locale.ROOT));
    }

    public static <A> Codec<A> lenient(Codec<A> codec, Supplier<? extends A> fallback) {
        return lenient(codec, fallback, true);
    }

    // Client-sendable data, never logs
    public static <A> Codec<A> quietLenient(Codec<A> codec, Supplier<? extends A> fallback) {
        return lenient(codec, fallback, false);
    }

    // Keeps partials, like parseOptional
    private static <A> Codec<A> lenient(Codec<A> codec, Supplier<? extends A> fallback, boolean logErrors) {
        return Codec.of(new Encoder<>() {
            @Override
            public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
                DataResult<T> result = tryEncode(codec, input, ops, prefix);
                if (result.isSuccess()) {
                    return result;
                }
                if (logErrors) {
                    LOGGER.warn("Unencodable entry, saving fallback: {}", result.error().orElseThrow().message());
                }
                Optional<T> partial = result.resultOrPartial();
                if (partial.isPresent()) {
                    return DataResult.success(partial.get());
                }
                DataResult<T> replacement = tryEncode(codec, fallback.get(), ops, prefix);
                return replacement.isSuccess() ? replacement : result;
            }
        }, new Decoder<>() {
            @Override
            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
                A value;
                try {
                    DataResult<A> result = codec.parse(ops, input);
                    value = (logErrors
                            ? result.resultOrPartial(error -> LOGGER.error("Malformed entry: {}", error))
                            : result.resultOrPartial()).orElseGet(fallback);
                } catch (RuntimeException e) {
                    if (logErrors) {
                        LOGGER.error("Malformed entry", e);
                    }
                    value = fallback.get();
                }
                return DataResult.success(Pair.of(value, input));
            }
        });
    }

    private static <A, T> DataResult<T> tryEncode(Encoder<A> encoder, A value, DynamicOps<T> ops, T prefix) {
        try {
            return encoder.encode(value, ops, prefix);
        } catch (RuntimeException e) {
            return DataResult.error(e::toString);
        }
    }

    public static <E> Codec<List<E>> lenientList(Codec<E> element) {
        return lenientList(element, Integer.MAX_VALUE);
    }

    public static <E> Codec<List<E>> lenientList(Codec<E> element, int maxSize) {
        return lenientList(element, maxSize, true);
    }

    public static <E> Codec<List<E>> quietLenientList(Codec<E> element, int maxSize) {
        return lenientList(element, maxSize, false);
    }

    private static <E> Codec<List<E>> lenientList(Codec<E> element, int maxSize, boolean logErrors) {
        Codec<Optional<E>> optional = element.xmap(Optional::of, Optional::get);
        return NeoForgeExtraCodecs.listWithoutEmpty(lenient(optional, Optional::empty, logErrors).listOf(0, maxSize));
    }

    public static <A> Optional<A> parse(Codec<A> codec, HolderLookup.Provider provider, Tag tag) {
        return parse(codec, new Dynamic<>(provider.createSerializationContext(NbtOps.INSTANCE), tag));
    }

    public static <A> Optional<A> parse(Codec<A> codec, Dynamic<?> data) {
        try {
            return codec.parse(data)
                    .ifError(error -> LOGGER.error("Failed to decode saved data: {}", error.message()))
                    .result();
        } catch (RuntimeException e) {
            LOGGER.error("Failed to decode saved data", e);
            return Optional.empty();
        }
    }

    public static <A> Tag encode(Codec<A> codec, HolderLookup.Provider provider, A value) {
        return codec.encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), value).getOrThrow();
    }
}
