package io.github.term4.minestommechanics.mechanics.projectile;

import io.github.term4.minestommechanics.MinestomMechanics;
import io.github.term4.minestommechanics.mechanics.projectile.entities.FishingBobberEntity;
import io.github.term4.minestommechanics.mechanics.projectile.entities.ProjectileEntity;
import io.github.term4.minestommechanics.mechanics.projectile.shootables.FishingRod;
import io.github.term4.minestommechanics.mechanics.projectile.types.FishingBobber;
import io.github.term4.minestommechanics.mechanics.vanilla18.Vanilla18;
import io.github.term4.minestommechanics.testsupport.FakePlayer;
import io.github.term4.minestommechanics.testsupport.HeadlessServerTest;
import io.github.term4.minestommechanics.util.tick.TickScaler;
import io.github.term4.minestommechanics.util.tick.TickScalingConfig;
import net.minestom.server.MinecraftServer;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.entity.metadata.other.FishingHookMeta;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.EntityTeleportPacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fishing bobber: flight lockstep against the vanilla integrators (1.8 {@code EntityFishingHook.t_()}: move, gravity
 * 0.04F, drag 0.92F; 26.1 {@code FishingHook.tick()}: gravity 0.03, move, drag 0.92), hook/pin/reel per source, the
 * 1.8 tracker wire (updateFrequency 5, velocity updates on), and rod cast/retract through the {@code FishingRod} launcher.
 */
class FishingBobberTest extends HeadlessServerTest {

    private static final int FLIGHT_TICKS = 20;
    private static final double DRAG_092F = 0.92f;
    private static final double GRAVITY_004F = 0.04f;

    @BeforeAll
    static void loadFlightArea() {
        for (int x = 0; x <= 7; x++)
            for (int z = 0; z <= 2; z++)
                instance.loadChunk(x, z).join();
    }

    private static LivingEntity angler(Pos at) {
        LivingEntity shooter = looseZombie();
        shooter.setItemInMainHand(ItemStack.of(Material.FISHING_ROD)); // the per-tick holding-a-rod check
        shooter.setInstance(instance, at).join();
        return shooter;
    }

    private static ProjectileEntity launch(ProjectileConfig config, LivingEntity shooter) {
        var snap = ProjectileSnapshot.of(shooter, FishingBobber.INSTANCE).withConfig(config);
        ProjectileEntity entity = new ProjectileSystem(MinestomMechanics.getInstance(), config).launch(snap);
        assertNotNull(entity);
        awaitSpawn(entity);
        return entity;
    }

    @Test
    void vanilla18FlightMatchesThe18Integration() {
        LivingEntity shooter = angler(new Pos(72.5, 150, 8.5, 37.0f, 12.5f));
        ProjectileEntity bobber = launch(Vanilla18.projectiles(), shooter);
        Pos spawn = bobber.getSpawnPosition();
        assertNotNull(spawn);
        Vec v = bobber.velocityBt();
        assertTrue(v.lengthSquared() > 0);
        // wireLockstep on the tracker wire: the sim starts from the 1.8 client's decoded spawn state
        assertEquals((int) (spawn.x() * 32) / 32.0, spawn.x());
        assertEquals((int) (spawn.y() * 32) / 32.0, spawn.y());
        assertEquals((int) (spawn.z() * 32) / 32.0, spawn.z());
        Vec lp = lpRoundTrip(v);
        assertEquals(new Vec(legacyShortAxis(lp.x()), legacyShortAxis(lp.y()), legacyShortAxis(lp.z())), v,
                "sim velocity must equal the 1.8 client's decode of its own wire");

        // 1.8 EntityFishingHook.t_(): move, then motY -= 0.04F, then all axes *= 0.92F (floats widened per tick)
        double px = spawn.x(), py = spawn.y(), pz = spawn.z();
        double vx = v.x(), vy = v.y(), vz = v.z();
        for (int tick = 1; tick <= FLIGHT_TICKS; tick++) {
            px += vx; py += vy; pz += vz;
            vy -= GRAVITY_004F;
            vx *= DRAG_092F; vy *= DRAG_092F; vz *= DRAG_092F;
            bobber.tick(tick * 50L);
            assertEquals(px, bobber.getPosition().x(), "x @ tick " + tick);
            assertEquals(py, bobber.getPosition().y(), "y @ tick " + tick);
            assertEquals(pz, bobber.getPosition().z(), "z @ tick " + tick);
        }
        bobber.remove();
        shooter.remove();
    }

