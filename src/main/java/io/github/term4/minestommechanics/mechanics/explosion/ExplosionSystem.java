package io.github.term4.minestommechanics.mechanics.explosion;

import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.MechanicsModule;
import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.api.event.ExplosionEvent;
import io.github.term4.minestommechanics.mechanics.attribute.defense.Bypass;
import io.github.term4.minestommechanics.mechanics.damage.DamageSnapshot;
import io.github.term4.minestommechanics.mechanics.damage.DamageSystem;
import io.github.term4.minestommechanics.mechanics.damage.types.explosion.ExplosionDamage;
import io.github.term4.minestommechanics.mechanics.explosion.ExplosionConfigResolver.ExplosionContext;
import io.github.term4.minestommechanics.mechanics.explosion.ExplosionConfigResolver.ResolvedExplosionConfig;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackConfig;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackSnapshot;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackSystem;
import io.github.term4.minestommechanics.mechanics.vanilla18.Knockback;
import io.github.term4.minestommechanics.util.Directions;
import net.kyori.adventure.key.Key;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.EventNode;
import net.minestom.server.instance.Explosion;
import net.minestom.server.instance.ExplosionSupplier;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.server.play.ExplosionPacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.utils.PacketSendingUtils;
import net.minestom.server.utils.WeightedList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Explosion system: entity selection + exposure raytrace + the vanilla falloff curve, fired through {@link ExplosionEvent}.
 * Knockback rides the {@link KnockbackSystem} (base + push) on a fresh hit, the explosion packet during i-frames; damage
 * rides the {@link DamageSystem}; blocks are delegated to a listener off {@link ExplosionEvent#center()} + power.
 */
public final class ExplosionSystem implements MechanicsModule {

    public static final Key KEY = Key.key("mm:explosion");

    /** Radius used when neither the call nor the config supplies one (vanilla TNT). */
    public static final float DEFAULT_POWER = 4.0f;

    private final @Nullable ExplosionConfig config;
    private final Services services;
    private final EventNode<@NotNull Event> node;

    public ExplosionSystem(MinestomMechanics mm, @Nullable ExplosionConfig config) {
        this.config = config;
        this.services = mm.services();
        this.node = EventNode.all("mm:explosion");
    }

    /** Per-player explosion: every player in the instance gets the visual, those in range their own falloff knockback. */
    public void explode(@NotNull Instance instance, @NotNull Point center, float power) {
        explode(instance, center, power, null);
    }

    public void explode(@NotNull Instance instance, @NotNull Point center, float power, @Nullable Entity source) {
        explode(instance, center, power, source, null);
    }

    /** {@code directHit} = the entity the projectile struck (or {@code null}); only it is subject to the {@link ExplosionConfig#knockbackImpactFloor knockback impact gate}. */
    public void explode(@NotNull Instance instance, @NotNull Point center, float power, @Nullable Entity source, @Nullable Entity directHit) {
        detonate(instance, center, power, source, directHit, resolve(instance, center, source));
    }

    /** Per-player explosion using the configured (or default) radius. */
    public void explode(@NotNull Instance instance, @NotNull Point center, @Nullable Entity source) {
        ResolvedExplosionConfig resolved = resolve(instance, center, source);
        float power = resolved.power() != null ? resolved.power().floatValue() : DEFAULT_POWER;
        detonate(instance, center, power, source, null, resolved);
    }

    private void detonate(Instance instance, Point center, float power, @Nullable Entity source, @Nullable Entity directHit, ResolvedExplosionConfig resolved) {
        List<ExplosionEvent.Target> targets = computeAndFire(instance, center, power, source, resolved);
        if (targets != null) applyPerPlayer(instance, center, power, source, directHit, targets, resolved);
    }

    /**
     * One grouped explosion (single packet to everyone): cheaper, but the shared {@code knockback} is applied uniformly
     * by every client (pass {@code null} for none). Damage is still per-entity. Use {@link #explode} for falloff knockback.
     */
    public void explodeUniform(@NotNull Instance instance, @NotNull Point center, float power,
                               @Nullable Entity source, @Nullable Vec knockback) {
        ResolvedExplosionConfig resolved = resolve(instance, center, source);
        List<ExplosionEvent.Target> targets = computeAndFire(instance, center, power, source, resolved);
        if (targets == null) return;
        applyDamage(source, center, targets, resolved.damageBypass());
        PacketSendingUtils.sendGroupedPacket(instance.getPlayers(), packet(center, power, knockback));
    }

    /** Config: the source's scope chain (player -&gt; instance -&gt; global) over the install config. */
    private ResolvedExplosionConfig resolve(Instance instance, Point center, @Nullable Entity source) {
        ExplosionConfig scoped = services.profiles().resolve(source, MechanicsKeys.EXPLOSION);
        return ExplosionConfigResolver.resolve(scoped != null ? scoped : config, ExplosionContext.of(instance, center, source, services));
    }

    /** Builds the per-entity result set, fires the event, and returns the (possibly listener-edited) targets, or {@code null} if cancelled. */
    private @Nullable List<ExplosionEvent.Target> computeAndFire(Instance instance, Point center, float power,
                                                                 @Nullable Entity source, ResolvedExplosionConfig resolved) {
        List<ExplosionEvent.Target> targets = computeTargets(instance, center, power, source, resolved);
        ExplosionEvent event = new ExplosionEvent(instance, center, power, source, resolved.fire(), targets);
        EventDispatcher.call(event);
        return event.isCancelled() ? null : event.targets();
    }

    private List<ExplosionEvent.Target> computeTargets(Instance instance, Point center, float power,
                                                       @Nullable Entity source, ResolvedExplosionConfig resolved) {
        List<ExplosionEvent.Target> targets = new ArrayList<>();
        double doubleRadius = power * 2.0;
        if (doubleRadius <= 0.0) return targets;
        for (Entity entity : instance.getNearbyEntities(center, doubleRadius + 1.0)) {
            boolean living = entity instanceof LivingEntity;
            boolean kbTarget = isKnockbackTarget(entity, resolved); // players + non-living physics entities by default
            if (!living && !kbTarget) continue;
            if (entity == source && !resolved.affectsSource()) continue;
            double distance = entity.getPosition().distance(center);
            if (distance > doubleRadius) continue;
            // default = STANDING eye even when sneaking (Hypixel); pushEye overrides (MineMen = sneak-aware getHeadHeight).
            // Non-living TNT = feet/0 (vanilla), not the 0.15 registry eye.
            double headHeight = !(entity instanceof LivingEntity) ? 0.0
                    : resolved.pushEye() != null ? resolved.pushEye().apply(entity)
                    : entity.getEntityType().registry().eyeHeight();
            Point eyeOrigin = entity.getPosition().add(0, headHeight, 0);
            float exposure = resolved.exposure() ? ExplosionExposure.seenPercent(instance, center, entity) : 1.0f;
            // knockback reduction (Blast Protection / KB resistance): TODO via the attribute layer
            ExplosionCalculator.Hit hit = ExplosionCalculator.compute(center, power, eyeOrigin, distance, exposure,
                    resolved.damageConstant(), resolved.floorDamage(), resolved.knockbackMultiplier(), 0.0);
            if (hit == null) continue;
            float damage = !living ? 0f : (resolved.flatDamage() != null ? resolved.flatDamage().floatValue() : hit.damage());
            damage *= (float) resolved.damageScale(); // post-floor, so a scaled vanilla curve stays step-quantized (MineMen FBF)
            Vec push = kbTarget ? hit.knockback() : null; // a non-KB target (mob) still takes damage, no push
            targets.add(new ExplosionEvent.Target(entity, distance, exposure, push, damage));
        }
        return targets;
    }

    /** Default explosion-KB targeting: players + non-living physics entities (TNT, falling blocks, items). Overridable via {@link ExplosionConfig#knockbackTargets}. */
    private static boolean isKnockbackTarget(Entity entity, ResolvedExplosionConfig resolved) {
        Predicate<Entity> targets = resolved.knockbackTargets();
        if (targets != null) return targets.test(entity);
        return entity instanceof Player || (!(entity instanceof LivingEntity) && entity.hasPhysics());
    }

    private void applyPerPlayer(Instance instance, Point center, float power, @Nullable Entity source,
                                @Nullable Entity directHit, List<ExplosionEvent.Target> targets, ResolvedExplosionConfig resolved) {
        DamageSystem damage = services.damage();
        KnockbackSystem knockback = services.knockback();
        // vanilla a() away from the source, folded before the push on a fresh hit; Hypixel overrides via damageKnockback
        KnockbackConfig damageKb = resolved.damageKnockback() != null ? resolved.damageKnockback() : Knockback.melee();
        Map<Player, Vec> packetKnockback = new HashMap<>(); // vanilla i-frame push, client-applied from the explosion packet
        List<Runnable> sendKnockback = new ArrayList<>();    // deferred so the explosion packet goes out first (Hypixel order)
        for (ExplosionEvent.Target target : targets) {
            Entity entity = target.entity();
            Vec push = target.knockback();
            // damage respects i-frames (vanilla order); the explosion knockback below does not
            DamageSystem.DamageOutcome outcome = DamageSystem.DamageOutcome.BLOCKED;
            if (damage != null && entity instanceof LivingEntity && target.damage() > 0f) {
                outcome = damage.apply(explosionDamage(entity, source, center, target.damage(), resolved.damageBypass()));
            }
            if (entity instanceof Player player) {
                // vanilla pushes only non-invulnerable players
                if (DamageSystem.isImmune(player)) continue;
                // Hypixel gate: only the direct-hit player; below the floor no explosion KB (bystanders never gated)
                Double impactFloor = resolved.knockbackImpactFloor();
                if (player == directHit && impactFloor != null
                        && ExplosionCalculator.impact(target.distance(), power, target.exposure()) < impactFloor) continue;
                // the explosion push always reaches the player (bypasses i-frames); only the wire path differs
                if (knockback != null && resolved.baseKnockback() > 0.0) {
                    // Hypixel: radial base (toward feet+baseHeight) + push, SET as one velocity packet; explosion packet stays motion-less
                    Vec base = radialBase(player.getPosition(), center, resolved.baseKnockback(), resolved.baseHeight(),
                            resolved.baseHorizontalScale(), resolved.baseDownwardScale());
                    Vec velocity = perSecond(push != null ? base.add(push) : base);
                    sendKnockback.add(() -> knockback.deliver(player, velocity));
                } else if (push != null) {
                    if (outcome == DamageSystem.DamageOutcome.FRESH_DAMAGE && knockback != null && source != null) {
                        Vec impulse = perSecond(push);
                        sendKnockback.add(() -> knockback.apply(new KnockbackSnapshot(player, false, source, null, null, damageKb), impulse)); // a() fold + push
                    } else if (resolved.packetPush()) {
                        packetKnockback.put(player, push); // i-frame: rides the explosion packet, not a velocity packet
                    } else if (knockback != null && outcome != DamageSystem.DamageOutcome.BLOCKED) {
                        // velocity-only (MineMen): sourceless fresh / i-frame overdamage = push ADDED to current motion
                        // (vanilla g(); a direct hit's same-tick contact KB survives). Blocked = nothing.
                        Vec velocity = perSecond(push);
                        sendKnockback.add(() -> knockback.deliver(player, player.getVelocity().add(velocity)));
                    }
                }
            } else if (push != null && knockback != null) {
                // non-player physics entity (TNT etc.): vanilla ADDS the push to current motion, not replaces
                entity.setVelocity(entity.getVelocity().add(perSecond(push)));
            }
        }
        // explosion packet (visual) first, then the velocity knockback - Hypixel's packet-before-KB order
        for (Player player : instance.getPlayers()) {
            player.sendPacket(packet(center, power, packetKnockback.get(player)));
        }
        sendKnockback.forEach(Runnable::run);
    }

    /** Explosion model vectors are blocks/tick (vanilla); the {@link KnockbackSystem} wire boundary takes blocks/second. */
    private static Vec perSecond(Vec perTick) {
        return perTick.mul(ServerFlag.SERVER_TICKS_PER_SECOND);
    }

    /** Radial base toward {@code height} above {@code position} (the entity's feet), ×{@code magnitude}: 1 up, {@code horizontalScale} sideways, {@code downwardScale} down. */
    private static Vec radialBase(Point position, Point center, double magnitude, double height,
                                  double horizontalScale, double downwardScale) {
        Point body = position.add(0, height, 0);
        Vec u = Directions.unit3D(body.x() - center.x(), body.y() - center.y(), body.z() - center.z(), 1.0e-7);
        if (u == null) return Vec.ZERO;
        double horizontal = horizontalScale * magnitude, vertical = (u.y() >= 0 ? magnitude : downwardScale * magnitude);
        return new Vec(horizontal * u.x(), vertical * u.y(), horizontal * u.z());
    }

    private void applyDamage(@Nullable Entity source, Point center, List<ExplosionEvent.Target> targets, @Nullable Bypass bypass) {
        DamageSystem damage = services.damage();
        if (damage == null) return;
        for (ExplosionEvent.Target target : targets) {
            if (target.entity() instanceof LivingEntity && target.damage() > 0f) {
                damage.apply(explosionDamage(target.entity(), source, center, target.damage(), bypass));
            }
        }
    }

    /** Explosion-damage snapshot for one target; {@code bypass} = mitigation skip (e.g. armor-only) or null. */
    private static DamageSnapshot explosionDamage(Entity target, @Nullable Entity source, Point center, float amount, @Nullable Bypass bypass) {
        DamageSnapshot snap = DamageSnapshot.of(target, ExplosionDamage.INSTANCE).withSource(source).withPoint(center).withAmount(amount);
        return bypass != null ? snap.withBypass(bypass) : snap;
    }

    private static ExplosionPacket packet(Point center, float power, @Nullable Point knockback) {
        return new ExplosionPacket(center, power, 0, knockback,
                Particle.EXPLOSION, SoundEvent.ENTITY_GENERIC_EXPLODE, WeightedList.of());
    }

    /** An {@link ExplosionSupplier} routing {@code instance.explode(...)} through this system (no source; breaks no blocks). */
    public ExplosionSupplier supplier() {
        return (x, y, z, strength, data) -> new RoutedExplosion(this, x, y, z, strength);
    }

    public EventNode<@NotNull Event> node() { return node; }

    public static ExplosionSystem install(MinestomMechanics mm) {
        return install(mm, mm.profiles().resolve(null, MechanicsKeys.EXPLOSION));
    }

    public static ExplosionSystem install(MinestomMechanics mm, @Nullable ExplosionConfig config) {
        var system = new ExplosionSystem(mm, config);
        mm.register(system);
        mm.install(system.node);
        return system;
    }

    private static final class RoutedExplosion extends Explosion {
        private final ExplosionSystem system;

        RoutedExplosion(ExplosionSystem system, float x, float y, float z, float strength) {
            super(x, y, z, strength);
            this.system = system;
        }

        @Override protected List<Point> prepare(Instance instance) { return List.of(); }

        @Override public void apply(Instance instance) {
            system.explode(instance, new Vec(getCenterX(), getCenterY(), getCenterZ()), getStrength());
        }
    }
}
