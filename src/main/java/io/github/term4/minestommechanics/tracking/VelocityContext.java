package io.github.term4.minestommechanics.tracking;

import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Rich, public face that {@link VelocityRule} implementations compose - the velocity analog of
 * {@code AttackEvent} for {@code CriticalRule}. Exposes position-delta motion, air-time, the launch
 * latch, launch-stamp info, and the vanilla physics constants, so a rule written outside the library
 * has the same primitives the built-ins use.
 *
 * <p>Lives in the tracking package and delegates to {@link GroundTracker} (the single tracking
 * authority) while remaining the only public surface a rule needs.
 */
public final class VelocityContext {

    // Vanilla physics constants (single canonical home; tracker + rules both read these).
    /**
     * Base jump velocity (vanilla {@code bF()}/{@code bE()} = 0.42). TODO: vanilla {@code bF()} also adds the
     * Jump Boost potion ({@code (amplifier + 1) * 0.1}) on top of this base (and later versions fold in a
     * jump-strength attribute); we model neither yet, so a launch under Jump Boost will under-read the arc.
     */
    public static final double JUMP_Y = 0.42;
    public static final double GRAVITY_PER_TICK = 0.08;
    public static final double GRAVITY_SCALE = 0.98;
    public static final double TERMINAL_VY = -3.92;
    /** In-air horizontal friction per tick (vanilla {@code motX *= 0.91} while airborne). */
    public static final double AIR_FRICTION_H = 0.91;
    /** Default block slipperiness (vanilla {@code Block.frictionFactor}); folds into ground friction. */
    public static final double DEFAULT_SLIPPERINESS = 0.6;
    /**
     * On-ground horizontal friction per tick = block slipperiness x {@link #AIR_FRICTION_H} (~0.546). This is
     * what bleeds the server-side {@code this.motX/motZ} residual while a player is grounded between hops, so a
     * fresh jump from rest seeds ~0.2 while a continuous bunny-hop builds the residual up toward its ~0.0478
     * steady state ({@code 0.2 / (1 - 0.91^11 * 0.546)} -&gt; takeoff ~0.248).
     */
    public static final double GROUND_FRICTION_H = DEFAULT_SLIPPERINESS * AIR_FRICTION_H;
    /**
     * Vanilla {@code m()} near-zero velocity clamp: it zeroes each of {@code motX/motY/motZ} when its
     * magnitude is below this (0.005) every tick, so a decaying velocity snaps to rest instead of trailing
     * dust ({@code 0.00748 -> 0} rather than {@code 0.004 -> 0.0004 -> ...}). Used as the default near-zero
     * clamp for the maintained motX residual and the gravity-arc velocity rules; a rule may pick its own.
     */
    public static final double NEAR_ZERO_CLAMP = 0.005;
    /** Bare {@code bF()} sprint-jump horizontal boost (vanilla constant) injected once per launch. */
    static final double SPRINT_JUMP_HORIZONTAL = 0.2;
    /**
     * Integer air-tick correction aligning our air-clock to the air-tick vanilla actually folds a knockback at.
     * Fixed at {@code -2}: vanilla's server-side {@code this.motY} arc lags the victim's true (client) air-time
     * by two ticks, so a hit we clock at {@code ticksInAir = T} must fold what vanilla folds at {@code T - 2}.
     * Calibrated 1:1 against a local vanilla 1.8 server.
     *
     * <p>Why two: the client runs ~2 ticks ahead of the server, so vanilla only starts ageing {@code this.motY}
     * once the rising packet lands - by then the client is ~2 air-ticks in, so an attack folds
     * {@code vy(trueAirTick - 2)}. Proof: re-hit a launched victim the instant 10-tick hurt-invuln expires (true
     * air-time = 10) and vanilla folds {@code vy(8)}, while our {@code ticksInAir} reads 10 (it tracks the
     * client) - so {@code -2} reproduces vanilla's stale fold. Server-side {@code kbAirTick} stamps share that
     * lag and so cannot reveal it; only the client-side count can.
     *
     * <p>Also the air-tick basis the launched horizontal residual decays on, so it bleeds in step with the arc.
     */
    public static final int VANILLA_LAUNCH_OFFSET = -2;

    /**
     * Launch origin: facing yaw, whether sprinting at takeoff, and {@code seedH} - the actual horizontal
     * {@code this.motX/motZ} (blocks/tick) at the launch tick, i.e. the bled-over ground residual plus the
     * {@code bF()} boost. The gravity arc seeds from {@code seedH} and bleeds it by air friction, so a hit's
     * horizontal fold matches whatever the player really took off with (0.2 from rest, ~0.248 mid-bunny-hop).
     */
    public record JumpInfo(double yaw, boolean sprinting, Vec seedH) {}

    private final Entity entity;

    private VelocityContext(Entity entity) { this.entity = entity; }

    public static VelocityContext of(Entity entity) { return new VelocityContext(entity); }

    public Entity entity() { return entity; }

    /** Position-delta velocity (blocks/tick); players via the per-tick snapshot, others via entity velocity. */
    public Vec positionDelta() { return GroundTracker.positionDelta(entity); }

    /** Ticks since the entity left the ground (0 on ground). */
    public int ticksInAir() { return GroundTracker.ticksInAir(entity); }

    /** Left the ground rising (jump or knockback boost) and still in that launch arc. */
    public boolean launched() { return GroundTracker.launched(entity); }

    /** Launch origin (yaw + sprint) while in a launch arc, or {@code null}. */
    public @Nullable JumpInfo recentJump() { return GroundTracker.recentJump(entity); }

    /**
     * Sprint-jump horizontal impulse for a facing yaw (blocks/tick) - the bare {@code bF()} boost
     * ({@code motX -= sin(f)*0.2}, {@code motZ += cos(f)*0.2}). NOTE: vanilla uses a float angle
     * ({@code f = yaw * 0.017453292F}) and the {@code MathHelper} sin/cos lookup table, so its components carry
     * ~1e-5 quantization vs this double-precision {@link Math#sin}/{@link Math#cos}. That is below knockback
     * velocity quantization, so it is not matched bit-exactly (TODO if exact float-table parity is ever needed).
     */
    public static Vec sprintJumpImpulse(double yaw) {
        double r = Math.toRadians(yaw);
        return new Vec(-Math.sin(r) * SPRINT_JUMP_HORIZONTAL, 0, Math.cos(r) * SPRINT_JUMP_HORIZONTAL);
    }

}
