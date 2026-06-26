package io.github.term4.minestommechanics.api.event;

import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfig;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfigResolver;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackSnapshot;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * The main knockback phase: inspect and modify a knockback instance before it is applied. Bracketed by
 * {@link PreKnockbackEvent} (before computation) and {@link KnockbackAppliedEvent} (after the velocity is set). Shares
 * the {@link CancellableMechanicsEvent} shape; optional {@link #velocity(Vec)} / {@link #direction(Vec)} overrides
 * bypass or steer the calculator. Per-hit config changes go through {@link #config(KnockbackConfig)};
 * {@link #resolvedConfig()} previews the effective values.
 */
public final class KnockbackEvent extends CancellableMechanicsEvent<KnockbackSnapshot> {

    private @Nullable Vec velocity;
    private @Nullable Vec direction;

    public KnockbackEvent(KnockbackSnapshot snap, Services services) {
        super(snap, services);
    }

    /** Knockback config used for calculation ({@code null} = the system config). */
    public @Nullable KnockbackConfig config() { return finalSnap().config(); }

    /** Replaces the config used for calculation for this hit (sugar for rebuilding {@link #finalSnap()}). */
    public void config(@Nullable KnockbackConfig config) { finalSnap(finalSnap().withConfig(config)); }

    /**
     * The effective plain values this hit will be calculated with: the {@link #config() current config} (else the
     * system config) merged over the calculator defaults and resolved against this hit. Re-resolved from the current
     * {@link #finalSnap()} on each call, so it tracks listener changes.
     */
    public KnockbackConfigResolver.ResolvedKnockbackConfig resolvedConfig() {
        return services().knockback().resolveConfig(finalSnap());
    }

    /** Whether this is a melee hit (gates the sprint extra / melee-only components). Override via {@link #finalSnap}. */
    public boolean melee() { return finalSnap().melee(); }

    /** If set, used instead of running the calculator. */
    public @Nullable Vec velocity() { return velocity; }
    public void velocity(@Nullable Vec velocity) { this.velocity = velocity; }

    /** If set, overrides the computed horizontal (xz) knockback direction (keeps the computed magnitude). */
    public @Nullable Vec direction() { return direction; }
    public void direction(@Nullable Vec direction) { this.direction = direction; }

    // delegating accessors
    public @Nullable Entity source() { return finalSnap().source(); }
    public @Nullable Entity target() { return finalSnap().target(); }
}