    @Test
    void modernFlightMatchesThe26Integration() {
        LivingEntity shooter = angler(new Pos(104.5, 150, 8.5, 37.0f, 12.5f));
        ProjectileEntity bobber = launch(
                io.github.term4.minestommechanics.mechanics.vanilla.Projectiles.config(), shooter);
        Pos spawn = bobber.getSpawnPosition();
        assertNotNull(spawn);
        Vec v = bobber.velocityBt();
        assertTrue(v.lengthSquared() > 0);

        // 26.1 FishingHook.tick(): motY -= 0.03, move, then all axes *= 0.92 (plain doubles)
        double px = spawn.x(), py = spawn.y(), pz = spawn.z();
        double vx = v.x(), vy = v.y(), vz = v.z();
        for (int tick = 1; tick <= FLIGHT_TICKS; tick++) {
            vy -= 0.03;
            px += vx; py += vy; pz += vz;
            vx *= 0.92; vy *= 0.92; vz *= 0.92;
            bobber.tick(tick * 50L);
            assertEquals(px, bobber.getPosition().x(), "x @ tick " + tick);
            assertEquals(py, bobber.getPosition().y(), "y @ tick " + tick);
            assertEquals(pz, bobber.getPosition().z(), "z @ tick " + tick);
        }
        bobber.remove();
        shooter.remove();
    }

    /** TPS scaling (clientTps 40 vs server 20): drag^2, gravity x4 - incl. the acceleration channel the bobber's
     *  gravity rides (and the fireball's thrust). Spawn velocity re-rates via fromClientVelocity (x2). */
    @Test
    void physicsScalesWithClientTps() {
        TickScaler.setGlobal(TickScalingConfig.builder().clientTps(40).build());
        try {
            LivingEntity shooter = angler(new Pos(88.5, 150, 8.5, 37.0f, 12.5f));
            ProjectileEntity bobber = launch(Vanilla18.projectiles(), shooter);
            Pos spawn = bobber.getSpawnPosition();
            assertNotNull(spawn);
            Vec v = bobber.velocityBt();
            double drag = Math.pow(DRAG_092F, 2.0);
            double gravity = GRAVITY_004F * 4;
            double px = spawn.x(), py = spawn.y(), pz = spawn.z();
            double vx = v.x(), vy = v.y(), vz = v.z();
            for (int tick = 1; tick <= 5; tick++) {
                px += vx; py += vy; pz += vz;
                vx *= drag; vy = (vy - gravity) * drag; vz *= drag;
                bobber.tick(tick * 50L);
                assertEquals(px, bobber.getPosition().x(), "x @ tick " + tick);
                assertEquals(py, bobber.getPosition().y(), "y @ tick " + tick);
                assertEquals(pz, bobber.getPosition().z(), "z @ tick " + tick);
            }
            bobber.remove();
            shooter.remove();
        } finally {
            TickScaler.setGlobal(null);
        }
    }

