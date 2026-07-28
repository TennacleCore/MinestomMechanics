package test.presets;

import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.api.event.projectile.PearlTeleportEvent;
import io.github.term4.polyp.mechanics.projectile.ProjectileConfig;
import io.github.term4.polyp.mechanics.projectile.ProjectileSnapshot;
import io.github.term4.polyp.mechanics.projectile.ProjectileSystem;
import io.github.term4.polyp.mechanics.projectile.entities.ProjectileEntity;
import io.github.term4.polyp.mechanics.projectile.types.Pearl;
import io.github.term4.polyp.presets.mmc18.Projectiles;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.event.EventListener;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.server.play.PlayerPositionAndLookPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MineMen pearl teleport, captured 2026-07-27 (~110 teleports incl. a four-face pillar): the pearl's CONTINUOUS
 * position with only the collision axis resolved to the hit face plus a pushback - horizontal 0.4 outward (mirrored
 * .4/.6 fractions on opposite faces), floor 0 (feet exactly on the face), ceiling 2 below; entity hits project to
 * the ground below the impact.
 */
class Mmc18PearlTest extends HeadlessServerTest {

    private static Pos pearlLanding(FakePlayer shooter, Pos from) {
        shooter.player.teleport(from).join();
        ProjectileConfig config = Projectiles.config();
        var snap = ProjectileSnapshot.of(shooter.player, Pearl.INSTANCE).withConfig(config);
        ProjectileEntity pearl = new ProjectileSystem(Polyp.getInstance(), config).launch(snap);
        assertNotNull(pearl);
        awaitSpawn(pearl);
        for (int tick = 1; tick <= 200 && !pearl.isRemoved(); tick++) pearl.tick(tick * 50L);
        assertTrue(pearl.isRemoved(), "pearl never impacted");
        return shooter.player.getPosition();
    }

    /** Feet land exactly on the floor plane; x/z stay the pearl's continuous position (no block snap). */
    @Test
    void floorHitLandsFeetOnTheFace() {
        FakePlayer shooter = FakePlayer.connect(instance, new Pos(400.3, 80, 400.3, 0f, 90f), "MmcPearlDn");
        try {
            Pos landed = pearlLanding(shooter, new Pos(400.3, 80, 400.3, 0f, 90f));
            assertEquals(64.0, landed.y(), 1e-9, "feet exactly on the floor plane");
            assertTrue(Math.abs(landed.x() - 400.3) < 0.25, "x stays continuous: " + landed);
        } finally {
            shooter.player.remove();
        }
    }

    /** Opposite pillar faces push out by 0.4 in mirrored directions (the four-face capture's .4/.6 signature). */
    @Test
    void wallHitPushesOutPointFourFromTheFace() {
        int px = 420, pz = 420; // pillar columns x=420..421
        for (int x = px; x <= px + 1; x++)
            for (int y = 64; y <= 70; y++)
                for (int z = pz - 1; z <= pz + 1; z++) instance.setBlock(x, y, z, Block.STONE);
        FakePlayer shooter = FakePlayer.connect(instance, new Pos(425.5, 65, 420.5, 90f, 0f), "MmcPearlWall");
        try {
            Pos east = pearlLanding(shooter, new Pos(425.5, 65, 420.5, 90f, 0f));   // yaw 90 = -x, into the east face at 422.0
            assertEquals(422.4, east.x(), 1e-9, "east face + 0.4 out: " + east);
            Pos west = pearlLanding(shooter, new Pos(416.5, 65, 420.5, -90f, 0f));  // yaw -90 = +x, into the west face at 420.0
            assertEquals(419.6, west.x(), 1e-9, "west face - 0.4 out: " + west);
            assertTrue(east.y() % 1 != 0.0 && west.y() % 1 != 0.0, "y stays the pearl's continuous height");
        } finally {
            shooter.player.remove();
        }
    }

