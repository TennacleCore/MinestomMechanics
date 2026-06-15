package io.github.term4.minestommechanics.mechanics.projectile;

import io.github.term4.minestommechanics.mechanics.projectile.entities.ManagedProjectile;
import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Pluggable per-projectile behavior: changes what a projectile does on impact / stick / unstick / tick WITHOUT
 * subclassing the entity - the projectile analog of the attack system's {@code Ruleset} processor. Attach it as the
 * {@code behavior} knob on a {@code ProjectileTypeConfig} (per type) or per-launch via
 * {@link ProjectileSnapshot#withBehavior}. Every hook is a no-op by default, so a behavior overrides only what it
 * cares about and is ADDITIVE - the projectile's built-in effects (an egg's chicken, an arrow's stick/pickup) still
 * run. {@code hit} is the struck entity for {@link #onImpact}, or {@code null} for a block hit.
 */
public interface ProjectileBehavior {

    /** A behavior that does nothing (the default when none is configured). */
    ProjectileBehavior NONE = new ProjectileBehavior() {};

    /** Fired once, the first tick after the projectile enters the world - the seam for spawn cosmetics (trail, sound). */
    default void onSpawn(ManagedProjectile projectile) {}

    /** Fired every tick the projectile is alive, after its built-in update. */
    default void onTick(ManagedProjectile projectile, long time) {}

    /** Fired once a hit lands (entity OR block) and is not cancelled, after the damage/knockback pipeline, before removal. */
    default void onImpact(ManagedProjectile projectile, @Nullable Entity hit) {}

    /** Fired when a hit does NOT remove the projectile - it bounced ({@code DEFLECT}) off or passed through {@code hit}
     *  (the struck entity, or {@code null}); the projectile keeps flying. */
    default void onDeflect(ManagedProjectile projectile, @Nullable Entity hit) {}

    /** Fired when the projectile sticks in a block (arrows). */
    default void onStuck(ManagedProjectile projectile) {}

    /** Fired when a stuck projectile unsticks (the block it was in was broken). */
    default void onUnstuck(ManagedProjectile projectile) {}

    /** Fired once when the projectile is removed from the world (despawn, pickup, break), closing the lifecycle. */
    default void onRemove(ManagedProjectile projectile) {}
}