    @Test
    void hooksPinsAndReelsTheVictim() {
        LivingEntity shooter = angler(new Pos(16.5, 64, 16.5, 0.0f, 0.0f));
        LivingEntity victim = zombie(new Pos(16.5, 64, 20.5));
        FishingBobberEntity bobber = (FishingBobberEntity) launch(Vanilla18.projectiles(), shooter);

        for (int tick = 1; tick <= 10 && bobber.getHookedEntity() == null; tick++) bobber.tick(tick * 50L);
        assertEquals(victim, bobber.getHookedEntity());
        // hooked METADATA stays unset on vanilla18: 1.8 has none, modern viewers see only the pin (real-Via look)
        assertNull(((FishingHookMeta) bobber.getEntityMeta()).getHookedEntity());
        assertFalse(bobber.isRemoved(), "the bobber stays after hooking");

        bobber.tick(999 * 50L); // the hooked tick pins to the victim at 0.8 body height
        Pos pin = victim.getPosition().add(0, victim.getBoundingBox().height() * 0.8, 0);
        assertEquals(pin.x(), bobber.getPosition().x(), 1e-9);
        assertEquals(pin.y(), bobber.getPosition().y(), 1e-9);
        assertEquals(pin.z(), bobber.getPosition().z(), 1e-9);

        // reel: motion += (angler - bobber) * 0.1, plus sqrt(dist) * 0.08 up (1.8); durability 3
        Pos anglerPos = shooter.getPosition();
        Pos bobberPos = bobber.getPosition();
        Vec toAngler = new Vec(anglerPos.x() - bobberPos.x(), anglerPos.y() - bobberPos.y(), anglerPos.z() - bobberPos.z());
        Vec expected = victim.getVelocity().add(toAngler.mul(0.1)
                .add(0, Math.sqrt(toAngler.length()) * 0.08, 0).mul(ServerFlag.SERVER_TICKS_PER_SECOND));
        assertEquals(3, bobber.retrieve());
        assertTrue(bobber.isRemoved());
        assertEquals(expected.x(), victim.getVelocity().x(), 1e-9);
        assertEquals(expected.y(), victim.getVelocity().y(), 1e-9);
        assertEquals(expected.z(), victim.getVelocity().z(), 1e-9);
        shooter.remove();
        victim.remove();
    }

    @Test
    void landsWithTheVanillaDampSettleAndRetrievesAtGroundCost() {
        LivingEntity shooter = angler(new Pos(24.5, 66, 24.5, 0.0f, 90.0f)); // aimed straight down
        FishingBobberEntity bobber = (FishingBobberEntity) launch(Vanilla18.projectiles(), shooter);
        Pos beforeContact = bobber.getPosition();
        int tick = 1;
        for (; tick <= 10 && !bobber.isOnGround(); tick++) {
            beforeContact = bobber.getPosition();
            bobber.tick(tick * 50L);
        }
        assertTrue(bobber.isOnGround());
        assertFalse(bobber.isStuck(), "1.8 never freezes the bobber (xTile is never set)");
        // the contact tick skips the move: still at the pre-move position (the client predicts the same halt)
        assertEquals(beforeContact.x(), bobber.getPosition().x());
        assertEquals(beforeContact.y(), bobber.getPosition().y());
        assertEquals(beforeContact.z(), bobber.getPosition().z());

        for (; tick <= 70; tick++) bobber.tick(tick * 50L); // damp cycles pull it down from the halt height
        assertFalse(bobber.isRemoved(), "no ground despawn in 1.8 (the 1200-tick counter is dead code)");
        assertEquals(64.0, bobber.getPosition().y(), 0.15, "settles onto the surface");

        for (; !bobber.isOnGround(); tick++) bobber.tick(tick * 50L); // onGround flickers between micro-bounces
        assertEquals(2, bobber.retrieve());
        assertTrue(bobber.isRemoved());
        shooter.remove();
    }

    /** The modern preset publishes the hooked metadata (the native 26.1 glued-bobber visual); vanilla18 must not. */
    @Test
    void modernPresetPublishesHookedMetadata() {
        LivingEntity shooter = angler(new Pos(48.5, 64, 40.5, 0.0f, 0.0f));
        LivingEntity victim = zombie(new Pos(48.5, 64, 44.5));
        FishingBobberEntity bobber = (FishingBobberEntity) launch(
                io.github.term4.minestommechanics.mechanics.vanilla.Projectiles.config(), shooter);
        for (int tick = 1; tick <= 10 && bobber.getHookedEntity() == null; tick++) bobber.tick(tick * 50L);
        assertEquals(victim, bobber.getHookedEntity());
        assertEquals(victim, ((FishingHookMeta) bobber.getEntityMeta()).getHookedEntity());
        bobber.remove();
        victim.remove();
        shooter.remove();
    }

    @Test
    void lineSnapDiscardsPastTheConfiguredDistance() {
        ProjectileConfig base = Vanilla18.projectiles();
        ProjectileConfig config = ProjectileConfig.builder(base)
                .typeConfigs(base.typeConfig(FishingBobber.KEY).toBuilder().lineSnapDistance(2.0).build())
                .build();
        LivingEntity shooter = angler(new Pos(32.5, 150, 8.5, 0.0f, -30.0f));
        ProjectileEntity bobber = launch(config, shooter);
        for (int tick = 1; tick <= 10 && !bobber.isRemoved(); tick++) bobber.tick(tick * 50L);
        assertTrue(bobber.isRemoved(), "past the line-snap distance the bobber discards");
        shooter.remove();
    }

