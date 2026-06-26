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
 *
 * <p>{@code animatiumFeatures} - explicit override for the {@link AnimatiumFeature} set sent to an Animatium client (which
 * applies 1.8 behaviour natively, letting us skip the matching server hacks). {@code null} (default) derives the set from the
 * knobs above ({@code attackHitboxMargin}/{@code legacyHitbox}/{@code restrictSprintUse}/{@code restrictSprintSneak}); set a
 * value to send exactly that set. Enforced by {@code CompatAnimatium}; no effect on non-Animatium clients.
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
    /** When {@code true}, Animatium clients get 1.8 water/lava movement (drag/gravity, no swim sprint/buoyancy, no lava current); {@code null} = unmanaged. Client-side only (Animatium feature). */
    public final @Nullable Boolean legacyFluids;
    /** When {@code true}, Animatium clients can't elytra-glide (1.8 has no elytra; they just fall); {@code null} = unmanaged. Client-side only (Animatium feature). */
    public final @Nullable Boolean disableElytraFlight;
    /** When {@code true}, Animatium clients get 1.8 creative/spectator flight (sneaking while flying slows horizontal); {@code null} = unmanaged. Client-side only (Animatium feature). */
    public final @Nullable Boolean oldFlight;
    /** When {@code true}, Animatium clients can start using an item while mining a block (1.8 parity; modern MC blocks use-item the moment you're destroying); {@code null} = unmanaged. Client-side only (Animatium feature). */
    public final @Nullable Boolean leftClickItemUsage;
    /** When {@code true}, Animatium clients don't auto-crouch to fit under a low ceiling (1.8 sneak is shift-only); {@code null} = unmanaged. Client-side only (Animatium feature). */
    public final @Nullable Boolean disableAutoSneak;
    /** Convenience bundle for the four 1.8 physics aspects below ({@code oldMomentum}/{@code disableBedBounce}/{@code disableHoneyPhysics}/{@code disableBubbleColumn}): {@code true} enables all, each per-aspect knob overrides it. {@code null} = unmanaged. Client-side only (Animatium feature). */
    public final @Nullable Boolean oldPhysics;
    /** Per-aspect physics override (1.8 parkour momentum threshold). {@code null} follows {@link #oldPhysics}; {@code true}/{@code false} forces it. Client-side only (Animatium feature). */
    public final @Nullable Boolean oldMomentum;
    /** Per-aspect physics override (1.8 beds don't bounce). {@code null} follows {@link #oldPhysics}; {@code true}/{@code false} forces it. Client-side only (Animatium feature). */
    public final @Nullable Boolean disableBedBounce;
    /** Per-aspect physics override (honey acts like a plain block - no slide, no jump/walk slowdown). {@code null} follows {@link #oldPhysics}; {@code true}/{@code false} forces it. Client-side only (Animatium feature). */
    public final @Nullable Boolean disableHoneyPhysics;
    /** Per-aspect physics override (no bubble-column push). {@code null} follows {@link #oldPhysics}; {@code true}/{@code false} forces it. Client-side only (Animatium feature). */
    public final @Nullable Boolean disableBubbleColumn;
    /** When {@code true}, Animatium clients aren't shoved by entity collision (NOT 1.8 parity - a preference); {@code null} = unmanaged. Client-side only (Animatium feature). */
    public final @Nullable Boolean disableEntityPush;
    /** When {@code true}, 1.8 block placement: no placing a block against an air cell (kills the creative "quick replace" floating block). Enforced both client-side (Animatium {@code OLD_PLACEMENT}) and server-side ({@code CompatPlacement}, any client); {@code null} = unmanaged. */
    public final @Nullable Boolean oldPlacement;
    /** When {@code true}, the modern attack cooldown + crosshair indicator is removed (huge {@code ATTACK_SPEED} so hits are always full, 1.8-style); {@code null} = unmanaged. Server-side (attribute), works for any client. */
    public final @Nullable Boolean removeAttackCooldown;
    /** Explicit set of Animatium features to send (overrides the knob-derived set); {@code null} = derive from the knobs above. */
    public final @Nullable Set<AnimatiumFeature> animatiumFeatures;

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
        legacyFluids = b.legacyFluids;
        disableElytraFlight = b.disableElytraFlight;
        oldFlight = b.oldFlight;
        leftClickItemUsage = b.leftClickItemUsage;
        disableAutoSneak = b.disableAutoSneak;
        oldPhysics = b.oldPhysics;
        oldMomentum = b.oldMomentum;
        disableBedBounce = b.disableBedBounce;
        disableHoneyPhysics = b.disableHoneyPhysics;
        disableBubbleColumn = b.disableBubbleColumn;
        disableEntityPush = b.disableEntityPush;
        oldPlacement = b.oldPlacement;
        removeAttackCooldown = b.removeAttackCooldown;
        animatiumFeatures = b.animatiumFeatures;
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
        private @Nullable Boolean legacyFluids;
        private @Nullable Boolean disableElytraFlight;
        private @Nullable Boolean oldFlight;
        private @Nullable Boolean leftClickItemUsage;
        private @Nullable Boolean disableAutoSneak;
        private @Nullable Boolean oldPhysics;
        private @Nullable Boolean oldMomentum;
        private @Nullable Boolean disableBedBounce;
        private @Nullable Boolean disableHoneyPhysics;
        private @Nullable Boolean disableBubbleColumn;
        private @Nullable Boolean disableEntityPush;
        private @Nullable Boolean oldPlacement;
        private @Nullable Boolean removeAttackCooldown;
        private @Nullable Set<AnimatiumFeature> animatiumFeatures;

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
            legacyFluids = c.legacyFluids;
            disableElytraFlight = c.disableElytraFlight;
            oldFlight = c.oldFlight;
            leftClickItemUsage = c.leftClickItemUsage;
            disableAutoSneak = c.disableAutoSneak;
            oldPhysics = c.oldPhysics;
            oldMomentum = c.oldMomentum;
            disableBedBounce = c.disableBedBounce;
            disableHoneyPhysics = c.disableHoneyPhysics;
            disableBubbleColumn = c.disableBubbleColumn;
            disableEntityPush = c.disableEntityPush;
            oldPlacement = c.oldPlacement;
            removeAttackCooldown = c.removeAttackCooldown;
            animatiumFeatures = c.animatiumFeatures;
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
        /** Give Animatium clients 1.8 water/lava movement (client-side; no effect on non-Animatium clients). */
        public Builder legacyFluids(@Nullable Boolean v) { legacyFluids = v; return this; }
        /** Prevent Animatium clients from elytra-gliding (1.8 has no elytra; client-side, no effect on non-Animatium clients). */
        public Builder disableElytraFlight(@Nullable Boolean v) { disableElytraFlight = v; return this; }
        /** Give Animatium clients 1.8 creative/spectator flight (sneaking while flying slows horizontal; client-side, no effect on non-Animatium clients). */
        public Builder oldFlight(@Nullable Boolean v) { oldFlight = v; return this; }
        /** Let Animatium clients start using an item while mining a block (1.8 parity; client-side, no effect on non-Animatium clients). */
        public Builder leftClickItemUsage(@Nullable Boolean v) { leftClickItemUsage = v; return this; }
        /** Stop Animatium clients auto-crouching to fit under a low ceiling (1.8 sneak is shift-only; client-side, no effect on non-Animatium clients). */
        public Builder disableAutoSneak(@Nullable Boolean v) { disableAutoSneak = v; return this; }
        /** Bundle: enable all four 1.8 physics aspects (momentum, bed bounce, honey, bubble columns) for Animatium clients; each {@code old*}/{@code disable*} knob below overrides it. Client-side, no effect on non-Animatium clients. */
        public Builder oldPhysics(@Nullable Boolean v) { oldPhysics = v; return this; }
        /** Override the 1.8 parkour momentum threshold aspect ({@code null} follows {@link #oldPhysics}). Client-side, no effect on non-Animatium clients. */
        public Builder oldMomentum(@Nullable Boolean v) { oldMomentum = v; return this; }
        /** Override the no-bed-bounce aspect ({@code null} follows {@link #oldPhysics}). Client-side, no effect on non-Animatium clients. */
        public Builder disableBedBounce(@Nullable Boolean v) { disableBedBounce = v; return this; }
        /** Override the honey-acts-like-a-plain-block aspect ({@code null} follows {@link #oldPhysics}). Client-side, no effect on non-Animatium clients. */
        public Builder disableHoneyPhysics(@Nullable Boolean v) { disableHoneyPhysics = v; return this; }
        /** Override the no-bubble-column-push aspect ({@code null} follows {@link #oldPhysics}). Client-side, no effect on non-Animatium clients. */
        public Builder disableBubbleColumn(@Nullable Boolean v) { disableBubbleColumn = v; return this; }
        /** Stop Animatium clients being shoved by entity collision (client-side; pair with a server-side push disable for full effect). */
        public Builder disableEntityPush(@Nullable Boolean v) { disableEntityPush = v; return this; }
        /** Give Animatium clients 1.8 block placement (a place won't refill a spot just broken - no same-tick floating block; client-side, no effect on non-Animatium clients). */
        public Builder oldPlacement(@Nullable Boolean v) { oldPlacement = v; return this; }
        /** Remove the modern attack cooldown + crosshair indicator (huge {@code ATTACK_SPEED}; 1.8-style full hits). Server-side, any client. */
        public Builder removeAttackCooldown(@Nullable Boolean v) { removeAttackCooldown = v; return this; }
        /** Override the Animatium feature set sent to Animatium clients ({@code null} = derive from the knobs above). Defensively copied. */
        public Builder animatiumFeatures(@Nullable Set<AnimatiumFeature> v) { animatiumFeatures = v != null ? Set.copyOf(v) : null; return this; }
        /** Convenience: send exactly these Animatium features (overriding the knob-derived set). */
        public Builder animatiumFeatures(AnimatiumFeature... features) { animatiumFeatures = Set.of(features); return this; }

        public CompatConfig build() { return new CompatConfig(this); }
    }
}
