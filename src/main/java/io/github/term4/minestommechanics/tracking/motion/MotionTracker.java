package io.github.term4.minestommechanics.tracking.motion;

import io.github.term4.minestommechanics.MechanicsProfiles;
import io.github.term4.minestommechanics.tracking.Tracker;
import io.github.term4.minestommechanics.util.BlockContact;
import io.github.term4.minestommechanics.util.TickClock;
import net.minestom.server.MinecraftServer;
import net.minestom.server.ServerFlag;
import net.minestom.server.collision.Aerodynamics;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.collision.CollisionUtils;
import net.minestom.server.collision.PhysicsResult;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.vehicle.PlayerInputs;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.registry.RegistryTag;
import net.minestom.server.registry.TagKey;
import net.minestom.server.tag.Tag;
import net.minestom.server.timer.TaskSchedule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.OptionalDouble;
import java.util.function.Predicate;

/**
 * Single authority for an entity's ground/air timeline and server-tracked motion (the replica of vanilla
 * {@code motX/motY/motZ}). A {@link PlayerMoveEvent} listener anchors the air clock to the client's jump packet and
 * records each move-delta; a per-tick task forward-simulates the vertical mot + the entity-push/flow residuals. These
 * back the reads {@link VelocityRule} composes: {@link #serverMotY}, {@link #horizontalMot}, {@link #entityPush},
 * {@link #flowPush}, {@link #onGround}, {@link #ticksInAir}/{@link #launched}/{@link #recentJump}, {@link #positionDelta}.
 * Physics constants are read live; only the vanilla numbers Minestom omits ({@link #SPRINT_IMPULSE}, the clamp) are fixed here.
 */
public final class MotionTracker implements Tracker {

    /** Server tick of the client's rising move-packet (its true jump tick); the gravity-arc anchor. */
    private static final Tag<Long> AIR_START_TICK = Tag.Transient("mm:air-start-tick");
    private static final Tag<Boolean> LAUNCHED = Tag.Transient("mm:launched");
    private static final Tag<Vec> MOVE_VELOCITY = Tag.Transient("mm:move-velocity");
    private static final Tag<MovePrev> MOVE_PREV = Tag.Transient("mm:move-prev");
    private static final Tag<LaunchStamp> LAUNCH_STAMP = Tag.Transient("mm:launch-stamp");
    /**
     * Server-side horizontal {@code motX/motZ} (b/t): the victim's own simulated motion (the {@code bF()} sprint-jump
     * boost), not the player's WASD and not prior knockback (vanilla restores the pre-KB velocity after broadcasting it).
     * Anchored at its last write, read lazily, bled by vanilla friction per elapsed tick. Folded into every hit.
     */
    private static final Tag<MotState> MOT_H = Tag.Transient("mm:mot-h");
    /**
     * Server-side {@code Entity.collide} push residual: what {@code motX/motZ} carries from overlapping entities
     * (1.8 {@code bL()}, a {@code 0.05} separation impulse per overlapping tick). Gated by {@link VelocityConfig#entityPush()}.
     */
    private static final Tag<Vec> ENTITY_PUSH = Tag.Transient("mm:entity-push");

    /**
     * Server-side water-<b>flow</b> residual: what {@code motX/motY/motZ} carries from a current (the {@code 0.014}
     * push, 3D with the falling-water down-term in Y), bled by fluid friction. Gated by {@link VelocityConfig#flowPush()}; pure tracking.
     */
    private static final Tag<Vec> FLOW_PUSH = Tag.Transient("mm:flow-push");
    /** The player's resolved flow {@link FluidFlow.Model} this tick; read lazily by {@link #frictionPerTick} for the in-water horizontal friction (e.g. the 26 sprint-swim 0.9). */
    private static final Tag<FluidFlow.Model> FLOW_MODEL = Tag.Transient("mm:flow-model");

    /**
     * Vanilla {@code motY}, forward-simulated per tick like {@code EntityLiving.m()}: near-zero clamp, then {@code move()}
     * (a collision probe of the descent; landing zeroes motY), then gravity. Seeded {@code 0.42} by {@link #latchLaunch}.
     * Reproduces the emergent quirks the analytic arc can't (an early-collide re-fall at touchdown).
     */
    private static final Tag<VertSim> VERT_SIM = Tag.Transient("mm:vert-sim");

    /** Vanilla sprint-jump impulse ({@code bF()}: {@code motX -= sin(yaw)*0.2, motZ += cos(yaw)*0.2}). */
    public static final double SPRINT_IMPULSE = 0.2;
    /** Default {@link #onGround(Entity, int)} prediction depth: the vanilla {@code move()} 1-tick collision sweep. */
    public static final int DEFAULT_GROUND_TICKS = 1;
    /** Default block friction when the supporting block cannot be read (vanilla {@code 0.6}). */
    private static final double DEFAULT_BLOCK_FRICTION = 0.6;

    /** Movement environment at the player's box - the extra physics vanilla applies beyond ground/air. Detected per tick ({@link #environmentOf}), held in {@link #ENV}; {@code NORMAL} = none. */
    private enum Env { NORMAL, WATER, LAVA, WEB, LADDER, BUBBLE, HONEY }

    /** Current movement environment (absent = {@link Env#NORMAL}); updated each tick, read by the residual friction. */
    private static final Tag<Env> ENV = Tag.Transient("mm:env");

