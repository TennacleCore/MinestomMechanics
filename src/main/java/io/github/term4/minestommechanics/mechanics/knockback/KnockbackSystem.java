package io.github.term4.minestommechanics.mechanics.knockback;

import io.github.term4.minestommechanics.MechanicsProfiles;
import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.api.event.KnockbackEvent;
import io.github.term4.minestommechanics.mechanics.Vanilla18;
import io.github.term4.minestommechanics.tracking.motion.LegacyVelocity;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.EventNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Knockback system, configured via {@link KnockbackConfig} or the {@link KnockbackEvent} API. No invul window of its
 * own (neither does vanilla); every {@link #apply} deals knockback and gating lives in the attack processor.
 */
public final class KnockbackSystem {

    private final KnockbackConfig config;
    private final KnockbackCalculator calc;
    private final MechanicsProfiles profiles;
    private final EventNode<@NotNull Event> node;

    public KnockbackSystem(MinestomMechanics mm, KnockbackConfig config) {
        this.config = config;
        this.profiles = mm.profiles();
        this.node = EventNode.all("mm:knockback");
        Services services = mm.services();
        KnockbackConfig defaults = Vanilla18.kb();
        this.calc = new KnockbackCalculator(services, defaults);
    }

    /** Effective config for a snapshot carrying none: the victim's scoped profile, else the install config. */
    private KnockbackConfig configFor(@Nullable Entity target) {
        KnockbackConfig scoped = profiles.knockbackFor(target);
        return scoped != null ? scoped : config;
    }

    public void apply(KnockbackSnapshot snap) {
        // config chain: snapshot -> victim scope -> install. inert when none resolves; an empty config applies at the vanilla floor
        if (snap.config() == null && configFor(snap.target()) == null) return;

        // KnockbackEvent API; the resolver hook previews the values compute() will use for the listeners' finalSnap
        var event = new KnockbackEvent(snap,
                s -> calc.resolveConfig(s.config() != null ? s : s.withConfig(configFor(s.target()))));
        EventDispatcher.call(event);
        if (event.isCancelled()) return;
        KnockbackSnapshot finalSnap = event.finalSnap();

        // Knockback requires a target
        if (finalSnap.target() == null) return;

        // Build knockback velocity vector
        @Nullable Vec velocity;

        // explicit velocity from the API wins
        if (event.velocity() != null) { velocity = event.velocity(); }

        // else compute from the snapshot (same config chain)
        else {
            velocity = finalSnap.config() != null
                    ? calc.compute(finalSnap)
                    : calc.compute(finalSnap.withConfig(configFor(finalSnap.target())));

            // API direction + computed magnitude
            if (velocity != null && event.direction() != null) {
                double mag = Math.sqrt(velocity.x() * velocity.x() + velocity.z() * velocity.z());
                Vec dir = event.direction().normalize();
                velocity = new Vec(dir.x() * mag, velocity.y(), dir.z() * mag);
            }
        }

        // vanilla broadcasts the KB but restores the victim's pre-KB server velocity, so it's never folded into the next
        // hit (the tracker isn't told). quantizeVelocity (default on) sends what a 1.8 server's wire encoding would.
        // TODO(perf): config is resolved twice per hit (compute + here for quantize); fold into one when the event layer is restructured.
        if (velocity != null) {
            Entity target = finalSnap.target();
            KnockbackSnapshot cfgSnap = finalSnap.config() != null ? finalSnap : finalSnap.withConfig(configFor(target));
            boolean quantize = !Boolean.FALSE.equals(calc.resolveConfig(cfgSnap).quantizeVelocity());
            target.setVelocity(quantize ? LegacyVelocity.snap(velocity) : velocity);
        }
    }

    public KnockbackConfig config() { return config; }

    /** This system's listener node ({@code mm:knockback}). Empty today - future hooks mount here. */
    public EventNode<@NotNull Event> node() { return node; }

    /** Installs inert (no install-level config): an {@link #apply} with no scoped or snapshot config applies nothing. Pass an empty config to apply at the vanilla floor. */
    public static KnockbackSystem install(MinestomMechanics mm) {
        return install(mm, (KnockbackConfig) null);
    }

    public static KnockbackSystem install(MinestomMechanics mm, KnockbackConfig config) {
        var system = new KnockbackSystem(mm, config);
        mm.registerKnockback(system);
        mm.install(system.node);
        return system;
    }

}