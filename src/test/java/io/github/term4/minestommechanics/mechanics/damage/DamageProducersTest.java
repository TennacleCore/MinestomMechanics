package io.github.term4.minestommechanics.mechanics.damage;

import io.github.term4.minestommechanics.testsupport.FakePlayer;
import io.github.term4.minestommechanics.testsupport.HeadlessServerTest;
import io.github.term4.minestommechanics.world.MechanicsWorld;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Producers read VIEWED blocks; a view-only observer must not take damage from a world they only watch. */
class DamageProducersTest extends HeadlessServerTest {

    @Test
    void viewOnlyObserversAreExempt() {
        var observer = FakePlayer.connect(instance, new Pos(0.5, 66, 0.5), "EnvObserver");
        Instance other = MinecraftServer.getInstanceManager().createInstanceContainer();
        try {
            assertFalse(DamageProducers.exempt(observer.player), "a plain survival player takes environmental damage");

            MechanicsWorld viewed = MechanicsWorld.of(other);
            MechanicsWorld.resolver(new MechanicsWorld.Resolver() {
                @Override public @Nullable MechanicsWorld resolve(@NotNull Entity entity) {
                    return entity.getTag(MechanicsWorld.ENTITY_TAG);
                }
                @Override public @Nullable MechanicsWorld viewedBlocks(@NotNull Player player) {
                    return player == observer.player ? viewed : null;
                }
            });
            assertTrue(DamageProducers.exempt(observer.player), "viewing is not being: viewed != own world");
        } finally {
            MechanicsWorld.resolver(MechanicsWorld.Resolver.DEFAULT);
            observer.player.remove();
            MinecraftServer.getInstanceManager().unregisterInstance(other);
        }
    }
}
