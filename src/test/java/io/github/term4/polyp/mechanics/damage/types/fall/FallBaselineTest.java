package io.github.term4.polyp.mechanics.damage.types.fall;

import io.github.term4.polyp.api.event.damage.types.FallDistanceResetEvent;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntityTickEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Fall distance accrues from y-deltas against a PREV baseline, so a teleport must re-anchor the baseline (or the jump
 * itself reads as a fall) while KEEPING the accumulated distance (vanilla does not reset fall distance on teleport).
 */
class FallBaselineTest extends HeadlessServerTest {

    private static void tick(LivingEntity e) {
        MinecraftServer.getGlobalEventHandler().call(new EntityTickEvent(e));
    }

    /** Vanilla 1.8 zeroes fall distance on a gamemode change; cancelling the event keeps it. */
    @Test
    void gameModeChangeResetsFallDistanceUnlessCancelled() {
        FakePlayer p = FakePlayer.connect(instance, new Pos(40.5, 100, 220.5), "FallGm");
        var events = MinecraftServer.getGlobalEventHandler();
        var keep = EventNode.all("keep-fall-distance");
        keep.addListener(FallDistanceResetEvent.class, e -> e.setCancelled(true));
        try {
            fall(p.player, 99);
            fall(p.player, 97);
            assertEquals(2f, FallDamage.fallDistance(p.player), 1e-6, "the fall accrued");
            p.player.setGameMode(GameMode.ADVENTURE);
            assertEquals(0f, FallDamage.fallDistance(p.player), 1e-6, "default: the switch cleared it");

            events.addChild(keep);
            fall(p.player, 95); // first move after the reset only re-anchors the baseline
            fall(p.player, 93);
            assertEquals(2f, FallDamage.fallDistance(p.player), 1e-6, "the fall re-accrued");
            p.player.setGameMode(GameMode.SURVIVAL);
            assertEquals(2f, FallDamage.fallDistance(p.player), 1e-6, "cancelled: distance kept");
        } finally {
            events.removeChild(keep);
            p.player.remove();
        }
    }

    private static void fall(Player p, double y) {
        MinecraftServer.getGlobalEventHandler().call(new PlayerMoveEvent(p, new Pos(40.5, y, 220.5), false));
    }

    @Test
    void downwardTeleportDoesNotAccrueFallDistance() {
        LivingEntity z = zombie(new Pos(0, 100, 210));
        tick(z);                                   // baseline @100
        z.refreshPosition(new Pos(0, 99, 210));
        tick(z);                                   // real fall: +1
        assertEquals(1f, FallDamage.fallDistance(z), 1e-6);

        z.teleport(new Pos(0, 50, 210)).join();    // the 49-block jump must not count
        tick(z);                                   // re-anchors @50
        z.refreshPosition(new Pos(0, 49.5, 210));
        tick(z);                                   // real fall resumes: +0.5
        assertEquals(1.5f, FallDamage.fallDistance(z), 1e-6);
    }
}
