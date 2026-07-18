package io.github.term4.minestommechanics.mechanics.cooldown;

import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.MechanicsProfile;
import io.github.term4.minestommechanics.testsupport.FakePlayer;
import io.github.term4.minestommechanics.testsupport.HeadlessServerTest;
import io.github.term4.minestommechanics.util.tick.TickSystem;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.play.SetCooldownPacket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Server-authoritative item cooldowns: {@link CooldownSystem#tryUse} arms on a successful use (sending the client
 * overlay), gates while active, and re-allows at expiry; unconfigured materials are never gated.
 */
class CooldownSystemTest extends HeadlessServerTest {

    private static CooldownSystem system;

    @BeforeAll
    static void installCooldowns() {
        system = CooldownSystem.install(mm);
    }

    @Test
    void gatesArmsAndExpires() {
        Instance inst = flatInstance(MechanicsProfile.builder()
                .set(MechanicsKeys.COOLDOWNS, CooldownConfig.builder().cooldown(Material.ENDER_PEARL, 20).build())
                .build());
        FakePlayer p = FakePlayer.connect(inst, new Pos(8.5, 64, 8.5), "Cooldown");
        setCombatTick(inst, 0);

        p.sent.clear();
        assertTrue(system.tryUse(p.player, Material.ENDER_PEARL), "first use allowed");
        assertTrue(p.sent.stream().anyMatch(x -> x instanceof SetCooldownPacket c
                && c.cooldownGroup().equals("minecraft:ender_pearl") && c.cooldownTicks() == 20), "overlay sent");
        assertTrue(system.isOnCooldown(p.player, Material.ENDER_PEARL));
        assertFalse(system.tryUse(p.player, Material.ENDER_PEARL), "spam use gated");
        assertTrue(system.tryUse(p.player, Material.SNOWBALL), "unconfigured material never gated");

        setCombatTick(inst, 20);
        assertFalse(system.isOnCooldown(p.player, Material.ENDER_PEARL));
        assertTrue(system.tryUse(p.player, Material.ENDER_PEARL), "re-allowed at expiry");
    }

    @SuppressWarnings("unchecked")
    private static void setCombatTick(Instance inst, long value) {
        try {
            Field f = TickSystem.class.getDeclaredField("CLOCKS");
            f.setAccessible(true);
            ((Map<Instance, AtomicLong>) f.get(null)).computeIfAbsent(inst, k -> new AtomicLong()).set(value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