    // water friction + gravity are per-model (FluidFlow.Model); ladder climb-up + climbable detection are per-model (ClimbModel)
    private static final double LAVA_FRICTION = 0.5;
    private static final double WATER_VERTICAL = 0.8;
    private static final double LAVA_VERTICAL = 0.5;
    private static final double FLUID_GRAVITY = 0.02;   // lava motY gravity
    private static final double LADDER_CLAMP = 0.15;    // ladder motX/Z + fall-speed cap
    private static final double LEVITATION_BASE = 0.05; // motY target = 0.05*(level+1)
    private static final double LEVITATION_RATE = 0.2;
    private static final double SLOW_FALL_GRAVITY = 0.01;
    // 26-only, gated by modernBlockPhysics
    private static final double BUBBLE_UP_STEP = 0.06, BUBBLE_UP_CAP = 0.7;
    private static final double BUBBLE_DOWN_STEP = 0.03, BUBBLE_DOWN_CAP = -0.3;
    private static final double HONEY_SLIDE = -0.1274;  // wall-slide floor
    private static final double FLUID_SWIM_STEP = 0.04; // jump/sneak in a fluid
    private static final double LIQUID_EDGE_BUMP = 0.3; // swim into a wall with headroom
    private static final double BLOCK_SPEED_FACTOR = 0.4; // soul sand / honey

    /** TEMP velocity-parity diagnosis: per-tick log of the reconstructed deltaMovement, to diff vs an instrumented MC26 Paper server. */
    private static final boolean DEBUG_VEL = false;
    private static final double FLOW_IMPULSE = 0.014;   // water current scale
    private static final double LAVA_FLOW_IMPULSE = 0.0023333333333333335; // 26 overworld lava current scale

    private static final Predicate<Block> IS_WATER = b -> b.compare(Block.WATER);
    private static final Predicate<Block> IS_LAVA = b -> b.compare(Block.LAVA);
    /** Cobweb - the {@code makeStuckInBlock} block both 1.8 and 26 zero the tracked velocity for (1.8 {@code Entity.move} / 26 {@code setDeltaMovement(ZERO)}). */
    private static final Predicate<Block> IS_WEB = b -> b.compare(Block.COBWEB);
    /** The full 26 {@code makeStuckInBlock} family (cobweb + sweet-berry-bush + powder-snow): all zero the tracked velocity like a cobweb. Sweet-berry/powder-snow are 26-only (MODERN gate). */
    private static final Predicate<Block> IS_WEB_MODERN = b -> b.compare(Block.COBWEB)
            || b.compare(Block.SWEET_BERRY_BUSH) || b.compare(Block.POWDER_SNOW);
    private static final Predicate<Block> IS_HONEY = b -> b.compare(Block.HONEY_BLOCK);

    /** Vanilla {@code minecraft:beds} block tag (bounce family) for the {@link #landMotY} bounce; {@code null} if absent (then a {@code _bed} key-suffix fallback). */
    private static final RegistryTag<Block> BEDS = Block.staticRegistry().getTag(TagKey.ofHash("#minecraft:beds"));

    /** Previous move-packet position + ground flag: packet-granular transition detection + move-delta velocity. */
    private record MovePrev(double x, double y, double z, boolean onGround) {}
    /** Launch origin: server tick, facing yaw, sprinting at takeoff, pre-boost residual, and the boosted seed. */
    private record LaunchStamp(long tick, double yaw, boolean sprinting, Vec residualH, Vec seedH) {}
    /** Horizontal residual as of {@code sinceTick}, bleeding by air or ground friction per the held state. */
    private record MotState(Vec motH, long sinceTick, boolean airborne) {}

    /**
     * Ticked {@code motY} state. {@code clamped} = vanilla (0.005 apex reseed); {@code raw} = no reseed ({@code clampY(0)}
     * presets). Index {@code [0]} = latest end-of-tick; older slots back the {@code launchOffset} lookback.
     */
    private static final class VertSim {
        static final int HISTORY = 4;
        final double[] clamped = new double[HISTORY];
        final double[] raw = new double[HISTORY];
        /** Last {@code move()} probe clamped a descent - vanilla's collision {@code onGround}. */
        boolean collided;
    }

    /** Launch origin for {@link VelocityRule}s. {@code seedH} = takeoff {@code motX/motZ} ({@code residualH} + the {@link #SPRINT_IMPULSE} boost when sprinting). */
    public record JumpInfo(double yaw, boolean sprinting, Vec residualH, Vec seedH) {}

    /** Resolves each player's velocity rule once per tick to read its fluid/climb/web toggles (gate the env handling). */
    private final MechanicsProfiles profiles;

    public MotionTracker(MechanicsProfiles profiles) { this.profiles = profiles; }

    /** Starts the per-tick ground-state fallback poll (a tick behind {@link #onMove}; catches status-only onGround packets). */
    @Override
    public void start() {
        MinecraftServer.getSchedulerManager()
                .buildTask(this::tick)
                .repeat(TaskSchedule.tick(1))
                .schedule();
    }

    /** Listener anchoring the air clock + move-delta to the client's own move packets (ping-invariant). */
    @Override
    public EventNode<@NotNull PlayerEvent> node() {
        EventNode<@NotNull PlayerEvent> node = EventNode.type("mm:motion-tracker", EventFilter.PLAYER);
        node.addListener(PlayerMoveEvent.class, this::onMove);
        return node;
    }

    private void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        long now = TickClock.now();
        boolean nowOnGround = e.isOnGround();
        Pos newPos = e.getNewPosition();
        // compare vs the previous packet so transition detection is independent of event/move ordering
        MovePrev prev = p.getTag(MOVE_PREV);
        p.setTag(MOVE_PREV, new MovePrev(newPos.x(), newPos.y(), newPos.z(), nowOnGround));
        if (prev == null) return; // need a baseline first

