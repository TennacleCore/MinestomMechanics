package io.github.term4.minestommechanics.mechanics.death;

import org.jetbrains.annotations.Nullable;

/**
 * Config for the death/respawn cleanup: clearing potion effects, resetting transient combat state, and hiding the dead
 * player's body from viewers until respawn. Assigned per scope via the
 * {@link io.github.term4.minestommechanics.MechanicsProfile} {@code DEATH} member and resolved per-victim by
 * {@code DamageSystem} on the death path. An unset knob reads as its vanilla default (on / 20-tick animation).
 */
public final class DeathConfig {

    private final @Nullable Boolean clearEffects;
    private final @Nullable Boolean resetCombatState;
    private final @Nullable Boolean hideCorpse;
    private final @Nullable Integer deathAnimationTicks;

    private DeathConfig(Builder b) {
        this.clearEffects = b.clearEffects;
        this.resetCombatState = b.resetCombatState;
        this.hideCorpse = b.hideCorpse;
        this.deathAnimationTicks = b.deathAnimationTicks;
    }

    /** Clear active potion effects on death (Minestom's {@code kill()} doesn't). Unset = on. */
    public @Nullable Boolean clearEffects() { return clearEffects; }

    /** Reset fire, velocity, drowning air, and stuck arrows on death. Unset = on. */
    public @Nullable Boolean resetCombatState() { return resetCombatState; }

    /** Hide the health-0 body from viewers until respawn (Minestom keeps broadcasting it). Unset = on. */
    public @Nullable Boolean hideCorpse() { return hideCorpse; }

    /** Ticks the death animation plays before {@link #hideCorpse} removes the body. Unset = 20. */
    public @Nullable Integer deathAnimationTicks() { return deathAnimationTicks; }

    /** Merges this config over {@code base}, per field (this if set, else base). */
    public DeathConfig fromBase(DeathConfig base) {
        return new Builder()
                .clearEffects(clearEffects != null ? clearEffects : base.clearEffects)
                .resetCombatState(resetCombatState != null ? resetCombatState : base.resetCombatState)
                .hideCorpse(hideCorpse != null ? hideCorpse : base.hideCorpse)
                .deathAnimationTicks(deathAnimationTicks != null ? deathAnimationTicks : base.deathAnimationTicks)
                .build();
    }

    public Builder toBuilder() { return new Builder(this); }
    public static Builder builder() { return new Builder(); }
    public static Builder builder(@Nullable DeathConfig base) { return base != null ? new Builder(base) : new Builder(); }

    public static final class Builder {
        private @Nullable Boolean clearEffects;
        private @Nullable Boolean resetCombatState;
        private @Nullable Boolean hideCorpse;
        private @Nullable Integer deathAnimationTicks;

        Builder() {}
        Builder(DeathConfig c) {
            clearEffects = c.clearEffects;
            resetCombatState = c.resetCombatState;
            hideCorpse = c.hideCorpse;
            deathAnimationTicks = c.deathAnimationTicks;
        }

        public Builder clearEffects(@Nullable Boolean v) { this.clearEffects = v; return this; }
        public Builder resetCombatState(@Nullable Boolean v) { this.resetCombatState = v; return this; }
        public Builder hideCorpse(@Nullable Boolean v) { this.hideCorpse = v; return this; }
        public Builder deathAnimationTicks(@Nullable Integer v) { this.deathAnimationTicks = v; return this; }

        public DeathConfig build() { return new DeathConfig(this); }
    }
}
