package io.github.term4.minestommechanics.presets.mmc18;

import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfig;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfigResolver.KnockbackContext;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackSystem;
import io.github.term4.minestommechanics.tracking.SprintTracker;
import io.github.term4.minestommechanics.tracking.motion.VelocityConfig;
import io.github.term4.minestommechanics.util.Directions;
import io.github.term4.minestommechanics.util.tick.TickScaler;
import io.github.term4.minestommechanics.presets.vanilla18.Vanilla18;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * mmc18 melee knockback, reverse-engineered from MineMen captures. Horizontal magnitude is the product
 * {@code H = B(L) * axial * scale(d)} - the vanilla base pipeline supplies only the direction; see {@link #baseStrength},
 * {@link #axialFactor}, {@link #rangeReduction}. Vertical is the launch-hold then decay ({@link #verticalLaunchHold}).
 *
 * <p>The 1.8 per-axis +-3.9 wire cap is NOT applied here: it lives in {@code LegacyVelocity} (gated by {@code quantizeVelocity}).
 * The vertical fold reads {@code ctx.victimVelocity()} - the velocity rule inherited from {@code Vanilla18}.
 */
public final class Knockback {

    private Knockback() {}

    /** Recent-sprint window (ticks): gates the sprint bonus/enchant (attacker) and the axial factor (victim). */
    static final int SPRINT_BUFFER = 5;

    public static KnockbackConfig melee() {
        return KnockbackConfig.builder(Vanilla18.knockback())
                .sprintBuffer(SPRINT_BUFFER)
                .horizontal(Knockback::baseStrength)
                .extraHorizontal(0.0)
                .vertical(VERTICAL_BASE)
                .extraVertical(0.0)
                .verticalBounds(0.050, VERTICAL_CAP)
                .yawWeight(0.5)
                .frictionH(0.0)
                .frictionV(VERTICAL_FRIC)
                .addCustomComponent(Knockback::verticalLaunchHold)
                .addCustomComponent(Knockback::axialFactor)
                .addCustomComponent(Knockback::rangeReduction)
                .build();
    }

    /**
     * MineMen projectile hit (bow/snowball/egg/pearl): the non-sprint melee profile - constant {@code B} horizontal (a
     * sprinting shooter gets no bonus), same vertical model - with Punch at +0.6/level (measured: 0.5274 -> 1.1274 -> 1.7274)
     * summed along the base's shooter-relative direction. Self-hit direction pending capture (bow = pure yaw; snowball/rod TBD).
     */
    public static KnockbackConfig projectile() {
        return projectileBase()
                .addCustomComponent(Knockback::selfHitBlend)
                .build();
    }

    /** The bow's arrows: same non-sprint profile, but a SELF shot pushes 100% along the live look ({@link #selfHitLook}). */
    public static KnockbackConfig arrow() {
        return projectileBase()
                .addCustomComponent(Knockback::selfHitLook)
                .build();
    }

    private static KnockbackConfig.Builder projectileBase() {
        return KnockbackConfig.builder(melee())
                .horizontal(HORIZONTAL_BASE)
                .extraHorizontal(0.6)
                .extraYawWeight(0.0)
                .yawWeight(0.0);
    }

    /**
     * MineMen rod hit, SHOOTER-relative (like vanilla - 1.8 {@code damageEntity} reads the indirect source, the angler):
     * the old lib's tuned values - H 0.525, V 0.365 capped 0.45 - on the vanilla 1.8 fold. Pending capture confirmation.
     */
    public static KnockbackConfig rod() {
        return KnockbackConfig.builder(Vanilla18.knockback())
                .horizontal(0.525)
                .vertical(0.365)
                .verticalBounds(null, 0.45)
                .addCustomComponent(Knockback::selfHitBlend)
                .build();
    }

    /**
     * The hurt-KB an explosion folds before its radial push: the melee profile ({@code B} horizontal, same vertical),
     * direction purely positional away from the damager entity (the fireball/TNT at detonation). The melee-gated
     * components self-disable on the non-melee snapshot.
     */
    public static KnockbackConfig explosionHurt() {
        return KnockbackConfig.builder(melee())
                .yawWeight(0.0)
                .addCustomComponent(Knockback::pointBlankFallback)
                .build();
    }

    // horizontal strength
    static final double HORIZONTAL_BASE = 0.5274;
    private static final double SPRINT_BONUS = 0.3271;
    private static final double ENCHANT_PER_LEVEL = 0.37979;

    /**
     * {@code B(L)} = base + sprint bonus + enchant, carried on the {@code horizontal} (x1) field so the sum stays intact -
     * {@code extraHorizontal x extraLevel} is uniform per level, so it couldn't give the sprint its own strength. Sprint
     * bonus and enchant are both gated on a sprinting attacker.
     */
    private static double baseStrength(KnockbackContext ctx) {
        Entity attacker = ctx.snap().source();
        double b = HORIZONTAL_BASE;
        if (attacker != null && recentlySprinting(ctx, attacker)) b += SPRINT_BONUS + ENCHANT_PER_LEVEL * ctx.snap().extraKnockback();
        return b;
    }

    // axial
    private static final double AXIAL = 1.0 / 9.0;

    /**
     * Scales horizontal by {@code 1 -/+ 1/9} from the victim's facing vs the attacker's: fleeing (same way) -> 8/9,
     * charging (toward) -> 10/9. The sprint gate means the victim is moving along their yaw, so yaw stands in for the
     * motion direction - no reconstructed velocity (MineMen tracker / fluid physics) needed. Gated on a sprinting
     * attacker AND the victim's CLIENT-side sprint buffer.
     */
    @Nullable
    private static Vec axialFactor(KnockbackContext ctx, Vec kb) {
        var snap = ctx.snap();
        Entity attacker = snap.source();
        Entity target = snap.target();
        if (attacker == null || target == null || !snap.melee()) return null;
        if (!recentlySprinting(ctx, attacker) || !clientRecentlySprinting(ctx, target)) return null;
        Vec victimFacing = Directions.fromYaw(target.getPosition().yaw());
        Vec attackerFacing = Directions.fromYaw(attacker.getPosition().yaw());
        double proj = victimFacing.x() * attackerFacing.x() + victimFacing.z() * attackerFacing.z();   // cos(victim yaw - attacker yaw)
        /*
        push-direction variant (needs the reconstructed victim velocity):
        Vec vel = ctx.victimVelocity();
        double proj = vel.x() * kb.x() + vel.z() * kb.z();
         */
        if (proj == 0) return null;
        double a = proj > 0 ? 1.0 - AXIAL : 1.0 + AXIAL;
        return new Vec(kb.x() * a, kb.y(), kb.z() * a);
    }

    // range reduction
    private static final double RANGE_START = 3.03;
    private static final double RANGE_SLOPE = 0.47;
    private static final double RANGE_FLOOR = 2.0 / 3.0;

    /**
     * Scales horizontal by {@code min(1, 1 - 0.47*(d - 3.03))}, then floors the result at {@code 2/3 * B(L)}, only for a
     * recently-sprinting attacker. The floor is on the FINAL magnitude and independent of the axial factor - fleeing and
     * stationary victims both bottom out at 2/3*B at range.
     */
    @Nullable
    private static Vec rangeReduction(KnockbackContext ctx, Vec kb) {
        var snap = ctx.snap();
        Entity attacker = snap.source();
        Entity target = snap.target();
        if (attacker == null || target == null || !snap.melee() || !recentlySprinting(ctx, attacker)) return null;
        var a = attacker.getPosition();
        var t = target.getPosition();
        double dist = Math.hypot(t.x() - a.x(), t.z() - a.z());
        double scale = Math.min(1.0, 1.0 - RANGE_SLOPE * (dist - RANGE_START));
        double floor = RANGE_FLOOR * baseStrength(ctx);
        double preH = Math.hypot(kb.x(), kb.z());
        double postH = Math.max(floor, preH * scale);
        if (postH >= preH) return null;
        double s = postH / preH;
        return new Vec(kb.x() * s, kb.y(), kb.z() * s);
    }

    /** Normal (server-state) recent sprint - the attacker gate for the sprint bonus, enchant, and axial. A queued
     *  {@link Attack} flush on a projectile-hit victim reads non-sprinting (measured: it lands at the plain base). */
    private static boolean recentlySprinting(KnockbackContext ctx, Entity e) {
        if (Attack.flushingNonSprint(ctx.snap().target())) return false;
        return SprintTracker.wasRecentlySprinting(ctx.services().sprintTracker(),
                e, TickScaler.duration(SPRINT_BUFFER, KnockbackSystem.KEY));
    }

    // MineMen's fixed degenerate-direction diagonal (yaw 135): the position stand-in whenever the real relative
    // position is meaningless - point-blank explosions AND projectile self-hits
    private static final Vec DEGENERATE_DIAGONAL = new Vec(-1, 0, -1).normalize();

    /**
     * MineMen's degenerate-direction fallback: damager horizontally on top of the victim (every self-fireball; vanilla's
     * {@code d0*d0+d1*d1 < 1e-4} threshold) -> the fixed world diagonal at full {@code B}, replacing vanilla's random pick.
     * Verified yaw/position-independent, always (-2983,-2983) shorts.
     */
    @Nullable
    private static Vec pointBlankFallback(KnockbackContext ctx, Vec kb) {
        var snap = ctx.snap();
        Entity source = snap.source();
        Entity target = snap.target();
        if (source == null || target == null) return null;
        double dx = target.getPosition().x() - source.getPosition().x();
        double dz = target.getPosition().z() - source.getPosition().z();
        if (dx * dx + dz * dz >= 1.0e-4) return null;
        return new Vec(DEGENERATE_DIAGONAL.x() * HORIZONTAL_BASE, kb.y(), DEGENERATE_DIAGONAL.z() * HORIZONTAL_BASE);
    }

    /** Self-hit strength: base + Punch's 0.6/level. Priced inside the self components because the pipeline folds the
     *  extra BEFORE components run - the horizontal replace would wipe it (and its direction is degenerate on self). */
    private static double selfStrength(KnockbackContext ctx) {
        return HORIZONTAL_BASE + 0.6 * ctx.snap().extraKnockback();
    }

    /**
     * Projectile SELF-hit horizontal: {@code (B/2)(diagonal + look)} - the melee 50/50 position+look blend with the
     * position half falling back to the fixed diagonal (attacker == victim). Capture-solved (snowball+rod selfhit
     * sessions 2026-07-17, 30 hits): kbYaw = the bisector of yaw-135 and the live look, |h| = B*cos(delta/2) to +-0.002,
     * vertical stays the melee cap.
     */
    @Nullable
    private static Vec selfHitBlend(KnockbackContext ctx, Vec kb) {
        var snap = ctx.snap();
        Entity source = snap.source();
        if (source == null || source != snap.target()) return null;
        Vec look = Directions.fromYaw(source.getPosition().yaw());
        double h = selfStrength(ctx) / 2;
        return new Vec((DEGENERATE_DIAGONAL.x() + look.x()) * h, kb.y(), (DEGENERATE_DIAGONAL.z() + look.z()) * h);
    }

    /** Bow SELF-hit horizontal: the full strength 100% along the live look (user-verified on MineMen). */
    @Nullable
    private static Vec selfHitLook(KnockbackContext ctx, Vec kb) {
        var snap = ctx.snap();
        Entity source = snap.source();
        if (source == null || source != snap.target()) return null;
        Vec look = Directions.fromYaw(source.getPosition().yaw());
        double h = selfStrength(ctx);
        return new Vec(look.x() * h, kb.y(), look.z() * h);
    }

    /** Victim's CLIENT-side sprint buffer (what the client reports, even if the server disagrees) - the victim gate for axial. */
    private static boolean clientRecentlySprinting(KnockbackContext ctx, Entity e) {
        return SprintTracker.wasClientRecentlySprinting(ctx.services().sprintTracker(),
                e, TickScaler.duration(SPRINT_BUFFER, KnockbackSystem.KEY));
    }

    private static final int VERTICAL_DECAY_N = 7;
    private static final double VERTICAL_CAP = 0.3614;
    private static final double VERTICAL_BASE = VERTICAL_CAP + VelocityConfig.GRAVITY / VERTICAL_DECAY_N;  // 0.3728286
    private static final double VERTICAL_FRIC = VERTICAL_DECAY_N * VelocityConfig.DRAG_V;                  // 6.86
    private static final double VERTICAL_HOLD_RELEASE = -VelocityConfig.JUMP_VELOCITY;                     // -0.42

    /** Pins vertical to the cap while the victim's reconstructed VY is still above the release threshold (launch arc); the config's {@code frictionV} handles the decay below it. */
    @Nullable
    private static Vec verticalLaunchHold(KnockbackContext ctx, Vec kb) {
        return ctx.victimVelocity().y() <= VERTICAL_HOLD_RELEASE ? null : new Vec(kb.x(), VERTICAL_CAP, kb.z());
    }
}
