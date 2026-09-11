package dev.craftgpt.compat.network;
import java.util.function.BiConsumer;
import java.util.function.Function;
/** Small typed codec combinators retaining the same field order on older Minecraft versions. */
public interface StreamCodec<B, T> {
    T decode(B buffer);
    void encode(B buffer, T value);
    static <B, T> StreamCodec<B, T> of(BiConsumer<B, T> encode, Function<B, T> decode) {
        return new StreamCodec<>() {
            public T decode(B buffer) { return decode.apply(buffer); }
            public void encode(B buffer, T value) { encode.accept(buffer, value); }
        };
    }
    @FunctionalInterface interface Constructor1<T1, C> { C apply(T1 v1); }
    static <B, C, T1> StreamCodec<B, C> composite(
        StreamCodec<? super B, T1> c1, Function<C, T1> f1, Constructor1<T1, C> constructor) {
        return of((buffer, value) -> {
            c1.encode(buffer, f1.apply(value));
        }, buffer -> constructor.apply(c1.decode(buffer)));
    }

    @FunctionalInterface interface Constructor2<T1, T2, C> { C apply(T1 v1, T2 v2); }
    static <B, C, T1, T2> StreamCodec<B, C> composite(
        StreamCodec<? super B, T1> c1, Function<C, T1> f1,
        StreamCodec<? super B, T2> c2, Function<C, T2> f2, Constructor2<T1, T2, C> constructor) {
        return of((buffer, value) -> {
            c1.encode(buffer, f1.apply(value));
            c2.encode(buffer, f2.apply(value));
        }, buffer -> constructor.apply(c1.decode(buffer), c2.decode(buffer)));
    }

    @FunctionalInterface interface Constructor3<T1, T2, T3, C> { C apply(T1 v1, T2 v2, T3 v3); }
    static <B, C, T1, T2, T3> StreamCodec<B, C> composite(
        StreamCodec<? super B, T1> c1, Function<C, T1> f1,
        StreamCodec<? super B, T2> c2, Function<C, T2> f2,
        StreamCodec<? super B, T3> c3, Function<C, T3> f3, Constructor3<T1, T2, T3, C> constructor) {
        return of((buffer, value) -> {
            c1.encode(buffer, f1.apply(value));
            c2.encode(buffer, f2.apply(value));
            c3.encode(buffer, f3.apply(value));
        }, buffer -> constructor.apply(c1.decode(buffer), c2.decode(buffer), c3.decode(buffer)));
    }

    @FunctionalInterface interface Constructor4<T1, T2, T3, T4, C> { C apply(T1 v1, T2 v2, T3 v3, T4 v4); }
    static <B, C, T1, T2, T3, T4> StreamCodec<B, C> composite(
        StreamCodec<? super B, T1> c1, Function<C, T1> f1,
        StreamCodec<? super B, T2> c2, Function<C, T2> f2,
        StreamCodec<? super B, T3> c3, Function<C, T3> f3,
        StreamCodec<? super B, T4> c4, Function<C, T4> f4, Constructor4<T1, T2, T3, T4, C> constructor) {
        return of((buffer, value) -> {
            c1.encode(buffer, f1.apply(value));
            c2.encode(buffer, f2.apply(value));
            c3.encode(buffer, f3.apply(value));
            c4.encode(buffer, f4.apply(value));
        }, buffer -> constructor.apply(c1.decode(buffer), c2.decode(buffer), c3.decode(buffer), c4.decode(buffer)));
    }

    @FunctionalInterface interface Constructor5<T1, T2, T3, T4, T5, C> { C apply(T1 v1, T2 v2, T3 v3, T4 v4, T5 v5); }
    static <B, C, T1, T2, T3, T4, T5> StreamCodec<B, C> composite(
        StreamCodec<? super B, T1> c1, Function<C, T1> f1,
        StreamCodec<? super B, T2> c2, Function<C, T2> f2,
        StreamCodec<? super B, T3> c3, Function<C, T3> f3,
        StreamCodec<? super B, T4> c4, Function<C, T4> f4,
        StreamCodec<? super B, T5> c5, Function<C, T5> f5, Constructor5<T1, T2, T3, T4, T5, C> constructor) {
        return of((buffer, value) -> {
            c1.encode(buffer, f1.apply(value));
            c2.encode(buffer, f2.apply(value));
            c3.encode(buffer, f3.apply(value));
            c4.encode(buffer, f4.apply(value));
            c5.encode(buffer, f5.apply(value));
        }, buffer -> constructor.apply(c1.decode(buffer), c2.decode(buffer), c3.decode(buffer), c4.decode(buffer), c5.decode(buffer)));
    }

    @FunctionalInterface interface Constructor6<T1, T2, T3, T4, T5, T6, C> { C apply(T1 v1, T2 v2, T3 v3, T4 v4, T5 v5, T6 v6); }
    static <B, C, T1, T2, T3, T4, T5, T6> StreamCodec<B, C> composite(
        StreamCodec<? super B, T1> c1, Function<C, T1> f1,
        StreamCodec<? super B, T2> c2, Function<C, T2> f2,
        StreamCodec<? super B, T3> c3, Function<C, T3> f3,
        StreamCodec<? super B, T4> c4, Function<C, T4> f4,
        StreamCodec<? super B, T5> c5, Function<C, T5> f5,
        StreamCodec<? super B, T6> c6, Function<C, T6> f6, Constructor6<T1, T2, T3, T4, T5, T6, C> constructor) {
        return of((buffer, value) -> {
            c1.encode(buffer, f1.apply(value));
            c2.encode(buffer, f2.apply(value));
            c3.encode(buffer, f3.apply(value));
            c4.encode(buffer, f4.apply(value));
            c5.encode(buffer, f5.apply(value));
            c6.encode(buffer, f6.apply(value));
        }, buffer -> constructor.apply(c1.decode(buffer), c2.decode(buffer), c3.decode(buffer), c4.decode(buffer), c5.decode(buffer), c6.decode(buffer)));
    }

