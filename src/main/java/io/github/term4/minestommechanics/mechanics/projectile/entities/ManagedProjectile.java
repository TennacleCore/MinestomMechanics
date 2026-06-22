package io.github.term4.minestommechanics.mechanics.projectile.entities;

import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.api.event.ProjectileHitEvent;
import io.github.term4.minestommechanics.mechanics.damage.DamageSnapshot;
import io.github.term4.minestommechanics.mechanics.damage.DamageSystem;
import io.github.term4.minestommechanics.mechanics.damage.types.DamageType;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfig;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackSnapshot;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackSystem;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileBehavior;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileConfigResolver;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileConfigResolver.ProjectileContext;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileConfigResolver.ResolvedHit;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileSnapshot;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.event.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Generic config-driven projectile. Hit knobs resolve at impact against a {@link ProjectileContext} (struck target +
 * throw origin). On an entity hit it applies the resolved damage ({@link DamageSystem}) + knockback ({@link KnockbackSystem})
 * then removes per {@code removeOnEntityHit}; a self-hit may pass through or deflect. Block hits remove per
 * {@code removeOnBlockHit}. Both fire the cancellable {@link ProjectileHitEvent}. Egg/pearl override {@link #onImpact}.
 */
public class ManagedProjectile extends ProjectileEntity {

    /** The merged per-type config (FieldValues unresolved); hit knobs resolve from it at impact. */
    private final ProjectileTypeConfig effectiveConfig;
    private final ProjectileSnapshot snap;
    /** Pluggable behavior layered over the built-in hooks (set by the launcher from config/snapshot); default no-op. */
    private ProjectileBehavior behavior = ProjectileBehavior.NONE;
    /** Latches the one-time {@link ProjectileBehavior#onSpawn} on the first tick after entering the world. */
    private boolean spawned;

    public ManagedProjectile(@Nullable Entity shooter, @NotNull EntityType entityType,
                             ProjectileSnapshot snap, ProjectileTypeConfig effectiveConfig) {
        super(shooter, entityType);
        this.snap = snap;
        this.effectiveConfig = effectiveConfig;
    }

    /** Sets the pluggable {@link ProjectileBehavior} (the launcher applies the resolved config / snapshot override). */
    public void setBehavior(@Nullable ProjectileBehavior behavior) { this.behavior = behavior != null ? behavior : ProjectileBehavior.NONE; }

    /** Resolves the hit knobs at impact: {@code target} is the struck entity, or {@code null} for a block hit. */
    private ResolvedHit resolveHit(@Nullable Entity target) {
        ProjectileContext ctx = ProjectileContext.of(snap, services())
                .atHit(target, getShooterOriginPos(), getPosition());
        return ProjectileConfigResolver.resolveHit(effectiveConfig, ctx);
    }

    @Override
    protected boolean onHit(@NotNull Entity target) {
        ResolvedHit hit = resolveHit(target);
        ProjectileHitEvent ev = new ProjectileHitEvent(this, snap, shooter, target, getPosition(),
                getShooterOriginPos(), hit, hitDamage(hit, target));
        EventDispatcher.call(ev);
        if (ev.isCancelled()) return false;

        // pre-damage outcome: a forced response wins, else the self-hit knob when target == shooter, else a normal HIT
        ProjectileTypeConfig.HitResponse pre = ev.response() != null ? ev.response()
                : (target == shooter ? hit.selfHit() : ProjectileTypeConfig.HitResponse.HIT);
        switch (pre) {
            case HIT -> { /* normal hit below */ }
            case PASS_THROUGH -> { passThrough(hit, target); return false; }
            case DEFLECT -> { bounce(hit, target); return false; }
            case DESTROY -> { fireImpact(target); return true; }
        }

        DamageSystem.DamageOutcome result = applyDamageAndKnockback(target, ev);
        // didn't land: the InvulnResponse picks by why - IMMUNE (creative/spectator) vs BLOCKED (invul window)
        if (!result.landed()) {
            ProjectileTypeConfig.InvulnResponse ir = hit.invulnHit();
            ProjectileTypeConfig.HitResponse blocked = result == DamageSystem.DamageOutcome.IMMUNE ? ir.immune() : ir.invulWindow();
            switch (blocked) {
                case PASS_THROUGH -> { passThrough(hit, target); return false; }
                case DEFLECT -> { bounce(hit, target); return false; }
                case HIT, DESTROY -> { /* fall through to onImpact + removeOnHit */ }
            }
        }
        fireImpact(target);
        return ev.removeOnHit();
    }

    /**
     * Routes the hit through damage first (vanilla: even a 0-damage thrown hit calls damageEntity - the hurt flash + invul
     * gate), then knockback only if it landed. With no damage type the hit always lands. Values are the event's effective ones.
     */
    private DamageSystem.DamageOutcome applyDamageAndKnockback(@NotNull Entity target, ProjectileHitEvent ev) {
        Services s = services();
        if (s == null) return DamageSystem.DamageOutcome.FRESH_DAMAGE;
        DamageSystem.DamageOutcome result = DamageSystem.DamageOutcome.FRESH_DAMAGE; // no damage type -> nothing absorbs the hit
        DamageType dt = ev.resolvedHit().damageType();
        if (dt != null && s.damage() != null) {
            result = s.damage().apply(DamageSnapshot.of(target, dt)
                    .withSource(shooter).withPoint(getPosition()).withAmount((float) ev.damage()));
        }
        // projectile-relative (origin = projectile) or shooter-relative; the knockback owns the velocity broadcast
        if (result.landed() && ev.knockback() != null && s.knockback() != null) {
            s.knockback().apply(buildKnockback(target, ev.knockbackSource(), ev.knockback()));
        }
        return result;
    }

    /** A {@code DEFLECT}: bounce off per the {@link ProjectileTypeConfig.Deflect} knob + (opt-in) the cosmetic crit trail. Fires {@link ProjectileBehavior#onDeflect}. */
    private void bounce(ResolvedHit hit, @Nullable Entity hitEntity) {
        deflect(hit.deflect());
        if (deflectTrailEnabled()) deflectVisible = true;
        behavior.onDeflect(this, hitEntity);
    }

    /** Whether the cosmetic deflect crit-trail is on for this shooter ({@code deflectParticles}, resolved via {@code FixesSystem}; default off). */
    private boolean deflectTrailEnabled() {
        Services s = services();
        if (s == null) return false;
        var fixes = s.fixes();
        return fixes != null && fixes.legacyArrowDeflectParticles(shooter);
    }

    /** A {@code PASS_THROUGH}: keep flying + (opt-in) the cosmetic crit trail. Fires {@link ProjectileBehavior#onDeflect}. */
    private void passThrough(ResolvedHit hit, @Nullable Entity hitEntity) {
        if (deflectTrailEnabled()) deflectVisible = true;
        behavior.onDeflect(this, hitEntity);
    }

    @Override
    protected boolean onStuck() {
        ResolvedHit hit = resolveHit(null);
        ProjectileHitEvent ev = new ProjectileHitEvent(this, snap, shooter, null, getPosition(),
                getShooterOriginPos(), hit, 0);
        EventDispatcher.call(ev);
        if (ev.isCancelled()) return false;
        behavior.onStuck(this);
        fireImpact(null);
        return ev.removeOnHit(); // block hit -> removeOnBlockHit (override ?? resolved)
    }

    /**
     * Type-specific impact effect, fired once a hit lands (entity or block) after the damage/knockback pipeline and
     * before removal. {@code hitEntity} = the struck entity, or {@code null} for a block hit. Override for egg/pearl. Default no-op.
     */
    protected void onImpact(@Nullable Entity hitEntity) {}

    /** Fires the type's {@link #onImpact} effect, then the pluggable {@link ProjectileBehavior#onImpact}. */
    private void fireImpact(@Nullable Entity hit) {
        onImpact(hit);
        behavior.onImpact(this, hit);
    }

    @Override
    protected void onUnstuck() {
        super.onUnstuck();
        behavior.onUnstuck(this);
    }

    @Override
    protected void updateProjectile(long time) {
        super.updateProjectile(time);
        if (!spawned) { spawned = true; behavior.onSpawn(this); } // first tick in the world
        behavior.onTick(this, time);
    }

    @Override
    public void remove() {
        if (!isRemoved()) behavior.onRemove(this); // once, before Minestom tears the entity down
        super.remove();
    }

    /**
     * The damage to deal to {@code target} on an entity hit. Default: the resolved config {@code damage} (a flat
     * amount). Arrows override this to compute vanilla velocity-based damage ({@code ceil(speed * 2) + crit}).
     */
    protected float hitDamage(ResolvedHit hit, @NotNull Entity target) { return (float) hit.damage(); }

    /**
     * Bounces the projectile off an entity it may not damage - keep flying, no damage/KB/break. The
     * {@link ProjectileTypeConfig.Deflect} knob transforms the velocity (1.8 {@code *-0.1}; 26.1 {@code *-0.5} + a small
     * turn), then re-arms shooter immunity so the bounced arrow can't instantly re-hit the shooter.
     */
    protected void deflect(ProjectileTypeConfig.Deflect d) {
        setVelocityBt(applyDeflect(d, velocityBt));
        rearmShooterImmunity();
        setDeflected();
    }

    /** Applies a {@link ProjectileTypeConfig.Deflect}: scale velocity by {@code multiplier} and rotate the heading by {@code turn} + a random jitter (degrees). */
    private static Vec applyDeflect(ProjectileTypeConfig.Deflect d, Vec velocity) {
        Vec v = velocity.mul(d.multiplier());
        double extra = d.turn() + (d.maxJitter() > d.minJitter()
                ? ThreadLocalRandom.current().nextDouble(d.minJitter(), d.maxJitter()) : d.minJitter());
        if (extra == 0) return v;
        double rad = Math.toRadians(extra), cos = Math.cos(rad), sin = Math.sin(rad);
        return new Vec(v.x() * cos - v.z() * sin, v.y(), v.x() * sin + v.z() * cos);
    }

    /**
     * Builds the hit knockback snapshot for the given {@link ProjectileTypeConfig.KnockbackSource} + config. The captured
     * {@link #punchLevel()} rides as the extra-knockback level (vanilla's {@code i}), scaling the config's {@code extra}*
     * knobs - the same channel the melee Knockback enchant uses; {@code 0} (no Punch) leaves the extra term inert.
     */
    private KnockbackSnapshot buildKnockback(@NotNull Entity target, ProjectileTypeConfig.KnockbackSource source, KnockbackConfig kb) {
        if (shooter != null && source == ProjectileTypeConfig.KnockbackSource.SHOOTER) {
            // shooter source (like melee): the calculator reads the shooter's position + look (yawWeight picks aim vs direction)
            return new KnockbackSnapshot(target, false, shooter, null, null, kb, punchLevel());
        }
        // projectile source: origin = projectile, direction = horizontal flight
        Vec h = new Vec(velocityBt.x(), 0, velocityBt.z());
        Vec flightDir = h.lengthSquared() < 1e-9 ? null : h.normalize();
        return new KnockbackSnapshot(target, false, null, getPosition(), flightDir, kb, punchLevel());
    }

    /** Live services lookup (the systems are install-time singletons); null-tolerant if none installed. */
    protected @Nullable Services services() {
        var mm = io.github.term4.minestommechanics.MinestomMechanics.getInstance();
        return mm.isInitialized() ? mm.services() : null;
    }
}
