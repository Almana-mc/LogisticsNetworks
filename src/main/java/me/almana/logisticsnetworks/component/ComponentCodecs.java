package me.almana.logisticsnetworks.component;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
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

    // Keeps partials, like parseOptional
    public static <A> Codec<A> lenient(Codec<A> codec, Supplier<? extends A> fallback) {
        return Codec.of(codec, new Decoder<>() {
            @Override
            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
                A value = codec.parse(ops, input)
                        .resultOrPartial(error -> LOGGER.error("Malformed entry: {}", error))
                        .orElseGet(fallback);
                return DataResult.success(Pair.of(value, input));
            }
        });
    }

    public static <E> Codec<List<E>> lenientList(Codec<E> element) {
        Codec<Optional<E>> optional = element.xmap(Optional::of, Optional::get);
        return NeoForgeExtraCodecs.listWithOptionalElements(lenient(optional, Optional::empty));
    }

    public static <A> Optional<A> parse(Codec<A> codec, HolderLookup.Provider provider, Tag tag) {
        return codec.parse(provider.createSerializationContext(NbtOps.INSTANCE), tag)
                .ifError(error -> LOGGER.error("Failed to decode saved data: {}", error.message()))
                .result();
    }

    public static <A> Tag encode(Codec<A> codec, HolderLookup.Provider provider, A value) {
        return codec.encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), value).getOrThrow();
    }
}
