package test.presets;

import io.github.term4.minestommechanics.api.event.explosion.ExplosionEvent;
import io.github.term4.minestommechanics.mechanics.explosion.BlockBreaking;
import io.github.term4.minestommechanics.mechanics.explosion.ExplosionExposure;
import io.github.term4.minestommechanics.mechanics.explosion.ExplosionSystem;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileSnapshot;
import io.github.term4.minestommechanics.mechanics.projectile.entities.FireballEntity;
import io.github.term4.minestommechanics.mechanics.projectile.types.Fireball;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig;
import io.github.term4.minestommechanics.presets.mmc18.Explosion;
import io.github.term4.minestommechanics.testsupport.FakePlayer;
import io.github.term4.minestommechanics.testsupport.HeadlessServerTest;
import io.github.term4.minestommechanics.world.MechanicsWorld;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityType;
import net.minestom.server.event.EventListener;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.server.play.EntityVelocityPacket;
import org.junit.jupiter.api.Test;

import java.util.List;

/** Probe: the same relative blast/player geometry at several absolute world offsets must give the same knockback. */
class Mmc18CoordinateKbProbeTest extends HeadlessServerTest {

    private static final double[] OFFSETS = {0, 10_000, 100_000, 937_168};
    private Vec wireAtOrigin;

    private static FireballEntity fireball() {
        return new FireballEntity(null, EntityType.FIREBALL,
                new ProjectileSnapshot(null, Fireball.INSTANCE, null, 1.0, null, null, null, null),
                ProjectileTypeConfig.builder(Fireball.KEY).build());
    }

