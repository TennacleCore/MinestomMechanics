package io.github.term4.minestommechanics.effect;

import io.github.term4.minestommechanics.MechanicsKeys;
import io.github.term4.minestommechanics.MechanicsProfile;
import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.api.event.EffectEvent;
import io.github.term4.minestommechanics.testsupport.FakePlayer;
import io.github.term4.minestommechanics.testsupport.HeadlessServerTest;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.event.EventListener;
import net.kyori.adventure.key.Key;
import net.minestom.server.network.packet.server.play.EntityAnimationPacket;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.network.packet.server.play.SoundEffectPacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.sound.SoundEvent;
import org.junit.jupiter.api.AfterEach;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The registry-based effect layer: {@link EffectRegistry} (register/override) + the generic {@link Effects#play}. */
class EffectsTest extends HeadlessServerTest {

    @AfterEach
    void clearScope() { MinestomMechanics.getInstance().profiles().setGlobal(null); }

    private void useRegistry(EffectRegistry reg) {
        MinestomMechanics.getInstance().profiles().setGlobal(
                MechanicsProfile.builder().set(MechanicsKeys.EFFECTS, reg).build());
    }

    // ARROW_CRIT is a positional particle (world broadcast), so the source itself receives it - a reliable test audience.
    private static int arrowCrits(FakePlayer p) {
        return (int) p.sent(ParticlePacket.class).stream().filter(e -> e.particle() == Particle.CRIT).count();
    }

    private static int critAnims(FakePlayer p) {
        return (int) p.sent(EntityAnimationPacket.class).stream()
                .filter(e -> e.animation() == EntityAnimationPacket.Animation.CRITICAL_EFFECT).count();
    }

    private static int sounds(FakePlayer p, SoundEvent ev) {
        return (int) p.sent(SoundEffectPacket.class).stream().filter(e -> e.soundEvent() == ev).count();
    }

    private static int eatSounds(FakePlayer p) { return sounds(p, SoundEvent.ENTITY_GENERIC_EAT); }

    @Test
    void registryIsCopyOnWriteAndOverrides() {
        Effect a = ctx -> {};
        Effect b = ctx -> {};
        EffectRegistry reg = EffectRegistry.empty().register(Effects.CRIT, a);
        assertSame(a, reg.get(Effects.CRIT));
        assertNull(reg.get(Effects.BURP), "an unregistered key resolves to null");
        EffectRegistry reg2 = reg.register(Effects.CRIT, b);
        assertSame(a, reg.get(Effects.CRIT), "the original registry is unchanged (copy-on-write)");
        assertSame(b, reg2.get(Effects.CRIT), "the copy carries the override");
    }

    @Test
    void playResolvesTheScopeRegistryAndReachesTheAudience() {
        useRegistry(Effects.vanilla18());
        FakePlayer p = FakePlayer.connect(instance, new Pos(5.5, 65, 5.5), "FxP");
        try {
            Effects.play(services, Effects.ARROW_CRIT, EffectContext.of(p.player));
            assertEquals(1, arrowCrits(p), "the registered particle reaches the scope audience");
        } finally {
            p.player.remove();
        }
    }

    @Test
    void noRegistryOrUnregisteredKeyIsANoOp() {
        FakePlayer p = FakePlayer.connect(instance, new Pos(6.5, 65, 6.5), "FxP2");
        try {
            Effects.play(services, Effects.ARROW_CRIT, EffectContext.of(p.player)); // no registry set
            assertEquals(0, arrowCrits(p), "no scope registry -> nothing plays");
            useRegistry(EffectRegistry.empty());
            Effects.play(services, Effects.ARROW_CRIT, EffectContext.of(p.player)); // key not registered
            assertEquals(0, arrowCrits(p), "an unregistered key -> nothing plays");
        } finally {
            p.player.remove();
        }
    }

    @Test
    void perKeyNoneSilencesOneEffect() {
        useRegistry(Effects.vanilla18().register(Effects.ARROW_CRIT, Effect.NONE));
        FakePlayer p = FakePlayer.connect(instance, new Pos(7.5, 65, 7.5), "FxP3");
        try {
            Effects.play(services, Effects.ARROW_CRIT, EffectContext.of(p.player));
            assertEquals(0, arrowCrits(p), "a key registered as NONE plays nothing");
        } finally {
            p.player.remove();
        }
    }

    @Test
    void eventCancelsAndSwaps() {
        useRegistry(Effects.vanilla18());
        FakePlayer a = FakePlayer.connect(instance, new Pos(8.5, 65, 8.5), "FxP4");
        EventListener<EffectEvent> canceller = EventListener.of(EffectEvent.class, EffectEvent::cancel);
        MinecraftServer.getGlobalEventHandler().addListener(canceller);
        try {
            Effects.play(services, Effects.ARROW_CRIT, EffectContext.of(a.player));
            assertEquals(0, arrowCrits(a), "a listener cancelled the effect");
        } finally {
            MinecraftServer.getGlobalEventHandler().removeListener(canceller);
            a.player.remove();
        }

        useRegistry(Effects.vanilla18().register(Effects.ARROW_CRIT, Effect.NONE)); // disabled in the registry...
        FakePlayer b = FakePlayer.connect(instance, new Pos(9.5, 65, 9.5), "FxP5");
        EventListener<EffectEvent> swapper = EventListener.of(EffectEvent.class,
                e -> e.effect(Effect.particle(Particle.CRIT, 1, 0.0, 0f)));
        MinecraftServer.getGlobalEventHandler().addListener(swapper);
        try {
            Effects.play(services, Effects.ARROW_CRIT, EffectContext.of(b.player));
            assertTrue(arrowCrits(b) >= 1, "...but a listener swapped a real effect back in");
        } finally {
            MinecraftServer.getGlobalEventHandler().removeListener(swapper);
            b.player.remove();
        }
    }

    @Test
    void critAnimationExcludesTheAttacker() {
        useRegistry(Effects.vanilla18());
        FakePlayer attacker = FakePlayer.connect(instance, new Pos(10.5, 65, 10.5), "Critter");
        LivingEntity victim = zombie(new Pos(11.5, 65, 10.5));
        try {
            Effects.play(services, Effects.CRIT, EffectContext.of(attacker.player, victim));
            // the attacker's own 1.8 client predicts the crit locally, so the server must not echo it back
            assertEquals(0, critAnims(attacker), "the crit is not sent to the attacker itself");
        } finally {
            attacker.player.remove();
            victim.remove();
        }
    }

    @Test
    void eatChewIsViewersOnlyOn18() {
        useRegistry(Effects.vanilla18());
        FakePlayer eater = FakePlayer.connect(instance, new Pos(5.5, 65, 5.5), "Eater");
        FakePlayer viewer = FakePlayer.connect(instance, new Pos(7.5, 65, 5.5), "Viewer");
        try {
            assertTrue(eater.player.getViewers().contains(viewer.player), "a nearby player tracks the eater");
            Effects.play(services, Effects.EAT, EffectContext.of(eater.player));
            // the client self-predicts its own chew from the eating metadata, so the server sends it to viewers only
            assertEquals(0, eatSounds(eater), "the eater self-predicts, so gets no server chew");
            assertEquals(1, eatSounds(viewer), "a nearby viewer hears the chew");
        } finally {
            eater.player.remove();
            viewer.player.remove();
        }
    }

    @Test
    void eatChewIsViewersOnlyOnModern() {
        useRegistry(Effects.modern());
        FakePlayer eater = FakePlayer.connect(instance, new Pos(5.5, 65, 5.5), "MEater");
        FakePlayer viewer = FakePlayer.connect(instance, new Pos(7.5, 65, 5.5), "MViewer");
        try {
            assertTrue(eater.player.getViewers().contains(viewer.player), "a nearby player tracks the eater");
            Effects.play(services, Effects.EAT, EffectContext.of(eater.player));
            // modern excludes the eater too - the client self-predicts, echoing it back would double
            assertEquals(0, eatSounds(eater), "the modern eater gets no server chew (no double)");
            assertEquals(1, eatSounds(viewer), "a nearby viewer hears the chew");
        } finally {
            eater.player.remove();
            viewer.player.remove();
        }
    }

    @Test
    void everyLaunchAndHitSoundIsRegisteredInBothPresets() {
        List<Key> keys = List.of(Effects.THROW_SNOWBALL, Effects.THROW_EGG, Effects.THROW_PEARL, Effects.THROW_FIREBALL,
                Effects.BOW_SHOOT, Effects.ROD_CAST, Effects.ROD_RETRIEVE, Effects.ARROW_HIT);
        for (EffectRegistry reg : List.of(Effects.vanilla18(), Effects.modern()))
            for (Key k : keys) assertNotNull(reg.get(k), k + " is registered");
    }

    @Test
    void throwSoundReachesEveryoneIncludingTheThrower() {
        useRegistry(Effects.vanilla18());
        FakePlayer thrower = FakePlayer.connect(instance, new Pos(5.5, 65, 5.5), "Thrower");
        FakePlayer viewer = FakePlayer.connect(instance, new Pos(7.5, 65, 5.5), "TViewer");
        try {
            assertTrue(thrower.player.getViewers().contains(viewer.player), "a nearby player tracks the thrower");
            Effects.play(services, Effects.THROW_SNOWBALL, EffectContext.of(thrower.player));
            // the 1.8 client does NOT self-predict the throw (unlike the chew), so the server must send it to the thrower too
            assertEquals(1, sounds(thrower, SoundEvent.ENTITY_SNOWBALL_THROW), "the thrower hears its own throw");
            assertEquals(1, sounds(viewer, SoundEvent.ENTITY_SNOWBALL_THROW), "a nearby viewer hears the throw");
        } finally {
            thrower.player.remove();
            viewer.player.remove();
        }
    }

    @Test
    void arrowHitMarkerDingsOnlyTheShooterAndOnlyWhenRegistered() {
        FakePlayer shooter = FakePlayer.connect(instance, new Pos(5.5, 65, 5.5), "Shooter");
        FakePlayer bystander = FakePlayer.connect(instance, new Pos(7.5, 65, 5.5), "Bystander");
        try {
            useRegistry(Effects.vanilla18()); // ARROW_HIT_PLAYER unregistered -> the ding is off
            Effects.play(services, Effects.ARROW_HIT_PLAYER, EffectContext.of(shooter.player, bystander.player));
            assertEquals(0, sounds(shooter, SoundEvent.ENTITY_ARROW_HIT_PLAYER), "vanilla does not ding");

            useRegistry(Effects.vanilla18().register(Effects.ARROW_HIT_PLAYER, Effects.arrowHitMarker())); // a PvP preset enables it
            Effects.play(services, Effects.ARROW_HIT_PLAYER, EffectContext.of(shooter.player, bystander.player));
            assertEquals(1, sounds(shooter, SoundEvent.ENTITY_ARROW_HIT_PLAYER), "the shooter hears the ding");
            assertEquals(0, sounds(bystander, SoundEvent.ENTITY_ARROW_HIT_PLAYER), "only the shooter, not a bystander");
        } finally {
            shooter.player.remove();
            bystander.player.remove();
        }
    }
}
