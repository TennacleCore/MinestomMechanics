package io.github.term4.minestommechanics.api.event;

import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfig;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackSnapshot;
import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * The pre-knockback gate: fired <em>before</em> the velocity is computed and the {@link KnockbackEvent}. Cancel to
 * suppress the knockback entirely (no-KB zones, grapple abilities), or redirect the inputs (melee flag / config) via
 * {@link #finalSnap}. The computed velocity isn't known yet - use {@link KnockbackEvent} to override it.
 */
public final class PreKnockbackEvent extends CancellableMechanicsEvent<KnockbackSnapshot> {

    public PreKnockbackEvent(KnockbackSnapshot snap, Services services) {
        super(snap, services);
    }

    /** {@code null} = the system config. */
    public @Nullable KnockbackConfig config() { return finalSnap().config(); }
    public void config(@Nullable KnockbackConfig config) { finalSnap(finalSnap().withConfig(config)); }

    /** Melee hit (gates sprint extra / melee-only components). */
    public boolean melee() { return finalSnap().melee(); }

    public @Nullable Entity source() { return finalSnap().source(); }
    public @Nullable Entity target() { return finalSnap().target(); }
}
