package io.github.term4.minestommechanics.mechanics.projectile;

import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.mechanics.projectile.entities.ProjectileEntity;
import io.github.term4.minestommechanics.mechanics.projectile.types.SplashPotion;
import io.github.term4.minestommechanics.testsupport.FakePlayer;
import io.github.term4.minestommechanics.testsupport.HeadlessServerTest;
import io.github.term4.minestommechanics.mechanics.vanilla18.Vanilla18;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.PotionContents;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket;
import net.minestom.server.network.packet.server.play.EntityTeleportPacket;
import net.minestom.server.network.packet.server.play.EntityVelocityPacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
import net.minestom.server.potion.PotionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Client-prediction lockstep (silent flight, the mmc18 splash model - vanilla presets use the tracker wire): the
 * server sim must reproduce, bit for bit, the flight the predicting client integrates from its decoded spawn wire -
 * the 1.8 client ({@code EntityThrowable.onUpdate}) on the LEGACY_1_8 grid, the 26.1 client
 * ({@code ThrowableProjectile.tick}) on the MODERN grid. Exactness is what lands the break on the client's bottle.
 */
class SplashPotionLockstepTest extends HeadlessServerTest {

    private static final int FLIGHT_TICKS = 20;
    private static final double DRAG_099F = 0.99f;
    private static final double GRAVITY_005F = 0.05f;

    @BeforeAll
    static void loadFlightArea() {
        for (int x = 0; x <= 2; x++)
            for (int z = 0; z <= 1; z++)
                instance.loadChunk(x, z).join();
    }

    /** Launches from a per-test spot; the shooter is removed so it can't sit in another test's flight corridor. */
    private static ProjectileEntity launch(ProjectileConfig config, Pos from) {
        LivingEntity shooter = looseZombie();
        shooter.setInstance(instance, from).join();
        ItemStack item = ItemStack.of(Material.SPLASH_POTION)
                .with(DataComponents.POTION_CONTENTS, new PotionContents(PotionType.SWIFTNESS));
        var snap = ProjectileSnapshot.of(shooter, SplashPotion.INSTANCE).withItem(item).withConfig(config);
        ProjectileEntity entity = new ProjectileSystem(MinestomMechanics.getInstance(), config).launch(snap);
        assertNotNull(entity);
        awaitSpawn(entity);
        shooter.remove();
        return entity;
    }

    /** The vanilla18 tracker wire, capture-verified vs a real 1.8 server: spawn, item metadata IMMEDIATELY after (the
     *  ViaRewind held-spawn releases on it), the m=0 velocity, then per interval one teleport CARRYING the live
     *  velocity (Via re-emits it as the 1.8 entity_velocity). The spawn velocity dup is Via-synthesized, not ours. */
    @Test
    void trackerWireOrderIsSpawnThenItemMetaThenMotion() {
        var viewer = FakePlayer.connect(instance, new Pos(24.5, 150, 24.5), "SplashOrder");
        LivingEntity shooter = looseZombie();
        shooter.setInstance(instance, new Pos(24.5, 150, 26.5, 0.0f, 20.0f)).join();
        ItemStack item = ItemStack.of(Material.SPLASH_POTION)
                .with(DataComponents.POTION_CONTENTS, new PotionContents(PotionType.SWIFTNESS));
        var config = Vanilla18.projectiles();
        var snap = ProjectileSnapshot.of(shooter, SplashPotion.INSTANCE).withItem(item).withConfig(config);
        viewer.sent.clear();
        ProjectileEntity potion = new ProjectileSystem(MinestomMechanics.getInstance(), config).launch(snap);
        assertNotNull(potion);
        awaitSpawn(potion);
        int id = potion.getEntityId();
        for (int tick = 1; tick <= 12; tick++) {
            if (tick == 10) assertEquals(3, viewer.packetsFor(id).size(), "silent until the tick-10 correction");
            potion.tick(tick * 50L);
        }

        List<ServerPacket> forPotion = viewer.packetsFor(id);
        assertEquals(4, forPotion.size(), "spawn + meta + m=0 velocity, then ONLY the tick-10 teleport carrying velocity: " + forPotion);
        assertInstanceOf(SpawnEntityPacket.class, forPotion.get(0), "spawn must be first: " + forPotion);
        assertInstanceOf(EntityMetaDataPacket.class, forPotion.get(1), "item metadata must immediately follow the spawn: " + forPotion);
        assertTrue(((EntityMetaDataPacket) forPotion.get(1)).entries().values().stream()
                .anyMatch(e -> e.value() instanceof ItemStack), "the spawn-adjacent metadata must carry the potion item");
        assertInstanceOf(EntityVelocityPacket.class, forPotion.get(2), "m=0 velocity after the first physics tick: " + forPotion);
        EntityTeleportPacket correction = assertInstanceOf(EntityTeleportPacket.class, forPotion.get(3));
        assertTrue(correction.delta().distanceSquared(Vec.ZERO) > 0, "the correction teleport carries the live velocity");
        viewer.player.remove();
        potion.remove();
        shooter.remove();
    }

