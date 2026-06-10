package io.github.term4.minestommechanics.mechanics.attack;

import io.github.term4.minestommechanics.MechanicsProfiles;
import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.api.event.AttackEvent;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.EventNode;
import org.jetbrains.annotations.NotNull;

/**
 * Attack pipeline: {@link HitDetection} turns raw input into snapshots, the system fires the
 * {@link AttackEvent} API and runs the configured ruleset. Has no invulnerability window or hit
 * buffering of its own - every detected hit is processed, and the damage / knockback systems gate
 * themselves on their own windows (vanilla: {@code EntityHuman.attack} always runs;
 * {@code damageEntity} decides what lands). Preset-specific behaviors (e.g. Minemen's hit queue,
 * future swing-hit detection) live in custom rulesets and detections.
 */
public final class AttackSystem {

    private final AttackConfig config;
    private final MechanicsProfiles profiles;
    private final Services services;
    private final EventNode<@NotNull Event> node;

    /**
     * @param detection how hits are detected; none given installs {@link HitDetection#PACKET}. Detection is
     *                  always live - `enabled` gates per hit through the config chain, so a world that boots
     *                  with a disabled install config can be switched live by assigning an enabled profile.
     */
    public AttackSystem(MinestomMechanics mm, AttackConfig config, HitDetection... detection) {
        this.config = config;
        this.profiles = mm.profiles();
        this.services = mm.services();
        this.node = EventNode.all("mm:attack");

        if (detection.length == 0) detection = new HitDetection[]{HitDetection.PACKET};
        for (HitDetection d : detection) d.install(node, this::apply);
    }

    /**
     * Feeds a detected hit through the attack pipeline: config chain (snapshot override -> attacker's
     * scoped profile -> install config) -> {@link AttackEvent} -> ruleset. Public so custom hit detection
     * (e.g. a preset's swing raycast) submits hits exactly like the built-in packet detection: build an
     * {@link AttackSnapshot}, call this.
     */
    public void apply(AttackSnapshot snap) {
        if (snap.config() == null) {
            AttackConfig scoped = profiles.attackFor(snap.attacker());
            snap = snap.withConfig(scoped != null ? scoped : config);
        }

        AttackEvent api = new AttackEvent(snap, services);
        EventDispatcher.call(api);
        // enabled is read off the live resolved config, so listeners may still swap in an enabled config
        // (event.config(...)) to let a specific hit through a disabled scope.
        if (api.isCancelled() || !api.process() || !api.resolvedConfig().enabled()) return;

        AttackEvent.AttackRule proc = api.processor() != null
                ? api.processor().create(services)
                : api.resolvedConfig().ruleset().create(services);
        proc.processAttack(api);
    }

    /** Installs the system. {@code detection} as in {@link #AttackSystem(MinestomMechanics, AttackConfig, HitDetection...)}. */
    public static AttackSystem install(MinestomMechanics mm, AttackConfig config, HitDetection... detection) {
        var system = new AttackSystem(mm, config, detection);
        mm.registerAttack(system);
        mm.install(system.node);
        return system;
    }

    public AttackConfig config() { return config; }

    /** This system's listener node ({@code mm:attack}); everything the system hooks lives under it. */
    public EventNode<@NotNull Event> node() { return node; }
}
