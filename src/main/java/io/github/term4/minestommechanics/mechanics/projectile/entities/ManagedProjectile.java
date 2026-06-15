package io.github.term4.minestommechanics.mechanics.projectile.entities;

import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.api.event.ProjectileHitEvent;
import io.github.term4.minestommechanics.mechanics.damage.DamageSnapshot;
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
 * Generic config-driven projectile. The HIT knobs are resolved at IMPACT (not launch) against a
 * {@link ProjectileContext} carrying the struck target + throw-time origin, so config lambdas can branch on
 * {@code ctx.isSelfHit()} / {@code ctx.throwOrigin()} without the event API. On an entity hit it applies the resolved
 * damage (via the {@link io.github.term4.minestommechanics.mechanics.damage.DamageSystem}) and knockback (via the
 * {@link KnockbackSystem}), then removes per {@code removeOnEntityHit}; a self-hit answered {@code PASS_THROUGH}
 * passes through (or {@code DEFLECT} bounces off). On a block hit it removes per {@code removeOnBlockHit}. Both fire the cancellable
 * {@link ProjectileHitEvent} first. Types with extra behavior (egg, pearl) override {@link #onImpact}.
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

        // Pre-damage outcome: a listener-forced response wins; else the self-hit knob when the target is the shooter
        // (vanilla snowball/egg HIT, the 1.8 pearl PASS_THROUGH); else a normal hit. PASS/DEFLECT/DESTROY short-circuit;
        // HIT falls through to the damage path (which may itself trigger the invuln fallback below).
        ProjectileTypeConfig.HitResponse pre = ev.response() != null ? ev.response()
                : (target == shooter ? hit.selfHit() : ProjectileTypeConfig.HitResponse.HIT);
        switch (pre) {
            case HIT -> { /* normal hit below */ }
            case PASS_THROUGH -> { passThrough(hit, target); return false; }
            case DEFLECT -> { bounce(hit, target); return false; }
            case DESTROY -> { fireImpact(target); return true; }
        }

        boolean landed = applyDamageAndKnockback(target, ev);
        // Hit blocked (target invulnerable / creative): the configured invulnHit response. Vanilla arrow PASSES THROUGH
        // (1.8 nulls the hit on a creative/invulnerable target); throwables DESTROY (break, like their die()). HIT and
        // DESTROY break via the impact path below.
        if (!landed) {
            switch (hit.invulnHit()) {
                case PASS_THROUGH -> { passThrough(hit, target); return false; }
                case DEFLECT -> { bounce(hit, target); return false; }
                case HIT, DESTROY -> { /* fall through to onImpact + removeOnHit */ }
            }
        }
        fireImpact(target);
        return ev.removeOnHit();
    }

    /**
     * Routes the hit through the DAMAGE system first (vanilla: even a 0-damage thrown hit calls damageEntity - plays
     * the hurt flash + opens/checks the invul window, the GATE), then applies the knockback only if it landed (a hit on
     * an already-invulnerable victim returns BLOCKED, suppressing KB). With no damageType there is no gate and the hit
     * always lands. Damage amount / knockback / source are the event's effective values (per-hit overrides ?? resolved).
     */
    private boolean applyDamageAndKnockback(@NotNull Entity target, ProjectileHitEvent ev) {
        Services s = services();
        if (s == null) return true;
        boolean landed = true;
        DamageType dt = ev.resolvedHit().damageType();
        if (dt != null && s.damage() != null) {
            landed = s.damage().apply(DamageSnapshot.of(target, dt)
                    .withSource(shooter).withPoint(getPosition()).withAmount((float) ev.damage())).landed();
        }
        // PROJECTILE-relative (origin = projectile, dir = flight) or SHOOTER-relative (yaw via the KB config's
        // yawWeight). The knockback owns the velocity broadcast.
        if (landed && ev.knockback() != null && s.knockback() != null) {
            s.knockback().apply(buildKnockback(target, ev.knockbackSource(), ev.knockback()));
        }
        return landed;
    }

    /** A {@code DEFLECT}: bounce off per the {@link ProjectileTypeConfig.Deflect} knob; (opt-in) flag the arrow visible - the crit trail
     *  fires in {@code ArrowEntity} while it bounces. Fires the behavior's {@link ProjectileBehavior#onDeflect}. */
    private void bounce(ResolvedHit hit, @Nullable Entity hitEntity) {
        deflect(hit.deflect());
        if (hit.deflectParticles()) deflectVisible = true;
        behavior.onDeflect(this, hitEntity);
    }

    /** A {@code PASS_THROUGH}: keep flying; (opt-in) flag {@code deflectVisible} so a server-side crit trail traces the
     *  path for 1.8 viewers (whose arrow entity goes invisible on the touch - a native 1.8 client bug, not fixable
     *  server-side). Fires the behavior's {@link ProjectileBehavior#onDeflect}. */
    private void passThrough(ResolvedHit hit, @Nullable Entity hitEntity) {
        if (hit.deflectParticles()) deflectVisible = true;
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
     * Type-specific impact effect, fired once a hit lands (entity OR block) and is not cancelled, after the
     * damage/knockback pipeline and before removal. {@code hitEntity} is the struck entity, or {@code null} for a
     * block hit. Override for egg (spawn chicken), ender pearl (teleport the shooter), etc. - both vanilla effects
     * fire on entity and block impact alike. Default: no-op. Branch on {@code hitEntity == getShooter()} for a
     * self-vs-other effect (a self-hit answered {@code PASS_THROUGH}/{@code DEFLECT} never reaches here).
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
     * Bounces the projectile off an entity it may not damage (an invuln hit, or a {@code DEFLECT} self-hit) - keep
     * flying, no damage/KB/break. The {@link ProjectileTypeConfig.Deflect} knob transforms the velocity (vanilla 1.8
     * {@code deflect(-0.1)} = {@code motion *= -0.1}; 26.1 {@code deflect(-0.5, 0, -10, 10)} = {@code -0.5} + a cosmetic
     * +-10-degree turn), then re-arm shooter immunity (1.8 {@code as = 0}) so the bounced-back arrow can't instantly
     * re-hit the shooter / loop on a self-deflect. The yaw flip falls out of the displacement rotation.
     */
    protected void deflect(ProjectileTypeConfig.Deflect d) {
        setVelocityBt(applyDeflect(d, velocityBt));
        rearmShooterImmunity();
        setDeflected();
    }

    /** Applies a {@link ProjectileTypeConfig.Deflect}: scale velocity by {@code multiplier} (negative reverses all axes)
     *  and rotate the horizontal heading by an extra {@code turn} + a random {@code [minJitter, maxJitter]} wobble (degrees). */
    private static Vec applyDeflect(ProjectileTypeConfig.Deflect d, Vec velocity) {
        Vec v = velocity.mul(d.multiplier());
        double extra = d.turn() + (d.maxJitter() > d.minJitter()
                ? ThreadLocalRandom.current().nextDouble(d.minJitter(), d.maxJitter()) : d.minJitter());
        if (extra == 0) return v;
        double rad = Math.toRadians(extra), cos = Math.cos(rad), sin = Math.sin(rad);
        return new Vec(v.x() * cos - v.z() * sin, v.y(), v.x() * sin + v.z() * cos);
    }

    /** Builds the hit knockback snapshot for the given {@link ProjectileTypeConfig.KnockbackSource} + config. */
    private KnockbackSnapshot buildKnockback(@NotNull Entity target, ProjectileTypeConfig.KnockbackSource source, KnockbackConfig kb) {
        if (shooter != null && source == ProjectileTypeConfig.KnockbackSource.SHOOTER) {
            // Source = shooter (like melee): the KnockbackCalculator reads the shooter's position + look, so the KB
            // config's yawWeight chooses shooter -> victim (0) vs the shooter's yaw (1) - no special source needed.
            return new KnockbackSnapshot(target, false, shooter, null, null, kb);
        }
        // PROJECTILE (or no shooter): origin = projectile position, direction = horizontal flight path.
        Vec h = new Vec(velocityBt.x(), 0, velocityBt.z());
        Vec flightDir = h.lengthSquared() < 1e-9 ? null : h.normalize();
        return new KnockbackSnapshot(target, false, null, getPosition(), flightDir, kb);
    }

    /** Live services lookup (the systems are install-time singletons); null-tolerant if none installed. */
    protected @Nullable Services services() {
        var mm = io.github.term4.minestommechanics.MinestomMechanics.getInstance();
        return mm.isInitialized() ? mm.services() : null;
    }
}
