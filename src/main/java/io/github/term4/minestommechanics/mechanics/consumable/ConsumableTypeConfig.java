package io.github.term4.minestommechanics.mechanics.consumable;

import io.github.term4.minestommechanics.codegen.GenerateBuilder;
import io.github.term4.minestommechanics.config.Config;
import io.github.term4.minestommechanics.config.FieldValue;
import io.github.term4.minestommechanics.mechanics.consumable.ConsumableConfigResolver.ConsumableContext;
import net.kyori.adventure.key.Key;
import net.minestom.server.potion.Potion;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Per-consumable config, keyed by {@link #key()}: every value is a {@link FieldValue} resolved against a
 * {@link ConsumableContext} (constant or per-consume lambda), unset fields falling back per-type override -&gt;
 * {@link ConsumableConfig#defaults()} -&gt; the type's {@code defaultConfig()} -&gt; hard fallbacks. The {@link #behavior}
 * knob is where presets supply the version-specific effects/hunger, so one {@link Consumable} identity gets 1.8 vs 26 behavior by scope.
 */
@GenerateBuilder
public final class ConsumableTypeConfig extends Config<ConsumableContext, ConsumableTypeConfig> {

    /**
     * How a consumable's applied effects render: {@link #SHOWN} (vanilla swirl + HUD icon), {@link #HIDDEN} (icon only,
     * no swirl), or {@link #CUSTOM} (no vanilla swirl; reserved for the platform particle system). Read via {@code ctx.particles()}.
     *
     * <p><b>Stub:</b> {@link #CUSTOM} suppresses the vanilla swirl like {@link #HIDDEN} for now; custom particle rendering lands with the platform particle system.
     */
    public enum ParticleVisibility {
        SHOWN, HIDDEN, CUSTOM;

        /** The {@code Potion} flag byte under this visibility: the HUD icon is always on; the vanilla swirl shows only for {@link #SHOWN}. */
        public byte potionFlags() {
            boolean swirl = this == SHOWN;
            // TODO(particles): CUSTOM suppresses the vanilla swirl like HIDDEN until the platform particle system renders custom particles here.
            return (byte) ((swirl ? Potion.PARTICLES_FLAG : 0) | Potion.ICON_FLAG);
        }
    }

    /** Type identity (not a knob): the key this config is registered under. */
    public final @Nullable Key key;

    /** Whether this consumable consumes (default {@code true}); {@code false} disables it for the scope. */
    public final @Nullable FieldValue<ConsumableContext, Boolean> enabled;
    /** May {@code user} start using it now (vanilla {@code Consumable.canConsume}); default {@code true} (drinks/custom).
     *  Food supplies the version's {@code canEat} (1.8 blocks creative, 26 allows it); a blocked use never enters the eating state. */
    public final @Nullable FieldValue<ConsumableContext, Boolean> canConsume;
    /** Consume duration in ticks (vanilla {@code 32} = 1.6s). */
    public final @Nullable FieldValue<ConsumableContext, Integer> consumeTicks;
    /** The {@link ConsumableBehavior} run on the use lifecycle (effects / hunger / custom). Default {@link ConsumableBehavior#NONE}. */
    public final @Nullable FieldValue<ConsumableContext, ConsumableBehavior> behavior;
    /** How this consumable's applied effects render (swirl / icon). Default {@link ParticleVisibility#SHOWN}. Read by the built-in effect behaviors via {@code ctx.particles()}. */
    public final @Nullable FieldValue<ConsumableContext, ParticleVisibility> particles;

    ConsumableTypeConfig(Builder b) {
        super(b.subConfig);
        this.key = b.key;
        this.enabled = b.enabled;
        this.canConsume = b.canConsume;
        this.consumeTicks = b.consumeTicks;
        this.behavior = b.behavior;
        this.particles = b.particles;
    }

    public Key key() { return key; }

    /** Merges this config over {@code base}: this config's set fields win, unset fields fall back per resolution. */
    public ConsumableTypeConfig fromBase(ConsumableTypeConfig base) {
        Builder b = new Builder(key != null ? key : base.key);
        b.mergeKnobs(this, base);
        b.subConfig = subConfig != null ? subConfig : base.subConfig;
        return b.build();
    }

    /** Builder for the generic (key-less) default config - the base every type inherits unless it overrides a knob. */
    public static Builder builder() { return new Builder((Key) null); }
    public static Builder builder(Key key) { return new Builder(key); }
    public static Builder builder(ConsumableTypeConfig base) { return new Builder(base); }
    public Builder toBuilder() { return new Builder(this); }

    public static final class Builder extends ConsumableTypeConfigBuilderBase<Builder> {

        @Override protected Builder self() { return this; }
        private Key key;
        private Function<ConsumableContext, ConsumableTypeConfig> subConfig;

        Builder(Key key) { this.key = key; }

        Builder(ConsumableTypeConfig c) {
            super(c);
            key = c.key;
            subConfig = c.subConfig;
        }

        public Builder key(Key k) { this.key = k; return this; }
        public Builder subConfig(Function<ConsumableContext, ConsumableTypeConfig> fn) { subConfig = fn; return this; }

        public ConsumableTypeConfig build() { return new ConsumableTypeConfig(this); }
    }
}
