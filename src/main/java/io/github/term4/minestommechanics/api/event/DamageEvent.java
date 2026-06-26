package io.github.term4.minestommechanics.api.event;

import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.mechanics.damage.DamageConfig;
import io.github.term4.minestommechanics.mechanics.damage.DamageSnapshot;
import io.github.term4.minestommechanics.mechanics.damage.DamageSystem;
import io.github.term4.minestommechanics.mechanics.damage.types.DamageType;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The main damage phase: inspect and modify a computed damage instance before it is applied. Fired after mitigation,
 * with the resolved {@link #amount()}; bracketed by {@link PreDamageEvent} (before computation) and
 * {@link DamageAppliedEvent} (after application). Shares the {@link CancellableMechanicsEvent} shape - an immutable
 * {@link #snapshot()} plus a mutable {@link #finalSnap()} used for application.
 */
public final class DamageEvent extends CancellableMechanicsEvent<DamageSnapshot> {

    private float amount;
    private final boolean invulnerable;
    private final int remainingInvul;
    private final float stored;
    private boolean bypassInvul;   // ignore the i-frame window
    private boolean bypassImmune;  // ignore fundamental immunity (creative/spectator)

    public DamageEvent(DamageSnapshot snap, float amount, Services services) {
        super(snap, services);
        this.amount = amount;
        this.invulnerable = DamageSystem.isInvulnerableToDamage(snap.target());
        this.remainingInvul = snap.target() instanceof LivingEntity le
                ? DamageSystem.remainingDamageInvul(le) : 0;
        this.stored = snap.target() instanceof LivingEntity le
                ? DamageSystem.lastDamage(le) : 0f;
    }

    /** Final damage amount to apply (mutable). */
    public float amount() { return amount; }
    public void amount(float amount) { this.amount = amount; }

    /** Damage config used for resolution ({@code null} = the system config). */
    public @Nullable DamageConfig config() { return finalSnap().config(); }

    /** Replaces the config used for resolution for this hit (sugar for rebuilding {@link #finalSnap()}). */
    public void config(@Nullable DamageConfig config) { finalSnap(finalSnap().withConfig(config)); }

    /** Whether the target was inside its i-frame window when this hit arrived. Not fundamental immunity. */
    public boolean invulnerable() { return invulnerable; }
    /** Remaining ticks of the target's damage-invulnerability window ({@code 0} when not in one). */
    public int remainingInvul() { return remainingInvul; }

    /** The highwater damage stored for the current invul window; overdamage applies only the delta {@code amount - stored}. {@code 0} when fresh. */
    public float stored() { return stored; }

    /** Whether to ignore the target's i-frame window (apply even mid-window). */
    public boolean bypassInvul() { return bypassInvul; }
    public void bypassInvul(boolean bypass) { this.bypassInvul = bypass; }
    /** Whether to ignore fundamental immunity - creative/spectator (e.g. void / admin kill). */
    public boolean bypassImmune() { return bypassImmune; }
    public void bypassImmune(boolean bypass) { this.bypassImmune = bypass; }

    // delegating accessors
    public DamageType type() { return finalSnap().type(); }
    public Entity target() { return finalSnap().target(); }
    public @Nullable Entity source() { return finalSnap().source(); }
    /** Item involved in the damage (melee weapon, later a projectile's bow), or {@code null}. */
    public @Nullable ItemStack item() { return finalSnap().item(); }
    /** Type-specific payload attached by the producer (e.g. the fall distance), or {@code null}. */
    public @Nullable Object detail() { return finalSnap().detail(); }
}