    @Test
    void notHoldingARodDiscards() {
        LivingEntity shooter = angler(new Pos(48.5, 150, 8.5, 0.0f, -30.0f));
        ProjectileEntity bobber = launch(Vanilla18.projectiles(), shooter);
        bobber.tick(50L);
        assertFalse(bobber.isRemoved());
        shooter.setItemInMainHand(ItemStack.AIR);
        bobber.tick(100L);
        assertTrue(bobber.isRemoved(), "letting go of the rod discards the bobber");
        shooter.remove();
    }

    @Test
    void floatsOnWater() {
        for (int x = 87; x <= 90; x++)
            for (int z = 23; z <= 26; z++) {
                instance.setBlock(x, 63, z, Block.WATER);
                instance.setBlock(x, 64, z, Block.WATER);
            }
        LivingEntity shooter = angler(new Pos(88.5, 70, 24.5, 0.0f, 90.0f));
        var snap = ProjectileSnapshot.of(shooter, FishingBobber.INSTANCE).withConfig(Vanilla18.projectiles())
                .withSpawnPos(new Pos(88.5, 65.5, 24.5)).withVelocity(new Vec(0, -0.5, 0)); // gentle drop into the pool
        ProjectileEntity bobber = new ProjectileSystem(MinestomMechanics.getInstance(), Vanilla18.projectiles()).launch(snap);
        assertNotNull(bobber);
        awaitSpawn(bobber);
        for (int tick = 1; tick <= 60; tick++) bobber.tick(tick * 50L);
        assertFalse(bobber.isRemoved());
        assertFalse(bobber.isStuck(), "buoyancy keeps it off the pool floor");
        double surface = 64 + 8.0 / 9;
        assertEquals(surface, bobber.getPosition().y(), 0.25, "settles at the water surface");
        bobber.remove();
        shooter.remove();
    }

    @Test
    void rodUseCastsAndRetracts() {
        var config = Vanilla18.projectiles();
        var system = new ProjectileSystem(MinestomMechanics.getInstance(), config);
        new FishingRod().install(system.node(), system);
        MinecraftServer.getGlobalEventHandler().addChild(system.node());
        try {
            FakePlayer caster = FakePlayer.connect(instance, new Pos(8.5, 65, 8.5), "RodCaster");
            ItemStack rod = ItemStack.of(Material.FISHING_ROD);
            caster.player.setItemInMainHand(rod);
            EventDispatcher.call(new PlayerUseItemEvent(caster.player, PlayerHand.MAIN, rod, 0));
            FishingBobberEntity bobber = caster.player.getTag(FishingBobberEntity.ACTIVE_BOBBER);
            assertNotNull(bobber, "a rod use casts a bobber");
            awaitSpawn(bobber);

            // a use in the same tick as the cast is ignored (the use_item_on/use_item pair, click spam)
            EventDispatcher.call(new PlayerUseItemEvent(caster.player, PlayerHand.MAIN, rod, 0));
            assertFalse(bobber.isRemoved(), "a same-tick second use must not retract");
            assertEquals(bobber, caster.player.getTag(FishingBobberEntity.ACTIVE_BOBBER));

            bobber.tick(50L);
            EventDispatcher.call(new PlayerUseItemEvent(caster.player, PlayerHand.MAIN, rod, 0));
            assertTrue(bobber.isRemoved(), "a later use retracts");
            assertNull(caster.player.getTag(FishingBobberEntity.ACTIVE_BOBBER));
            caster.player.remove();
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(system.node());
        }
    }

