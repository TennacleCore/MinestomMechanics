package io.github.term4.minestommechanics.mechanics.projectile.entities;

import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.mechanics.explosion.ExplosionSystem;
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
 * {@link ExplosionSystem} (knockback + falloff damage + the block-occlusion exposure that scales it). Like vanilla, the
 * detonation is at the pre-move position (the {@code onImpact} {@code getPosition()}), not the swept collision point.
 */
public class FireballEntity extends ManagedProjectile {

    /** Vanilla ghast self-propulsion magnitude added along the aim each tick ({@code mot += aim·0.1; mot *= 0.95} -> terminal ~1.9). Independent of the launch speed. */
    private static final double SELF_PROPULSION = 0.1;
    /** The fireball's OWN collision box: a POINT (raytrace-like detonation), decoupled from its 1x1 hittable {@link #getBoundingBox()}. */
    private static final BoundingBox POINT = new BoundingBox(0, 0, 0);

    /** Explosion power on detonation (vanilla ghast {@code yield = 1}); the launcher stamps the configured value (Hypixel = 2). */
    private float explosionPower = 1.0f;
    /** Latches the self-propulsion vector ({@code aim·0.1}) on the first moving tick. */
    private boolean propelled;

    public FireballEntity(@Nullable Entity shooter, @NotNull EntityType entityType,
                          ProjectileSnapshot snap, ProjectileTypeConfig effectiveConfig) {
        super(shooter, entityType, snap, effectiveConfig);
    }

    /** Sets the explosion radius/power produced on detonation (the launcher stamps the resolved value). */
    public void setExplosionPower(float power) { this.explosionPower = power; }

    @Override
    protected boolean collidableTarget() { return true; } // vanilla fireball ad()=true: projectiles/attacks can hit + deflect it

    @Override
    protected BoundingBox collisionBox() { return POINT; } // detonate raytrace-like; getBoundingBox() (1x1) is the box others hit to deflect it

    @Override
    public boolean deflectBy(@Nullable Entity deflector) {
        // Hypixel: the owner can NEVER deflect its own fireball (capture: 13/13 self-hits at 23-167ms all ignored); this
        // also blocks instant re-deflect chains, since a deflect reassigns ownership to the deflector.
        if (deflector == null || deflector == shooter) return false;
        // vanilla EntityFireball.damageEntity / 26.1 AIM_DEFLECT: motion = the deflector's look (unit ~1.0 b/t), the
        // self-propulsion re-latches along it, and ownership transfers to the deflector.
        Vec look = deflector.getPosition().direction();
        setVelocity(look.mul(ServerFlag.SERVER_TICKS_PER_SECOND)); // b/s; unit look = 1.0 b/t, like vanilla ap()
        setAcceleration(look.mul(SELF_PROPULSION));
        reassignShooter(deflector);
        rearmShooterImmunity(); // don't let the redirected fireball detonate on its new owner
        return true;
    }

    @Override
    protected void movementTick() {
        // latch self-propulsion (aim·0.1) on the first moving tick, decoupled from launch speed (Hypixel launches ~1.0 b/t but still propels 0.1/tick toward the ~1.9 terminal)
        if (!propelled && velocityBt().lengthSquared() > 1.0e-9) {
            setAcceleration(velocityBt().normalize().mul(SELF_PROPULSION));
            propelled = true;
        }
        super.movementTick();
    }

    @Override
    protected void onImpact(@Nullable Entity hitEntity) {
        super.onImpact(hitEntity);
        // bare fireball detonates same-tick (vanilla 1.8 EntityLargeFireball.a / 26.1 LargeFireball.onHit); one carrying a behavior lets IT own the timing (Hypixel delays a tick so the projectile KB lands first)
        Instance instance = getInstance();
        if (!hasBehavior() && instance != null) detonate(instance, getPosition(), hitEntity);
    }

    /** Fires this fireball's explosion at {@code center} via the {@link ExplosionSystem}; {@code this} stays the source + impact-gate target, so a behavior can capture the center and schedule this a tick later. */
    public void detonate(@NotNull Instance instance, @NotNull Point center, @Nullable Entity hitEntity) {
        Services s = services();
        ExplosionSystem explosion = s != null ? s.explosion() : null;
        if (explosion != null) explosion.explode(instance, center, explosionPower, this, hitEntity);
    }
}
