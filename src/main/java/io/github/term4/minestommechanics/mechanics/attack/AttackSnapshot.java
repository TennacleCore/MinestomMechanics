package io.github.term4.minestommechanics.mechanics.attack;

import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * A detected hit. Attacks are melee by definition (attack packet, or a future swing-raycast hit) - the
 * processing ruleset stamps that onto the damage/knockback snapshots it produces.
 *
 * @param attacker the entity initiating the attack
 * @param target   the target entity of the attack (nullable for raytraced swing misses)
 * @param config   the attack config, or {@code null} to resolve the scope chain (profile -> install config)
 */
public record AttackSnapshot(Entity attacker, @Nullable Entity target, @Nullable AttackConfig config) {

    public AttackSnapshot withAttacker(Entity e) { return new AttackSnapshot(e, target, config); }
    public AttackSnapshot withTarget(Entity e) { return new AttackSnapshot(attacker, e, config); }
    public AttackSnapshot withConfig(AttackConfig c) { return new AttackSnapshot(attacker, target, c); }

    public Builder toBuilder() { return new Builder(attacker, target, config); }

    public static final class Builder {
        private Entity attacker;
        private @Nullable Entity target;
        private @Nullable AttackConfig config;

        Builder(Entity a, @Nullable Entity t, @Nullable AttackConfig cfg) { attacker = a; target = t; config = cfg; }

        public Builder attacker(Entity e) { attacker = e; return this; }
        public Builder target(Entity e) { target = e; return this; }
        public Builder config(AttackConfig cfg) { config = cfg; return this; }

        public AttackSnapshot build() {
            return new AttackSnapshot(attacker, target, config);
        }
    }
}
