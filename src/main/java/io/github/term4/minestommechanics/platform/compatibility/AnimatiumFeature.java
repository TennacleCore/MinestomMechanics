package io.github.term4.minestommechanics.platform.compatibility;

/**
 * A 1.8-behaviour feature an Animatium client can apply <em>natively</em> (client-side), mirroring Animatium's
 * {@code ServerFeature} enum - the {@link #bit} is the bit index in the {@code animatium:set_server_features} {@link
 * java.util.BitSet} payload (the wire contract; keep in sync with Animatium). The server sets a player's features and the
 * client clears+applies them, so for those features we can skip our server-side hacks (see {@link CompatAnimatium}).
 *
 * <p>{@link #ALL} (bit 0) enables every feature at once. The mapped subset we drive from {@link CompatConfig}:
 * {@link #PICK_INFLATION} (1.8 attack box) = our {@code attackHitboxMargin}; {@link #OLD_SNEAK_HEIGHT} (1.8 sneak eye)
 * = our {@code legacyHitbox}; {@link #FIX_SPRINT_ITEM_USE}/{@link #FIX_SPRINT_SNEAKING} (no sprint while using/sneaking)
 * = our {@code restrictSprintUse}/{@code restrictSprintSneak}; {@link #DISABLE_SWIM_POSE}/{@link #DISABLE_CRAWL_POSE}/
 * {@link #DISABLE_ELYTRA_POSE} (force {@code STANDING} client-side) = our {@code disabledPoses}. The rest are listed for
 * completeness so a server can request them explicitly via {@code CompatConfig.animatiumFeatures}.
 *
 * <p>Bits {@code 0..9} are stock Animatium; {@code 10..12} require the forked pose-disable feature. Sending an unknown bit
 * to a stock client is harmless - its reader skips ids it doesn't recognise.
 */
public enum AnimatiumFeature {
    ALL(0),
    MISS_PENALTY(1),
    LEFT_CLICK_ITEM_USAGE(2),
    MINING_ITEM_USAGE(3),
    HIDE_ROD_BOBBER(4),
    PICK_INFLATION(5),
    OLD_SNEAK_HEIGHT(6),
    CLIENTSIDE_ENTITIES(7),
    FIX_SPRINT_ITEM_USE(8),
    FIX_SPRINT_SNEAKING(9),
    // --- forked pose-disable feature (Track 2): force STANDING client-side so an Animatium client never enters the pose ---
    DISABLE_SWIM_POSE(10),   // suppress the swim STATE client-side (Pose.SWIMMING via isSwimming + the modern swim physics/crouch-block)
    DISABLE_CRAWL_POSE(11),  // squeeze-to-fit crawl (Pose.SWIMMING via the fit fallback) - reserved; not yet wired (collision concern)
    DISABLE_ELYTRA_POSE(12), // elytra (Pose.FALL_FLYING)
    // --- forked legacy-fluids feature: 1.8 water/lava movement (drag/gravity, no swim-sprint/buoyancy, no lava current) ---
    OLD_FLUID_PHYSICS(13),
    // --- forked: prevent elytra gliding entirely (1.8 has no elytra) ---
    DISABLE_ELYTRA_FLIGHT(14),
    // --- forked: 1.8 creative/spectator flight (sneaking while flying slows horizontal) ---
    OLD_FLIGHT(15),
    // --- forked: hide the offhand HUD slot + block the swap key (1.8 has no offhand; server CompatOffhand stays authoritative) ---
    DISABLE_OFFHAND(16),
    // --- forked: disable the modern auto-crouch-to-fit (1.8 sneak is shift-only; no involuntary fit-crouch under a low ceiling) ---
    DISABLE_AUTO_SNEAK(17),
    // --- forked: 1.8 parkour momentum threshold (0.005 per-axis instead of the modern combined 0.003) ---
    OLD_MOMENTUM(18),
    // --- forked: stop the local player being shoved by entity collision (NOT 1.8 parity - a server preference) ---
    DISABLE_ENTITY_PUSH(19),
    // --- forked: 1.8 block-placement rules (don't let a place refill a spot the player just broke - no same-tick floating block) ---
    OLD_PLACEMENT(20),
    // --- forked: 1.8 block physics, split from the former OLD_PHYSICS bundle (driven by CompatConfig.oldPhysics + per-aspect knobs) ---
    DISABLE_BED_BOUNCE(21),     // 1.8 beds don't bounce
    DISABLE_HONEY_PHYSICS(22),  // honey acts like a plain block (no slide, no jump/walk slowdown)
    DISABLE_BUBBLE_COLUMN(23),  // no bubble-column push
    // --- forked + WIRE-FORMAT: decode SET_ENTITY_MOTION as byte-exact 1.8 shorts instead of lossy LpVec3. Unlike the toggles
    // above, sending this to a client that can't decode it corrupts the stream - the server must confirm native support first ---
    SHORTS_VELOCITY(24);

    /** Bit index in the {@code set_server_features} BitSet payload (= Animatium's {@code ServerFeature} id). */
    public final int bit;

    AnimatiumFeature(int bit) { this.bit = bit; }
}
