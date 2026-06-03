package io.github.term4.minestommechanics;

import io.github.term4.minestommechanics.mechanics.attack.AttackSystem;
import io.github.term4.minestommechanics.mechanics.damage.DamageSystem;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackSystem;
import io.github.term4.minestommechanics.tracking.GroundTracker;
import io.github.term4.minestommechanics.tracking.SprintTracker;
import org.jetbrains.annotations.Nullable;

/**
 * Registered systems on MinestomMechanics. Provides access to any installed systems.
 */
public record Services(MinestomMechanics mm) {

    // For now, enable these in the order they are listed here
    public @Nullable SprintTracker sprintTracker() { return mm.sprintTracker(); }
    public @Nullable GroundTracker groundTracker() { return mm.groundTracker(); }
    public @Nullable AttackSystem attack() { return mm.attackSystem(); }
    public @Nullable KnockbackSystem knockback() { return mm.knockbackSystem(); }
    public @Nullable DamageSystem damage() { return mm.damageSystem(); }

}
