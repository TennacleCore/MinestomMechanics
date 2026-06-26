package io.github.term4.minestommechanics.mechanics.knockback;

import io.github.term4.minestommechanics.MechanicsProfiles;
import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.MechanicsModule;
import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.api.event.KnockbackEvent;
import io.github.term4.minestommechanics.api.event.PreKnockbackEvent;
import io.github.term4.minestommechanics.api.event.KnockbackAppliedEvent;
import io.github.term4.minestommechanics.mechanics.vanilla18.Knockback;
import io.github.term4.minestommechanics.tracking.motion.LegacyVelocity;
import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.ListenerHandle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Knockback system, configured via {@link KnockbackConfig} or the {@link KnockbackEvent} API. No invul window of its
 * own (neither does vanilla); every {@link #apply} deals knockback and gating lives in the attack processor.
 */
public final class KnockbackSystem implements MechanicsModule {

    /** This system's identity for per-module TPS scaling (its {@code referenceTps} feel-baseline). */
    public static final Key KEY = Key.key("mm:knockback");

    private final KnockbackConfig config;
    private final KnockbackCalculator calc;
    private final MechanicsProfiles profiles;
    private final Services services;
    private final EventNode<@NotNull Event> node;

    // Auxiliary phases are fired only when a listener exists (the main KnockbackEvent always fires).
    private static final ListenerHandle<PreKnockbackEvent> PRE_KNOCKBACK = EventDispatcher.getHandle(PreKnockbackEvent.class);
    private static final ListenerHandle<KnockbackAppliedEvent> KNOCKBACK_APPLIED = EventDispatcher.getHandle(KnockbackAppliedEvent.class);

    public KnockbackSystem(MinestomMechanics mm, KnockbackConfig config) {
        this.config = config;
        this.profiles = mm.profiles();
        this.node = EventNode.all("mm:knockback");
        this.services = mm.services();
        KnockbackConfig defaults = Knockback.melee();
        this.calc = new KnockbackCalculator(this.services, defaults);
    }

    /** Resolves the effective knockback values for {@code snap} (config chain + calculator defaults); the {@link KnockbackEvent} preview. */
    public KnockbackConfigResolver.ResolvedKnockbackConfig resolveConfig(KnockbackSnapshot snap) {
        return calc.resolveConfig(snap.config() != null ? snap : snap.withConfig(configFor(snap.target())));
    }

    /** Effective config for a snapshot carrying none: the victim's scoped profile, else the install config. */
    private KnockbackConfig configFor(@Nullable Entity target) {
        KnockbackConfig scoped = profiles.resolve(target, MechanicsKeys.KNOCKBACK);
        return scoped != null ? scoped : config;
    }

    public void apply(KnockbackSnapshot snap) {
        // config chain: snapshot -> victim scope -> install. inert when none resolves; an empty config applies at the vanilla floor
        if (snap.config() == null && configFor(snap.target()) == null) return;

        // Pre phase (lazy): cancel the knockback before it's computed (no-KB zones, abilities), or redirect inputs.
        if (PRE_KNOCKBACK.hasListener()) {
            PreKnockbackEvent pre = new PreKnockbackEvent(snap, services);
            EventDispatcher.call(pre);
            if (pre.isCancelled()) return;
            snap = pre.finalSnap();
        }

        // Main phase: inspect / modify the computed knockback before it is applied.
        var event = new KnockbackEvent(snap, services);
        EventDispatcher.call(event);
        if (event.isCancelled()) return;
        KnockbackSnapshot finalSnap = event.finalSnap();

        // Knockback requires a target
        Entity target = finalSnap.target();
        if (target == null) return;
        KnockbackSnapshot cfgSnap = finalSnap.config() != null ? finalSnap : finalSnap.withConfig(configFor(target));

        // Build the velocity. The computed path resolves the config once and reuses it for quantize below; an API velocity
        // override skips compute, so it resolves lazily just for quantize.
        @Nullable Vec velocity;
        @Nullable KnockbackConfigResolver.ResolvedKnockbackConfig resolved;
        if (event.velocity() != null) {
            velocity = event.velocity();
            resolved = null;
        } else {
            KnockbackCalculator.KnockbackResult result = calc.computeResult(cfgSnap);
            velocity = result != null ? result.velocity() : null;
            resolved = result != null ? result.config() : null;
            // API direction + computed magnitude
            if (velocity != null && event.direction() != null) {
                double mag = Math.sqrt(velocity.x() * velocity.x() + velocity.z() * velocity.z());
                Vec dir = event.direction().normalize();
                velocity = new Vec(dir.x() * mag, velocity.y(), dir.z() * mag);
            }
        }

        // vanilla broadcasts the KB but restores the victim's pre-KB server velocity, so it's never folded into the next
        // hit (the tracker isn't told). quantizeVelocity (default on) sends what a 1.8 server's wire encoding would.
        if (velocity != null) {
            if (resolved == null) resolved = calc.resolveConfig(cfgSnap);
            boolean quantize = !Boolean.FALSE.equals(resolved.quantizeVelocity());
            Vec applied = quantize ? LegacyVelocity.snap(velocity) : velocity;
            target.setVelocity(applied);
            // Applied phase (lazy): the velocity was set.
            if (KNOCKBACK_APPLIED.hasListener()) EventDispatcher.call(new KnockbackAppliedEvent(finalSnap, applied, services));
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
        mm.register(system);
        mm.install(system.node);
        return system;
    }

}