    @Test
    void probe() {
        InstanceContainer inst = MinecraftServer.getInstanceManager().createInstanceContainer();
        inst.setGenerator(unit -> unit.modifier().fillHeight(0, 64, Block.STONE));
        ExplosionSystem explosions = ExplosionSystem.install(mm,
                Explosion.config().toBuilder().blockBreaking((BlockBreaking) null).build());

        StringBuilder report = new StringBuilder("\n=== A: exposure knife-edge at the floor seam (player on floor, blast 2.25 away) ===\n");
        for (double off : OFFSETS) {
            int ccx = (int) Math.floor((off + 8) / 16);
            for (int cx = ccx - 2; cx <= ccx + 2; cx++)
                for (int cz = -2; cz <= 2; cz++) inst.loadChunk(cx, cz).join();
            Pos feet = new Pos(off + 8 + 2.6875, 64.0, 8 + 0.9375);
            FakePlayer fp = FakePlayer.connect(inst, feet, "pa" + (int) (off / 1000));
            awaitSpawn(fp.player);
            fp.player.teleport(feet).join();
            report.append(String.format("off=%7.0f  ", off));
            for (double cy : new double[]{64.0, Math.nextDown(64.0), 64.0 - 1e-9, 64.09}) {
                Vec center = new Vec(off + 8 + 0.4375, cy, 8 + 0.5625);
                float e = ExplosionExposure.seenPercent18FullCube(MechanicsWorld.of(inst), center, fp.player);
                report.append(String.format("y=%s -> %.4f   ", cy == 64.0 ? "64.0" : cy == 64.09 ? "64.09" : "64-eps", e));
            }
            report.append('\n');
            fp.player.remove();
        }

        report.append("\n=== B: real fireball dropped straight down, full production path ===\n");
        for (double off : OFFSETS) {
            Pos feet = new Pos(off + 8 + 2.6875, 64.0, 8 + 0.9375);
            FakePlayer fp = FakePlayer.connect(inst, feet, "pb" + (int) (off / 1000));
            awaitSpawn(fp.player);
            fp.player.teleport(feet).join();

            Point[] detCenter = {null};
            Vec[] push = {null};
            float[] expo = {-1};
            var listener = EventListener.of(ExplosionEvent.class, e -> {
                detCenter[0] = e.center();
                for (ExplosionEvent.Target t : e.targets())
                    if (t.entity() == fp.player) { push[0] = t.knockback(); expo[0] = t.exposure(); }
            });
            MinecraftServer.getGlobalEventHandler().addListener(listener);

            FireballEntity fb = fireball();
            fb.setExplosionPower(2.0f);
            fb.setInstance(inst, new Pos(off + 8 + 0.4375, 66.5, 8 + 0.5625)).join();
            fb.setVelocityBt(new Vec(0, -1.0, 0));
            fp.sent.clear();
            for (int i = 0; i < 20 && !fb.isRemoved(); i++) fb.tick(i);
            MinecraftServer.getGlobalEventHandler().removeListener(listener);

            List<EntityVelocityPacket> vels = fp.sent(EntityVelocityPacket.class).stream()
                    .filter(p -> p.entityId() == fp.player.getEntityId()).toList();
            report.append(String.format("off=%7.0f removed=%b center.y=%.17g exposure=%.4f%n         push=%s%n         wire=%s%n",
                    off, fb.isRemoved(), detCenter[0] == null ? Double.NaN : detCenter[0].y(), expo[0],
                    push[0], vels.isEmpty() ? "NONE" : vels.getLast().velocity()));
            if (off == 0) wireAtOrigin = vels.getLast().velocity();
            else org.junit.jupiter.api.Assertions.assertEquals(wireAtOrigin, vels.getLast().velocity(),
                    "wire KB must be bit-identical at offset " + off);
            fp.player.remove();
        }
        report.append("\n=== C: sub-block position sweep (blast y flush vs lifted; player fractional x) ===\n");
        for (double cy : new double[]{64.0, Math.nextDown(64.0), 64.09, 64.322}) {
            report.append(String.format("center.y=%-22s ", cy));
            for (double fx : new double[]{0.0, 0.25, 0.5, 0.75}) {
                Pos feet = new Pos(8 + 2.25 + fx, 64.0, 8 + 0.5);
                FakePlayer fp = FakePlayer.connect(inst, feet, "pc" + (int) (cy * 100) + (int) (fx * 100));
                awaitSpawn(fp.player);
                fp.player.teleport(feet).join();
                Vec center = new Vec(8 + 0.4375, cy, 8 + 0.5625);
                float e = ExplosionExposure.seenPercent18FullCube(MechanicsWorld.of(inst), center, fp.player);
                report.append(String.format("fx%.2f->%.3f  ", fx, e));
                fp.player.remove();
            }
            report.append('\n');
        }
        System.out.println(report);
    }

