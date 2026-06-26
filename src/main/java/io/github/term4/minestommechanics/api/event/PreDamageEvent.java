package io.github.term4.minestommechanics.api.event;

import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.mechanics.damage.DamageConfig;
import io.github.term4.minestommechanics.mechanics.damage.DamageSnapshot;
import io.github.term4.minestommechanics.mechanics.damage.types.DamageType;
import net.minestom.server.entity.Entity;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The pre-damage gate: fired the moment a damage instance is proposed, <em>before</em> computation/mitigation and the
 * {@link DamageEvent}. Cancel to drop the hit entirely (it never consumes an i-frame window or runs mitigation), or
 * redirect the inputs (type / source / target / config) via {@link #finalSnap}. The final amount is not known yet -
 * use {@link DamageEvent} to adjust the computed amount.
 */
public final class PreDamageEvent extends CancellableMechanicsEvent<DamageSnapshot> {

    public PreDamageEvent(DamageSnapshot snap, Services services) {
        super(snap, services);
    }

    /** Damage config used for resolution ({@code null} = the system config). */
    public @Nullable DamageConfig config() { return finalSnap().config(); }

    /** Replaces the config used for resolution for this hit (sugar for rebuilding {@link #finalSnap()}). */
    public void config(@Nullable DamageConfig config) { finalSnap(finalSnap().withConfig(config)); }

    // delegating accessors
    public DamageType type() { return finalSnap().type(); }
    public Entity target() { return finalSnap().target(); }
    public @Nullable Entity source() { return finalSnap().source(); }
    /** Item involved in the damage (melee weapon, later a projectile's bow), or {@code null}. */
    public @Nullable ItemStack item() { return finalSnap().item(); }
    /** Type-specific payload attached by the producer (e.g. the fall distance), or {@code null}. */
    public @Nullable Object detail() { return finalSnap().detail(); }
}
