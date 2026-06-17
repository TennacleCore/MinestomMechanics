package io.github.term4.minestommechanics.config;

import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * A single configurable value resolved against a context {@code CTX}: a constant, a context-aware
 * function, or a function with a constant fallback.
 *
 * <p>When built from a plain {@link #constant(Object)}, the value is also kept in {@code constant}
 * so callers with no context (e.g. a hit-independent default) can read it via
 * {@link #constantOrNull()} without invoking the function.
 *
 * @param <CTX>     the resolution context (e.g. a damage/attack context record)
 * @param <T>       the resolved value type
 * @param fn        the resolution function
 * @param constant  the constant value when context-independent, otherwise {@code null}
 */
public record FieldValue<CTX, T>(Function<CTX, T> fn, @Nullable T constant) {

    /** A constant value, ignoring the context. */
    public static <CTX, T> FieldValue<CTX, T> constant(T v) {
        return new FieldValue<>(ctx -> v, v);
    }

    /** A context-aware value. */
    public static <CTX, T> FieldValue<CTX, T> of(Function<CTX, T> f) {
        return new FieldValue<>(f, null);
    }

    /** A context-aware value that falls back to {@code fallback} when the function returns {@code null}. */
    public static <CTX, T> FieldValue<CTX, T> ofWithFallback(T fallback, Function<CTX, T> fn) {
        return new FieldValue<>(ctx -> {
            T r = fn.apply(ctx);
            return r != null ? r : fallback;
        }, null);
    }

    /** Resolves this value against the given context. */
    public T resolve(CTX ctx) {
        return fn.apply(ctx);
    }

    /** The constant value when this was built from a constant, else {@code null} (context-dependent). */
    public @Nullable T constantOrNull() {
        return constant;
    }

    /** Returns a value that uses {@code fallback} when this one resolves to {@code null}. */
    public FieldValue<CTX, T> or(FieldValue<CTX, T> fallback) {
        return new FieldValue<>(ctx -> {
            T r = fn.apply(ctx);
            return r != null ? r : fallback.fn.apply(ctx);
        }, null);
    }
}
