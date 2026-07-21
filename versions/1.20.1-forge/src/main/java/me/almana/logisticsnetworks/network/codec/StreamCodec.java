package me.almana.logisticsnetworks.network.codec;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public interface StreamCodec<B, T> {

    void encode(B buf, T value);

    T decode(B buf);

    static <B, T> StreamCodec<B, T> of(BiConsumer<B, T> encoder, Function<B, T> decoder) {
        return new StreamCodec<>() {
            @Override
            public void encode(B buf, T value) {
                encoder.accept(buf, value);
            }

            @Override
            public T decode(B buf) {
                return decoder.apply(buf);
            }
        };
    }

    static <B, T, A> StreamCodec<B, T> composite(
            StreamCodec<B, A> codecA,
            Function<T, A> getterA,
            Function<A, T> ctor) {
        return of(
                (buf, value) -> codecA.encode(buf, getterA.apply(value)),
                buf -> ctor.apply(codecA.decode(buf)));
    }

    static <B, T, A, C> StreamCodec<B, T> composite(
            StreamCodec<B, A> codecA,
            Function<T, A> getterA,
            StreamCodec<B, C> codecC,
            Function<T, C> getterC,
            BiFunction<A, C, T> ctor) {
        return of(
                (buf, value) -> {
                    codecA.encode(buf, getterA.apply(value));
                    codecC.encode(buf, getterC.apply(value));
                },
                buf -> ctor.apply(codecA.decode(buf), codecC.decode(buf)));
    }
}