    @FunctionalInterface interface Constructor7<T1, T2, T3, T4, T5, T6, T7, C> { C apply(T1 v1, T2 v2, T3 v3, T4 v4, T5 v5, T6 v6, T7 v7); }
    static <B, C, T1, T2, T3, T4, T5, T6, T7> StreamCodec<B, C> composite(
        StreamCodec<? super B, T1> c1, Function<C, T1> f1,
        StreamCodec<? super B, T2> c2, Function<C, T2> f2,
        StreamCodec<? super B, T3> c3, Function<C, T3> f3,
        StreamCodec<? super B, T4> c4, Function<C, T4> f4,
        StreamCodec<? super B, T5> c5, Function<C, T5> f5,
        StreamCodec<? super B, T6> c6, Function<C, T6> f6,
        StreamCodec<? super B, T7> c7, Function<C, T7> f7, Constructor7<T1, T2, T3, T4, T5, T6, T7, C> constructor) {
        return of((buffer, value) -> {
            c1.encode(buffer, f1.apply(value));
            c2.encode(buffer, f2.apply(value));
            c3.encode(buffer, f3.apply(value));
            c4.encode(buffer, f4.apply(value));
            c5.encode(buffer, f5.apply(value));
            c6.encode(buffer, f6.apply(value));
            c7.encode(buffer, f7.apply(value));
        }, buffer -> constructor.apply(c1.decode(buffer), c2.decode(buffer), c3.decode(buffer), c4.decode(buffer), c5.decode(buffer), c6.decode(buffer), c7.decode(buffer)));
    }

    @FunctionalInterface interface Constructor8<T1, T2, T3, T4, T5, T6, T7, T8, C> { C apply(T1 v1, T2 v2, T3 v3, T4 v4, T5 v5, T6 v6, T7 v7, T8 v8); }
    static <B, C, T1, T2, T3, T4, T5, T6, T7, T8> StreamCodec<B, C> composite(
        StreamCodec<? super B, T1> c1, Function<C, T1> f1,
        StreamCodec<? super B, T2> c2, Function<C, T2> f2,
        StreamCodec<? super B, T3> c3, Function<C, T3> f3,
        StreamCodec<? super B, T4> c4, Function<C, T4> f4,
        StreamCodec<? super B, T5> c5, Function<C, T5> f5,
        StreamCodec<? super B, T6> c6, Function<C, T6> f6,
        StreamCodec<? super B, T7> c7, Function<C, T7> f7,
        StreamCodec<? super B, T8> c8, Function<C, T8> f8, Constructor8<T1, T2, T3, T4, T5, T6, T7, T8, C> constructor) {
        return of((buffer, value) -> {
            c1.encode(buffer, f1.apply(value));
            c2.encode(buffer, f2.apply(value));
            c3.encode(buffer, f3.apply(value));
            c4.encode(buffer, f4.apply(value));
            c5.encode(buffer, f5.apply(value));
            c6.encode(buffer, f6.apply(value));
            c7.encode(buffer, f7.apply(value));
            c8.encode(buffer, f8.apply(value));
        }, buffer -> constructor.apply(c1.decode(buffer), c2.decode(buffer), c3.decode(buffer), c4.decode(buffer), c5.decode(buffer), c6.decode(buffer), c7.decode(buffer), c8.decode(buffer)));
    }

    @FunctionalInterface interface Constructor9<T1, T2, T3, T4, T5, T6, T7, T8, T9, C> { C apply(T1 v1, T2 v2, T3 v3, T4 v4, T5 v5, T6 v6, T7 v7, T8 v8, T9 v9); }
    static <B, C, T1, T2, T3, T4, T5, T6, T7, T8, T9> StreamCodec<B, C> composite(
        StreamCodec<? super B, T1> c1, Function<C, T1> f1,
        StreamCodec<? super B, T2> c2, Function<C, T2> f2,
        StreamCodec<? super B, T3> c3, Function<C, T3> f3,
        StreamCodec<? super B, T4> c4, Function<C, T4> f4,
        StreamCodec<? super B, T5> c5, Function<C, T5> f5,
        StreamCodec<? super B, T6> c6, Function<C, T6> f6,
        StreamCodec<? super B, T7> c7, Function<C, T7> f7,
        StreamCodec<? super B, T8> c8, Function<C, T8> f8,
        StreamCodec<? super B, T9> c9, Function<C, T9> f9, Constructor9<T1, T2, T3, T4, T5, T6, T7, T8, T9, C> constructor) {
        return of((buffer, value) -> {
            c1.encode(buffer, f1.apply(value));
            c2.encode(buffer, f2.apply(value));
            c3.encode(buffer, f3.apply(value));
            c4.encode(buffer, f4.apply(value));
            c5.encode(buffer, f5.apply(value));
            c6.encode(buffer, f6.apply(value));
            c7.encode(buffer, f7.apply(value));
            c8.encode(buffer, f8.apply(value));
            c9.encode(buffer, f9.apply(value));
        }, buffer -> constructor.apply(c1.decode(buffer), c2.decode(buffer), c3.decode(buffer), c4.decode(buffer), c5.decode(buffer), c6.decode(buffer), c7.decode(buffer), c8.decode(buffer), c9.decode(buffer)));
    }
}
