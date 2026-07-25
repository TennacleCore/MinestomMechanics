package io.github.term4.minestommechanics.mechanics.projectile.entities;

import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.mechanics.explosion.ExplosionSystem;
import io.github.term4.minestommechanics.world.MechanicsWorld;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileSnapshot;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig;
import net.minestom.server.ServerFlag;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.instance.Instance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Ghast/fire-charge fireball: a self-propelled projectile (constant acceleration along its aim, no gravity - vanilla
 * {@code mot += dir; mot *= 0.95}) that detonates on any contact. The {@link #onImpact} explosion routes through the
 * {@link ExplosionSystem} (knockback + falloff damage + the block-occlusion exposure that scales it). Like vanilla it
 * blows at the pre-move position ({@code onImpact} {@code getPosition()}) - the SAME centre drives knockback and block
 * destruction, and the explosion-KB model is byte-fit to that centre, so it must not move (block-break offset lives in
 * the fireball's FLIGHT speed, not the detonation point).
 */
public class FireballEntity extends ManagedProjectile {

    /** Vanilla ghast thrust along the aim each tick ({@code mot += aim·0.1; mot *= 0.95} -> terminal ~1.9). Independent of the launch speed. */
    private static final double SELF_PROPULSION = 0.1;
    /** The fireball's OWN collision box: a POINT (raytrace-like detonation), decoupled from its 1x1 hittable {@link #getBoundingBox()}. */
    private static final BoundingBox POINT = new BoundingBox(0, 0, 0);

    /** Vanilla ghast {@code yield = 1}; Hypixel 2. */
    private float explosionPower = 1.0f;
    /** Latches {@link #SELF_PROPULSION} once propulsion begins (after any coast). */
    private boolean propelled;
    /** Moving ticks to coast at the launch velocity before igniting; {@code 0} = propel immediately (vanilla). */
    private int coastTicks;
    /** Speed the ignition snaps to when the coast ends; {@code 0} = keep coasting into pure propulsion. */
    private double cruiseSpeed;
    private int moves;
    /** Break blocks from the CONTACT point instead of the pre-move centre (MineMen); KB/packet stay pre-move. */
    private boolean blockBreakAtContact;
    /** Contact nudged into the entered block, latched at impact while the flight velocity is still live. */
    private @Nullable Point contactBlockCenter;

    public FireballEntity(@Nullable Entity shooter, @NotNull EntityType entityType,
                          ProjectileSnapshot snap, ProjectileTypeConfig effectiveConfig) {
        super(shooter, entityType, snap, effectiveConfig);
    }

    public void setExplosionPower(float power) { this.explosionPower = power; }

    /** Coast at the launch velocity for {@code coastTicks} moving ticks, then snap to {@code cruiseSpeed} and start propulsion (Hypixel/MineMen fireball flight). */
    public void setIgnition(int coastTicks, double cruiseSpeed) {
        this.coastTicks = coastTicks;
        this.cruiseSpeed = cruiseSpeed;
    }

    public void setBlockBreakAtContact(boolean v) { this.blockBreakAtContact = v; }

    @Override
    protected boolean coasting() { return moves < coastTicks; }

    @Override
    protected boolean collidableTarget() { return true; } // vanilla fireball ad()=true: projectiles/attacks can hit + deflect it

    @Override
    protected BoundingBox collisionBox() { return POINT; } // detonate raytrace-like; getBoundingBox() (1x1) is the box others hit to deflect it

    @Override
    public boolean deflectBy(@Nullable Entity deflector) {
        // the owner can never deflect its own fireball (Hypixel capture: 13/13 self-hits ignored); a deflect
        // reassigns ownership, which also blocks instant re-deflect chains
        if (deflector == null || deflector == shooter) return false;
        // vanilla EntityFireball.damageEntity / 26.1 AIM_DEFLECT: motion = the deflector's look, propulsion re-latches
        Vec look = deflector.getPosition().direction();
        setVelocity(look.mul(ServerFlag.SERVER_TICKS_PER_SECOND)); // b/s; unit look = 1.0 b/t, like vanilla ap()
        setAcceleration(look.mul(SELF_PROPULSION));
        reassignShooter(deflector);
        rearmShooterImmunity(); // don't let the redirected fireball detonate on its new owner
        return true;
    }

    @Override
    protected void movementTick() {
        // propulsion latches only once flying under power (post-coast); coast() skips drag/accel so the hold stays exactly at launch speed
        if (!propelled && !coasting() && velocityBt().lengthSquared() > 1.0e-9) {
            setAcceleration(velocityBt().normalize().mul(SELF_PROPULSION));
            propelled = true;
        }
        boolean wasCoasting = coasting();
        super.movementTick();
        if (isStuck() || isRemoved()) return;
        if (wasCoasting && ++moves == coastTicks && cruiseSpeed > 0.0) {
            Vec v = velocityBt();
            // broadcast the ignition so the cruise value shows on the wire before the next tick propels past it
            if (v.lengthSquared() > 1.0e-12) setVelocity(v.normalize().mul(cruiseSpeed * ServerFlag.SERVER_TICKS_PER_SECOND));
        }
    }

    @Override
    protected void onImpact(@Nullable Entity hitEntity) {
        super.onImpact(hitEntity);
        // contact nudged BACK into the cell it arrived from (the on-face point would coin-flip the containing cell):
        // the blast acts from the AIR side of the hit face, so wood protruding from an end-stone wall breaks off a
        // near-miss while rays entering the wall still die inside it (user-observed; an into-the-block centre killed
        // every ray in the wall's first sample). Latched while the flight velocity is live - a behavior may detonate a tick later.
        if (blockBreakAtContact && blockContactPoint != null && velocityBt().lengthSquared() > 1.0e-12) {
            contactBlockCenter = blockContactPoint.sub(velocityBt().normalize().mul(1.0e-3));
        }
        // bare fireball detonates same-tick (vanilla); one carrying a behavior lets IT own the timing (Hypixel delays a tick)
        Instance instance = getInstance();
        if (!hasBehavior() && instance != null) detonate(instance, getPosition(), hitEntity);
    }

    /** {@code this} stays the source + impact-gate target, so a behavior can capture the center and fire this a tick later. */
    public void detonate(@NotNull Instance instance, @NotNull Point center, @Nullable Entity hitEntity) {
        Services s = services();
        ExplosionSystem explosion = s != null ? s.explosion() : null;
        Point blockCenter = contactBlockCenter != null ? contactBlockCenter : center;
        if (explosion != null) explosion.explode(MechanicsWorld.of(this, instance), center, blockCenter, explosionPower, this, hitEntity);
    }
}
