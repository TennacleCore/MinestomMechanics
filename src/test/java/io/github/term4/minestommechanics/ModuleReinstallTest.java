package io.github.term4.minestommechanics;

import io.github.term4.minestommechanics.mechanics.attack.AttackSystem;
import io.github.term4.minestommechanics.mechanics.attribute.AttributeSystem;
import io.github.term4.minestommechanics.mechanics.blocking.BlockingSystem;
import io.github.term4.minestommechanics.mechanics.consumable.ConsumableSystem;
import io.github.term4.minestommechanics.mechanics.damage.DamageSystem;
import io.github.term4.minestommechanics.mechanics.durability.DurabilitySystem;
import io.github.term4.minestommechanics.mechanics.explosion.ExplosionSystem;
import io.github.term4.minestommechanics.mechanics.hunger.HungerSystem;
import io.github.term4.minestommechanics.mechanics.knockback.KnockbackSystem;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileSystem;
import io.github.term4.minestommechanics.presets.vanilla18.Attributes;
import io.github.term4.minestommechanics.presets.vanilla18.Damage;
import io.github.term4.minestommechanics.presets.vanilla18.Knockback;
import io.github.term4.minestommechanics.platform.fixes.FixesSystem;
import io.github.term4.minestommechanics.testsupport.HeadlessServerTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Re-installing a system must REPLACE its listeners, not stack them ({@code register} detaches the predecessor's
 * node - the stacking bug was doubled consume restores). Harness-pinned systems re-install with the harness config.
 */
class ModuleReinstallTest extends HeadlessServerTest {

    private record Case(String name, Supplier<MechanicsModule> install) {}

    @Test
    void reinstallDetachesThePredecessorsNode() {
        List<Case> cases = List.of(
                new Case("damage", () -> DamageSystem.install(mm, Damage.config())),
                new Case("knockback", () -> KnockbackSystem.install(mm, Knockback.melee())),
                new Case("attributes", () -> AttributeSystem.install(mm, Attributes.config())),
                new Case("attack", () -> AttackSystem.install(mm)),
                new Case("hunger", () -> HungerSystem.install(mm)),
                new Case("consumable", () -> ConsumableSystem.install(mm)),
                new Case("blocking", () -> BlockingSystem.install(mm)),
                new Case("projectile", () -> ProjectileSystem.install(mm)),
                new Case("explosion", () -> ExplosionSystem.install(mm)),
                new Case("durability", () -> DurabilitySystem.install(mm)),
                new Case("fixes", () -> FixesSystem.install(mm)));
        for (Case c : cases) {
            MechanicsModule first = c.install().get();
            MechanicsModule second = c.install().get();
            assertNotNull(first.node(), c.name() + ": module exposes its node");
            assertNull(first.node().getParent(), c.name() + ": the replaced install's node is detached");
            assertNotNull(second.node().getParent(), c.name() + ": the live install's node is attached");
            assertSame(second, mm.module(second.getClass()), c.name() + ": the registry holds the live install");
        }
    }
}
