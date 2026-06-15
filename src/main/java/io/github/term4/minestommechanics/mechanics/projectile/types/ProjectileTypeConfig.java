package io.github.term4.minestommechanics.mechanics.projectile.types;

import io.github.term4.minestommechanics.config.Config;
import io.github.term4.minestommechanics.config.FieldValue;
import io.github.term4.minestommechanics.mechanics.damage.types.DamageType;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfig;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileBehavior;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileConfig;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileConfigResolver.ProjectileContext;
import io.github.term4.minestommechanics.tracking.VelocityRule;
import net.kyori.adventure.key.Key;
import net.minestom.server.collision.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Per-type projectile configuration: everything tunable about one projectile (snowball, arrow, ...), keyed by
 * {@link #key()}. Extends the shared {@link Config} base like {@link io.github.term4.minestommechanics.mechanics.attack.AttackConfig}
 * and {@link ProjectileConfig}, so every value is a public {@link FieldValue} resolved against a {@link ProjectileContext}
 * by {@code ProjectileConfigResolver} - any knob can be a constant or a per-launch lambda (e.g. read {@code ctx.item()}
 * for a custom item). Unset fields fall back through the resolver chain - per-type override -&gt; the active
 * {@link ProjectileConfig}'s generic {@link ProjectileConfig#defaults() defaults} -&gt; the type's intrinsic
 * {@link ProjectileType#defaultConfig()} -&gt; hard fallbacks - so a sparse override only restates what it changes,
 * and presets keep the shared baseline in one generic config (e.g. {@code Vanilla18.projectileDefaults()}).
 *
 * <p>Configurable parity with the old system: bounding box, spawn offset (forward + vertical + sideways from eye),
 * initial speed/power curve, spread, shooter-momentum scale (horizontal + vertical) + its {@link VelocityRule} source,
 * aerodynamics (gravity + horizontal/vertical drag), shooter-immunity ticks, position/velocity-sync intervals, the hit
 * {@link KnockbackConfig} and {@link DamageType}/amount, the {@link Deflect deflect transform}, the remove-on-hit policies, and a
 * pluggable {@link ProjectileBehavior}. Launchers resolve this once per launch and stamp the entity - entities stay dumb.
 */
public final class ProjectileTypeConfig extends Config<ProjectileContext, ProjectileTypeConfig> {

    /**
     * Where a hit's knockback originates. The two frames are orthogonal to the {@link KnockbackConfig}'s direction
     * blending ({@code yawWeight}): the source picks the origin + the look basis, the config shapes the final vector.
     * <ul>
     *   <li>{@link #PROJECTILE} - origin = the projectile, look basis = its flight path (vanilla default).</li>
     *   <li>{@link #SHOOTER} - origin = the shooter, look basis = the shooter's facing. Carries the shooter as the
     *       snapshot {@code source} (like melee), so {@code yawWeight = 0} pushes shooter -&gt; victim and
     *       {@code yawWeight = 1} pushes along the shooter's yaw - e.g. a self-hit KB along the look is just a
     *       {@code KnockbackConfig} with {@code yawWeight(1)}, no special source.</li>
     * </ul>
     * Full control is available by cancelling {@code ProjectileHitEvent} and applying your own snapshot.
     */
    public enum KnockbackSource { PROJECTILE, SHOOTER }

    /**
     * What a projectile does for a hit it does not deal normally - used by BOTH {@link #selfHit} (it hit its own
     * shooter) and {@link #invulnHit} (the hit was blocked because the target was invulnerable / creative):
     * <ul>
     *   <li>{@link #HIT} - the normal hit (damage / knockback / impact / break per {@code removeOnEntityHit}). For
     *       {@code selfHit} = "you may hit yourself"; for {@code invulnHit} it is N/A (the hit already did not land)
     *       and behaves like {@link #DESTROY}.</li>
     *   <li>{@link #PASS_THROUGH} - ignore the entity; the projectile keeps flying unchanged (the 1.8 ender pearl
     *       through its thrower; a Hypixel "self does nothing").</li>
     *   <li>{@link #DEFLECT} - bounce off per the {@link #deflect}; no damage/KB/break, keeps flying.</li>
     *   <li>{@link #DESTROY} - break the projectile (its impact effect fires, then it is removed); no damage/KB. The
     *       vanilla throwable response to an invuln hit (snowball/egg/pearl {@code die()} regardless).</li>
     * </ul>
     * Note: "bypass" (deal the hit anyway despite invul) is NOT here - that is the damage type's {@code bypassInvul}
     * (a damage-config concern); if a type bypasses, the hit lands and never reaches {@code invulnHit}.
     */
    public enum HitResponse { HIT, PASS_THROUGH, DEFLECT, DESTROY }

    /**
     * When a projectile applies drag + gravity relative to its per-tick move - the documented 1.8 -&gt; 26.1 delta:
     * <ul>
     *   <li>{@link #DRAG_AFTER_MOVE} - move first, then decay (vanilla 1.8 {@code EntityProjectile}/{@code EntityArrow}).</li>
     *   <li>{@link #DRAG_BEFORE_MOVE} - decay first, then move with the decayed velocity (vanilla 26.1 {@code Projectile}).</li>
     * </ul>
     */
    public enum PhysicsOrder { DRAG_AFTER_MOVE, DRAG_BEFORE_MOVE }

    /**
     * Pickup geometry for a collectable projectile (arrows). A projectile is collected when its {@code boxWidth x
     * boxHeight} box intersects a player's bounding box inflated by {@code (inflateH, inflateV, inflateH)} - the exact
     * vanilla test ({@code Player#aiStep} / 1.8 {@code EntityHuman}: {@code grow(1, 0.5, 1)}; arrow box
     * {@code setSize(0.5, 0.5)}). Defaults are vanilla; only {@code ArrowEntity} reads it.
     */
    public record PickupBox(double inflateH, double inflateV, double boxWidth, double boxHeight) {
        /** Vanilla 1.8 / 26.1 pickup geometry: player box inflated (1, 0.5, 1), arrow box 0.5 x 0.5. */
        public static final PickupBox VANILLA = new PickupBox(1.0, 0.5, 0.5, 0.5);
    }

    /**
     * How a {@link HitResponse#DEFLECT} transforms the velocity (the {@link #deflect} knob): scale by {@code multiplier}
     * (a NEGATIVE value reverses all three axes - the vanilla bounce), then rotate the horizontal heading by an EXTRA
     * {@code turn} degrees plus a random wobble in {@code [minJitter, maxJitter]}. The rendered yaw follows from the
     * displacement-based rotation. Vanilla 1.8 ({@code motion *= -0.1}) = {@code deflect(-0.1)}; vanilla 26.1
     * {@code ProjectileDeflection.REVERSE} ({@code *= -0.5} + {@code 170 + random*20} yaw) = {@code deflect(-0.5, 0, -10, 10)}.
     */
    public record Deflect(double multiplier, double turn, double minJitter, double maxJitter) {
        /** Plain reverse + damp with no extra turn: {@code motion *= multiplier} (negative reverses). */
        public static Deflect of(double multiplier) { return new Deflect(multiplier, 0.0, 0.0, 0.0); }
    }

    /** Type identity (not a knob): the key this config is registered under. */
    public final Key key;

    public final @Nullable FieldValue<ProjectileContext, Boolean> enabled;
    public final @Nullable FieldValue<ProjectileContext, BoundingBox> boundingBox;
    public final @Nullable FieldValue<ProjectileContext, Double> gravity;
    public final @Nullable FieldValue<ProjectileContext, Double> horizontalDrag;
    public final @Nullable FieldValue<ProjectileContext, Double> verticalDrag;
    /** Forward spawn offset along the shooter's look direction (blocks). */
    public final @Nullable FieldValue<ProjectileContext, Double> spawnOffsetForward;
    /** Vertical spawn offset from the shooter's eye height (blocks). */
    public final @Nullable FieldValue<ProjectileContext, Double> spawnOffsetVertical;
    /** Sideways spawn offset perpendicular to the look (vanilla 1.8 throwing-hand shift, {@code 0.16}; 26.1: 0). */
    public final @Nullable FieldValue<ProjectileContext, Double> spawnOffsetSideways;
    public final @Nullable FieldValue<ProjectileContext, Double> speed;
    public final @Nullable FieldValue<ProjectileContext, Double> spread;
    /** Fraction of the shooter's horizontal velocity (x/z) folded into the launch velocity. {@code 0} = none (vanilla
     *  1.8); {@code 1} = full (vanilla 26.1). The velocity is read via {@link #velocity} (its {@link VelocityRule}). */
    public final @Nullable FieldValue<ProjectileContext, Double> momentumHorizontal;
    /** Fraction of the shooter's vertical velocity (y) folded in. {@code 0} = none (vanilla 1.8); 26.1 folds it only
     *  when airborne - express that as a lambda, e.g. {@code ctx -> ctx.shooter().isOnGround() ? 0 : 1}. */
    public final @Nullable FieldValue<ProjectileContext, Double> momentumVertical;
    /** How the shooter's velocity is read for momentum inheritance. Resolution mirrors knockback: this override -&gt;
     *  the shooter's scoped {@code MechanicsProfile.velocity} -&gt; {@link VelocityRule#DEFAULT}. 26.1 = {@code delta()}
     *  ({@code getKnownMovement}); only matters when a momentum scale is non-zero. */
    public final @Nullable FieldValue<ProjectileContext, VelocityRule> velocity;
    public final @Nullable FieldValue<ProjectileContext, Integer> shooterImmunityTicks;
    /** Entity-hit margin: the target's bbox grows by this on EACH side for the hit ray-test (vanilla 1.8 {@code 0.3}
     *  for arrows + throwables); a flight knob, stamped on the entity and read per-tick. */
    public final @Nullable FieldValue<ProjectileContext, Double> entityHitGrow;
    /** What the projectile does when it hits its own shooter ({@link HitResponse}; default {@code HIT} = vanilla). */
    public final @Nullable FieldValue<ProjectileContext, HitResponse> selfHit;
    public final @Nullable FieldValue<ProjectileContext, Integer> syncInterval;
    /** How often (ticks) to broadcast the in-flight velocity: {@code <= 0} = NEVER (vanilla arrow - velocity is sent
     *  once in the spawn packet, the client predicts the rest; this is what fixes the block-edge "slide"), {@code 1} =
     *  every tick (Minestom default), {@code N} = every N ticks. A flight knob, stamped on the entity. */
    public final @Nullable FieldValue<ProjectileContext, Integer> velocitySyncInterval;
    /** When drag + gravity apply relative to the per-tick move ({@link PhysicsOrder}): 1.8 = {@code DRAG_AFTER_MOVE}
     *  (default), 26.1 = {@code DRAG_BEFORE_MOVE}. A flight knob, stamped on the entity. */
    public final @Nullable FieldValue<ProjectileContext, PhysicsOrder> physicsOrder;
    /** Shooter immunity model: {@code false} (default, 1.8) = immune for the fixed {@link #shooterImmunityTicks}; {@code true}
     *  (26.1) = immune until the projectile geometrically LEAVES the shooter's bounding box. A flight knob. */
    public final @Nullable FieldValue<ProjectileContext, Boolean> leftOwnerImmunity;
    /** Distance (blocks) a stuck projectile is pulled back along its flight direction so the tip pokes out of the block
     *  face instead of sitting flush (vanilla {@code 0.05}). A flight knob, stamped on the entity. */
    public final @Nullable FieldValue<ProjectileContext, Double> stickPullback;
    /** Pickup cooldown (ticks) after a collectable projectile sticks - the vanilla arrow {@code shake} ({@code 7}); no
     *  collecting until it elapses. Arrow-only (like {@link #pickupBox}); a flight knob. */
    public final @Nullable FieldValue<ProjectileContext, Integer> shakeTicks;
    /** Pluggable behavior (onImpact/onStuck/onUnstuck/onTick) layered over the projectile's built-in effects, so a
     *  server can change behavior without subclassing the entity. Default {@link ProjectileBehavior#NONE}. */
    public final @Nullable FieldValue<ProjectileContext, ProjectileBehavior> behavior;
    public final @Nullable FieldValue<ProjectileContext, KnockbackConfig> knockback;
    public final @Nullable FieldValue<ProjectileContext, KnockbackSource> knockbackSource;
    public final @Nullable FieldValue<ProjectileContext, Double> damage;
    public final @Nullable FieldValue<ProjectileContext, DamageType> damageType;
    public final @Nullable FieldValue<ProjectileContext, Boolean> removeOnEntityHit;
    public final @Nullable FieldValue<ProjectileContext, Boolean> removeOnBlockHit;
    /** What the projectile does when its hit is blocked (target invulnerable/creative): {@link HitResponse} -
     *  vanilla arrows {@code DEFLECT}, throwables {@code DESTROY} (default). */
    public final @Nullable FieldValue<ProjectileContext, HitResponse> invulnHit;
    /** How a {@link HitResponse#DEFLECT} transforms the velocity ({@link Deflect}): scale by {@code multiplier}
     *  (negative reverses) + an optional extra {@code turn} +- jitter. Vanilla 1.8 = {@code deflect(-0.1)}, vanilla
     *  26.1 = {@code deflect(-0.5, 0, -10, 10)} ({@code -0.5} reverse + a cosmetic +-10-degree wobble). Default {@code deflect(-0.1)}. */
    public final @Nullable FieldValue<ProjectileContext, Deflect> deflect;
    /** Cosmetic ONLY: make an arrow that bounced off OR passed through an entity visible again to 1.8 viewers who
     *  mispredicted the interaction - re-spawns the entity for them + a server-sent crit trail (a non-vanilla fix for
     *  the vanilla "arrow goes invisible after touching an entity" glitch; off by default, arrows only). */
    public final @Nullable FieldValue<ProjectileContext, Boolean> deflectParticles;
    /** Pickup geometry (collectable projectiles only, e.g. arrows); default {@link PickupBox#VANILLA}. */
    public final @Nullable FieldValue<ProjectileContext, PickupBox> pickupBox;

    ProjectileTypeConfig(Builder b) {
        super(b.subConfig);
        this.key = b.key;
        this.enabled = b.enabled;
        this.boundingBox = b.boundingBox;
        this.gravity = b.gravity;
        this.horizontalDrag = b.horizontalDrag;
        this.verticalDrag = b.verticalDrag;
        this.spawnOffsetForward = b.spawnOffsetForward;
        this.spawnOffsetVertical = b.spawnOffsetVertical;
        this.spawnOffsetSideways = b.spawnOffsetSideways;
        this.speed = b.speed;
        this.spread = b.spread;
        this.momentumHorizontal = b.momentumHorizontal;
        this.momentumVertical = b.momentumVertical;
        this.velocity = b.velocity;
        this.shooterImmunityTicks = b.shooterImmunityTicks;
        this.entityHitGrow = b.entityHitGrow;
        this.selfHit = b.selfHit;
        this.syncInterval = b.syncInterval;
        this.velocitySyncInterval = b.velocitySyncInterval;
        this.physicsOrder = b.physicsOrder;
        this.leftOwnerImmunity = b.leftOwnerImmunity;
        this.stickPullback = b.stickPullback;
        this.shakeTicks = b.shakeTicks;
        this.behavior = b.behavior;
        this.knockback = b.knockback;
        this.knockbackSource = b.knockbackSource;
        this.damage = b.damage;
        this.damageType = b.damageType;
        this.removeOnEntityHit = b.removeOnEntityHit;
        this.removeOnBlockHit = b.removeOnBlockHit;
        this.invulnHit = b.invulnHit;
        this.deflect = b.deflect;
        this.deflectParticles = b.deflectParticles;
        this.pickupBox = b.pickupBox;
    }

    public Key key() { return key; }

    /** Merges this config over {@code base}: this config's set fields win, unset fields fall back per resolution. */
    public ProjectileTypeConfig fromBase(ProjectileTypeConfig base) {
        Builder b = new Builder(key != null ? key : base.key);
        b.subConfig = subConfig != null ? subConfig : base.subConfig;
        b.enabled = merge(enabled, base.enabled);
        b.boundingBox = merge(boundingBox, base.boundingBox);
        b.gravity = merge(gravity, base.gravity);
        b.horizontalDrag = merge(horizontalDrag, base.horizontalDrag);
        b.verticalDrag = merge(verticalDrag, base.verticalDrag);
        b.spawnOffsetForward = merge(spawnOffsetForward, base.spawnOffsetForward);
        b.spawnOffsetVertical = merge(spawnOffsetVertical, base.spawnOffsetVertical);
        b.spawnOffsetSideways = merge(spawnOffsetSideways, base.spawnOffsetSideways);
        b.speed = merge(speed, base.speed);
        b.spread = merge(spread, base.spread);
        b.momentumHorizontal = merge(momentumHorizontal, base.momentumHorizontal);
        b.momentumVertical = merge(momentumVertical, base.momentumVertical);
        b.velocity = merge(velocity, base.velocity);
        b.shooterImmunityTicks = merge(shooterImmunityTicks, base.shooterImmunityTicks);
        b.entityHitGrow = merge(entityHitGrow, base.entityHitGrow);
        b.selfHit = merge(selfHit, base.selfHit);
        b.syncInterval = merge(syncInterval, base.syncInterval);
        b.velocitySyncInterval = merge(velocitySyncInterval, base.velocitySyncInterval);
        b.physicsOrder = merge(physicsOrder, base.physicsOrder);
        b.leftOwnerImmunity = merge(leftOwnerImmunity, base.leftOwnerImmunity);
        b.stickPullback = merge(stickPullback, base.stickPullback);
        b.shakeTicks = merge(shakeTicks, base.shakeTicks);
        b.behavior = merge(behavior, base.behavior);
        b.knockback = merge(knockback, base.knockback);
        b.knockbackSource = merge(knockbackSource, base.knockbackSource);
        b.damage = merge(damage, base.damage);
        b.damageType = merge(damageType, base.damageType);
        b.removeOnEntityHit = merge(removeOnEntityHit, base.removeOnEntityHit);
        b.removeOnBlockHit = merge(removeOnBlockHit, base.removeOnBlockHit);
        b.invulnHit = merge(invulnHit, base.invulnHit);
        b.deflect = merge(deflect, base.deflect);
        b.deflectParticles = merge(deflectParticles, base.deflectParticles);
        b.pickupBox = merge(pickupBox, base.pickupBox);
        return b.build();
    }

    /**
     * Builder for the generic (key-less) default config - the base every type in a {@link ProjectileConfig}
     * inherits unless it overrides a knob. Presets define their projectile baseline here.
     */
    public static Builder builder() { return new Builder((Key) null); }
    public static Builder builder(Key key) { return new Builder(key); }
    /** Builder seeded from {@code base}'s fields, e.g. {@code builder(Vanilla18.projectileDefaults()).damage(5.0)}. */
    public static Builder builder(ProjectileTypeConfig base) { return new Builder(base); }
    /** A builder pre-filled with this config's fields. */
    public Builder toBuilder() { return new Builder(this); }

    /** Builder. Each knob takes a constant or a {@link ProjectileContext} lambda (per-launch). */
    public static final class Builder {
        private Key key;
        private Function<ProjectileContext, ProjectileTypeConfig> subConfig;
        private FieldValue<ProjectileContext, Boolean> enabled;
        private FieldValue<ProjectileContext, BoundingBox> boundingBox;
        private FieldValue<ProjectileContext, Double> gravity;
        private FieldValue<ProjectileContext, Double> horizontalDrag;
        private FieldValue<ProjectileContext, Double> verticalDrag;
        private FieldValue<ProjectileContext, Double> spawnOffsetForward;
        private FieldValue<ProjectileContext, Double> spawnOffsetVertical;
        private FieldValue<ProjectileContext, Double> spawnOffsetSideways;
        private FieldValue<ProjectileContext, Double> speed;
        private FieldValue<ProjectileContext, Double> spread;
        private FieldValue<ProjectileContext, Double> momentumHorizontal;
        private FieldValue<ProjectileContext, Double> momentumVertical;
        private FieldValue<ProjectileContext, VelocityRule> velocity;
        private FieldValue<ProjectileContext, Integer> shooterImmunityTicks;
        private FieldValue<ProjectileContext, Double> entityHitGrow;
        private FieldValue<ProjectileContext, HitResponse> selfHit;
        private FieldValue<ProjectileContext, Integer> syncInterval;
        private FieldValue<ProjectileContext, Integer> velocitySyncInterval;
        private FieldValue<ProjectileContext, PhysicsOrder> physicsOrder;
        private FieldValue<ProjectileContext, Boolean> leftOwnerImmunity;
        private FieldValue<ProjectileContext, Double> stickPullback;
        private FieldValue<ProjectileContext, Integer> shakeTicks;
        private FieldValue<ProjectileContext, ProjectileBehavior> behavior;
        private FieldValue<ProjectileContext, KnockbackConfig> knockback;
        private FieldValue<ProjectileContext, KnockbackSource> knockbackSource;
        private FieldValue<ProjectileContext, Double> damage;
        private FieldValue<ProjectileContext, DamageType> damageType;
        private FieldValue<ProjectileContext, Boolean> removeOnEntityHit;
        private FieldValue<ProjectileContext, Boolean> removeOnBlockHit;
        private FieldValue<ProjectileContext, HitResponse> invulnHit;
        private FieldValue<ProjectileContext, Deflect> deflect;
        private FieldValue<ProjectileContext, Boolean> deflectParticles;
        private FieldValue<ProjectileContext, PickupBox> pickupBox;

        Builder(Key key) { this.key = key; }

        Builder(ProjectileTypeConfig c) {
            key = c.key;
            subConfig = c.subConfig;
            enabled = c.enabled;
            boundingBox = c.boundingBox;
            gravity = c.gravity;
            horizontalDrag = c.horizontalDrag;
            verticalDrag = c.verticalDrag;
            spawnOffsetForward = c.spawnOffsetForward;
            spawnOffsetVertical = c.spawnOffsetVertical;
            spawnOffsetSideways = c.spawnOffsetSideways;
            speed = c.speed;
            spread = c.spread;
            momentumHorizontal = c.momentumHorizontal;
            momentumVertical = c.momentumVertical;
            velocity = c.velocity;
            shooterImmunityTicks = c.shooterImmunityTicks;
            entityHitGrow = c.entityHitGrow;
            selfHit = c.selfHit;
            syncInterval = c.syncInterval;
            velocitySyncInterval = c.velocitySyncInterval;
            physicsOrder = c.physicsOrder;
            leftOwnerImmunity = c.leftOwnerImmunity;
            stickPullback = c.stickPullback;
            shakeTicks = c.shakeTicks;
            behavior = c.behavior;
            knockback = c.knockback;
            knockbackSource = c.knockbackSource;
            damage = c.damage;
            damageType = c.damageType;
            removeOnEntityHit = c.removeOnEntityHit;
            removeOnBlockHit = c.removeOnBlockHit;
            invulnHit = c.invulnHit;
            deflect = c.deflect;
            deflectParticles = c.deflectParticles;
            pickupBox = c.pickupBox;
        }

        public Builder key(Key k) { this.key = k; return this; }
        public Builder enabled(Boolean v) { enabled = FieldValue.constant(v); return this; }
        public Builder enabled(Function<ProjectileContext, Boolean> fn) { enabled = FieldValue.of(fn); return this; }
        public Builder boundingBox(BoundingBox v) { boundingBox = FieldValue.constant(v); return this; }
        public Builder boundingBox(double width, double height, double depth) { boundingBox = FieldValue.constant(new BoundingBox(width, height, depth)); return this; }
        public Builder boundingBox(Function<ProjectileContext, BoundingBox> fn) { boundingBox = FieldValue.of(fn); return this; }
        public Builder gravity(Double v) { gravity = FieldValue.constant(v); return this; }
        public Builder gravity(Function<ProjectileContext, Double> fn) { gravity = FieldValue.of(fn); return this; }
        public Builder horizontalDrag(Double v) { horizontalDrag = FieldValue.constant(v); return this; }
        public Builder horizontalDrag(Function<ProjectileContext, Double> fn) { horizontalDrag = FieldValue.of(fn); return this; }
        public Builder verticalDrag(Double v) { verticalDrag = FieldValue.constant(v); return this; }
        public Builder verticalDrag(Function<ProjectileContext, Double> fn) { verticalDrag = FieldValue.of(fn); return this; }
        /** Sets all three spawn offsets at once: {@code forward} along the look, {@code vertical} from the eye, {@code sideways} perpendicular. */
        public Builder spawnOffset(double forward, double vertical, double sideways) {
            spawnOffsetForward = FieldValue.constant(forward);
            spawnOffsetVertical = FieldValue.constant(vertical);
            spawnOffsetSideways = FieldValue.constant(sideways);
            return this;
        }
        /** Forward spawn offset along the shooter's look direction (blocks). */
        public Builder spawnOffsetForward(Double v) { spawnOffsetForward = FieldValue.constant(v); return this; }
        public Builder spawnOffsetForward(Function<ProjectileContext, Double> fn) { spawnOffsetForward = FieldValue.of(fn); return this; }
        /** Vertical spawn offset from the shooter's eye height (blocks). */
        public Builder spawnOffsetVertical(Double v) { spawnOffsetVertical = FieldValue.constant(v); return this; }
        public Builder spawnOffsetVertical(Function<ProjectileContext, Double> fn) { spawnOffsetVertical = FieldValue.of(fn); return this; }
        /** Sideways spawn offset perpendicular to the look (vanilla 1.8 throwing-hand shift {@code 0.16}; 26.1: 0). */
        public Builder spawnOffsetSideways(Double v) { spawnOffsetSideways = FieldValue.constant(v); return this; }
        public Builder spawnOffsetSideways(Function<ProjectileContext, Double> fn) { spawnOffsetSideways = FieldValue.of(fn); return this; }
        public Builder speed(Double v) { speed = FieldValue.constant(v); return this; }
        public Builder speed(Function<ProjectileContext, Double> fn) { speed = FieldValue.of(fn); return this; }
        public Builder spread(Double v) { spread = FieldValue.constant(v); return this; }
        public Builder spread(Function<ProjectileContext, Double> fn) { spread = FieldValue.of(fn); return this; }
        /** Sets both shooter-momentum scales at once (fraction of the read velocity folded in: x/z, y). */
        public Builder momentum(double horizontal, double vertical) { momentumHorizontal = FieldValue.constant(horizontal); momentumVertical = FieldValue.constant(vertical); return this; }
        /** Horizontal shooter-momentum scale (fraction of the {@link #velocity} read x/z; {@code 0} = none, {@code 1} = full). */
        public Builder momentumHorizontal(Double v) { momentumHorizontal = FieldValue.constant(v); return this; }
        public Builder momentumHorizontal(Function<ProjectileContext, Double> fn) { momentumHorizontal = FieldValue.of(fn); return this; }
        /** Vertical shooter-momentum scale (fraction of the read velocity y; for 26.1's airborne-only, a lambda
         *  {@code ctx -> ctx.shooter().isOnGround() ? 0 : 1}). */
        public Builder momentumVertical(Double v) { momentumVertical = FieldValue.constant(v); return this; }
        public Builder momentumVertical(Function<ProjectileContext, Double> fn) { momentumVertical = FieldValue.of(fn); return this; }
        /** How the shooter's velocity is read for momentum: this override -&gt; the shooter's profile velocity -&gt;
         *  {@link VelocityRule#DEFAULT} (26.1 momentum uses {@code VelocityRule.delta()}). */
        public Builder velocity(VelocityRule v) { velocity = FieldValue.constant(v); return this; }
        public Builder velocity(Function<ProjectileContext, VelocityRule> fn) { velocity = FieldValue.of(fn); return this; }
        public Builder shooterImmunityTicks(Integer v) { shooterImmunityTicks = FieldValue.constant(v); return this; }
        public Builder shooterImmunityTicks(Function<ProjectileContext, Integer> fn) { shooterImmunityTicks = FieldValue.of(fn); return this; }
        /** Entity-hit margin: grow the target's bbox by this on each side for the hit ray-test (vanilla 1.8 {@code 0.3}). */
        public Builder entityHitGrow(Double v) { entityHitGrow = FieldValue.constant(v); return this; }
        public Builder entityHitGrow(Function<ProjectileContext, Double> fn) { entityHitGrow = FieldValue.of(fn); return this; }
        /** What the projectile does when it hits its own shooter ({@link HitResponse}, default {@code HIT}):
         *  {@code PASS_THROUGH} = the 1.8 ender pearl / Hypixel "self does nothing"; {@code DEFLECT} = bounce off. */
        public Builder selfHit(HitResponse v) { selfHit = FieldValue.constant(v); return this; }
        public Builder selfHit(Function<ProjectileContext, HitResponse> fn) { selfHit = FieldValue.of(fn); return this; }
        public Builder syncInterval(Integer v) { syncInterval = FieldValue.constant(v); return this; }
        public Builder syncInterval(Function<ProjectileContext, Integer> fn) { syncInterval = FieldValue.of(fn); return this; }
        /** In-flight velocity broadcast interval (ticks): {@code <= 0} = never (vanilla arrow, the edge-slide fix),
         *  {@code 1} = every tick, {@code N} = every N ticks. */
        public Builder velocitySyncInterval(Integer v) { velocitySyncInterval = FieldValue.constant(v); return this; }
        public Builder velocitySyncInterval(Function<ProjectileContext, Integer> fn) { velocitySyncInterval = FieldValue.of(fn); return this; }
        /** When drag + gravity apply relative to the move ({@code DRAG_AFTER_MOVE} = 1.8, {@code DRAG_BEFORE_MOVE} = 26.1). */
        public Builder physicsOrder(PhysicsOrder v) { physicsOrder = FieldValue.constant(v); return this; }
        public Builder physicsOrder(Function<ProjectileContext, PhysicsOrder> fn) { physicsOrder = FieldValue.of(fn); return this; }
        /** {@code true} = immune to the shooter until the projectile leaves its bbox (26.1); {@code false} = fixed-tick immunity (1.8). */
        public Builder leftOwnerImmunity(Boolean v) { leftOwnerImmunity = FieldValue.constant(v); return this; }
        public Builder leftOwnerImmunity(Function<ProjectileContext, Boolean> fn) { leftOwnerImmunity = FieldValue.of(fn); return this; }
        /** Distance a stuck projectile is pulled back along its flight dir so the tip pokes out of the block face (vanilla {@code 0.05}). */
        public Builder stickPullback(Double v) { stickPullback = FieldValue.constant(v); return this; }
        public Builder stickPullback(Function<ProjectileContext, Double> fn) { stickPullback = FieldValue.of(fn); return this; }
        /** Pickup cooldown (ticks) after a collectable projectile sticks (vanilla arrow {@code shake} = {@code 7}); arrow-only. */
        public Builder shakeTicks(Integer v) { shakeTicks = FieldValue.constant(v); return this; }
        public Builder shakeTicks(Function<ProjectileContext, Integer> fn) { shakeTicks = FieldValue.of(fn); return this; }
        /** Pluggable {@link ProjectileBehavior} (onImpact/onStuck/onUnstuck/onTick) layered over the built-in effects. */
        public Builder behavior(ProjectileBehavior v) { behavior = FieldValue.constant(v); return this; }
        public Builder behavior(Function<ProjectileContext, ProjectileBehavior> fn) { behavior = FieldValue.of(fn); return this; }
        public Builder knockback(KnockbackConfig v) { knockback = FieldValue.constant(v); return this; }
        public Builder knockback(Function<ProjectileContext, KnockbackConfig> fn) { knockback = FieldValue.of(fn); return this; }
        public Builder knockbackSource(KnockbackSource v) { knockbackSource = FieldValue.constant(v); return this; }
        public Builder knockbackSource(Function<ProjectileContext, KnockbackSource> fn) { knockbackSource = FieldValue.of(fn); return this; }
        public Builder damage(Double v) { damage = FieldValue.constant(v); return this; }
        public Builder damage(Function<ProjectileContext, Double> fn) { damage = FieldValue.of(fn); return this; }
        public Builder damageType(DamageType v) { damageType = FieldValue.constant(v); return this; }
        public Builder damageType(Function<ProjectileContext, DamageType> fn) { damageType = FieldValue.of(fn); return this; }
        public Builder removeOnEntityHit(Boolean v) { removeOnEntityHit = FieldValue.constant(v); return this; }
        public Builder removeOnBlockHit(Boolean v) { removeOnBlockHit = FieldValue.constant(v); return this; }
        /** What the projectile does when its hit is blocked (target invulnerable/creative); vanilla arrows
         *  {@code DEFLECT}, throwables {@code DESTROY}. */
        public Builder invulnHit(HitResponse v) { invulnHit = FieldValue.constant(v); return this; }
        public Builder invulnHit(Function<ProjectileContext, HitResponse> fn) { invulnHit = FieldValue.of(fn); return this; }
        /** A {@code DEFLECT} that just scales velocity by {@code multiplier} (negative reverses; vanilla 1.8 = {@code deflect(-0.1)}). */
        public Builder deflect(double multiplier) { deflect = FieldValue.constant(Deflect.of(multiplier)); return this; }
        /** A {@code DEFLECT} scaling velocity by {@code multiplier} (negative reverses) and rotating the heading by an
         *  extra {@code turn} +- a random {@code [min,max]} wobble in degrees (vanilla 26.1 = {@code deflect(-0.5, 0, -10, 10)}). */
        public Builder deflect(double multiplier, double turn, double min, double max) { deflect = FieldValue.constant(new Deflect(multiplier, turn, min, max)); return this; }
        public Builder deflect(Function<ProjectileContext, Deflect> fn) { deflect = FieldValue.of(fn); return this; }
        /** Make an arrow that bounced off / passed through an entity visible again to 1.8 viewers (re-spawn + crit
         *  trail) - a non-vanilla fix for the vanilla invisible-arrow-after-entity-touch glitch (off; arrows only). */
        public Builder deflectParticles(Boolean v) { deflectParticles = FieldValue.constant(v); return this; }
        public Builder deflectParticles(Function<ProjectileContext, Boolean> fn) { deflectParticles = FieldValue.of(fn); return this; }
        /** Pickup geometry for a collectable projectile (arrows); default {@link PickupBox#VANILLA}. */
        public Builder pickupBox(PickupBox v) { pickupBox = FieldValue.constant(v); return this; }
        public Builder pickupBox(Function<ProjectileContext, PickupBox> fn) { pickupBox = FieldValue.of(fn); return this; }
        public Builder subConfig(Function<ProjectileContext, ProjectileTypeConfig> fn) { subConfig = fn; return this; }

        public ProjectileTypeConfig build() { return new ProjectileTypeConfig(this); }
    }
}
