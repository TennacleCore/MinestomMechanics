package io.github.term4.minestommechanics.mechanics.projectile;

import io.github.term4.minestommechanics.mechanics.projectile.entities.ManagedProjectile;
import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Pluggable per-projectile behavior: changes what a projectile does on impact/stick/unstick/tick without subclassing -
 * the projectile analog of the attack {@code Ruleset}. Attach via the {@code behavior} config knob or per-launch
 * ({@link ProjectileSnapshot#withBehavior}). Every hook is a no-op by default and additive (built-in effects still run).
 */
public interface ProjectileBehavior {

    /** A behavior that does nothing (the default when none is configured). */
    ProjectileBehavior NONE = new ProjectileBehavior() {};

    /** Fired once, the first tick after the projectile enters the world - the seam for spawn cosmetics (trail, sound). */
    default void onSpawn(ManagedProjectile projectile) {}

    /** Fired every tick the projectile is alive, after its built-in update. */
    default void onTick(ManagedProjectile projectile, long time) {}

    /** Per-target hit gate ANDed with the entity's own {@code canHit} - lets a behavior pass through targets
     *  (the mmc18 pseudo-hook rod ignores players after its one hit). */
    default boolean canHit(ManagedProjectile projectile, Entity target) { return true; }

    /** Fired once a hit lands (entity or block) and is not cancelled, after the damage/knockback pipeline, before removal. */
    default void onImpact(ManagedProjectile projectile, @Nullable Entity hit) {}

    /** Fired when a hit doesn't remove the projectile (deflect / pass-through); it keeps flying. */
    default void onDeflect(ManagedProjectile projectile, @Nullable Entity hit) {}

    /** Fired when the projectile sticks in a block (arrows). */
    default void onStuck(ManagedProjectile projectile) {}

    /** Fired when a stuck projectile unsticks (the block it was in was broken). */
    default void onUnstuck(ManagedProjectile projectile) {}

    /** Fired once when the projectile is removed from the world (despawn, pickup, break), closing the lifecycle. */
    default void onRemove(ManagedProjectile projectile) {}
}
