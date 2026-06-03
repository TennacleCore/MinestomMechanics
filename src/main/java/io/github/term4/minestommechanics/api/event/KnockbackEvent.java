package io.github.term4.minestommechanics.api.event;

import io.github.term4.minestommechanics.mechanics.Cause;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfig;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackSnapshot;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackSystem;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.event.Event;
import org.jetbrains.annotations.Nullable;

// This is the public facing API users can hook into to get information or change how a knockback event happens
public final class KnockbackEvent implements Event {

    private final KnockbackSnapshot snap;
    private KnockbackSnapshot finalSnap;
    private @Nullable Vec velocity;
    private @Nullable Vec direction;
    private final boolean invulnerable;
    private boolean bypassInvul;

    private boolean cancelled;

    public KnockbackEvent(KnockbackSnapshot snap) {
        this.snap = snap;
        this.invulnerable = snap.target() != null && KnockbackSystem.isInvulnerableToKnockback(snap.target());
    }

    /** Original attack data (immutable) */
    public KnockbackSnapshot snapshot() { return snap; }

    /**
     * Snapshot used in knockback calculation.
     * Set via {@code event.finalSnap(event.snapshot().toBuilder().target(x).build())}
     */
    public KnockbackSnapshot finalSnap() {
        return finalSnap != null ? finalSnap : snap;
    }
    public void finalSnap(KnockbackSnapshot snap) { this.finalSnap = snap; }

    /** Knockback config used for calculation. Override by setting {@link #finalSnap(KnockbackSnapshot)} with a new config. */
    public @Nullable KnockbackConfig config() { return finalSnap().config(); }

    /** Knockback cause. Override by setting {@link #finalSnap(KnockbackSnapshot)} with a new cause. */
    public Cause cause() { return finalSnap().cause(); }

    /** If set, used instead of running the calculator */
    public @Nullable Vec velocity() { return velocity; }
    public void velocity(@Nullable Vec velocity) { this.velocity = velocity; }

    /** If set, overides the computed horizontal (xz) knockback direction. */
    public @Nullable Vec direction() { return direction; }
    public void direction(@Nullable Vec direction) { this.direction = direction; }

    public boolean invulnerable() { return invulnerable; }
    public boolean bypassInvul() { return bypassInvul; }

    /** Deal knockback even if the target is invulnerable. */
    public void bypassInvul(boolean b) { this.bypassInvul = b; }

    public boolean cancelled() { return cancelled; }

    /** Cancel the knockback event */
    public void cancel() { this.cancelled = true; }

    // public accessors
    public Entity source() { return finalSnap().source(); }
    public @Nullable Entity target() { return finalSnap().target(); }

}
