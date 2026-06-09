package io.github.term4.minestommechanics.tracking;

import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * The input a {@link VelocityRule} composes - the velocity analog of {@code AttackEvent} for {@code CriticalRule}.
 * Bundles the entity, its base {@link Physics} (the entity's own {@link Physics#fromEntity}, which a rule may
 * override via its spec), and the {@link MotionTracker} reads (air-time, launch latch + stamp, position-delta
 * motion), so a rule written outside the library has the same primitives the built-ins use.
 */
public final class VelocityContext {

    private final Entity entity;
    private final Physics physics;
    private final @Nullable SprintTracker sprintTracker;

    private VelocityContext(Entity entity, Physics physics, @Nullable SprintTracker sprintTracker) {
        this.entity = entity;
        this.physics = physics;
        this.sprintTracker = sprintTracker;
    }

    /** Context seeded with the entity's own {@link Physics#fromEntity} ("straight from Minestom"), no sprint tracker. */
    public static VelocityContext of(Entity entity) {
        return new VelocityContext(entity, Physics.fromEntity(entity), null);
    }

    /**
     * Context seeded with the entity's own {@link Physics#fromEntity} and the given {@link SprintTracker}, so a rule
     * can consult the client sprint state ({@link #wasClientRecentlySprinting}). A {@code null} tracker degrades the
     * sprint reads to the server-side {@link Entity#isSprinting()} (see {@link SprintTracker}).
     */
    public static VelocityContext of(Entity entity, @Nullable SprintTracker sprintTracker) {
        return new VelocityContext(entity, Physics.fromEntity(entity), sprintTracker);
    }

    public Entity entity() { return entity; }

    /** The entity's base physics; a rule may override via its spec. */
    public Physics physics() { return physics; }

    /** The sprint tracker backing the sprint reads, or {@code null} (reads fall back to server sprint state). */
    public @Nullable SprintTracker sprintTracker() { return sprintTracker; }

    /** Whether the entity was (server-side) sprinting within the last {@code ticks}. */
    public boolean wasRecentlySprinting(long ticks) {
        return SprintTracker.wasRecentlySprinting(sprintTracker, entity, ticks);
    }

    /** Whether the entity's <em>client</em> was sprinting within the last {@code ticks}. */
    public boolean wasClientRecentlySprinting(int ticks) {
        return SprintTracker.wasClientRecentlySprinting(sprintTracker, entity, ticks);
    }

    /** Position-delta velocity (blocks/tick); players via the per-tick snapshot, others via entity velocity. */
    public Vec positionDelta() { return MotionTracker.positionDelta(entity); }

    /** Ticks since the entity left the ground (0 on ground). */
    public int ticksInAir() { return MotionTracker.ticksInAir(entity); }

    /** Left the ground rising (jump or knockback boost) and still in that launch arc. */
    public boolean launched() { return MotionTracker.launched(entity); }

    /**
     * Whether the entity is grounded per its resolved {@link GroundRule} ({@link MotionTracker#resolveGroundRule}: a
     * per-entity override, else {@code fallback}, else {@link GroundRule#DEFAULT}). The arc gates its air clock on
     * this, so a grounded victim folds the resting arc instead of a stale descent.
     */
    public boolean grounded(@Nullable GroundRule fallback) {
        return MotionTracker.resolveGroundRule(entity, fallback).isOnGround(entity);
    }

    /** Launch origin (yaw + sprint + takeoff horizontal seed) while in a launch arc, or {@code null}. */
    public @Nullable MotionTracker.JumpInfo recentJump() { return MotionTracker.recentJump(entity); }
}
