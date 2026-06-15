package io.github.term4.minestommechanics.mechanics.projectile.entities;

import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig;
import io.github.term4.minestommechanics.util.Directions;
import net.minestom.server.ServerFlag;
import net.minestom.server.collision.Aerodynamics;
import net.minestom.server.collision.CollisionUtils;
import net.minestom.server.collision.EntityCollisionResult;
import net.minestom.server.collision.PhysicsResult;
import net.minestom.server.collision.ShapeImpl;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.projectile.ProjectileMeta;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.entity.projectile.ProjectileCollideWithBlockEvent;
import net.minestom.server.event.entity.projectile.ProjectileCollideWithEntityEvent;
import net.minestom.server.event.entity.projectile.ProjectileUncollideEvent;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.play.EntityTeleportPacket;
import net.minestom.server.network.packet.server.play.EntityVelocityPacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
import net.minestom.server.utils.chunk.ChunkCache;
import net.minestom.server.utils.chunk.ChunkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Base projectile entity: 1.8-style physics, block stick, entity hits. See {@code docs/projectiles-design.md} for
 * the architecture and the FIX LEDGER this class cites ({@code F1}, {@code F3}, {@code F5}, ...). A stuck arrow keeps
 * its movement frozen but is NOT radio-silent: {@link #resyncStuck()} periodically re-asserts position + rotation,
 * mirroring vanilla's {@code ServerEntity} so a 1.8 client (through Via) self-heals - this supersedes the old
 * LegacyProjectileCompat hint/pullback plan (F7/F8/F12). Modern clients hold via the {@code inGround} metadata.
 *
 * <p>Velocity is blocks/<b>tick</b> internally (library convention); {@code super.velocity} mirrors it in
 * blocks/second so Minestom internals and external reads stay sane. Physics constants come from
 * {@link Aerodynamics} (set per type by the launcher), read live each tick - no TPS scaling yet, but all
 * timing is tick-denominated so a scaling pass stays mechanical.
 */
public abstract class ProjectileEntity extends Entity {

    /** Default ticks a freshly launched projectile cannot hit its own shooter (vanilla pass-through at spawn). */
    public static final int DEFAULT_SHOOTER_IMMUNITY_TICKS = 5;

    /** Default entity-hit margin: vanilla 1.8 grows the TARGET's bbox by {@code 0.3} each side and ray-tests the
     *  projectile's center; equivalently we grow our zero-size box by 0.3 each side ({@code growSymmetrically}, not
     *  {@code expand}). Per-type override {@code ProjectileTypeConfig.entityHitGrow}; see the design doc §5. */
    public static final double DEFAULT_ENTITY_HIT_GROW = 0.3;

    /** Margin (blocks) the shooter's box is grown by for the {@link #leftOwnerImmunity} "has the projectile left its
     *  owner" check - vanilla 26.1 {@code Projectile.isOutsideOwnerCollisionRange} uses {@code .inflate(1.0)}. */
    private static final double LEFT_OWNER_INFLATE = 1.0;

    /** Ticks the shooter is immune (configurable per type; set by the launcher). */
    protected int shooterImmunityTicks = DEFAULT_SHOOTER_IMMUNITY_TICKS;
    /** Alive-tick until which the shooter is immune again after a deflect (vanilla {@code as = 0} re-arm); see
     *  {@link #rearmShooterImmunity}. {@code <= 0} = no active re-arm (only the initial {@link #shooterImmunityTicks}). */
    private long shooterImmuneUntilAlive;
    /** Entity-hit margin grown onto the target on each side (configurable per type; stamped by the launcher). */
    protected double entityHitGrow = DEFAULT_ENTITY_HIT_GROW;
    /** In-flight velocity broadcast interval: {@code <= 0} = never (vanilla arrow, the edge-slide fix), {@code N} =
     *  every N ticks. Stamped by the launcher; gates the per-tick velocity packet in {@link #sendPacketToViewers}. */
    protected int velocitySyncInterval;
    /** Counts the automatic per-tick velocity packets, for the {@link #velocitySyncInterval} throttle. */
    private long autoVelocityCounter;
    /** Drag+gravity order relative to the per-tick move: 1.8 {@code DRAG_AFTER_MOVE} (default), 26.1 {@code DRAG_BEFORE_MOVE}. */
    protected ProjectileTypeConfig.PhysicsOrder physicsOrder = ProjectileTypeConfig.PhysicsOrder.DRAG_AFTER_MOVE;
    /** 26.1 shooter-immunity model: when {@code true}, the shooter is immune until the projectile leaves its box. */
    protected boolean leftOwnerImmunity;
    /** Whether the projectile has left the shooter's (grown) bounding box at least once (drives {@link #leftOwnerImmunity}). */
    private boolean leftOwner;
    /** Pull-back distance (blocks) along the flight dir on stick so the tip pokes out of the block face (vanilla 0.05). */
    protected double stickPullback = 0.05;
    /** Velocity in blocks/tick (library convention; {@code super.velocity} mirrors b/s). */
    protected Vec velocityBt = Vec.ZERO;
    /** Shooter position + view at launch, for knockback origin (vanilla uses the shooter, not the projectile). */
    protected Pos shooterOriginPos;
    protected @Nullable Entity shooter;
    /** Where the projectile entered the world (spawn position on the wire grid); for trajectory / origin queries. */
    private @Nullable Pos spawnPosition;

    // Stuck state: collisionDirection != null means stuck.
    /** Single-axis face normal of the hit surface (signed travel direction on the hit axis). */
    protected @Nullable Vec collisionDirection;
    /** Exact collision point - block lookups + unstuck checks. */
    protected @Nullable Point stuckCollisionPoint;
    /** Where the arrow is placed on stick: the physics-resolved position pulled back 0.05 along flight (vanilla, see
     *  {@link #stick}), so the tip pokes ~0.05 out of the block face. */
    private @Nullable Pos stuckPlacement;
    /** Counts ticks since sticking; drives the periodic stuck re-sync (vanilla parity - see {@link #tick}). */
    private long stuckSyncCounter;
    /** Throttles the in-flight teleport to {@code syncInterval} (the vanilla projectile cadence). A per-tick absolute
     *  teleport SHAKES the client (it fights the client's own interpolation + flight prediction); vanilla instead sends
     *  a sparse teleport and lets the client predict between them. See {@link #synchronizePosition}. */
    private long flightSyncCounter;

    private @Nullable PhysicsResult previousPhysicsResult;
    private float prevYaw, prevPitch;
    private float stuckYaw, stuckPitch;
    private boolean justBecameStuck;
    /** Set by {@link #setDeflected} when a hit bounced the projectile this tick (velocity reversed, not removed); the
     *  entity-collision block then stops the forward move at the hit point so it doesn't overshoot before bouncing. */
    private boolean deflectedThisTick;
    /** Set when the {@code deflectParticles} opt-in fires (by {@link ManagedProjectile}): on a deflect / pass-through, let a
     *  subclass spawn a server-side flight trail at the authoritative position. The arrow ENTITY itself CANNOT be made
     *  visible to a 1.8 client after it touches an entity (a native 1.8 client bug - see the design doc §3f), so the
     *  trail is all we can show; every server-side re-spawn/decoy attempt failed. */
    protected boolean deflectVisible;
    /** Removal requested from inside movementTick; applied in {@link #tick} AFTER super.tick (post-touchTick) to
     *  avoid nulling {@code instance} before Minestom's {@code Entity.tick} reads it (NPE), and without entering the
     *  stuck/radio-silence state (which would skip the entity scheduler and never run a deferred remove). */
    private boolean pendingRemove;

    protected ProjectileEntity(@Nullable Entity shooter, @NotNull EntityType entityType) {
        super(entityType);
        this.shooter = shooter;
        this.shooterOriginPos = shooter != null ? shooter.getPosition() : Pos.ZERO;
        this.collidesWithEntities = false;
        this.preventBlockPlacement = false;
        if (getEntityMeta() instanceof ProjectileMeta meta) meta.setShooter(shooter);
        // F1: zero-size bounding box - collision points resolve exactly on block boundaries; even 0.01
        // overshoots and breaks the modern client's floor(pos) -> block -> inGround check.
        setBoundingBox(0, 0, 0);
    }

    // =========================================================================
    // Hooks for subclasses / launchers
    // =========================================================================

    /** Called when the projectile hits an entity. Return {@code true} to remove the projectile. */
    protected boolean onHit(@NotNull Entity entity) { return false; }

    /** Called when the projectile sticks in a block. Return {@code true} to remove the projectile. */
    protected boolean onStuck() { return false; }

    /** Called when the projectile unsticks (block broken). */
    protected void onUnstuck() {}

    /** Per-tick subclass update while alive (pickup, despawn timers); also runs while stuck. */
    protected void updateProjectile(long time) {}

    /** Whether this projectile can hit {@code entity} (shooter immunity is handled separately). */
    protected boolean canHit(@NotNull Entity entity) {
        if (!(entity instanceof LivingEntity living) || living.isDead()) return false;
        return !(entity instanceof Player p) || p.getGameMode() != GameMode.SPECTATOR;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    public @Nullable Entity getShooter() { return shooter; }

    public @NotNull Pos getShooterOriginPos() { return shooterOriginPos; }

    /** Stamps the shooter's position/view at launch (knockback origin). */
    public void setShooterOriginPos(@NotNull Pos pos) { this.shooterOriginPos = pos; }

    public boolean isStuck() { return collisionDirection != null; }

    /** Where the projectile entered the world (its wire-grid spawn position), or {@code null} before launch. */
    public @Nullable Pos getSpawnPosition() { return spawnPosition; }
    /** Records the spawn position (launcher applies it). */
    public void setSpawnPosition(@NotNull Pos pos) { this.spawnPosition = pos; }

    /** The exact point on the block face this projectile stuck to (arrows), or {@code null} if not stuck. */
    public @Nullable Point getStuckPoint() { return stuckCollisionPoint; }

    /** Position of the block this projectile is embedded in (just past the stuck face), or {@code null} if not stuck. */
    public @Nullable Point getStuckBlockPosition() {
        if (stuckCollisionPoint == null || collisionDirection == null) return null;
        return stuckCollisionPoint.add(collisionDirection.mul(0.5)).asBlockVec();
    }

    /** The block this projectile is stuck in (queried live), or {@code null} if not stuck / no instance. */
    public @Nullable Block getStuckBlock() {
        Point pos = getStuckBlockPosition();
        return pos != null && instance != null ? instance.getBlock(pos) : null;
    }

    /** Sets how many ticks the shooter is immune to this projectile (launcher applies the resolved config). */
    public void setShooterImmunityTicks(int ticks) { this.shooterImmunityTicks = ticks; }

    /** Sets the entity-hit margin grown onto the target on each side (launcher applies the resolved config). */
    public void setEntityHitGrow(double grow) { this.entityHitGrow = grow; }

    /** Sets the in-flight velocity broadcast interval ({@code <= 0} = never; the launcher applies the resolved config). */
    public void setVelocitySyncInterval(int interval) { this.velocitySyncInterval = interval; }

    /** Sets when drag + gravity apply relative to the move (launcher applies the resolved config). */
    public void setPhysicsOrder(@NotNull ProjectileTypeConfig.PhysicsOrder order) { this.physicsOrder = order; }

    /** Sets the 26.1 leave-owner shooter-immunity model (launcher applies the resolved config). */
    public void setLeftOwnerImmunity(boolean v) { this.leftOwnerImmunity = v; }

    /** Sets the stuck-tip pull-back distance (launcher applies the resolved config). */
    public void setStickPullback(double v) { this.stickPullback = v; }

    /** Re-arms shooter immunity for another {@link #shooterImmunityTicks} (vanilla {@code as = 0} after a deflect, so
     *  a bounced-back arrow can't instantly re-hit the shooter / loop on a self-deflect). */
    protected void rearmShooterImmunity() { this.shooterImmuneUntilAlive = getAliveTicks() + shooterImmunityTicks; }

    /** Flags that the projectile bounced this tick (see {@link #deflectedThisTick}); called by {@link ManagedProjectile#deflect}. */
    protected void setDeflected() { this.deflectedThisTick = true; }

    /** Velocity in blocks/tick. */
    public @NotNull Vec velocityBt() { return velocityBt; }

    /** Sets the velocity in blocks/tick (mirrors to {@code super.velocity} in blocks/second). */
    public void setVelocityBt(@NotNull Vec bt) {
        this.velocityBt = bt;
        this.velocity = bt.mul(ServerFlag.SERVER_TICKS_PER_SECOND);
    }

    // =========================================================================
    // Tick: vanilla-style periodic re-sync while stuck (supersedes the old F2 radio silence)
    // =========================================================================

    @Override
    public void tick(long time) {
        if (isStuck()) {
            if (isRemoved()) return;
            // Movement frozen, but NOT radio-silent: a periodic absolute teleport (zero velocity) re-asserts pos +
            // rotation like vanilla ServerEntity, so a mispredicted 1.8 stuck arrow self-heals. Counter starts at 0 ->
            // first re-sync fires the tick after sticking (the one-tick delay). Modern holds via inGround. Doc §3a.
            updateProjectile(time);
            if (isRemoved()) return;
            if (stuckSyncCounter++ % Math.max(1L, getSynchronizationTicks()) == 0) resyncStuck();
            if (shouldUnstuck()) unstick();
            return;
        }
        super.tick(time);
        // Apply a removal requested during movementTick now that touchTick has run (instance still valid through it).
        if (pendingRemove) { pendingRemove = false; remove(); return; }
        if (!isRemoved()) updateProjectile(time);
    }

    @Override
    protected void movementTick() {
        this.gravityTickCount = isStuck() ? 0 : gravityTickCount + 1;
        if (vehicle != null || isStuck()) return;

        if (instance.isInVoid(position)) {
            pendingRemove = true;
            return;
        }

        // 26.1 applies drag + gravity BEFORE the move (1.8 after - see physicsOrder / applyDragGravity).
        if (physicsOrder == ProjectileTypeConfig.PhysicsOrder.DRAG_BEFORE_MOVE) applyDragGravity();

        // --- Block physics (swept) ---
        ChunkCache blockGetter = new ChunkCache(instance, currentChunk, Block.AIR);
        PhysicsResult physics = CollisionUtils.handlePhysics(
                blockGetter, getBoundingBox(), position, velocityBt, previousPhysicsResult, true);
        this.previousPhysicsResult = physics;
        Pos newPosition = CollisionUtils.applyWorldBorder(instance.getWorldBorder(), position, physics.newPosition());

        // --- Entity collision (swept alongside the block physics) ---
        // Vanilla 1.8: grow the TARGET by 0.3 on each side and ray-test the projectile's center path; we do the
        // Minkowski dual - grow the zero-size projectile box by 0.3 on each side (growSymmetrically, NOT expand - see
        // ENTITY_HIT_GROW) and sweep from the (un-offset) center along velocity.
        // 26.1 leftOwner model: immune to the shooter until the projectile geometrically leaves its box; 1.8 = fixed
        // ticks. Either way the post-deflect re-arm (shooterImmuneUntilAlive) still applies (1.8 as = 0 on a bounce).
        if (leftOwnerImmunity && !leftOwner && shooter != null && !withinShooterBox(position)) leftOwner = true;
        boolean shooterImmune = (leftOwnerImmunity ? (shooter != null && !leftOwner)
                : getAliveTicks() < shooterImmunityTicks) || getAliveTicks() < shooterImmuneUntilAlive;
        Collection<EntityCollisionResult> hits = CollisionUtils.checkEntityCollisions(
                instance, boundingBox.growSymmetrically(entityHitGrow, entityHitGrow, entityHitGrow), position, velocityBt, 3,
                e -> e != this && !(shooterImmune && e == shooter) && canHit(e), physics);
        if (!hits.isEmpty()) {
            EntityCollisionResult hit = hits.iterator().next();
            var event = new ProjectileCollideWithEntityEvent(this, hit.collisionPoint().asPos(), hit.entity());
            EventDispatcher.call(event);
            if (!event.isCancelled() && onHit(hit.entity())) {
                pendingRemove = true; // removed in tick() after touchTick (see field doc)
                return;
            }
            // A deflect (onHit reversed the velocity but did not remove): stop the forward move at the hit point so the
            // arrow does not overshoot the target before bouncing back next tick (the reversed velocity carries it).
            if (deflectedThisTick) {
                deflectedThisTick = false;
                refreshPosition(hit.collisionPoint().asPos().withView(prevYaw, prevPitch), false, false);
                // deflectParticles opt-in: the server-side crit trail (driven by deflectVisible in ArrowEntity) traces
                // the bounce for 1.8 viewers; the arrow entity stays invisible to them (native 1.8 client bug, doc §3f).
                return;
            }
        }

        Chunk finalChunk = ChunkUtils.retrieve(instance, currentChunk, newPosition);
        if (!ChunkUtils.isLoaded(finalChunk)) return;

        this.justBecameStuck = false;

        // --- Block stick: per-axis collision shape -> hit block / point / axis ---
        if (physics.hasCollision()) {
            for (int axis = 0; axis < 3; axis++) {
                if (physics.collisionShapes()[axis] instanceof ShapeImpl) {
                    Point hitPoint = physics.collisionPoints()[axis];
                    Block hitBlock = instance.getBlock(hitPoint.sub(0, Vec.EPSILON, 0), Block.Getter.Condition.TYPE);
                    stick(hitBlock, hitPoint, axis, newPosition);
                    if (isRemoved() || pendingRemove) return; // breaker: stop physics, removal happens in tick()
                    break;
                }
            }
        }

        // 1.8 applies drag + gravity AFTER the move (skip on the stick tick - velocity is already zeroed + frozen).
        if (physicsOrder == ProjectileTypeConfig.PhysicsOrder.DRAG_AFTER_MOVE && !justBecameStuck) applyDragGravity();
        this.onGround = physics.isOnGround();

        // --- Rotation: displacement-based; latched at impact when sticking ---
        float yaw = prevYaw, pitch = prevPitch;
        if (justBecameStuck) {
            yaw = stuckYaw;
            pitch = stuckPitch;
        } else {
            Vec displacement = newPosition.sub(position).asVec();
            if (displacement.lengthSquared() > 1e-8) {
                yaw = Directions.yaw(displacement);
                pitch = Directions.pitch(displacement);
            }
        }
        this.prevYaw = yaw;
        this.prevPitch = pitch;

        // F3: place at the physics-resolved position (not the collision point) - fixes modern clients seeing the
        // projectile float in front of the block face. On the stick tick, use the 0.05-pulled-back stuckPlacement.
        Pos place = justBecameStuck && stuckPlacement != null ? stuckPlacement : newPosition;
        refreshPosition(place.withView(yaw, pitch), false, false);
        if (justBecameStuck) this.lastSyncedPosition = getPosition();
    }

    /** One tick of air resistance + gravity on {@link #velocityBt} (live {@link Aerodynamics}), mirrored to
     *  {@code super.velocity} (b/s). Called before or after the move per {@link #physicsOrder}. */
    private void applyDragGravity() {
        Aerodynamics aero = getAerodynamics();
        velocityBt = velocityBt
                .mul(aero.horizontalAirResistance(), aero.verticalAirResistance(), aero.horizontalAirResistance())
                .sub(0, hasNoGravity() ? 0 : aero.gravity(), 0);
        this.velocity = velocityBt.mul(ServerFlag.SERVER_TICKS_PER_SECOND);
    }

    /** Whether {@code point} is inside the shooter's bounding box grown by {@link #LEFT_OWNER_INFLATE} - the 26.1
     *  {@link #leftOwnerImmunity} "has the projectile left its owner" test (vanilla {@code Projectile.isOutsideOwnerCollisionRange}
     *  uses {@code .inflate(1.0)}). */
    private boolean withinShooterBox(Point point) {
        if (shooter == null) return false;
        var bb = shooter.getBoundingBox();
        Pos sp = shooter.getPosition();
        double g = LEFT_OWNER_INFLATE;
        return point.x() >= sp.x() + bb.relativeStart().x() - g && point.x() <= sp.x() + bb.relativeEnd().x() + g
                && point.y() >= sp.y() + bb.relativeStart().y() - g && point.y() <= sp.y() + bb.relativeEnd().y() + g
                && point.z() >= sp.z() + bb.relativeStart().z() - g && point.z() <= sp.z() + bb.relativeEnd().z() + g;
    }

    private void stick(Block hitBlock, Point hitPoint, int hitAxis, Pos resolvedPosition) {
        var event = new ProjectileCollideWithBlockEvent(this, hitPoint.asPos(), hitBlock);
        EventDispatcher.call(event);
        if (event.isCancelled()) return;

        // Latch the flight rotation (for the stuck render) + the face normal, while velocity is still the flight value.
        if (velocityBt.lengthSquared() > 1e-8) {
            stuckYaw = Directions.yaw(velocityBt);
            stuckPitch = Directions.pitch(velocityBt);
        } else {
            stuckYaw = prevYaw;
            stuckPitch = prevPitch;
        }
        Vec normal = switch (hitAxis) {
            case 0 -> new Vec(Math.signum(velocityBt.x()), 0, 0);
            case 1 -> new Vec(0, Math.signum(velocityBt.y()), 0);
            case 2 -> new Vec(0, 0, Math.signum(velocityBt.z()));
            default -> velocityBt.normalize();
        };

        // onStuck() fires the hit event + onImpact(null) and returns removeOnBlockHit: true = BREAK (vanilla
        // throwables die() on any hit and NEVER stick), false = STICK (arrow). Decide BEFORE entering the stuck
        // state, so a breaker doesn't go radio-silent (which would skip the scheduler and never get removed).
        boolean breakOnHit = onStuck();
        if (isRemoved()) return;
        if (breakOnHit) {
            setVelocityBt(Vec.ZERO);
            pendingRemove = true; // removed in tick() after touchTick - no stuck state, no radio silence
            return;
        }

        // Stick (arrows): enter the stuck state. The position/rotation re-sync (including the one-tick-delayed first
        // teleport - the old Atlas F4 trick) is driven by the periodic resyncStuck() in tick(); reset its counter so
        // the first re-assert fires next tick.
        this.collisionDirection = normal;
        this.stuckCollisionPoint = hitPoint;
        // Vanilla 0.05 pull-back along the flight direction (1.8 EntityArrow: locX -= motX/|mot| * 0.05; 26.1 a
        // per-axis signum*0.05): the arrow tip pokes ~stickPullback out of the block face instead of flush. velocityBt
        // is still the flight value here (zeroed below).
        Vec flightDir = velocityBt.lengthSquared() > 1e-8 ? velocityBt.normalize() : normal;
        this.stuckPlacement = resolvedPosition.sub(flightDir.mul(stickPullback));
        this.stuckSyncCounter = 0;
        this.justBecameStuck = true;
        setNoGravity(true);
        setVelocityBt(Vec.ZERO);
    }

    private boolean shouldUnstuck() {
        if (collisionDirection == null || stuckCollisionPoint == null) return false;
        // Block broken = unstick. (An intersect-box check false-unsticks on fences/slabs - ledger.)
        Point intoBlock = stuckCollisionPoint.add(collisionDirection.mul(0.5));
        // Don't unstick while the chunk is unloaded/(re)loading - e.g. a shooter relog briefly empties it, which
        // would otherwise read AIR and drop the arrow (it falls away and becomes uncollectable).
        Chunk chunk = instance.getChunkAt(intoBlock.x(), intoBlock.z());
        if (chunk == null || !chunk.isLoaded()) return false;
        return instance.getBlock(intoBlock.asBlockVec(), Block.Getter.Condition.TYPE).isAir();
    }

    private void unstick() {
        EventDispatcher.call(new ProjectileUncollideEvent(this));
        collisionDirection = null;
        stuckCollisionPoint = null;
        setNoGravity(false);
        onUnstuck();
        // NOTE: a relogged 1.8 client may briefly "freeze then fall" here - it holds the arrow in a client-side
        // inGround state (which ignores position teleports) until its own block-changed check fires. This matches
        // vanilla 26 + Via (and even vanilla 1.8 occasionally), so we accept it rather than force a re-spawn.
    }

    // =========================================================================
    // Wire sync
    // =========================================================================

    /** The 1.8 wire wants blocks/tick; {@code velocityBt} already is. Reports ZERO while stuck so the stick-tick
     *  velocity broadcast (and any sync) never nudges a 1.8 client off the stuck position. */
    @Override
    protected Vec getVelocityForPacket() {
        return isStuck() ? Vec.ZERO : velocityBt;
    }

    // F5: absolute-teleport position sync (Minestom's relative-move sync is invisible to 1.8 via Via - "the arrow never
    // moves"). VANILLA MODEL: a projectile gets an absolute teleport only every updateInterval (1.8 arrow = 20t,
    // throwable = 10t - they move >4 blocks/interval, so vanilla's relative-move path always falls back to a teleport
    // anyway) and the CLIENT predicts the arc in between.
    @Override
    protected void synchronizePosition() {
        // While stuck, the periodic resyncStuck() in tick() owns the position broadcast (super.tick / this method
        // don't run then); on the stick tick itself we must NOT send (the resync's first teleport next tick is the
        // intended one-tick-delayed correction).
        if (isStuck()) {
            this.lastSyncedPosition = getPosition();
            return;
        }
        // Throttle the teleport to syncInterval (the vanilla cadence: a projectile gets a sparse absolute teleport and
        // the client predicts the arc between). Sending it EVERY tick was tried and SHAKES both 1.8 and 26.1 clients - a
        // per-tick absolute teleport fights the client's own interpolation + flight prediction (predict forward, yank
        // back, repeat). This override is invoked every tick once Minestom's scheduled sync starts firing; the counter
        // does the throttling. (The per-tick VELOCITY packet Entity.tick sends alongside this is suppressed - see
        // sendPacketToViewers - because it caused the block-edge "slide": vanilla sends an arrow's velocity ONCE at
        // spawn and the 1.8 client predicts the rest in lockstep; re-broadcasting it every tick perturbs that.)
        if (flightSyncCounter++ % Math.max(1L, getSynchronizationTicks()) != 0) return;
        Pos pos = getPosition();
        sendPacketToViewersAndSelf(new EntityTeleportPacket(getEntityId(), pos, getVelocityForPacket(), 0, onGround));
        this.lastSyncedPosition = pos;
    }

    // Vanilla parity for a stuck arrow: re-assert absolute position + rotation (+ zero velocity) periodically so a
    // 1.8 client (via Via) self-heals any misprediction (relog angle, edge-hit overshoot). Mirrors what vanilla's
    // ServerEntity does every updateInterval; the modern client, frozen via inGround metadata, treats it as a no-op.
    private void resyncStuck() {
        Pos pos = getPosition();
        sendPacketToViewersAndSelf(new EntityTeleportPacket(getEntityId(), pos, Vec.ZERO, 0, true));
        sendVelocityToViewers(Vec.ZERO); // vanilla sends a one-time zero when an arrow stops (allowed past the suppression)
        this.lastSyncedPosition = pos;
    }

    /** Set true ONLY around our deliberate velocity broadcasts, so {@link #sendPacketToViewers} lets them through while
     *  dropping Minestom's automatic per-tick velocity packet (the edge-slide fix - see that override). */
    private boolean allowVelocityPacket;

    /** Sends a velocity packet to viewers, bypassing the per-tick suppression below. */
    private void sendVelocityToViewers(@NotNull Vec velocity) {
        allowVelocityPacket = true;
        try {
            sendPacketToViewersAndSelf(new EntityVelocityPacket(getEntityId(), velocity));
        } finally {
            allowVelocityPacket = false;
        }
    }

    /**
     * The block-edge "slide" fix. Vanilla 1.8 registers arrows {@code sendVelocity=false}: velocity goes ONCE in the
     * spawn packet, then the client predicts the whole flight locally in lockstep. Minestom re-broadcasts velocity every
     * tick; over Via those (lagged, quantized) packets perturb that prediction so the client's own raytrace mispredicts
     * edges. So we drop the automatic per-tick {@code EntityVelocityPacket} (gated by {@code velocitySyncInterval}); the
     * spawn velocity rides the {@code SpawnEntityPacket} and the deliberate stuck-zero rides {@link #sendVelocityToViewers}.
     * See the design doc §3f.
     */
    @Override
    public void sendPacketToViewers(@NotNull SendablePacket packet) {
        if (packet instanceof EntityVelocityPacket && !allowVelocityPacket) {
            if (velocitySyncInterval <= 0) return; // vanilla arrow: no per-tick velocity, client predicts from spawn
            if (autoVelocityCounter++ % velocitySyncInterval != 0) return; // else every velocitySyncInterval ticks
        }
        super.sendPacketToViewers(packet);
    }

    // F6/F13: new viewer (spawn, relog, chunk-cross). data > 0 makes ViaVersion include velocity bytes in the 1.8
    // SpawnObject. A stuck arrow spawns with zero velocity for BOTH clients (getVelocityForPacket reports zero while
    // stuck); the modern client then holds it via the inGround metadata, and the 1.8 client self-heals any spawn
    // misprediction from the periodic resyncStuck() teleports (vanilla parity - no per-version spawn split needed).
    @Override
    public void updateNewViewer(@NotNull Player player) {
        int data = shooter != null ? shooter.getEntityId() : 0;
        Pos pos = getPosition();
        player.sendPacket(new SpawnEntityPacket(getEntityId(), getUuid(), getEntityType(), pos, pos.yaw(), data, getVelocityForPacket()));
        player.sendPacket(getMetadataPacket());
    }

}