    /** The vert-sim landing probe: does the downward collision sweep find the floor at large coordinates? */
    @Test
    void collisionProbeFindsTheFloorEverywhere() {
        InstanceContainer inst = MinecraftServer.getInstanceManager().createInstanceContainer();
        StringBuilder report = new StringBuilder("\n=== E: downward collision probe vs coordinates ===\n");
        for (double[] s : new double[][]{{8.5, 41, -4.5}, {94834.5, 70, 94799.5}}) {
            int bx = (int) Math.floor(s[0]), bz = (int) Math.floor(s[2]), top = (int) s[1];
            for (int cx = (bx >> 4) - 1; cx <= (bx >> 4) + 1; cx++)
                for (int cz = (bz >> 4) - 1; cz <= (bz >> 4) + 1; cz++) inst.loadChunk(cx, cz).join();
            for (int x = bx - 2; x <= bx + 2; x++)
                for (int z = bz - 2; z <= bz + 2; z++) inst.setBlock(x, top - 1, z, Block.STONE);
            FakePlayer fp = FakePlayer.connect(inst, new Pos(s[0], top, s[2]), "pe" + bx);
            awaitSpawn(fp.player);
            fp.player.teleport(new Pos(s[0], top, s[2])).join();
            for (double dy : new double[]{-0.0784, -0.5, -1e-9}) {
                var res = net.minestom.server.collision.CollisionUtils.handlePhysics(fp.player, new Vec(0, dy, 0));
                report.append(String.format("at(%9.1f,%d,%9.1f) dy=%9.2e -> onGround=%b newY=%.9f%n",
                        s[0], top, s[2], dy, res.isOnGround(), res.newPosition().y()));
            }
            fp.player.remove();
        }

        // the user's ACTUAL captured walking positions (fractional, y exactly on the floor plane)
        double[][] real = {
                {-10.404524538597432, 41, -4.466171766473123}, {-11.973982010245484, 41, -3.225595027903492},
                {-14.356486868580763, 41, -8.104996823226584},
                {94834.13067865756, 70, 94799.66444053838}, {94834.51607861694, 70, 94799.66586866172},
                {94834.83598308968, 70, 94799.66658272338}, {94834.96526503745, 70, 94799.66658272338},
                {94834.57324368226, 70, 94798.40848042745}, {94833.66983623308, 70, 94798.17741920185}};
        for (double[] s : real) {
            int bx = (int) Math.floor(s[0]), bz = (int) Math.floor(s[2]), top = (int) s[1];
            for (int cx = (bx >> 4) - 1; cx <= (bx >> 4) + 1; cx++)
                for (int cz = (bz >> 4) - 1; cz <= (bz >> 4) + 1; cz++) inst.loadChunk(cx, cz).join();
            for (int x = bx - 2; x <= bx + 2; x++)
                for (int z = bz - 2; z <= bz + 2; z++) inst.setBlock(x, top - 1, z, Block.STONE);
            FakePlayer fp = FakePlayer.connect(inst, new Pos(s[0], top, s[2]), "pf" + Math.abs(bx) + "_" + Math.abs(bz));
            awaitSpawn(fp.player);
            fp.player.teleport(new Pos(s[0], top, s[2])).join();
            var res = net.minestom.server.collision.CollisionUtils.handlePhysics(fp.player, new Vec(0, -0.0784, 0));
            report.append(String.format("real(%18.13f,%d,%18.13f) dy=-0.0784 -> onGround=%b newY=%.9f%n",
                    s[0], top, s[2], res.isOnGround(), res.newPosition().y()));
            fp.player.remove();
        }
        System.out.println(report);
    }

    /** Flush-on-floor landing sweep: does dy=-0.0784 from y exactly on the floor plane collide, by sign/magnitude/state? */
    @Test
    void flushProbeMatrix() {
        InstanceContainer inst = MinecraftServer.getInstanceManager().createInstanceContainer();
        StringBuilder report = new StringBuilder("\n=== F: flush probe matrix (dy=-0.0784 from y=top exactly) ===\n");
        for (double x : new double[]{8.5, -11.5, 94834.5, -94834.5}) {
            int bx = (int) Math.floor(x), bz = 8, top = 64;
            for (int cx = (bx >> 4) - 1; cx <= (bx >> 4) + 1; cx++)
                for (int cz = -1; cz <= 1; cz++) inst.loadChunk(cx, cz).join();
            for (int px = bx - 2; px <= bx + 2; px++)
                for (int pz = bz - 2; pz <= bz + 2; pz++) inst.setBlock(px, top - 1, pz, Block.STONE);
            FakePlayer fp = FakePlayer.connect(inst, new Pos(x, top, 8.5), "pm" + Math.abs(bx) + (x < 0 ? "n" : "p"));
            awaitSpawn(fp.player);
            fp.player.teleport(new Pos(x, top, 8.5)).join();
            var tele = net.minestom.server.collision.CollisionUtils.handlePhysics(fp.player, new Vec(0, -0.0784, 0));
            fp.player.refreshPosition(new Pos(x, top, 8.5), true, false);
            var refr = net.minestom.server.collision.CollisionUtils.handlePhysics(fp.player, new Vec(0, -0.0784, 0));
            report.append(String.format("x=%10.1f  teleport-state onGround=%b   refresh-state onGround=%b%n",
                    x, tele.isOnGround(), refr.isOnGround()));
            fp.player.remove();
        }
        System.out.println(report);
    }