    /** The 1.8 tracker wire (addEntity 64, 5, true): spawn CARRIES the launch velocity + the owner id (the client
     *  predicts + draws the line from it), the m=0 velocity after the first tick, then a correction teleport
     *  carrying live velocity every 5 ticks. */
    @Test
    void trackerWireCarriesSpawnVelocityAndFiveTickCorrections() {
        var viewer = FakePlayer.connect(instance, new Pos(60.5, 150, 24.5), "BobberWire");
        LivingEntity shooter = angler(new Pos(60.5, 150, 26.5, 0.0f, 20.0f));
        viewer.sent.clear();
        ProjectileEntity bobber = launch(Vanilla18.projectiles(), shooter);
        int id = bobber.getEntityId();
        for (int tick = 1; tick <= 7; tick++) {
            if (tick == 5) assertEquals(3, viewer.packetsFor(id).size(), "spawn + meta + m=0 velocity until the tick-5 correction");
            bobber.tick(tick * 50L);
        }
        List<ServerPacket> forBobber = viewer.packetsFor(id);
        assertEquals(4, forBobber.size(), "one correction teleport at tick 5: " + forBobber);
        SpawnEntityPacket spawn = assertInstanceOf(SpawnEntityPacket.class, forBobber.get(0));
        assertEquals(shooter.getEntityId(), spawn.data(), "spawn data = the owner id (drives the 1.8 line)");
        assertTrue(spawn.velocity().lengthSquared() > 0, "the tracker spawn carries the launch velocity");
        EntityTeleportPacket correction = assertInstanceOf(EntityTeleportPacket.class, forBobber.get(3));
        assertTrue(correction.delta().distanceSquared(Vec.ZERO) > 0, "the correction teleport carries the live velocity");
        viewer.player.remove();
        bobber.remove();
        shooter.remove();
    }

    /** mmc18: syncInterval(0) + velocitySyncInterval(0) = a fully client-predicted wire - spawn + meta, then silence -
     *  with the spawn state on the 1.8 grid so the client's prediction IS the flight. */
    @Test
    void mmc18SilentWireIsFullyClientPredicted() {
        var viewer = FakePlayer.connect(instance, new Pos(120.5, 150, 8.5), "SilentWire");
        LivingEntity shooter = angler(new Pos(120.5, 150, 10.5, 37.0f, 12.5f));
        viewer.sent.clear();
        ProjectileEntity bobber = launch(test.presets.mmc18.Projectiles.config(), shooter);
        Pos spawn = bobber.getSpawnPosition();
        assertNotNull(spawn);
        assertEquals((int) (spawn.x() * 32) / 32.0, spawn.x());
        assertEquals((int) (spawn.y() * 32) / 32.0, spawn.y());
        assertEquals((int) (spawn.z() * 32) / 32.0, spawn.z());
        Vec v = bobber.velocityBt();
        Vec lp = lpRoundTrip(v);
        assertEquals(new Vec(legacyShortAxis(lp.x()), legacyShortAxis(lp.y()), legacyShortAxis(lp.z())), v);

        double px = spawn.x(), py = spawn.y(), pz = spawn.z();
        double vx = v.x(), vy = v.y(), vz = v.z();
        for (int tick = 1; tick <= 15; tick++) {
            px += vx; py += vy; pz += vz;
            vy -= GRAVITY_004F;
            vx *= DRAG_092F; vy *= DRAG_092F; vz *= DRAG_092F;
            bobber.tick(tick * 50L);
            assertEquals(px, bobber.getPosition().x(), "x @ tick " + tick);
            assertEquals(py, bobber.getPosition().y(), "y @ tick " + tick);
            assertEquals(pz, bobber.getPosition().z(), "z @ tick " + tick);
        }
        assertEquals(2, viewer.packetsFor(bobber.getEntityId()).size(), "spawn + meta, then a silent wire");
        viewer.player.remove();
        bobber.remove();
        shooter.remove();
    }

