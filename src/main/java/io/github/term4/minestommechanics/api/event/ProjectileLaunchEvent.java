package io.github.term4.minestommechanics.api.event;

import io.github.term4.minestommechanics.mechanics.projectile.ProjectileBehavior;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileConfigResolver.ResolvedFlight;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileSnapshot;
import io.github.term4.minestommechanics.mechanics.projectile.entities.ProjectileEntity;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.event.trait.CancellableEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired once a projectile {@link #projectile() entity} has been built and configured but BEFORE it enters the world.
 * One event covers the whole launch:
 * <ul>
 *   <li><b>cancel</b> - {@code setCancelled(true)} discards the entity; it never spawns.</li>
 *   <li><b>redirect</b> - mutate {@link #setSpawnPos}/{@link #setVelocity} (velocity is b/t).</li>
 *   <li><b>behavior</b> - {@link #behavior(ProjectileBehavior)} attaches a per-launch {@link ProjectileBehavior}
 *       (onSpawn/onTick/onImpact/...), overriding the config's; the event-driven way to give a custom item custom
 *       flight/impact behavior without touching the config (pairs with {@link ProjectileHitEvent}'s per-hit overrides).</li>
 *   <li><b>attach</b> - keep a reference to {@link #projectile()} to add cosmetics directly (metadata, a manual task).</li>
 * </ul>
 * To react to the END of flight (e.g. spawn an entity where it lands), use {@link ProjectileHitEvent} instead.
 */
public class ProjectileLaunchEvent implements CancellableEvent {

    private final ProjectileSnapshot snapshot;
    private final ProjectileEntity projectile;
    private final ResolvedFlight resolvedFlight;
    private Pos spawnPos;
    private Vec velocity;
    private @Nullable ProjectileBehavior behavior;
    private boolean cancelled;

    public ProjectileLaunchEvent(ProjectileSnapshot snapshot, ProjectileEntity projectile, ResolvedFlight resolvedFlight, Pos spawnPos, Vec velocity) {
        this.snapshot = snapshot;
        this.projectile = projectile;
        this.resolvedFlight = resolvedFlight;
        this.spawnPos = spawnPos;
        this.velocity = velocity;
    }

    public ProjectileSnapshot snapshot() { return snapshot; }
    public @Nullable Entity shooter() { return snapshot.shooter(); }

    /** The resolved flight knobs (physics, spawn offsets, sync, immunity) the entity was stamped with - a preview, like
     *  {@code DamageEvent.resolvedConfig()}; the entity is already configured, so mutate {@link #setSpawnPos}/{@link #setVelocity} to redirect. */
    public ResolvedFlight resolvedFlight() { return resolvedFlight; }

    /** The built projectile entity, not yet in the world - a typed handle: set physics ({@code setAerodynamics},
     *  {@code setPhysicsOrder}, ...), velocity, behavior, attach cosmetics, or cancel to discard it. */
    public @NotNull ProjectileEntity projectile() { return projectile; }

    public @NotNull Pos spawnPos() { return spawnPos; }
    public void setSpawnPos(@NotNull Pos pos) { this.spawnPos = pos; }

    /** Initial velocity in blocks/tick. */
    public @NotNull Vec velocity() { return velocity; }
    public void setVelocity(@NotNull Vec velocityBt) { this.velocity = velocityBt; }

    /** Per-launch {@link ProjectileBehavior} override ({@code null} = keep the config/snapshot one). Applied after the
     *  event - the event-driven seam for custom-item flight/impact behavior. */
    public @Nullable ProjectileBehavior behavior() { return behavior; }
    public void behavior(@Nullable ProjectileBehavior behavior) { this.behavior = behavior; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
}
