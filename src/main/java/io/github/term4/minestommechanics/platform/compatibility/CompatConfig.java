package io.github.term4.minestommechanics.platform.compatibility;

import net.minestom.server.entity.EntityPose;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Immutable cross-version compatibility config: per-scope knobs that make the server present consistent mechanics to
 * mixed-version clients (e.g. serving 1.8-style behavior to modern clients). Scoped via {@code MechanicsProfile.compat}
 * and pushed to {@code OptimizedPlayer} at spawn / on profile change by {@code PlayerConfigApplier}, like {@code PlayerConfig}.
 * Plain values - rarely-changing platform knobs, not per-hit values, so deliberately no {@code FieldValue}/subconfig
 * machinery. Unset ({@code null}) fields are left unmanaged.
 *
 * <p>{@code disabledPoses} - poses the server forces back to {@link EntityPose#STANDING} (e.g. {@code SWIMMING} for
 * swim/crawl, {@code FALL_FLYING} for elytra) so a modern client can't enter a pose 1.8 lacks. The pose visual itself is
 * client-authoritative (the client recomputes its own pose each tick), so this only fixes the server + other viewers; the
 * gameplay half is {@code restrictMovement}.
 *
 * <p>{@code restrictMovement} - rejects a move that would newly place the player's <em>server</em> hitbox in block
 * collision, so a client rendering itself crawling/swimming still cannot traverse a gap its server hitbox can't fit through
 * (Minemen behaviour). Uses the player's current bounding box, so with {@code legacyHitbox} on, the 1.5-block sneak gap is
 * restricted too. Enforced by {@code CompatMovement}.
 *
 * <p>{@code legacyHitbox} - keeps the server bounding box at standing dimensions regardless of pose (no modern crouch
 * shrink to 1.5) and uses 1.8 eye heights (1.54 sneaking vs the modern crouch eye), for server-side collision / drowning /
 * projectile spawn. The client still renders its own (shrunk) pose; this is the server-treated half. Enforced by
 * {@code OptimizedPlayer.getBoundingBox}/{@code getEyeHeight}.
 *
 * <p>{@code attackHitboxMargin} - the {@code attack_range} item-component {@code hitbox_margin} stamped onto the player's
 * items as the client sees them, so a modern 1.21.11+ client gets a 1.8-style attack box ({@code 0.1}) instead of the
 * modern default ({@code 0.3}). Held-item only (a bare-hand attack hardcodes {@code 0.0} client-side - unfixable here).
 * Enforced by {@code OptimizedPlayer} intercepting outgoing inventory packets.
 *
 * <p>{@code disableOffhand} - blocks a modern client from putting items in / using the offhand 1.8 lacks: the gameplay
 * F-swap and any inventory click targeting the offhand slot are cancelled. Enforced by {@code CompatOffhand}. No effect on
 * 1.8 clients (they have no offhand).
 *
 * <p>{@code restrictSprintSneak} / {@code restrictSprintUse} / {@code restrictSwimSpeed} - independently remove the modern
 * speed advantage of sprinting while sneaking / using an item / swimming (all forbidden or slower in 1.8). Enforced by
 * {@code CompatMovement} clamping the horizontal move (stripping the sprint bonus) and setting the client back, only while
 * the player is sprinting in that state (the knockback i-frame window is skipped so hits still launch).
 *
 * <p>{@code blockPlaceReach} - max distance (blocks) from the server eye to a placement's clicked point; a farther placement
 * is cancelled. Closes the modern sneak-bridge over-reach (the lower crouch eye out-reaches 1.8). Uses the server eye
 * (1.8 preset under {@code legacyHitbox}), so pair the two. Enforced by {@code CompatPlacement}; {@code null} = unmanaged.
 */
public final class CompatConfig {

    /** Poses forced back to {@code STANDING}; {@code null} = unmanaged, empty = none disabled. */
    public final @Nullable Set<EntityPose> disabledPoses;
    /** When {@code true}, a move placing the player's server hitbox in block collision is rejected (no crawl/sneak through a gap it can't fit). {@code null} = unmanaged. */
    public final @Nullable Boolean restrictMovement;
    /** When {@code true}, the server hitbox stays standing dimensions (no crouch shrink) and uses 1.8 eye heights. {@code null} = unmanaged. */
    public final @Nullable Boolean legacyHitbox;
    /** {@code attack_range.hitbox_margin} stamped on the client's view of held items (1.8 = {@code 0.1f}); {@code null} = unmanaged (leave items untouched). */
    public final @Nullable Float attackHitboxMargin;
    /** When {@code true}, the offhand is disabled (F-swap + offhand-slot clicks cancelled); {@code null} = unmanaged. */
    public final @Nullable Boolean disableOffhand;
    /** When {@code true}, cancels the sprint speed boost while sneaking (1.8 can't sprint-sneak); {@code null} = unmanaged. */
    public final @Nullable Boolean restrictSprintSneak;
    /** When {@code true}, cancels the sprint speed boost while using an item (1.8 can't sprint-use); {@code null} = unmanaged. */
    public final @Nullable Boolean restrictSprintUse;
    /** When {@code true}, cancels the sprint speed boost while in water (caps modern fast-swim toward 1.8 water speed); {@code null} = unmanaged. */
    public final @Nullable Boolean restrictSwimSpeed;
    /** Max distance (blocks) from the server eye to a placement's clicked point before it's cancelled (1.8 sneak-reach parity); {@code null} = unmanaged. */
    public final @Nullable Double blockPlaceReach;

