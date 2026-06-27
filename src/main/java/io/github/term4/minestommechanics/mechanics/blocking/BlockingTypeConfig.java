package io.github.term4.minestommechanics.mechanics.blocking;

import io.github.term4.minestommechanics.config.Config;
import io.github.term4.minestommechanics.config.FieldValue;
import io.github.term4.minestommechanics.mechanics.blocking.BlockingConfigResolver.BlockingContext;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Function;

/**
 * Per-item blocking knobs, held in a {@link BlockingConfig} keyed by {@code Material}: every value is a {@link FieldValue}
 * resolved against a {@link BlockingContext} (constant or per-hit), unset fields falling back per-material entry -&gt;
 * {@link BlockingConfig#defaults()} -&gt; hard fallbacks. The {@link #behavior} knob picks how blocking works (sword /
 * shield / custom); the rest are its parameters (reduction curve, shield arc / delay / bypasses).
 */
public final class BlockingTypeConfig extends Config<BlockingContext, BlockingTypeConfig> {

    /** Whether this item blocks (default {@code true}); {@code false} disables blocking for it in the scope. */
    public final @Nullable FieldValue<BlockingContext, Boolean> enabled;
    /** How blocking works for this item. Default {@link BlockingBehavior#SWORD}. */
    public final @Nullable FieldValue<BlockingContext, BlockingBehavior> behavior;
    /** Reduction curve base: damage removed {@code = clamp(base + factor*damage)}. 1.8 sword {@code -0.5}. Default {@code 0}. */
    public final @Nullable FieldValue<BlockingContext, Double> reductionBase;
    /** Reduction curve factor. 1.8 sword {@code 0.5}; full block {@code 1.0}. Default {@code 1.0}. */
    public final @Nullable FieldValue<BlockingContext, Double> reductionFactor;
    /** Shield: ticks of holding before the block activates. Default {@code 0} (instant, sword). */
    public final @Nullable FieldValue<BlockingContext, Integer> blockDelayTicks;
    /** Shield: max angle (deg) off the defender's facing that still blocks; {@code null} = omnidirectional (sword). */
    public final @Nullable FieldValue<BlockingContext, Double> blockingAngle;
    /** Shield: damage-type keys this item is bypassed by (never blocks them). Default empty. */
    public final @Nullable FieldValue<BlockingContext, Set<Key>> bypassedTypes;

    BlockingTypeConfig(Builder b) {
        super(b.subConfig);
        this.enabled = b.enabled;
        this.behavior = b.behavior;
        this.reductionBase = b.reductionBase;
        this.reductionFactor = b.reductionFactor;
        this.blockDelayTicks = b.blockDelayTicks;
        this.blockingAngle = b.blockingAngle;
        this.bypassedTypes = b.bypassedTypes;
    }

    /** Merges this config over {@code base}: this config's set fields win, unset fields fall back per resolution. */
    public BlockingTypeConfig fromBase(BlockingTypeConfig base) {
        Builder b = new Builder();
        b.subConfig = subConfig != null ? subConfig : base.subConfig;
        b.enabled = merge(enabled, base.enabled);
        b.behavior = merge(behavior, base.behavior);
        b.reductionBase = merge(reductionBase, base.reductionBase);
        b.reductionFactor = merge(reductionFactor, base.reductionFactor);
        b.blockDelayTicks = merge(blockDelayTicks, base.blockDelayTicks);
        b.blockingAngle = merge(blockingAngle, base.blockingAngle);
        b.bypassedTypes = merge(bypassedTypes, base.bypassedTypes);
        return b.build();
    }

    public static Builder builder() { return new Builder(); }
    public static Builder builder(BlockingTypeConfig base) { return new Builder(base); }
    public Builder toBuilder() { return new Builder(this); }

    public static final class Builder {
        private Function<BlockingContext, BlockingTypeConfig> subConfig;
        private FieldValue<BlockingContext, Boolean> enabled;
        private FieldValue<BlockingContext, BlockingBehavior> behavior;
        private FieldValue<BlockingContext, Double> reductionBase;
        private FieldValue<BlockingContext, Double> reductionFactor;
        private FieldValue<BlockingContext, Integer> blockDelayTicks;
        private FieldValue<BlockingContext, Double> blockingAngle;
        private FieldValue<BlockingContext, Set<Key>> bypassedTypes;

        Builder() {}

        Builder(BlockingTypeConfig c) {
            subConfig = c.subConfig;
            enabled = c.enabled;
            behavior = c.behavior;
            reductionBase = c.reductionBase;
            reductionFactor = c.reductionFactor;
            blockDelayTicks = c.blockDelayTicks;
            blockingAngle = c.blockingAngle;
            bypassedTypes = c.bypassedTypes;
        }

        public Builder subConfig(Function<BlockingContext, BlockingTypeConfig> fn) { subConfig = fn; return this; }
        public Builder enabled(Boolean v) { enabled = FieldValue.constant(v); return this; }
        public Builder enabled(Function<BlockingContext, Boolean> fn) { enabled = FieldValue.of(fn); return this; }
        public Builder behavior(BlockingBehavior v) { behavior = FieldValue.constant(v); return this; }
        public Builder behavior(Function<BlockingContext, BlockingBehavior> fn) { behavior = FieldValue.of(fn); return this; }
        public Builder reductionBase(Double v) { reductionBase = FieldValue.constant(v); return this; }
        public Builder reductionBase(Function<BlockingContext, Double> fn) { reductionBase = FieldValue.of(fn); return this; }
        public Builder reductionFactor(Double v) { reductionFactor = FieldValue.constant(v); return this; }
        public Builder reductionFactor(Function<BlockingContext, Double> fn) { reductionFactor = FieldValue.of(fn); return this; }
        public Builder blockDelayTicks(Integer v) { blockDelayTicks = FieldValue.constant(v); return this; }
        public Builder blockDelayTicks(Function<BlockingContext, Integer> fn) { blockDelayTicks = FieldValue.of(fn); return this; }
        public Builder blockingAngle(Double v) { blockingAngle = FieldValue.constant(v); return this; }
        public Builder blockingAngle(Function<BlockingContext, Double> fn) { blockingAngle = FieldValue.of(fn); return this; }
        public Builder bypassedTypes(Set<Key> v) { bypassedTypes = FieldValue.constant(v); return this; }
        public Builder bypassedTypes(Function<BlockingContext, Set<Key>> fn) { bypassedTypes = FieldValue.of(fn); return this; }

        public BlockingTypeConfig build() { return new BlockingTypeConfig(this); }
    }
}
