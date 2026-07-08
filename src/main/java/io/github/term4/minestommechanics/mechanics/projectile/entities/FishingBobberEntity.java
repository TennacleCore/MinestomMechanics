package io.github.term4.minestommechanics.mechanics.projectile.entities;

import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.config.FieldValue;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackSystem;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileConfigResolver.ProjectileContext;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileSnapshot;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileSystem;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig.RodDurability;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig.RodPull;
import io.github.term4.minestommechanics.tracking.motion.MotionTracker;
import io.github.term4.minestommechanics.util.tick.TickScaler;
import net.minestom.server.ServerFlag;
import net.minestom.server.collision.Aerodynamics;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.other.FishingHookMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.play.EntityTeleportPacket;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Fishing bobber: flies until it hooks an entity (0-damage hit, then pinned at 0.8 body height - what draws the 1.8
 * client's line to the victim) or lands; discards when the angler dies, drops the rod, or the line passes
 * {@code lineSnapDistance}. {@link #retrieve} pulls the hooked entity per {@code rodPull} and returns the
 * {@code rodDurability} cost; {@link #setHookedEntity} is the hook seam for custom rod behaviors (mmc18 pseudo-hook).
 *
 * <p>1.8 ground contact is a live damp-settle loop, never a frozen state (vanilla never records the hit block, so
 * {@code inGround} clears every tick with a {@code rand*0.2} damp); the predicting client settles the same way.
 * 26.1 keeps the frozen stick + 1200-tick despawn.
 *
 * <p>Both vanilla integrators apply gravity before drag (unlike throwables); the stamped {@code physicsOrder} picks
 * the placement - {@code DRAG_AFTER_MOVE} = 1.8 (move, gravity, drag) via the acceleration channel,
 * {@code DRAG_BEFORE_MOVE} = 26.1 (gravity, move, drag) via a pre-move subtract - and with it the water model.
 */
public class FishingBobberEntity extends ManagedProjectile {

    /** The angler's live bobber (set by the {@code FishingRod} launcher, cleared on remove). */
    public static final Tag<FishingBobberEntity> ACTIVE_BOBBER = Tag.Transient("mm:active-bobber");

    /** Vanilla bobber box height, for the 1.8 water-coverage slabs (the physics box is a point). */
    private static final double BOX_HEIGHT = 0.25;
    private static final int GROUND_DESPAWN_TICKS = 1200;

    private final RodPull rodPull;
    private final RodDurability rodDurability;
    private final double lineSnapDistanceSq;
    private final boolean hookedMetadata;

    private @Nullable Entity hooked;
    /** 26.1 BOBBING: latched on water entry, never leaves (out-of-water bobbing just regains gravity). */
    private boolean bobbing;
    private long stuckTicks;
    /** 1.8 ground contact last tick: apply the vanilla {@code rand*0.2} damp before this tick's physics. */
    private boolean contactPending;
    /** Grounded since the last real fall; gates the hit event/behavior to the first contact of a landing. */
    private boolean grounded;

    /** Gravity (b/t) moved off the {@link Aerodynamics} so the bobber controls its placement; see class doc. */
    private double gravityBt;
    /** The stamped order asked for the 26.1 gravity-before-move placement. */
    private boolean modernOrder;
    private boolean latched;

    public FishingBobberEntity(@Nullable Entity shooter, @NotNull EntityType entityType,
                               ProjectileSnapshot snap, ProjectileTypeConfig effectiveConfig) {
        super(shooter, entityType, snap, effectiveConfig);
        ((FishingHookMeta) getEntityMeta()).setOwnerEntity(shooter);
        ProjectileContext ctx = ProjectileContext.of(snap, services());
        this.rodPull = FieldValue.resolve(effectiveConfig.rodPull, ctx, RodPull.VANILLA_1_8);
        this.rodDurability = FieldValue.resolve(effectiveConfig.rodDurability, ctx, RodDurability.VANILLA_1_8);
        double lineSnap = FieldValue.resolve(effectiveConfig.lineSnapDistance, ctx, 32.0);
        this.lineSnapDistanceSq = lineSnap * lineSnap;
        this.hookedMetadata = FieldValue.resolve(effectiveConfig.hookedMetadata, ctx, Boolean.TRUE);
    }

    public @Nullable Entity getHookedEntity() { return hooked; }

    /** Hooks/releases the line ({@code hookedMetadata} gates the modern glued-bobber visual) and, hooked, zeroes the
     *  motion; the pin lands the same tick (see {@link #updateProjectile}). Public so a custom behavior can flash it (mmc18 pseudo-hook). */
    public void setHookedEntity(@Nullable Entity entity) {
        this.hooked = entity;
        if (hookedMetadata) ((FishingHookMeta) getEntityMeta()).setHookedEntity(entity);
        if (entity != null) setVelocity(Vec.ZERO);
    }

    @Override
    protected void onImpact(@Nullable Entity hitEntity) {
        super.onImpact(hitEntity);
        if (hitEntity == null) return;
        setHookedEntity(hitEntity);
        setDeflected(); // halt the hit tick's move at the hit point - don't carry the full tick THROUGH the victim
    }

    @Override
    protected void movementTick() {
        if (hooked != null) {
            if (hookedValid()) return; // no physics while hooked; the pin lands in updateProjectile
            setHookedEntity(null);
        }
        if (!latched) latch();
        if (modernOrder) {
            if (tickModernWater()) return; // the FLYING -> BOBBING damp tick doesn't move
        } else {
            if (contactPending) {
                // vanilla's next-tick unstick (xTile is never set, so inGround always clears): damp, then physics
                contactPending = false;
                ThreadLocalRandom r = ThreadLocalRandom.current();
                velocityBt = new Vec(velocityBt.x() * r.nextFloat() * 0.2f,
                        velocityBt.y() * r.nextFloat() * 0.2f,
                        velocityBt.z() * r.nextFloat() * 0.2f);
                this.velocity = velocityBt.mul(ServerFlag.SERVER_TICKS_PER_SECOND);
            }
            tickLegacyWater();
        }
        super.movementTick();
        // a real fall (settle nudges stay under 0.1) re-arms the landing events for the next ground contact
        if (grounded && !contactPending && Math.abs(velocityBt.y()) > 0.1) grounded = false;
    }

    private void latch() {
        Aerodynamics aero = getAerodynamics();
        gravityBt = aero.gravity();
        modernOrder = physicsOrder == ProjectileTypeConfig.PhysicsOrder.DRAG_BEFORE_MOVE;
        physicsOrder = ProjectileTypeConfig.PhysicsOrder.DRAG_AFTER_MOVE; // both integrators drag after the move
        setAerodynamics(new Aerodynamics(0, aero.horizontalAirResistance(), aero.verticalAirResistance()));
        latched = true;
    }

    /** 1.8: {@code motY += 0.04 * (2*coverage - 1)} (buoyant when submerged) rides the acceleration channel so it
     *  lands before drag; in water drag tightens to {@code 0.92*0.9} with an extra {@code 0.8} vertical. */
    private void tickLegacyWater() {
        double coverage = waterCoverage();
        float f2 = 0.92f;
        double verticalExtra = 1.0;
        if (coverage > 0) {
            f2 = (float) (f2 * 0.9);
            verticalExtra = 0.8;
        }
        setAcceleration(new Vec(0, gravityBt * (2 * coverage - 1), 0));
        setAerodynamics(new Aerodynamics(0, f2, verticalExtra * f2));
    }

    /** 26.1 water states + the gravity-before-move placement. Returns {@code true} on the water-entry tick (26.1
     *  damps the velocity and returns without moving). */
    private boolean tickModernWater() {
        Pos pos = getPosition();
        int blockY = (int) Math.floor(pos.y());
        double liquidHeight = Math.max(0, waterSurface(pos.x(), pos.y(), pos.z()) - blockY);
        boolean inWater = liquidHeight > 0;
        if (!bobbing && inWater) {
            velocityBt = velocityBt.mul(0.3, 0.2, 0.3);
            this.velocity = velocityBt.mul(ServerFlag.SERVER_TICKS_PER_SECOND);
            bobbing = true;
            return true;
        }
        if (bobbing && inWater) {
            double force = pos.y() + velocityBt.y() - (blockY + liquidHeight);
            if (Math.abs(force) < 0.01) force += Math.signum(force) * 0.1;
            velocityBt = new Vec(velocityBt.x() * TickScaler.dragPerTick(0.9),
                    velocityBt.y() - TickScaler.gravityPerTick(force * ThreadLocalRandom.current().nextFloat() * 0.2),
                    velocityBt.z() * TickScaler.dragPerTick(0.9));
        }
        if (!inWater) velocityBt = velocityBt.sub(0, TickScaler.gravityPerTick(gravityBt), 0);
        this.velocity = velocityBt.mul(ServerFlag.SERVER_TICKS_PER_SECOND);
        return false;
    }

    /** 1.8 water coverage: the 0.25 bobber box in 5 slabs, each counting when the water surface is above its bottom. */
    private double waterCoverage() {
        Pos pos = getPosition();
        double coverage = 0;
        for (int slab = 0; slab < 5; slab++) {
            double slabMin = pos.y() + BOX_HEIGHT * slab / 5;
            if (waterSurface(pos.x(), slabMin, pos.z()) > slabMin) coverage += 1.0 / 5;
        }
        return coverage;
    }

    /** Water surface Y of the block containing the point, or {@code -inf}: full when water continues above, else the
     *  1.8 level height ({@code 1 - (level+1)/9}, source = 8/9; falling counts as source). */
    private double waterSurface(double x, double y, double z) {
        Instance instance = getInstance();
        if (instance == null) return Double.NEGATIVE_INFINITY;
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
        Block block = instance.getBlock(bx, by, bz);
        if (!block.compare(Block.WATER)) return Double.NEGATIVE_INFINITY;
        if (instance.getBlock(bx, by + 1, bz).compare(Block.WATER)) return by + 1;
        String level = block.getProperty("level");
        int lvl = level != null ? Integer.parseInt(level) : 0;
        if (lvl >= 8) lvl = 0;
        return by + 1 - (lvl + 1) / 9.0;
    }

    @Override
    protected boolean canHit(@NotNull Entity entity) {
        return !bobbing && super.canHit(entity); // 26.1 only collides while FLYING; 1.8 never enters the state
    }

    @Override
    protected Pos stickPlacement(Pos resolvedPosition, Vec flightDir) {
        // 1.8 skips the move on the hit tick - the bobber halts short of the face, and the predicting client
        // halts at the same spot; landing at the face would snap it forward on the next correction
        return modernOrder ? super.stickPlacement(resolvedPosition, flightDir) : getPosition();
    }

    @Override
    protected boolean freezeOnStick() { return modernOrder; } // 1.8 never freezes: contact halts, then damp-settles

    @Override
    protected boolean onStuck() {
        if (modernOrder) return super.onStuck();
        contactPending = true;
        // vanilla zeroes ticksInAir on every unstick, so the owner (au >= 5 gate) can't hook themselves walking
        // over their grounded bobber
        rearmShooterImmunity();
        if (grounded) return false; // settle-cycle re-contacts: damp only, no repeated hit events
        grounded = true;
        return super.onStuck();
    }

    private boolean hookedValid() {
        return hooked != null && !hooked.isRemoved() && hooked.getInstance() == getInstance()
                && !(hooked instanceof LivingEntity living && living.isDead());
    }

    /** Holds the bobber at {@code target}; on a silent wire a changed pin is pushed explicitly - the 1.8 client has
     *  no hooked metadata, its "line to the victim" is the bobber BEING at the victim. */
    private void pinTo(Pos target) {
        Pos pos = getPosition();
        Pos pinned = target.withView(pos.yaw(), pos.pitch());
        refreshPosition(pinned, false, false);
        if (getSynchronizationTicks() <= 0 && !pinned.samePoint(lastSyncedPosition)) {
            sendPacketToViewersAndSelf(new EntityTeleportPacket(getEntityId(), pinned, Vec.ZERO, 0, false));
            this.lastSyncedPosition = pinned;
        }
    }

    @Override
    protected void updateProjectile(long time) {
        super.updateProjectile(time);
        if (isRemoved()) return;
        if (shouldStopFishing()) { remove(); return; }
        if (isStuck()) {
            if (++stuckTicks >= TickScaler.duration(GROUND_DESPAWN_TICKS, ProjectileSystem.KEY)) { remove(); return; }
        } else {
            stuckTicks = 0;
        }
        // pin at end-of-tick, past the hit tick's halt - the hook packets reach a predicting client that's already
        // ahead by its latency, so the pin must ride the SAME tick as the hook or it reads as a fly-through
        if (hooked != null && hookedValid()) {
            pinTo(hooked.getPosition().add(0, hooked.getBoundingBox().height() * 0.8, 0));
        }
    }

    private boolean shouldStopFishing() {
        Entity owner = getShooter();
        if (owner == null || owner.isRemoved()) return true;
        if (owner instanceof LivingEntity living) {
            if (living.isDead()) return true;
            if (!holdsRod(living)) return true;
        }
        return getPosition().distanceSquared(owner.getPosition()) > lineSnapDistanceSq;
    }

    private static boolean holdsRod(LivingEntity holder) {
        return holder.getItemInMainHand().material() == Material.FISHING_ROD
                || holder.getItemInOffHand().material() == Material.FISHING_ROD;
    }

    /** Reels the line in: pulls the hooked entity toward the angler per {@code rodPull}, removes the bobber, and
     *  returns the rod durability cost ({@code rodDurability}: hooked entity / stuck in ground / else 0). */
    public int retrieve() {
        int cost = 0;
        if (hookedValid()) {
            pullHooked();
            cost = rodDurability.entity();
        }
        if (isStuck() || isOnGround()) cost = rodDurability.ground();
        remove();
        return cost;
    }

    private void pullHooked() {
        Entity owner = getShooter();
        if (owner == null || hooked == null) return;
        if (hooked instanceof Player && !rodPull.pullPlayers()) return;
        Pos anglerPos = owner.getPosition();
        Pos pos = getPosition();
        Vec toAngler = new Vec(anglerPos.x() - pos.x(), anglerPos.y() - pos.y(), anglerPos.z() - pos.z());
        Vec delta = toAngler.mul(rodPull.factor())
                .add(0, Math.sqrt(toAngler.length()) * rodPull.yBoost(), 0);
        if (hooked instanceof Player player) {
            // fold onto the client's tracked motion; the knockback wire owns quantize + the legacy-exact bridge (b/s)
            Vec out = MotionTracker.positionDelta(player).add(delta).mul(ServerFlag.SERVER_TICKS_PER_SECOND);
            Services s = services();
            KnockbackSystem kb = s != null ? s.knockback() : null;
            if (kb != null) kb.deliver(player, out);
            else player.setVelocity(out);
        } else {
            hooked.setVelocity(hooked.getVelocity().add(delta.mul(ServerFlag.SERVER_TICKS_PER_SECOND)));
        }
    }

    @Override
    public void remove() {
        Entity owner = getShooter();
        if (owner != null && owner.getTag(ACTIVE_BOBBER) == this) owner.removeTag(ACTIVE_BOBBER);
        super.remove();
    }
}