    @Test
    void pseudoHookFlashesReflashesAndFallsAway() {
        var config = test.presets.mmc18.Projectiles.config();
        var system = new ProjectileSystem(MinestomMechanics.getInstance(), config);
        config.shootables().forEach(s -> s.install(system.node(), system)); // incl. the PseudoHook re-flash listener
        MinecraftServer.getGlobalEventHandler().addChild(system.node());
        try {
            FakePlayer victimFp = FakePlayer.connect(instance, new Pos(32.5, 64, 44.5), "RodVictim");
            var victim = victimFp.player;
            LivingEntity shooter = angler(new Pos(32.5, 64, 40.5, 0.0f, 0.0f)); // faces +z toward the victim
            FishingBobberEntity bobber = (FishingBobberEntity) launch(config, shooter);
            int tick = 1;
            for (; tick <= 10 && bobber.getHookedEntity() == null; tick++) bobber.tick(tick * 50L);
            assertEquals(victim, bobber.getHookedEntity(), "the pseudo-hook flashes on the victim");
            var tagged = victim.getTag(test.presets.mmc18.PseudoHook.HOOKED_BY);
            assertNotNull(tagged);
            assertFalse(tagged.isEmpty());

            bobber.tick(tick++ * 50L); // the pin tick (hookDisplayTicks 1): held at the victim's 0.8 body height
            Pos pin = victim.getPosition().add(0, victim.getBoundingBox().height() * 0.8, 0);
            assertEquals(pin.x(), bobber.getPosition().x(), 1e-9);
            assertEquals(pin.y(), bobber.getPosition().y(), 1e-9);
            assertEquals(pin.z(), bobber.getPosition().z(), 1e-9);
            bobber.tick(tick++ * 50L);
            assertNull(bobber.getHookedEntity(), "the flash releases after hookDisplayTicks");
            assertFalse(bobber.isRemoved(), "the bobber stays for re-flashes until retract");

            EventDispatcher.call(new PlayerMoveEvent(victim, victim.getPosition().add(0.3, 0, 0), true));
            assertEquals(victim, bobber.getHookedEntity(), "re-hooks on the victim's position move");
            bobber.tick(tick++ * 50L); // pin
            bobber.tick(tick++ * 50L); // release
            assertNull(bobber.getHookedEntity());
            EventDispatcher.call(new PlayerMoveEvent(victim, victim.getPosition().withView(90f, 0f), true));
            assertNull(bobber.getHookedEntity(), "a look-only move must not re-flash");

            Vec fall = null;
            for (int i = 0; i < 12 && fall == null; i++, tick++) {
                bobber.tick(tick * 50L);
                Vec vel = bobber.velocityBt();
                if (Math.hypot(vel.x(), vel.z()) > 0.02) fall = vel;
            }
            assertNotNull(fall, "the fall velocity applies after the flash");
            // 0.8 b/s = the old lib's Minestom units: a 0.04 b/t drop-off, not an impulse
            assertEquals(0.04, Math.hypot(fall.x(), fall.z()), 1e-9, "gentle slide away from the shooter");
            assertTrue(fall.z() > 0, "away = the victim's side of the shooter (+z)");
            assertEquals(-0.02, fall.y(), 1e-9);

            assertEquals(0, bobber.retrieve(), "nothing hooked at retract");
            assertNull(victim.getTag(test.presets.mmc18.PseudoHook.HOOKED_BY), "retract clears the pseudo tag");
            victim.remove();
            shooter.remove();
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(system.node());
        }
    }

    /** Once settled, the vanilla send threshold (4/32 + 60-tick keepalive) keeps the wire quiet: the predicting
     *  client plays its own landing instead of being yanked by same-spot corrections. */
    @Test
    void restedBobberSendsNoPositionCorrections() {
        var viewer = FakePlayer.connect(instance, new Pos(88.5, 66, 40.5), "RestWire");
        LivingEntity shooter = angler(new Pos(90.5, 66, 40.5, 0.0f, 90.0f));
        ProjectileEntity bobber = launch(Vanilla18.projectiles(), shooter);
        int id = bobber.getEntityId();
        int tick = 1;
        for (; tick <= 60; tick++) bobber.tick(tick * 50L); // land + settle
        long teleportsAfterSettle = viewer.packetsFor(id).stream().filter(p -> p instanceof EntityTeleportPacket).count();
        for (; tick <= 100; tick++) bobber.tick(tick * 50L);
        long teleportsAtRest = viewer.packetsFor(id).stream().filter(p -> p instanceof EntityTeleportPacket).count()
                - teleportsAfterSettle;
        assertTrue(teleportsAtRest <= 1, "at most the 60-tick keepalive while rested, got " + teleportsAtRest);
        viewer.player.remove();
        bobber.remove();
        shooter.remove();
    }

}
