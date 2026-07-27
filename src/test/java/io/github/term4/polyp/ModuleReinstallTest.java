package io.github.term4.polyp;

import io.github.term4.polyp.mechanics.attack.AttackSystem;
import io.github.term4.polyp.mechanics.attribute.AttributeSystem;
import io.github.term4.polyp.mechanics.blocking.BlockingSystem;
import io.github.term4.polyp.mechanics.consumable.ConsumableSystem;
import io.github.term4.polyp.mechanics.damage.DamageSystem;
import io.github.term4.polyp.mechanics.durability.DurabilitySystem;
import io.github.term4.polyp.mechanics.explosion.ExplosionSystem;
import io.github.term4.polyp.mechanics.hunger.HungerSystem;
import io.github.term4.polyp.mechanics.knockback.KnockbackSystem;
import io.github.term4.polyp.mechanics.projectile.ProjectileSystem;
import io.github.term4.polyp.presets.vanilla18.Attributes;
import io.github.term4.polyp.presets.vanilla18.Damage;
import io.github.term4.polyp.presets.vanilla18.Knockback;
import io.github.term4.polyp.platform.fixes.FixesSystem;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Re-install must replace listeners, not stack them - the stacking bug was doubled consume restores. */
class ModuleReinstallTest extends HeadlessServerTest {

    private record Case(String name, Supplier<MechanicsModule> install) {}

    @Test
    void reinstallDetachesThePredecessorsNode() {
        List<Case> cases = List.of(
                new Case("damage", () -> DamageSystem.install(polyp, Damage.config())),
                new Case("knockback", () -> KnockbackSystem.install(polyp, Knockback.melee())),
                new Case("attributes", () -> AttributeSystem.install(polyp, Attributes.config())),
                new Case("attack", () -> AttackSystem.install(polyp)),
                new Case("hunger", () -> HungerSystem.install(polyp)),
                new Case("consumable", () -> ConsumableSystem.install(polyp)),
                new Case("blocking", () -> BlockingSystem.install(polyp)),
                new Case("projectile", () -> ProjectileSystem.install(polyp)),
                new Case("explosion", () -> ExplosionSystem.install(polyp)),
                new Case("durability", () -> DurabilitySystem.install(polyp)),
                new Case("fixes", () -> FixesSystem.install(polyp)));
        for (Case c : cases) {
            MechanicsModule first = c.install().get();
            MechanicsModule second = c.install().get();
            assertNotNull(first.node(), c.name() + ": module exposes its node");
            assertNull(first.node().getParent(), c.name() + ": the replaced install's node is detached");
            assertNotNull(second.node().getParent(), c.name() + ": the live install's node is attached");
            assertSame(second, polyp.module(second.getClass()), c.name() + ": the registry holds the live install");
        }
    }
}
