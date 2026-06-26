package io.github.term4.minestommechanics.api.event;

import io.github.term4.minestommechanics.Services;
import io.github.term4.minestommechanics.mechanics.attack.AttackConfig;
import io.github.term4.minestommechanics.mechanics.attack.AttackSnapshot;
import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * The pre-attack gate: fired the moment a hit is detected, <em>before</em> config resolution, the {@link AttackEvent},
 * and the ruleset - the earliest combat hook. Cancel to drop the hit before any processing, or redirect the inputs
 * (target / config) via {@link #finalSnap}. Observe-only today (reach logging; a future anticheat can veto here).
 */
public final class PreAttackEvent extends CancellableMechanicsEvent<AttackSnapshot> {

    public PreAttackEvent(AttackSnapshot snapshot, Services services) {
        super(snapshot, services);
    }

    /** Attack config carried so far, or {@code null} when not yet resolved (resolution runs after this gate). */
    public @Nullable AttackConfig config() { return finalSnap().config(); }

    /** Replaces the config for this hit (resolution after the gate honours it). */
    public void config(@Nullable AttackConfig config) { finalSnap(finalSnap().withConfig(config)); }

    public Entity attacker() { return finalSnap().attacker(); }

    /** The hit target, or {@code null} for a target-less detection (e.g. a raytraced swing miss). */
    public @Nullable Entity target() { return finalSnap().target(); }
}