        // keep the client's last reported motion (a per-tick getPosition() snapshot races the hit)
        p.setTag(MOVE_VELOCITY, new Vec(newPos.x() - prev.x(), newPos.y() - prev.y(), newPos.z() - prev.z()));

        boolean wasOnGround = prev.onGround();
        double dy = newPos.y() - prev.y();

        // grounded/flying: clear the arc. primary landing anchor (tick() only trails this)
        if (nowOnGround || p.isFlying()) {
            if (nowOnGround) freezeOnLanding(p, now);
            p.removeTag(AIR_START_TICK);
            p.removeTag(LAUNCHED);
            return;
        }

        if (wasOnGround) {
            // ground->air: the rising packet is the jump tick. vanilla seeds 0.42 even into a fluid, so latch unconditionally
            if (dy > 0) latchLaunch(p, now, newPos.yaw()); // rising = launched, else walk-off
            p.setTag(AIR_START_TICK, now);
        } else {
            // already airborne: keep an anchor + latch a mid-air re-launch, but not in a fluid/ladder
            if (p.getTag(AIR_START_TICK) == null) p.setTag(AIR_START_TICK, now);
            if (dy > 0 && p.getTag(ENV) == null && !Boolean.TRUE.equals(p.getTag(LAUNCHED))) latchLaunch(p, now, newPos.yaw());
        }
    }

    /** Latches a launch: folds the {@link #SPRINT_IMPULSE} boost onto the bled residual as the takeoff seed, re-anchors airborne, and seeds the sim's motY to {@code 0.42}. */
    private static void latchLaunch(Player p, long now, double yaw) {
        boolean sprinting = p.isSprinting();
        Vec residual = residualAt(p, p.getTag(MOT_H), now);
        Vec boost = sprinting ? sprintJumpImpulse(yaw) : Vec.ZERO;
        Vec seedH = residual.add(boost);
        p.setTag(MOT_H, new MotState(seedH, now, true));
        p.setTag(LAUNCHED, true);
        p.setTag(LAUNCH_STAMP, new LaunchStamp(now, yaw, sprinting, residual, seedH));
        // a rising departure seeds motY 0.42 (jumps + ground KB alike), like bF() firing after m()
        VertSim sim = p.getTag(VERT_SIM);
        if (sim != null) {
            // TODO(attributes): seed from the JUMP_STRENGTH attribute + jump boost once that package exists (keep the float-exact 0.42)
            sim.clamped[0] = VelocityConfig.JUMP_VELOCITY;
            sim.raw[0] = VelocityConfig.JUMP_VELOCITY;
            sim.collided = false;
        }
    }

    /** Vanilla {@code bF()} sprint-jump horizontal impulse for a facing yaw (b/t). */
    private static Vec sprintJumpImpulse(double yaw) {
        double r = Math.toRadians(yaw);
        return new Vec(-Math.sin(r) * SPRINT_IMPULSE, 0, Math.cos(r) * SPRINT_IMPULSE);
    }

    /** The anchored residual bled to {@code now} ({@code motH x friction^elapsed}), then the near-zero clamp so a stale residual snaps to 0. */
    private static Vec residualAt(Player p, MotState s, long now) {
        if (s == null) return Vec.ZERO;
        int ticks = (int) Math.max(0, now - s.sinceTick());
        Vec decayed = s.motH().mul(Math.pow(frictionPerTick(p, s.airborne()), ticks));
        return new Vec(clampNearZero(decayed.x()), 0, clampNearZero(decayed.z()));
    }

    /** Per-tick horizontal friction of the mot model: air drag airborne, block friction x air drag on ground (water/lava/web override). Both the {@code MOT_H} and entity-push residuals bleed by this. */
    static double frictionPerTick(Player p, boolean airborne) {
        // in a fluid the residual bleeds by the fluid's fixed friction; re-anchored on every env change so this holds over the interval
        Env env = p.getTag(ENV);
        // water friction is per-model (0.8 walk, 0.9 sprint-swim); bubble = water
        if (env == Env.WATER || env == Env.BUBBLE) {
            FluidFlow.Model m = p.getTag(FLOW_MODEL);
            return (m != null ? m : FluidFlow.Model.LEGACY).waterFriction(p.isSprinting());
        }
        if (env == Env.LAVA) return LAVA_FRICTION;
        if (env == Env.WEB) return 0.0; // web zeroes motX/Z each tick
        double hDrag = p.getAerodynamics().horizontalAirResistance();
        // soul sand / honey slow all horizontal mot, residual included
        return (airborne ? hDrag : blockFriction(p) * hDrag) * blockSpeedFactor(p);
    }

    /** Vanilla {@code getBlockSpeedFactor}: {@code 0.4} on soul sand / honey (the block under the feet), else {@code 1.0}. Both 0.4 (26 {@code Blocks}); soul sand also slows in 1.8 (value approximated). */
    private static double blockSpeedFactor(Player p) {
        Instance inst = p.getInstance();
        if (inst == null) return 1.0;
        Block below = inst.getBlock(p.getPosition().sub(0, 0.5000001, 0));
        return below.compare(Block.SOUL_SAND) || below.compare(Block.HONEY_BLOCK) ? BLOCK_SPEED_FACTOR : 1.0;
    }

    /** Friction of the block under the player (vanilla {@code frictionFactor}; what {@code PhysicsUtils} reads). */
    private static double blockFriction(Player p) {
        var instance = p.getInstance();
        if (instance == null) return DEFAULT_BLOCK_FRICTION;
        return instance.getBlock(p.getPosition().sub(0, 0.5000001, 0)).registry().friction();
    }

    /** At an air-&gt;ground transition, re-anchor the residual as grounded (bled to {@code now}) so the gap to the next jump bleeds by ground friction. No-op if already grounded. */
    private static void freezeOnLanding(Player p, long now) {
        MotState s = p.getTag(MOT_H);
        if (s == null || !s.airborne()) return;
        p.setTag(MOT_H, new MotState(residualAt(p, s, now), now, false));
    }

    private void tick() {
        for (Player p : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            // fallback, a tick behind onMove: catches status-only onGround packets (no PlayerMoveEvent)
            if (p.isOnGround()) {
                freezeOnLanding(p, TickClock.now());
                p.removeTag(AIR_START_TICK);
                p.removeTag(LAUNCHED);
            }
            // resolve the rule once; detect the env before the sim so friction/gravity match. each category gated by the rule (default on)
            VelocityRule rule = profiles.velocityFor(p);
            ClimbModel climbModel = VelocityRule.climbModel(rule);
            boolean modernBlocks = VelocityRule.modernBlockPhysicsEnabled(rule);
            FluidFlow.Model flowModel = VelocityRule.flowModel(rule);
            p.setTag(FLOW_MODEL, flowModel); // for the lazy frictionPerTick
            Env env = tickEnvironment(p, TickClock.now(), environmentOf(p,
                    VelocityRule.fluidPhysicsEnabled(rule),
                    VelocityRule.climbPhysicsEnabled(rule),
                    VelocityRule.webPhysicsEnabled(rule),
                    climbModel, modernBlocks));
            // vanilla order: clamp, move, gravity, then entity push
            tickVertSim(p, env, climbModel, modernBlocks, flowModel);
            // web zeroes everything, push included
            if (env == Env.WEB) p.removeTag(ENTITY_PUSH);
            else tickEntityPush(p);
            // flow residual; ticked even out of water so it bleeds out. simulated rules only
            if (VelocityRule.flowPushEnabled(rule))
                tickFlowPush(p, env, flowModel, VelocityRule.flowLavaEnabled(rule));
            else p.removeTag(FLOW_PUSH);
            if (DEBUG_VEL && p.getInstance() != null) {
                VertSim vs = p.getTag(VERT_SIM);
                Vec h = residualAt(p, p.getTag(MOT_H), TickClock.now()); // sprint-impulse residual
                Vec flow = p.getTag(FLOW_PUSH);                          // flow residual
                double fx = flow != null ? flow.x() : 0.0, fz = flow != null ? flow.z() : 0.0;
                double motY = vs != null ? vs.raw[0] : 0.0;
                // skip idle-on-ground spam
                if (env != Env.NORMAL || !p.isOnGround() || h.x() != 0 || h.z() != 0 || fx != 0 || fz != 0) {
                    System.out.printf(java.util.Locale.ROOT, "[VEL-MM] %s blk=%s env=%s motY=%+.5f motH=(%+.5f,%+.5f) flow=(%+.5f,%+.5f) dy=%+.5f ground=%b%n",
                            p.getUsername(), p.getInstance().getBlock(p.getPosition()).name(), env,
                            motY, h.x(), h.z(), fx, fz, positionDelta(p).y(), p.isOnGround());
                }
            }
        }
    }

    /** One vanilla {@code m()} travel step for the ticked motY: clamp, {@code move()} collide-zero, gravity - or the
     *  fluid / web / ladder vertical law when {@code env} is not {@link Env#NORMAL}. */
    private static void tickVertSim(Player p, Env env, ClimbModel climbModel, boolean modernBlocks, FluidFlow.Model flowModel) {
        VertSim sim = p.getTag(VERT_SIM);
        if (sim == null) p.setTag(VERT_SIM, sim = new VertSim());
        if (p.isFlying() || p.getInstance() == null) {
            shift(sim.clamped, 0);
            shift(sim.raw, 0);
            sim.collided = p.isOnGround();
            return;
        }
        Aerodynamics aero = p.getAerodynamics();
        double g = aero.gravity(), s = aero.verticalAirResistance();
        double c = sim.clamped[0], r = sim.raw[0];

        // web zeroes motY
        if (env == Env.WEB) { shift(sim.clamped, 0); shift(sim.raw, 0); sim.collided = false; return; }

        // vanilla order: per-env start-of-tick adjust, then move() collides (a landing zeroes motY), then the per-env step
        if (env == Env.NORMAL && Math.abs(c) < VelocityConfig.CLAMP) c = 0;
        if (env == Env.LADDER) {
            c = Math.max(-LADDER_CLAMP, c); r = Math.max(-LADDER_CLAMP, r);
            // sneak-hold: a sneaking descent clamps to 0
            if (p.isSneaking()) { if (c < 0) c = 0; if (r < 0) r = 0; }
        }
        PhysicsResult colC = c < 0 ? CollisionUtils.handlePhysics(p, new Vec(0, c, 0)) : null;
        boolean collidedC = colC != null && colC.isOnGround();
        PhysicsResult colR = r < 0 ? (r == c ? colC : CollisionUtils.handlePhysics(p, new Vec(0, r, 0))) : null;
        boolean collidedR = colR != null && colR.isOnGround();
        // a landing zeroes motY, or bounces on slime/bed
        if (collidedC) c = landMotY(p, colC, c, modernBlocks);
        if (collidedR) r = landMotY(p, colR, r, modernBlocks);
        // climb-up: a model may reset motY while ascending (positionDelta.y is the signal only)
        if (env == Env.LADDER) {
            OptionalDouble climbUp = climbModel.climbUpMotY(positionDelta(p).y());
            if (climbUp.isPresent()) { c = climbUp.getAsDouble(); r = climbUp.getAsDouble(); }
        }
        switch (env) {
            // swim input on top of the fluid drag
            case WATER -> { double wg = flowModel.waterGravity(p.isSprinting()); double k = fluidSwim(p); boolean eb = edgeBump(p); shift(sim.clamped, eb ? LIQUID_EDGE_BUMP : c * WATER_VERTICAL - wg + k); shift(sim.raw, eb ? LIQUID_EDGE_BUMP : r * WATER_VERTICAL - wg + k); }
            case LAVA  -> { double k = fluidSwim(p); boolean eb = edgeBump(p); shift(sim.clamped, eb ? LIQUID_EDGE_BUMP : c * LAVA_VERTICAL - FLUID_GRAVITY + k); shift(sim.raw, eb ? LIQUID_EDGE_BUMP : r * LAVA_VERTICAL - FLUID_GRAVITY + k); }
            // bubble: water drag, then the column drag toward its cap
            case BUBBLE -> { double wg = flowModel.waterGravity(p.isSprinting()); boolean dn = bubbleDown(p); shift(sim.clamped, bubbleStep(c * WATER_VERTICAL - wg, dn)); shift(sim.raw, bubbleStep(r * WATER_VERTICAL - wg, dn)); }
            // honey: floor a descent at the slide speed
            case HONEY -> { shift(sim.clamped, Math.max(airStep(p, c, g, s), HONEY_SLIDE)); shift(sim.raw, Math.max(airStep(p, r, g, s), HONEY_SLIDE)); }
            default    -> { shift(sim.clamped, airStep(p, c, g, s)); shift(sim.raw, airStep(p, r, g, s)); } // normal + ladder
        }
        sim.collided = collidedC;
    }

    /** Vanilla swim input in a fluid: {@code +0.04}/tick holding jump while airborne, {@code -0.04} holding sneak, read from {@link Player#inputs()}. */
    private static double fluidSwim(Player p) {
        PlayerInputs in = p.inputs();
        double up = !p.isOnGround() && in.jump() ? FLUID_SWIM_STEP : 0;
        double down = in.shift() ? FLUID_SWIM_STEP : 0;
        return up - down;
    }

    /**
     * Vanilla water/lava edge-bump (26 {@code jumpOutOfFluid}): swimming into a wall with head-room auto-jumps to motY
     * {@code 0.3}. A HEURISTIC reconstructing the {@code horizontalCollision} Minestom doesn't track (movement key held
     * + ~0 horizontal displacement + space to rise {@code 0.6}).
     */
    private static boolean edgeBump(Player p) {
        PlayerInputs in = p.inputs();
        if (!(in.forward() || in.backward() || in.left() || in.right())) return false;
        Vec d = positionDelta(p);
        if (d.x() * d.x() + d.z() * d.z() > 0.0004) return false; // displaced horizontally -> not blocked
        Instance inst = p.getInstance();
        return inst != null && !CollisionUtils.handlePhysics(p, new Vec(0, 0.6, 0)).collisionY();
    }

    /** One bubble-column vertical step: motY drags toward the column cap (26 {@code Entity.handleOnInsideBubbleColumn}) - up if soul-sand based, down if magma based. */
    private static double bubbleStep(double v, boolean down) {
        return down ? Math.max(BUBBLE_DOWN_CAP, v - BUBBLE_DOWN_STEP) : Math.min(BUBBLE_UP_CAP, v + BUBBLE_UP_STEP);
    }

    /** Whether the bubble column at the player's feet drags DOWN (magma base, {@code drag=true}) vs up (soul-sand base). */
    private static boolean bubbleDown(Player p) {
        Instance inst = p.getInstance();
        return inst != null && "true".equals(inst.getBlock(p.getPosition()).getProperty("drag"));
    }

    /**
     * Post-landing motY for a downward collision: vanilla zeroes it ({@code Block.a}), but a <b>bounce block</b>
     * inverts it ({@code bounceUp}) unless the player suppresses it by sneaking - <b>slime</b> at factor {@code 1.0}
     * (both 1.8 {@code BlockSlime} and 26 {@code SlimeBlock}) and, when {@code modernBlocks} is on, <b>bed</b> at
     * {@code 0.66} (26-only - 1.8 beds do not bounce; 26 {@code BedBlock:150}, {@code factor=1.0} for living).
     * {@code res.newPosition} is the rest position; the supporting block is under its feet.
     */
    private static double landMotY(Player p, PhysicsResult res, double motY, boolean modernBlocks) {
        if (!p.isSneaking()) {
            Instance inst = p.getInstance();
            if (inst != null) {
                Block below = inst.getBlock(res.newPosition().sub(0, 0.5000001, 0));
                if (below.compare(Block.SLIME_BLOCK)) return -motY;
                if (modernBlocks && isBed(below)) return -motY * 0.66;
            }
        }
        return 0;
    }

    /** Whether {@code b} is a bed (any colour), via the {@code #minecraft:beds} tag with a {@code _bed} key-suffix fallback. */
    private static boolean isBed(Block b) {
        return BEDS != null ? BEDS.contains(b) : b.key().value().endsWith("_bed");
    }

    /**
     * One NORMAL/LADDER vertical air step (gravity then {@code *0.98} drag), with Levitation (motY approaches
     * {@code 0.05*(level+1)} at rate {@code 0.2}) and Slow Falling (gravity capped at {@code 0.01} descending). Both
     * 26-only, applied only when the effect is present.
     */
    private static double airStep(Player p, double v, double g, double s) {
        int lev = p.getEffectLevel(PotionEffect.LEVITATION);
        if (lev >= 0) return (v + (LEVITATION_BASE * (lev + 1) - v) * LEVITATION_RATE) * s;
        double gEff = v <= 0 && p.hasEffect(PotionEffect.SLOW_FALLING) ? Math.min(g, SLOW_FALL_GRAVITY) : g;
        return (v - gEff) * s;
    }

    /** On a movement {@link Env} transition, re-anchors the residual at the boundary (web zeroes it; ladder clamps to +-{@link #LADDER_CLAMP}) so each segment bleeds by its own friction. Returns {@code env}. */
    private static Env tickEnvironment(Player p, long now, Env env) {
        Env old = envOf(p);
        if (env != old) {
            MotState s = p.getTag(MOT_H);
            if (s != null) {
                Vec frozen = residualAt(p, s, now); // bled by the OLD env friction - the ENV tag is still 'old' here
                Vec adjusted = switch (env) {
                    case WEB -> Vec.ZERO;
                    case LADDER -> new Vec(clampAbs(frozen.x(), LADDER_CLAMP), 0, clampAbs(frozen.z(), LADDER_CLAMP));
                    default -> frozen;
                };
                if (adjusted.isZero()) p.removeTag(MOT_H);
                else p.setTag(MOT_H, new MotState(adjusted, now, s.airborne()));
            }
            if (env == Env.NORMAL) p.removeTag(ENV);
            else p.setTag(ENV, env);
        }
        return env;
    }

    /** The player's current {@link Env}, defaulting to {@link Env#NORMAL} when unset. */
    private static Env envOf(Player p) {
        Env e = p.getTag(ENV);
        return e == null ? Env.NORMAL : e;
    }

    /** Movement env at the player's box, vanilla precedence WEB &gt; WATER &gt; LAVA &gt; LADDER &gt; HONEY. Each category gated by its toggle (a disabled medium falls through). Fluids/web use the cell-walk; climb is the feet block. */
    private static Env environmentOf(Player p, boolean fluidOn, boolean climbOn, boolean webOn, ClimbModel climbModel, boolean modernBlocks) {
        Instance inst = p.getInstance();
        if (inst == null) return Env.NORMAL;
        // web overrides fluid/normal; modern adds the 26-only stuck blocks
        if (webOn && BlockContact.touching(p, modernBlocks ? IS_WEB_MODERN : IS_WEB)) return Env.WEB;
        BoundingBox box = p.getBoundingBox();
        Pos pos = p.getPosition();
        // bubble column: a water variant, checked before water
        if (fluidOn && modernBlocks && inst.getBlock(pos).compare(Block.BUBBLE_COLUMN)) return Env.BUBBLE;
        if (fluidOn && BlockContact.scanCells(inst, pos, BlockContact.adjust(box, 0, -0.4, 0), BlockContact.VANILLA_INSET, IS_WATER))
            return Env.WATER;
        if (fluidOn && BlockContact.scanCells(inst, pos, BlockContact.adjust(box, -0.1, -0.4, -0.1), 0, IS_LAVA))
            return Env.LAVA;
        if (!climbOn) return Env.NORMAL;
        // climb: the feet block, no spectators. the model decides what's climbable
        if (p.getGameMode() != GameMode.SPECTATOR && climbModel.isClimbable(inst.getBlock(pos))) {
            return Env.LADDER;
        }
        // honey wall-slide, lowest precedence
        if (modernBlocks && BlockContact.touching(p, IS_HONEY)) return Env.HONEY;
        return Env.NORMAL;
    }

    /** Vanilla {@code MathHelper.a}: clamps {@code v} to {@code [-limit, limit]}. */
    private static double clampAbs(double v, double limit) {
        return Math.max(-limit, Math.min(limit, v));
    }

    /** Vanilla {@code m()} near-zero clamp: zeroes one component whose magnitude is below {@link VelocityConfig#CLAMP}. */
    private static double clampNearZero(double v) {
        return Math.abs(v) < VelocityConfig.CLAMP ? 0.0 : v;
    }

    private static void shift(double[] h, double v) {
        for (int i = h.length - 1; i > 0; i--) h[i] = h[i - 1];
        h[0] = v;
    }

    /** Whether the sim's last {@code move()} probe clamped a descent (vanilla collision {@code onGround}). Fires before a laggy client's landing packet, so fall damage ends in sync with the combat ground checks. */
    public static boolean simCollided(Entity entity) {
        if (!(entity instanceof Player p)) return false;
        VertSim sim = p.getTag(VERT_SIM);
        return sim != null && sim.collided;
    }

    /** The ticked vanilla {@code motY} (b/t), or {@code null} when no sim state exists. {@code lookback} reads that many end-of-tick values back; {@code clamped} picks the apex-reseed vs no-reseed variant. */
    public static @Nullable Double serverMotY(Entity entity, int lookback, boolean clamped) {
        if (!(entity instanceof Player p)) return null;
        VertSim sim = p.getTag(VERT_SIM);
        if (sim == null) return null;
        int i = Math.min(Math.max(lookback, 0), VertSim.HISTORY - 1);
        return clamped ? sim.clamped[i] : sim.raw[i];
    }

    // --- entity push residual ---

    /** Vanilla {@code bL()} list range: own bounding box grown {@code 0.2} per side (expand takes total size). */
    private static final double PUSH_GROW = 0.4;
    /** Vanilla {@code Entity.collide} impulse. */
    private static final double PUSH_IMPULSE = 0.05;
    /** Vanilla skips the push below this absMax horizontal distance (perfectly stacked players never separate). */
    private static final double PUSH_MIN_DIST = 0.01;
    /** Chunk-query radius around the player; generous enough for any overlapping entity's half-width. */
    private static final double PUSH_QUERY_RANGE = 3.0;

    /** Vanilla per-tick order: travel bleeds the existing residual, then {@code bL()} adds this tick's pushes raw. */
    private static void tickEntityPush(Player p) {
        Vec acc = bleedPush(p, entityPush(p)).add(pushesFor(p));
        if (acc.isZero()) p.removeTag(ENTITY_PUSH);
        else p.setTag(ENTITY_PUSH, acc);
    }

    /** One friction step + vanilla's near-zero clamp, mirroring the travel step the residual rode in vanilla. */
    private static Vec bleedPush(Player p, Vec acc) {
        if (acc.isZero()) return acc;
        Vec decayed = acc.mul(frictionPerTick(p, !p.isOnGround()));
        return new Vec(clampNearZero(decayed.x()), 0, clampNearZero(decayed.z()));
    }

    /** This tick's incoming pushes: vanilla {@code Entity.collide} from every overlapping living entity. */
    private static Vec pushesFor(Player p) {
        var instance = p.getInstance();
        if (instance == null) return Vec.ZERO;
        var range = p.getBoundingBox().expand(PUSH_GROW, 0, PUSH_GROW);

        double px = 0, pz = 0;
        for (Entity other : instance.getNearbyEntities(p.getPosition(), PUSH_QUERY_RANGE)) {
            if (other == p || !(other instanceof LivingEntity living) || living.isDead()) continue;
            if (other instanceof Player op && op.getGameMode() == GameMode.SPECTATOR) continue;
            if (!range.intersectEntity(p.getPosition(), other)) continue;

            double dx = other.getPosition().x() - p.getPosition().x();
            double dz = other.getPosition().z() - p.getPosition().z();
            // vanilla normalizes by sqrt(absMax), not euclidean
            double absMax = Math.max(Math.abs(dx), Math.abs(dz));
            if (absMax < PUSH_MIN_DIST) continue;
            double norm = Math.sqrt(absMax);
            double scale = Math.min(1.0, 1.0 / norm) / norm * PUSH_IMPULSE;

            // a non-player living pushes only every other tick
            int passes = other instanceof Player || other.getAliveTicks() % 2 != 0 ? 2 : 1;
            px -= dx * scale * passes;
            pz -= dz * scale * passes;
        }
        return new Vec(px, 0, pz);
    }

    /** The entity's current {@code Entity.collide} push residual (zero-Y, b/t), or {@link Vec#ZERO}. Folded by a {@link VelocityRule} arc when {@link VelocityConfig#entityPush()} is on. */
    public static Vec entityPush(Entity entity) {
        if (!(entity instanceof Player p)) return Vec.ZERO;
        Vec v = p.getTag(ENTITY_PUSH);
        return v == null ? Vec.ZERO : v;
    }

    // --- water flow residual ---

    /**
     * Ticks the water/lava flow residual: add-then-bleed (vanilla adds the current in {@code K()} BEFORE the travel
     * friction). A non-fluid env feeds a zero impulse so the residual bleeds out after leaving the fluid.
     */
    private static void tickFlowPush(Player p, Env env, FluidFlow.Model model, boolean lavaEnabled) {
        Vec impulse;
        if (env == Env.WATER) {
            impulse = flowImpulse(p, model, Block.WATER, FLOW_IMPULSE);
        } else if (env == Env.LAVA && model.pushesInLava() && lavaEnabled) {
            // 26 also pushes in lava
            impulse = flowImpulse(p, model, Block.LAVA, LAVA_FLOW_IMPULSE);
        } else {
            impulse = Vec.ZERO;
        }
        Vec acc = bleedFlow(p, flowPush(p).add(impulse));
        // LEGACY zeroes against a wall; modern (Hypixel) doesn't
        if (model.zeroesAgainstWall()) acc = zeroBlockedAxes(p, acc);
        if (acc.isZero()) p.removeTag(FLOW_PUSH);
        else p.setTag(FLOW_PUSH, acc);
    }

    /** This tick's raw current impulse (b/t) for {@code fluid} ({@link Block#WATER}/{@link Block#LAVA}) at {@code scale}, per the {@code model} - see {@link FluidFlow.Model#impulse}. */
    private static Vec flowImpulse(Player p, FluidFlow.Model model, Block fluid, double scale) {
        Instance instance = p.getInstance();
        if (instance == null) return Vec.ZERO;
        Vec mov = positionDelta(p);
        return model.impulse(instance, p.getPosition(), p.getBoundingBox(), fluid, scale, mov.x(), mov.z());
    }

    /** One fluid-friction step for the 3D flow residual + near-zero clamp. Unlike {@link #bleedPush} this keeps Y (the falling-water down-term rides it). */
    private static Vec bleedFlow(Player p, Vec acc) {
        if (acc.isZero()) return acc;
        Vec decayed = acc.mul(frictionPerTick(p, !p.isOnGround()));
        return new Vec(clampNearZero(decayed.x()), clampNearZero(decayed.y()), clampNearZero(decayed.z()));
    }

    /** Mirrors vanilla {@code Entity.move}'s {@code if (collided) motX/motZ = 0}: zeroes the flow residual on a collision-blocked axis, so a current pinning a player against a wall never accumulates. */
    private static Vec zeroBlockedAxes(Player p, Vec acc) {
        if (acc.isZero() || p.getInstance() == null) return acc;
        PhysicsResult r = CollisionUtils.handlePhysics(p, new Vec(acc.x(), 0, acc.z()));
        return new Vec(r.collisionX() ? 0.0 : acc.x(), acc.y(), r.collisionZ() ? 0.0 : acc.z());
    }

    /** The entity's current water-flow residual (b/t), or {@link Vec#ZERO}. Folded by a {@link VelocityRule} arc (x/z current + the Y down-term) when {@link VelocityConfig#flowPush()} is on. Players only. */
    public static Vec flowPush(Entity entity) {
        if (!(entity instanceof Player p)) return Vec.ZERO;
        Vec v = p.getTag(FLOW_PUSH);
        return v == null ? Vec.ZERO : v;
    }

    /** Ticks since the client's jump packet (the gravity-arc clock) - ping-invariant. 0 on the ground / with no air anchor. */
    public static int ticksInAir(Entity entity) {
        if (entity == null) return 0;
        Long start = entity.getTag(AIR_START_TICK);
        if (start == null) return 0;
        return (int) Math.max(0, TickClock.now() - start);
    }

    /** Whether the entity is in an upward-launched arc (jump/KB boost) vs a ledge walk-off. Cleared on landing or flight. */
    public static boolean launched(Entity entity) {
        return entity instanceof Player p && Boolean.TRUE.equals(p.getTag(LAUNCHED));
    }

    /** Launch origin (yaw + sprint + takeoff horizontal residual/seed) while in a launch arc, or {@code null}. */
    public static @Nullable JumpInfo recentJump(Entity entity) {
        if (!(entity instanceof Player p) || !launched(p)) return null;
        LaunchStamp s = p.getTag(LAUNCH_STAMP);
        return s == null ? null : new JumpInfo(s.yaw(), s.sprinting(), s.residualH(), s.seedH());
    }

    /**
     * Server-tracked horizontal mot (b/t), friction-bled to {@code now + tickOffset} - the value every hit folds. This
     * is the victim's own simulated motion (the sprint-jump boost), not prior knockback (vanilla restores the pre-KB
     * velocity after broadcasting it).
     */
    public static Vec horizontalMot(Entity entity, int tickOffset) {
        if (!(entity instanceof Player p)) return Vec.ZERO;
        return residualAt(p, p.getTag(MOT_H), TickClock.now() + tickOffset);
    }

    /**
     * Scales the tracked horizontal residual by {@code factor}, re-anchored at now - vanilla's {@code motX/motZ *= 0.6}
     * attacker self-slowdown on a landed sprint hit. Mutates only the tracked velocity, not damage/KB. No-op for non-players.
     */
    public static void scaleHorizontalResidual(Entity entity, double factor) {
        if (!(entity instanceof Player p)) return;
        MotState s = p.getTag(MOT_H);
        if (s == null) return;
        long now = TickClock.now();
        Vec bled = residualAt(p, s, now);
        p.setTag(MOT_H, new MotState(bled.mul(factor), now, s.airborne()));
    }

    /** Move-delta velocity (b/t) from the client's packets; players via the per-move snapshot, others via entity velocity. */
    public static Vec positionDelta(Entity entity) {
        if (entity instanceof Player p) {
            Vec d = p.getTag(MOVE_VELOCITY);
            return d == null ? Vec.ZERO : d;
        }
        // entity velocity is blocks/second
        return entity.getVelocity().div(ServerFlag.SERVER_TICKS_PER_SECOND);
    }

    /** {@link #onGround(Entity, int)} with the {@link #DEFAULT_GROUND_TICKS default} 1-tick sweep. */
    public static boolean onGround(@Nullable Entity entity) {
        return onGround(entity, DEFAULT_GROUND_TICKS);
    }

    /**
     * Whether the entity is on the ground (vanilla {@code verticalCollision && motY < 0}): {@code 0} = the raw client
     * flag; {@code >= 1} = the flag OR a collision probe seeded from the server-simulated {@code motY} (so a high-ping
     * victim grounds about as fast as vanilla), forward-predicting {@code ticks} deep. A rising arc is never grounded.
     * Runs a collision sweep - query per hit/event, not per tick.
     */
    public static boolean onGround(@Nullable Entity entity, int ticks) {
        if (entity == null) return false;
        if (ticks <= 0 || !(entity instanceof Player p)) return entity.isOnGround();
        if (entity.isOnGround()) return true;
        if (entity.getInstance() == null) return false;

        // the sim's collision = vanilla's onGround
        if (simCollided(p)) return true;

        Aerodynamics aero = entity.getAerodynamics();
        double g = aero.gravity();
        double s = aero.verticalAirResistance();
        double vy = serverSimMotY(p, g, s); // server arc, not the client delta
        if (vy >= 0) return false; // arc rising - never grounded
        double probe = 0;
        for (int i = 0; i < ticks; i++) {
            probe += vy;
            vy = (vy - g) * s;
        }
        PhysicsResult r = CollisionUtils.handlePhysics(entity, new Vec(0, probe, 0));
        return r.isOnGround();
    }

    /** Server-side {@code motY}: the ticked sim's latest value, else the analytic gravity arc. Unlike {@link #positionDelta} it keeps accelerating, so a high-ping fall is probed at the depth vanilla sees. */
    private static double serverSimMotY(Player p, double g, double s) {
        VertSim sim = p.getTag(VERT_SIM);
        if (sim != null) return sim.clamped[0];
        int air = ticksInAir(p);
        double vy = launched(p) ? VelocityConfig.JUMP_VELOCITY : 0.0;
        for (int i = 0; i < air; i++) vy = (vy - g) * s;
        return vy;
    }

    /** Whether the entity is falling: airborne ({@link #onGround(Entity)}) and descending ({@link #positionDelta}), not in a fluid/web/ladder, not flying. */
    public static boolean isFalling(@Nullable Entity entity) {
        if (entity == null) return false;
        if (entity instanceof Player p) {
            if (p.isFlying()) return false;
            // not free fall in a fluid/web/ladder
            if (p.getTag(ENV) != null) return false;
        }
        if (onGround(entity)) return false;
        return positionDelta(entity).y() < 0;
    }
}