    /** A wall hit also pushes 0.4 back along the FREE horizontal axis, opposite the travel: of two near-mirrored
     *  throws into the same face, the +z-travel one lands at LOWER z (without the shift it would land higher). */
    @Test
    void wallHitPushesBackAlongTheFreeAxis() {
        int px = 430, pz = 430; // pillar columns x=430..431
        for (int x = px; x <= px + 1; x++)
            for (int y = 64; y <= 70; y++)
                for (int z = pz - 2; z <= pz + 2; z++) instance.setBlock(x, y, z, Block.STONE);
        FakePlayer shooter = FakePlayer.connect(instance, new Pos(435.5, 65, 430.5, 88f, 0f), "MmcPearlFree");
        try {
            Pos plusZ = pearlLanding(shooter, new Pos(435.5, 65, 430.5, 88f, 0f));   // toward -x, drifting +z
            Pos minusZ = pearlLanding(shooter, new Pos(435.5, 65, 430.5, 92f, 0f));  // mirrored, drifting -z
            assertEquals(432.4, plusZ.x(), 1e-9, "east face + 0.4 out: " + plusZ);
            // mirror center = throw line shifted by the vanilla sideways spawn offset (-sin(yaw)*0.16)
            assertEquals(430.34, (plusZ.z() + minusZ.z()) / 2, 0.01, "mirrored contacts around the spawn line");
            assertTrue(plusZ.z() < minusZ.z(), "the 0.4 push-back crosses the mirrored landings: " + plusZ + " vs " + minusZ);
        } finally {
            shooter.player.remove();
        }
    }

