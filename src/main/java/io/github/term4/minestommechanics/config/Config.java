package io.github.term4.minestommechanics.config;

import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Base for layered, context-resolved configs: the optional {@link #subConfig} overlay plus the
 * {@link #merge}/{@link #resolve} helpers.
 */
public abstract class Config<CTX, SELF extends Config<CTX, SELF>> {

    /** Overlay applied over this config: the resolver resolves {@code subConfig.apply(ctx).fromBase(this)}. */
    @Nullable public final Function<CTX, SELF> subConfig;

    protected Config(@Nullable Function<CTX, SELF> subConfig) {
        this.subConfig = subConfig;
    }

    /** {@code a} layered over {@code b}: {@code a} wins, falling back per resolution. */
    protected static <CTX, T> FieldValue<CTX, T> merge(@Nullable FieldValue<CTX, T> a,
                                                       @Nullable FieldValue<CTX, T> b) {
        return FieldValue.merge(a, b);
    }

    /** {@code null} when the field is unset. */
    protected static <CTX, T> @Nullable T resolve(@Nullable FieldValue<CTX, T> fv, CTX ctx) {
        return fv != null ? fv.resolve(ctx) : null;
    }
}
