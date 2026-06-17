package io.github.term4.minestommechanics.api.event;

import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfig;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileConfigResolver.ResolvedHit;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileSnapshot;
import io.github.term4.minestommechanics.mechanics.projectile.entities.ProjectileEntity;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig.HitResponse;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig.KnockbackSource;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.event.trait.CancellableEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when a projectile hits an entity or a block, before the effects apply. Same shape as {@link DamageEvent} /
 * {@link KnockbackEvent}: {@link #snapshot()} + a {@link #resolvedHit()} preview + per-hit overrides applied instead of
 * the resolved values (each unset = use resolved): {@link #damage(double)}, {@link #knockback(KnockbackConfig)} /
 * {@link #knockbackSource(KnockbackSource)}, {@link #removeOnHit(boolean)}, and {@link #response(HitResponse)} to force
 * the outcome. {@code cancel} suppresses everything (projectile keeps flying). {@link #target()} is {@code null} for a block hit.
 */
public class ProjectileHitEvent implements CancellableEvent {

    private final ProjectileEntity projectile;
    private final ProjectileSnapshot snapshot;
    private final @Nullable Entity shooter;
    private final @Nullable Entity target;
    private final Point hitPoint;
    private final @Nullable Pos throwOrigin;
    private final ResolvedHit resolved;
    private final double resolvedDamage;

    private @Nullable Double damage;
    private @Nullable KnockbackConfig knockback;
    private @Nullable KnockbackSource knockbackSource;
    private @Nullable Boolean removeOnHit;
    private @Nullable HitResponse response;

    private boolean cancelled;

    public ProjectileHitEvent(ProjectileEntity projectile, ProjectileSnapshot snapshot, @Nullable Entity shooter,
                              @Nullable Entity target, Point hitPoint, @Nullable Pos throwOrigin,
                              ResolvedHit resolved, double resolvedDamage) {
        this.projectile = projectile;
        this.snapshot = snapshot;
        this.shooter = shooter;
        this.target = target;
        this.hitPoint = hitPoint;
        this.throwOrigin = throwOrigin;
        this.resolved = resolved;
        this.resolvedDamage = resolvedDamage;
    }

    public @NotNull ProjectileEntity projectile() { return projectile; }
    /** The launch snapshot (shooter, type, item, power, ...). */
    public ProjectileSnapshot snapshot() { return snapshot; }
    public @Nullable Entity shooter() { return shooter; }
    /** The hit entity, or {@code null} for a block hit. */
    public @Nullable Entity target() { return target; }
    public @NotNull Point hitPoint() { return hitPoint; }
    public boolean isBlockHit() { return target == null; }
    /** Whether the struck entity is the shooter itself. */
    public boolean isSelfHit() { return target != null && target == shooter; }
    /** The shooter's position + yaw/pitch stamped at throw time, or {@code null} if there was no shooter. */
    public @Nullable Pos throwOrigin() { return throwOrigin; }

    /** The resolved hit knobs for this hit (the config preview, before overrides). */
    public ResolvedHit resolvedHit() { return resolved; }

    /** Final damage this hit will deal: the {@link #damage(double) override} when set, else the resolved amount
     *  (arrows: {@code ceil(speed * perSpeed) + crit}). {@code 0} for a block hit. */
    public double damage() { return damage != null ? damage : resolvedDamage; }
    public void damage(double amount) { this.damage = amount; }

    /** Knockback applied on a landing hit: the override when set, else the resolved {@link KnockbackConfig} ({@code null} = none). */
    public @Nullable KnockbackConfig knockback() { return knockback != null ? knockback : resolved.knockback(); }
    public void knockback(@Nullable KnockbackConfig kb) { this.knockback = kb; }

    /** Knockback origin frame: the override when set, else the resolved {@link KnockbackSource}. */
    public KnockbackSource knockbackSource() { return knockbackSource != null ? knockbackSource : resolved.knockbackSource(); }
    public void knockbackSource(KnockbackSource source) { this.knockbackSource = source; }

    /** Whether the projectile is removed on this hit: the override when set, else the resolved remove-on-{entity,block}-hit. */
    public boolean removeOnHit() {
        if (removeOnHit != null) return removeOnHit;
        return isBlockHit() ? resolved.removeOnBlockHit() : resolved.removeOnEntityHit();
    }
    public void removeOnHit(boolean remove) { this.removeOnHit = remove; }

    /** Forced outcome for this hit, or {@code null} to use the config's self-hit / invuln-hit logic. {@code HIT} =
     *  deal damage + knockback + impact; {@code PASS_THROUGH} = keep flying; {@code DEFLECT} = bounce; {@code DESTROY}
     *  = impact effect then remove. */
    public @Nullable HitResponse response() { return response; }
    public void response(@Nullable HitResponse response) { this.response = response; }

    /** Cancel the hit: no damage/knockback/impact/removal - the projectile keeps flying. */
    public void cancel() { setCancelled(true); }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
}
