package test.presets;

import io.github.term4.minestommechanics.api.event.AttackEvent;
import io.github.term4.minestommechanics.mechanics.attack.AttackSnapshot;
import io.github.term4.minestommechanics.mechanics.damage.DamageSystem;
import io.github.term4.minestommechanics.testsupport.HeadlessServerTest;
import io.github.term4.minestommechanics.util.tick.TickSystem;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Contract + regression for the mmc18 1-tick hit queue against the damage-invulnerability window. Combat reads the
 * instance-local {@link TickSystem}; headless tests have no tick loop, so the clock is positioned directly (via
 * reflection, mirroring how the rest of the suite drives the global clock). The queue's expiry phase is exercised by
 * the inline drain on the next attack, which is what these tests assert.
 */
class Mmc18HitQueueTest extends HeadlessServerTest {

    @BeforeEach
    void resetClockAndQueue() throws Exception {
        pendingHits().clear();
        setCombatTick(0);
    }

    @AfterEach
    void clearQueue() throws Exception {
        pendingHits().clear();
    }

    @Test
    void queuedHitFlushConsumesSameTickLiveAttack() {
        LivingEntity attacker = zombie(new Pos(0, 64, 600));
        LivingEntity victim = zombie(new Pos(0, 64, 601));
        attacker.setItemInMainHand(ItemStack.of(Material.DIAMOND_SWORD));
        victim.setHealth(20f);

        AttackEvent.AttackRule rule = mmc18.attackProcessor().create(services);

        setCombatTick(0);
        rule.processAttack(attack(attacker, victim, false));
        float afterFirst = (float) victim.getHealth();
        float firstDamage = 20f - afterFirst;
        assertEquals(10, DamageSystem.remainingDamageInvul(victim));

        setCombatTick(9);
        rule.processAttack(attack(attacker, victim, false));
        assertEquals(afterFirst, victim.getHealth(), 1e-4f, "queueing must not damage");
        assertEquals(1, DamageSystem.remainingDamageInvul(victim));

        setCombatTick(10);
        rule.processAttack(attack(attacker, victim, true));
        assertEquals(afterFirst - firstDamage, victim.getHealth(), 1e-4f,
                "the queued hit lands, but the same-tick live hit must not overdamage");
        assertEquals(10, DamageSystem.remainingDamageInvul(victim));
    }

    /** Regression for the phase bug: under per-tick spam, fresh hits are gated to exactly 10 combat ticks apart. */
    @Test
    void freshHitsAreExactly10CombatTicksApart_underSpam() {
        LivingEntity attacker = zombie(new Pos(0, 64, 602));
        LivingEntity victim = zombie(new Pos(0, 64, 603));
        attacker.setItemInMainHand(ItemStack.of(Material.DIAMOND_SWORD));

        AttackEvent.AttackRule rule = mmc18.attackProcessor().create(services);

        List<Long> freshTicks = new ArrayList<>();
        for (long t = 0; t <= 40; t++) {
            setCombatTick(t);
            victim.setHealth(20f); // keep the victim alive; fresh detection is via the i-frame window, not health
            rule.processAttack(attack(attacker, victim, false));
            // only a FRESH hit (re)opens a full window; overdamage/queued never reset it to its full duration
            if (DamageSystem.remainingDamageInvul(victim) == 10) freshTicks.add(t);
        }

        assertFalse(freshTicks.isEmpty(), "spam should land fresh hits");
        for (int i = 1; i < freshTicks.size(); i++) {
            assertEquals(10L, freshTicks.get(i) - freshTicks.get(i - 1),
                    "consecutive fresh hits must be exactly 10 combat ticks apart, got " + freshTicks);
        }
    }

    private static AttackEvent attack(LivingEntity attacker, LivingEntity victim, boolean critical) {
        AttackEvent event = new AttackEvent(new AttackSnapshot(attacker, victim, mmc18.atk()), services);
        event.overrideCritical(critical);
        return event;
    }

    /** Positions the test instance's combat clock (no public setter by design; mirrors the suite's clock reflection). */
    @SuppressWarnings("unchecked")
    private static void setCombatTick(long value) {
        try {
            Field f = TickSystem.class.getDeclaredField("CLOCKS");
            f.setAccessible(true);
            Map<Instance, AtomicLong> clocks = (Map<Instance, AtomicLong>) f.get(null);
            clocks.computeIfAbsent(instance, k -> new AtomicLong()).set(value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<LivingEntity, ?> pendingHits() throws Exception {
        Field f = mmc18.class.getDeclaredField("pendingHit");
        f.setAccessible(true);
        return (Map<LivingEntity, ?>) f.get(null);
    }
}