    private CompatConfig(Builder b) {
        disabledPoses = b.disabledPoses;
        restrictMovement = b.restrictMovement;
        legacyHitbox = b.legacyHitbox;
        attackHitboxMargin = b.attackHitboxMargin;
        disableOffhand = b.disableOffhand;
        restrictSprintSneak = b.restrictSprintSneak;
        restrictSprintUse = b.restrictSprintUse;
        restrictSwimSpeed = b.restrictSwimSpeed;
        blockPlaceReach = b.blockPlaceReach;
    }

    public Builder toBuilder() { return new Builder(this); }

    public static Builder builder() { return builder(null); }
    public static Builder builder(@Nullable CompatConfig base) { return base != null ? new Builder(base) : new Builder(); }

    public static final class Builder {
        private @Nullable Set<EntityPose> disabledPoses;
        private @Nullable Boolean restrictMovement;
        private @Nullable Boolean legacyHitbox;
        private @Nullable Float attackHitboxMargin;
        private @Nullable Boolean disableOffhand;
        private @Nullable Boolean restrictSprintSneak;
        private @Nullable Boolean restrictSprintUse;
        private @Nullable Boolean restrictSwimSpeed;
        private @Nullable Double blockPlaceReach;

        Builder() {}

        Builder(CompatConfig c) {
            disabledPoses = c.disabledPoses;
            restrictMovement = c.restrictMovement;
            legacyHitbox = c.legacyHitbox;
            attackHitboxMargin = c.attackHitboxMargin;
            disableOffhand = c.disableOffhand;
            restrictSprintSneak = c.restrictSprintSneak;
            restrictSprintUse = c.restrictSprintUse;
            restrictSwimSpeed = c.restrictSwimSpeed;
            blockPlaceReach = c.blockPlaceReach;
        }

        /** Poses forced back to {@code STANDING} for in-scope players ({@code null} = unmanaged). Defensively copied. */
        public Builder disabledPoses(@Nullable Set<EntityPose> v) { disabledPoses = v != null ? Set.copyOf(v) : null; return this; }
        /** Convenience: disable the given poses (e.g. {@code SWIMMING}, {@code FALL_FLYING}). */
        public Builder disabledPoses(EntityPose... poses) { disabledPoses = Set.of(poses); return this; }
        /** Reject moves that place the player's server hitbox in block collision (no crawl/sneak through a too-small gap). */
        public Builder restrictMovement(@Nullable Boolean v) { restrictMovement = v; return this; }
        /** Keep the server hitbox at standing dimensions (no crouch shrink) + 1.8 eye heights. */
        public Builder legacyHitbox(@Nullable Boolean v) { legacyHitbox = v; return this; }
        /** Stamp {@code attack_range.hitbox_margin} on the client's held items (1.8 = {@code 0.1f}; {@code null} = leave untouched). */
        public Builder attackHitboxMargin(@Nullable Float v) { attackHitboxMargin = v; return this; }
        /** Disable the offhand for in-scope players (cancels the F-swap + offhand-slot clicks). No effect on 1.8 clients. */
        public Builder disableOffhand(@Nullable Boolean v) { disableOffhand = v; return this; }
        /** Cancel the sprint speed boost while sneaking (1.8 parity). */
        public Builder restrictSprintSneak(@Nullable Boolean v) { restrictSprintSneak = v; return this; }
        /** Cancel the sprint speed boost while using an item (1.8 parity). */
        public Builder restrictSprintUse(@Nullable Boolean v) { restrictSprintUse = v; return this; }
        /** Cancel the sprint speed boost while in water (caps modern fast-swim toward 1.8). */
        public Builder restrictSwimSpeed(@Nullable Boolean v) { restrictSwimSpeed = v; return this; }
        /** Cancel placements whose clicked point is farther than {@code reach} blocks from the server eye (1.8 sneak-reach parity; pair with {@code legacyHitbox}). */
        public Builder blockPlaceReach(@Nullable Double v) { blockPlaceReach = v; return this; }

        public CompatConfig build() { return new CompatConfig(this); }
    }
}
