package io.github.term4.minestommechanics.api.event.projectile;

import io.github.term4.minestommechanics.world.MechanicsWorld;
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
 * Fired after a projectile entity is built and configured but before it enters the world: cancel to discard, redirect
 * via {@link #setSpawnPos}/{@link #setVelocity}, override {@link #behavior(ProjectileBehavior)}, or keep the
 * {@link #projectile()} handle for cosmetics. End of flight is {@link ProjectileHitEvent}.
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

    /** Resolved flight knobs the entity was stamped with; redirect via {@link #setSpawnPos}/{@link #setVelocity}. */
    public ResolvedFlight resolvedFlight() { return resolvedFlight; }

    /** The built projectile entity (not yet in the world) - a typed handle for physics, velocity, behavior, cosmetics. */
    public @NotNull ProjectileEntity projectile() { return projectile; }
    /** The world the projectile launches into (the shooter's). */
    public MechanicsWorld world() { return MechanicsWorld.of(snapshot.shooter()); }

    public @NotNull Pos spawnPos() { return spawnPos; }
    public void setSpawnPos(@NotNull Pos pos) { this.spawnPos = pos; }

    /** Initial velocity in blocks/tick. */
    public @NotNull Vec velocity() { return velocity; }
    public void setVelocity(@NotNull Vec velocityBt) { this.velocity = velocityBt; }

    /** Per-launch {@link ProjectileBehavior} override ({@code null} = keep the config/snapshot one). */
    public @Nullable ProjectileBehavior behavior() { return behavior; }
    public void behavior(@Nullable ProjectileBehavior behavior) { this.behavior = behavior; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
}