    @Test
    void mmc18SimMatchesThe18ClientBitForBit() {
        ProjectileEntity potion = launch(io.github.term4.minestommechanics.presets.mmc18.Projectiles.config(),
                new Pos(8.5, 150, 8.5, 37.0f, 12.5f));
        Pos spawn = potion.getSpawnPosition();
        assertNotNull(spawn);
        // spawn state is the 1.8 client's decoded wire: 1/32 position grid, velocity a fixed point of LP encode + Via shorts
        assertEquals((int) (spawn.x() * 32) / 32.0, spawn.x());
        assertEquals((int) (spawn.y() * 32) / 32.0, spawn.y());
        assertEquals((int) (spawn.z() * 32) / 32.0, spawn.z());
        Vec v = potion.velocityBt();
        assertTrue(v.lengthSquared() > 0);
        Vec lp = lpRoundTrip(v);
        assertEquals(new Vec(legacyShortAxis(lp.x()), legacyShortAxis(lp.y()), legacyShortAxis(lp.z())), v,
                "sim velocity must equal the 1.8 client's decode of its own wire");

        // 1.8 EntityThrowable.onUpdate: move, then *= 0.99F, then y -= 0.05F (floats widened per tick)
        double px = spawn.x(), py = spawn.y(), pz = spawn.z();
        double vx = v.x(), vy = v.y(), vz = v.z();
        for (int tick = 1; tick <= FLIGHT_TICKS; tick++) {
            px += vx; py += vy; pz += vz;
            vx *= DRAG_099F; vy *= DRAG_099F; vz *= DRAG_099F;
            vy -= GRAVITY_005F;
            potion.tick(tick * 50L);
            assertEquals(px, potion.getPosition().x(), "x @ tick " + tick);
            assertEquals(py, potion.getPosition().y(), "y @ tick " + tick);
            assertEquals(pz, potion.getPosition().z(), "z @ tick " + tick);
        }
        potion.remove();
    }

    @Test
    void modernSimMatchesThe26ClientBitForBit() {
        // the modern splash on silent flight (the knob; the vanilla preset itself ships the tracker wire)
        ProjectileConfig config = ProjectileConfig.builder(io.github.term4.minestommechanics.mechanics.vanilla.Projectiles.config())
                .typeConfigs(io.github.term4.minestommechanics.mechanics.vanilla.Projectiles.splashPotion().toBuilder()
                        .velocitySyncInterval(0).build())
                .build();
        ProjectileEntity potion = launch(config, new Pos(40.5, 150, 8.5, 37.0f, 12.5f));
        Pos spawn = potion.getSpawnPosition();
        assertNotNull(spawn);
        Vec v = potion.velocityBt();
        assertTrue(v.lengthSquared() > 0);
        assertEquals(lpRoundTrip(v), v, "sim velocity must equal the modern client's LP decode");

        // 26.1 ThrowableProjectile.tick: y -= 0.05 (double), *= 0.99F (widened), then move
        double px = spawn.x(), py = spawn.y(), pz = spawn.z();
        double vx = v.x(), vy = v.y(), vz = v.z();
        for (int tick = 1; tick <= FLIGHT_TICKS; tick++) {
            vy -= 0.05;
            vx *= DRAG_099F; vy *= DRAG_099F; vz *= DRAG_099F;
            px += vx; py += vy; pz += vz;
            potion.tick(tick * 50L);
            assertEquals(px, potion.getPosition().x(), "x @ tick " + tick);
            assertEquals(py, potion.getPosition().y(), "y @ tick " + tick);
            assertEquals(pz, potion.getPosition().z(), "z @ tick " + tick);
        }
        potion.remove();
    }
}
