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

    /** Resolves a possibly-unset field: an unset field or a {@code null} resolution falls back to {@code def}. */
    public static <CTX, T> T resolve(@Nullable FieldValue<CTX, T> field, CTX ctx, T def) {
        T v = field != null ? field.resolve(ctx) : null;
        return v != null ? v : def;
    }

    /** The constant value when this was built from a constant, else {@code null} (context-dependent). */
    public @Nullable T constantOrNull() {
        return constant;
    }

    /** {@code a} layered over {@code b} ({@code a} wins, falling back per resolution); either side may be {@code null}. */
    public static <CTX, T> FieldValue<CTX, T> merge(FieldValue<CTX, T> a, FieldValue<CTX, T> b) {
        if (b == null) return a;
        if (a == null) return b;
        return a.or(b);
    }

    /** Returns a value that uses {@code fallback} when this one resolves to {@code null}. */
    public FieldValue<CTX, T> or(FieldValue<CTX, T> fallback) {
        return new FieldValue<>(ctx -> {
            T r = fn.apply(ctx);
            return r != null ? r : fallback.fn.apply(ctx);
        }, null);
    }
}
