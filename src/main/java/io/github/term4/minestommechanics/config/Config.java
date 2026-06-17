package io.github.term4.minestommechanics.config;

import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Shared base for layered, context-resolved configs: holds the optional {@link #subConfig} overlay
 * and the {@link #merge}/{@link #resolve} helpers. The self-referential {@code SELF} type keeps
 * {@code subConfig} and {@code fromBase} typed across the config hierarchy.
 *
 * @param <CTX>  the resolution context shared by this config's {@link FieldValue}s
 * @param <SELF> the concrete config type
 */
public abstract class Config<CTX, SELF extends Config<CTX, SELF>> {

    /**
     * Optional context-aware overlay applied <em>over</em> this config before resolution. When set,
     * the resolver computes {@code subConfig.apply(ctx).fromBase(this)} and resolves from the result,
     * letting a config swap in different values per context.
     */
    @Nullable public final Function<CTX, SELF> subConfig;

    protected Config(@Nullable Function<CTX, SELF> subConfig) {
        this.subConfig = subConfig;
    }

    /** Returns {@code a} layered over {@code b}: {@code a} wins, falling back to {@code b} per resolution. */
    protected static <CTX, T> FieldValue<CTX, T> merge(@Nullable FieldValue<CTX, T> a,
                                                       @Nullable FieldValue<CTX, T> b) {
        if (b == null) return a;
        if (a == null) return b;
        return a.or(b);
    }

    /** Resolves a (possibly {@code null}) field against the context, returning {@code null} when unset. */
    protected static <CTX, T> @Nullable T resolve(@Nullable FieldValue<CTX, T> fv, CTX ctx) {
        return fv != null ? fv.resolve(ctx) : null;
    }
}