    /** A ceiling hit places the feet two below the underside - the player fits, unlike Hypixel's clip-in. */
    @Test
    void ceilingHitFitsTwoBelowTheFace() {
        int cx = 440, cz = 440, ceilY = 75;
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) instance.setBlock(cx + dx, ceilY, cz + dz, Block.STONE);
        FakePlayer shooter = FakePlayer.connect(instance, new Pos(cx + 0.3, 65, cz + 0.3, 0f, -90f), "MmcPearlUp");
        try {
            Pos landed = pearlLanding(shooter, new Pos(cx + 0.3, 65, cz + 0.3, 0f, -90f));
            assertEquals(73.0, landed.y(), 1e-9, "feet = ceiling face - 2: " + landed);
            assertTrue(Math.abs(landed.x() - (cx + 0.3)) < 0.25, "x stays continuous: " + landed);
        } finally {
            shooter.player.remove();
        }
    }

    /** A refused target (blocked feet..head column - mmcgeometryclip, 96/96) leaves the thrower unmoved. */
    @Test
    void blockedColumnRefusesTheTeleport() {
        int wz = 525, roofY = 66;
        for (int x = 516; x <= 524; x++) {
            for (int y = 64; y <= 70; y++) instance.setBlock(x, y, wz, Block.STONE); // wall
            for (int z = 521; z < wz; z++) instance.setBlock(x, roofY, z, Block.STONE); // roof in front of it
        }
        Pos from = new Pos(520.5, 64, 520.5, 0f, 0f); // flat throw under the roof into the wall: head into roof
        FakePlayer shooter = FakePlayer.connect(instance, from, "MmcPearlRoof");
        try {
            Pos landed = pearlLanding(shooter, from);
            assertEquals(520.5, landed.x(), 1e-6, "unmoved: " + landed);
            assertEquals(64.0, landed.y(), 1e-6, "unmoved: " + landed);
        } finally {
            shooter.player.remove();
        }
    }

    /** The refusal checks the block COLUMN only, not the 0.6-wide box: a ceiling target hugging a side wall
     *  still teleports (the corpus's one full-box mismatch, allowed by minemen). */
    @Test
    void wallHuggingCeilingTargetStillTeleports() {
        int cx = 540, cz = 540, ceilY = 75;
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) instance.setBlock(cx + dx, ceilY, cz + dz, Block.STONE);
        for (int y = 64; y < ceilY; y++) instance.setBlock(cx + 1, y, cz, Block.STONE); // side wall beside the throw column
        Pos from = new Pos(cx + 0.9, 65, cz + 0.3, 0f, -90f); // straight up, pearl column hugs the side wall
        FakePlayer shooter = FakePlayer.connect(instance, from, "MmcPearlHug");
        try {
            Pos landed = pearlLanding(shooter, from);
            assertEquals(73.0, landed.y(), 1e-9, "ceiling rule fires despite the box overlapping the side wall: " + landed);
        } finally {
            shooter.player.remove();
        }
    }

    /** A refusal is NOT wire-silent: minemen teleports you to your own location, so the client gets a real
     *  position echo (re-syncing its prediction) even though nobody moves. */
    @Test
    void refusalEchoesThePositionOnTheWire() {
        int wz = 565, roofY = 66;
        for (int x = 556; x <= 564; x++) {
            for (int y = 64; y <= 70; y++) instance.setBlock(x, y, wz, Block.STONE); // wall
            for (int z = 561; z < wz; z++) instance.setBlock(x, roofY, z, Block.STONE); // roof in front of it
        }
        Pos from = new Pos(560.5, 64, 560.5, 0f, 0f); // flat throw under the roof into the wall: head into roof
        FakePlayer shooter = FakePlayer.connect(instance, from, "MmcPearlEcho");
        try {
            shooter.player.teleport(from).join();
            ProjectileConfig config = Projectiles.config();
            var snap = ProjectileSnapshot.of(shooter.player, Pearl.INSTANCE).withConfig(config);
            ProjectileEntity pearl = new ProjectileSystem(Polyp.getInstance(), config).launch(snap);
            assertNotNull(pearl);
            awaitSpawn(pearl);
            shooter.sent.clear();
            for (int tick = 1; tick <= 200 && !pearl.isRemoved(); tick++) pearl.tick(tick * 50L);
            assertTrue(pearl.isRemoved(), "pearl never impacted");
            var echoes = shooter.sent.stream()
                    .filter(p -> p instanceof PlayerPositionAndLookPacket)
                    .map(p -> (PlayerPositionAndLookPacket) p).toList();
            assertEquals(1, echoes.size(), "the refusal sends one position echo: " + echoes);
            assertEquals(from.withView(0f, 0f), Pos.fromPoint(echoes.getFirst().position()).withView(0f, 0f), "echo = the unmoved position");
            assertEquals(from.x(), shooter.player.getPosition().x(), 1e-6, "nobody moves");
        } finally {
            shooter.player.remove();
        }
    }

    /** A cancelled {@link PearlTeleportEvent} consumes the pearl and moves nobody - the game-layer seam. */
    @Test
    void cancelledTeleportMovesNothing() {
        int wz = 505;
        for (int x = 496; x <= 504; x++)
            for (int y = 64; y <= 70; y++) instance.setBlock(x, y, wz, Block.STONE); // wall
        var boundary = EventListener.of(PearlTeleportEvent.class, e -> e.setCancelled(true));
        MinecraftServer.getGlobalEventHandler().addListener(boundary);
        Pos from = new Pos(500.5, 64, 500.5, 0f, 0f); // yaw 0 = +z, flat throw into the wall
        FakePlayer shooter = FakePlayer.connect(instance, from, "MmcPearlClip");
        try {
            Pos landed = pearlLanding(shooter, from);
            assertEquals(500.5, landed.x(), 1e-6, "unmoved: " + landed);
            assertEquals(64.0, landed.y(), 1e-6, "unmoved: " + landed);
            assertEquals(500.5, landed.z(), 1e-6, "unmoved: " + landed);
        } finally {
            MinecraftServer.getGlobalEventHandler().removeListener(boundary);
            shooter.player.remove();
        }
    }

    /** An entity hit teleports to the pearl's x/z at the top of the ground below the impact. */
    @Test
    void entityHitProjectsToTheGroundBelow() {
        var victim = zombie(new Pos(460.5, 64, 462.5));
        FakePlayer shooter = FakePlayer.connect(instance, new Pos(460.5, 64, 458.5, 0f, 0f), "MmcPearlEnt");
        try {
            Pos landed = pearlLanding(shooter, new Pos(460.5, 64, 458.5, 0f, 0f)); // yaw 0 = +z, into the zombie
            assertEquals(64.0, landed.y(), 1e-9, "ground plane below the body hit: " + landed);
            assertTrue(Math.abs(landed.x() - 460.5) < 0.25, "x stays the pearl's: " + landed);
            assertTrue(landed.z() > 460.5 && landed.z() < 463.5, "z at the victim: " + landed);
        } finally {
            shooter.player.remove();
            victim.remove();
        }
    }
}