    /** User repro: straight-down self-shot gave wire vy 1.6655 at (0,40,-8) but 1.3541 at (94830,70,93793). */
    @Test
    void selfShotStraightDownAtUserCoordinates() {
        InstanceContainer inst = MinecraftServer.getInstanceManager().createInstanceContainer();
        ExplosionSystem.install(mm, Explosion.config().toBuilder().blockBreaking((BlockBreaking) null).build());
        double[][] sites = {{94830.5, 70, 93793.5}, {0.5, 40, -7.5}, {94830.5, 40, 93793.5}, {0.5, 70, -7.5}};
        StringBuilder report = new StringBuilder("\n=== D: straight-down self-shot at user coordinates ===\n");
        for (double[] s : sites) {
            int bx = (int) Math.floor(s[0]), bz = (int) Math.floor(s[2]), top = (int) s[1];
            for (int cx = (bx >> 4) - 1; cx <= (bx >> 4) + 1; cx++)
                for (int cz = (bz >> 4) - 1; cz <= (bz >> 4) + 1; cz++) inst.loadChunk(cx, cz).join();
            for (int x = bx - 4; x <= bx + 4; x++)
                for (int z = bz - 4; z <= bz + 4; z++) inst.setBlock(x, top - 1, z, Block.STONE);

            Pos feet = new Pos(s[0], top, s[2]);
            FakePlayer fp = FakePlayer.connect(inst, feet, "pd" + bx + "_" + top + (bz < 0 ? "n" : "p"));
            awaitSpawn(fp.player);
            fp.player.teleport(feet).join();

            Point[] detCenter = {null};
            var listener = EventListener.of(ExplosionEvent.class, e -> detCenter[0] = e.center());
            MinecraftServer.getGlobalEventHandler().addListener(listener);

            FireballEntity fb = new FireballEntity(fp.player, EntityType.FIREBALL,
                    new ProjectileSnapshot(fp.player, Fireball.INSTANCE, null, 1.0, null, null, null, null),
                    ProjectileTypeConfig.builder(Fireball.KEY).build());
            fb.setExplosionPower(2.0f);
            fb.setAerodynamics(new net.minestom.server.collision.Aerodynamics(0.0, 0.95, 0.95));
            fb.setIgnition(1, 1.0457);
            fb.setInstance(inst, new Pos(s[0], top + 1.62, s[2])).join();
            fb.setVelocityBt(new Vec(0, -0.5645 * 0.95, 0));
            fp.sent.clear();
            StringBuilder path = new StringBuilder();
            for (int i = 0; i < 10 && !fb.isRemoved(); i++) {
                fb.tick(i);
                path.append(String.format("%.6f ", fb.getPosition().y()));
            }
            MinecraftServer.getGlobalEventHandler().removeListener(listener);

            List<EntityVelocityPacket> vels = fp.sent(EntityVelocityPacket.class).stream()
                    .filter(p -> p.entityId() == fp.player.getEntityId()).toList();
            report.append(String.format("site(%9.1f,%3d,%9.1f) removed=%b center=(%.6f, %.17g, %.6f)%n"
                            + "    tickY: %s%n    wire=%s%n",
                    s[0], top, s[2], fb.isRemoved(),
                    detCenter[0] == null ? Double.NaN : detCenter[0].x(),
                    detCenter[0] == null ? Double.NaN : detCenter[0].y(),
                    detCenter[0] == null ? Double.NaN : detCenter[0].z(),
                    path, vels.isEmpty() ? "NONE" : vels.getLast().velocity()));
            fp.player.remove();
        }
        System.out.println(report);
    }
}
