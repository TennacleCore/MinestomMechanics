package io.github.term4.minestommechanics.mechanics.knockback;

import io.github.term4.echofix.EchoFixPlayer;
import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.api.event.KnockbackEvent;
import io.github.term4.minestommechanics.Vanilla18;
import io.github.term4.minestommechanics.util.TickClock;
import io.github.term4.minestommechanics.util.TickState;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Knockback system: Config can be changed via KnockbackConfig or the KnockbackEvent API */
public final class KnockbackSystem {

    private static final Tag<TickState> INVUL_KNOCKBACK = Tag.Transient("mm:invul-knockback");

    private final MinestomMechanics mm;
    private final EventNode<@NotNull Event> apiEvents;
    private final KnockbackConfig config;
    private final KnockbackCalculator calc;

    public KnockbackSystem(MinestomMechanics mm, KnockbackConfig config) {
        this.mm = mm;
        this.config = config;
        this.apiEvents = mm.events();
        Services services = mm.services();
        KnockbackConfig defaults = Vanilla18.kb();
        this.calc = new KnockbackCalculator(services, defaults);
    }

    public void apply(KnockbackSnapshot snap) {

        // KnockbackEvent API
        var event = new KnockbackEvent(snap);
        apiEvents.call(event);
        if (event.cancelled()) return;
        KnockbackSnapshot finalSnap = event.finalSnap();

        // Knockback requires a target
        if (finalSnap.target() == null) return;

        // Early return if target is in its knockback invul window (event.invulnerable() uses INVUL_KNOCKBACK).
        if (event.invulnerable() && !event.bypassInvul()) return;

        // Build knockback velocity vector
        @Nullable Vec velocity;

        // If the API explicitly set the velocity, use that
        if (event.velocity() != null) { velocity = event.velocity(); }

        // If no velocity was provided, calculate using the snapshot
        else {
            velocity = finalSnap.config() != null ? calc.compute(finalSnap) : calc.compute(finalSnap.withConfig(config));

            // If the API explicitly set the direction, use the calculated velocity magnitude & the API's direction
            if (velocity != null && event.direction() != null) {
                double mag = Math.sqrt(velocity.x() * velocity.x() + velocity.z() * velocity.z());
                Vec dir = event.direction().normalize();
                velocity = new Vec(dir.x() * mag, velocity.y(), dir.z() * mag);
            }
        }

        // Apply knockback to target
        if (velocity != null) {
            Entity target = finalSnap.target();
            target.setVelocity(velocity);
            var cfg = calc.resolveConfig(finalSnap);
            if (cfg.kbInvulnTicks() != null && cfg.kbInvulnTicks() > 0) {
                setKnockbackInvulnerable(finalSnap.target(), cfg.kbInvulnTicks());
            }
        }

    }

    public KnockbackConfig config() { return config; }

    public static KnockbackSystem install(MinestomMechanics mm, KnockbackConfig config) {
        var system = new KnockbackSystem(mm, config);
        mm.registerKnockback(system);
        return system;
    }

    public static void setKnockbackInvulnerable(Entity e, int duration) {
        if (!(e instanceof LivingEntity le) || duration <= 0) return;
        le.setTag(INVUL_KNOCKBACK, new TickState(TickClock.now(), duration));
    }

    public static boolean isInvulnerableToKnockback(Entity e) {
        if (!(e instanceof LivingEntity le)) return false;
        TickState s = le.getTag(INVUL_KNOCKBACK);
        return s != null && s.isActive();
    }

    /** Remaining knockback invul ticks for the entity. 0 if not invulnerable. */
    public static int remainingKnockbackInvulTicks(LivingEntity le) {
        TickState s = le.getTag(INVUL_KNOCKBACK);
        return s != null ? s.remainingTicks() : 0;
    }

}