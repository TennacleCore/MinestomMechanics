package io.github.term4.minestommechanics.mechanics.scrims18;

import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileConfig;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileSnapshot;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileSystem;
import io.github.term4.minestommechanics.mechanics.projectile.entities.ProjectileEntity;
import io.github.term4.minestommechanics.mechanics.projectile.types.Snowball;
import io.github.term4.minestommechanics.mechanics.vanilla18.Vanilla18;
import io.github.term4.minestommechanics.testsupport.FakePlayer;
import io.github.term4.minestommechanics.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.play.EntityTeleportPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** scrims18 projectiles are fully client-predicted: spawn + velocity, then never a periodic position sync. */
class Scrims18ProjectileTest extends HeadlessServerTest {

    private static long teleports(FakePlayer viewer, ProjectileEntity ball) {
        return viewer.sent.stream()
                .map(sp -> SendablePacket.extractServerPacket(ConnectionState.PLAY, sp))
                .filter(pk -> pk instanceof EntityTeleportPacket tp && tp.entityId() == ball.getEntityId())
                .count();
    }

    private static long syncTeleportsOver30Ticks(FakePlayer shooter, ProjectileConfig config) {
        var snap = ProjectileSnapshot.of(shooter.player, Snowball.INSTANCE).withConfig(config);
        ProjectileEntity ball = new ProjectileSystem(MinestomMechanics.getInstance(), config).launch(snap);
        assertNotNull(ball);
        awaitSpawn(ball);
        shooter.sent.clear(); // discard the spawn + its velocity; count only post-spawn syncs
        for (int tick = 1; tick <= 30 && !ball.isRemoved(); tick++) ball.tick(tick * 50L);
        long n = teleports(shooter, ball);
        if (!ball.isRemoved()) ball.remove();
        return n;
    }

    @Test
    void scrimsProjectilesNeverSynchronizeButVanillaDoes() {
        FakePlayer shooter = FakePlayer.connect(instance, new Pos(200.5, 90, 200.5, 0.0f, -10.0f), "ScrimShooter");
        try {
            assertEquals(0, syncTeleportsOver30Ticks(shooter, Scrims18.projectiles()),
                    "scrims18: spawn + velocity, then never synchronizes");
            assertTrue(syncTeleportsOver30Ticks(shooter, Vanilla18.projectiles()) > 0,
                    "vanilla18: the tracker keeps re-syncing position (the contrast)");
        } finally {
            shooter.player.remove();
        }
    }
}
