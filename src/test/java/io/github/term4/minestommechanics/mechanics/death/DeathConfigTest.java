package io.github.term4.minestommechanics.mechanics.death;

import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.MechanicsProfile;
import io.github.term4.minestommechanics.mechanics.attribute.catalog.effect.Speed;
import io.github.term4.minestommechanics.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DeathConfigTest extends HeadlessServerTest {

    @Test
    void fromBaseFillsUnsetKnobsFromBase() {
        DeathConfig base = DeathConfig.builder()
                .clearEffects(true).resetCombatState(true).hideCorpse(true).deathAnimationTicks(20).build();
        DeathConfig merged = DeathConfig.builder().hideCorpse(false).build().fromBase(base);
        assertEquals(Boolean.TRUE, merged.clearEffects());
        assertEquals(Boolean.TRUE, merged.resetCombatState());
        assertEquals(Boolean.FALSE, merged.hideCorpse());
        assertEquals(Integer.valueOf(20), merged.deathAnimationTicks());
    }

    @Test
    void emptyBuilderLeavesEveryKnobUnset() {
        DeathConfig empty = DeathConfig.builder().build();
        assertNull(empty.clearEffects());
        assertNull(empty.resetCombatState());
        assertNull(empty.hideCorpse());
        assertNull(empty.deathAnimationTicks());
    }

    @Test
    void deathHonorsClearEffectsDisabled() {
        MechanicsProfile prev = mm.profiles().global();
        mm.profiles().setGlobal(MechanicsProfile.builder()
                .set(MechanicsKeys.DEATH, DeathConfig.builder().clearEffects(false).build()).build());
        try {
            LivingEntity e = zombie(new Pos(0, 64, 68));
            PotionEffect speed = PotionEffect.fromKey(Speed.KEY);
            assertNotNull(speed, "speed effect");
            e.addEffect(new Potion(speed, 0, 600));
            e.kill();
            assertFalse(e.getActiveEffects().isEmpty(), "clearEffects=false keeps effects on death");
        } finally {
            mm.profiles().setGlobal(prev);
        }
    }
}
