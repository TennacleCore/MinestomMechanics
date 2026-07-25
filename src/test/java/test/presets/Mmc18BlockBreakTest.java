package test.presets;

import io.github.term4.minestommechanics.MechanicsProfile;
import io.github.term4.minestommechanics.mechanics.explosion.ExplosionSystem;
import io.github.term4.minestommechanics.mechanics.projectile.ProjectileSnapshot;
import io.github.term4.minestommechanics.mechanics.projectile.entities.FireballEntity;
import io.github.term4.minestommechanics.mechanics.projectile.types.Fireball;
import io.github.term4.minestommechanics.mechanics.projectile.types.ProjectileTypeConfig;
import io.github.term4.minestommechanics.presets.mmc18.Explosion;
import io.github.term4.minestommechanics.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MineMen TNT block-break, fitted on the 2026-07-25 flat-pad captures (TNT at rest on a 12x12 one-layer pad over
 * obsidian, power 4): resistance x0.3 charged once per block. Measured broke counts - end stone 21-27, planks 49-56,
 * wool 78-81; vanilla charging gives ~2/~12/~65, so the ranges also prove the PER_BLOCK knob is live.
 */
class Mmc18BlockBreakTest extends HeadlessServerTest {

    private static final int PAD_X = 700, PAD_Y = 64, PAD_Z = 700;

    /** The capture arrangement: obsidian base with one {@code top} layer, blast at the pad surface. */
    private static Instance pad(Block top) {
        Instance inst = flatInstance(MechanicsProfile.builder().build());
        for (int dx = -6; dx <= 5; dx++) {
            for (int dz = -6; dz <= 5; dz++) {
                inst.setBlock(PAD_X + dx, PAD_Y - 2, PAD_Z + dz, Block.OBSIDIAN);
                inst.setBlock(PAD_X + dx, PAD_Y - 1, PAD_Z + dz, Block.OBSIDIAN);
                inst.setBlock(PAD_X + dx, PAD_Y, PAD_Z + dz, top);
            }
        }
        return inst;
    }

    private static int broke(Block top, float power, @org.jetbrains.annotations.Nullable Entity source, long seedShift) {
        Instance inst = pad(top);
        ExplosionSystem sys = new ExplosionSystem(mm, Explosion.config());
        // captured centers sit at the exact pad surface with a fractional x/z; seedShift varies it like the TNT hop
        Pos center = new Pos(PAD_X + 0.3 + (seedShift % 5) * 0.1, PAD_Y + 1, PAD_Z + 0.3 + (seedShift % 3) * 0.15);
        sys.explode(inst, center, power, source);
        int gone = 0;
        for (int dx = -6; dx <= 5; dx++)
            for (int dz = -6; dz <= 5; dz++)
                if (inst.getBlock(PAD_X + dx, PAD_Y, PAD_Z + dz).isAir()) gone++;
        return gone;
    }

    /** Mean of a few shots vs the capture range (wide margins: the ray intensity is random per shot). */
    private static void assertRange(Block top, float power, @org.jetbrains.annotations.Nullable Entity source, int measuredLo, int measuredHi) {
        int total = 0, shots = 4;
        for (int i = 0; i < shots; i++) total += broke(top, power, source, i);
        double mean = total / (double) shots;
        assertTrue(mean >= measuredLo * 0.7 && mean <= measuredHi * 1.3,
                top.name() + " mean broke " + mean + " outside capture range [" + measuredLo + "," + measuredHi + "]");
    }

    private static FireballEntity fireball() {
        return new FireballEntity(null, EntityType.FIREBALL,
                new ProjectileSnapshot(null, Fireball.INSTANCE, null, 1.0, null, null, null, null),
                ProjectileTypeConfig.builder(Fireball.KEY).build());
    }

    @Test
    void endStonePadBreaksLikeMinemen() { assertRange(Block.END_STONE, 4.0f, null, 21, 27); }

    @Test
    void planksPadBreaksLikeMinemen() { assertRange(Block.OAK_PLANKS, 4.0f, null, 49, 56); }

    @Test
    void woolPadBreaksLikeMinemen() { assertRange(Block.RED_WOOL, 4.0f, null, 78, 81); }

    // fireball flat-pad captures (surface-level shots, power 2): planks 14-19, wool 26-29
    @Test
    void fireballPadCountsMatchCaptures() {
        assertRange(Block.OAK_PLANKS, 2.0f, fireball(), 14, 19);
        assertRange(Block.RED_WOOL, 2.0f, fireball(), 26, 29);
    }

    /** User-observed rule: fireballs never destroy end stone; the same blast from TNT (sourceless here) does. */
    @Test
    void fireballNeverBreaksEndStone() {
        assertEquals(0, broke(Block.END_STONE, 2.0f, fireball(), 0), "fireball spared the end stone");
        assertTrue(broke(Block.END_STONE, 2.0f, null, 0) > 0, "non-fireball blast still breaks it");
    }

    /** The blast acts from the AIR side of the hit face: a fireball striking the wall NEXT to wood protruding from it
     *  breaks the wood (user-observed; an into-the-block centre died in the wall's first sample and broke nothing). */
    @Test
    void nearMissWallHitBreaksProtrudingWood() {
        int wx = 760, wy = 66, wz = 760;
        Instance inst = flatInstance(MechanicsProfile.builder().build());
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = -2; dy <= 2; dy++)
                inst.setBlock(wx + dx, wy + dy, wz, Block.END_STONE);
        inst.setBlock(wx, wy, wz - 1, Block.OAK_PLANKS); // protruding from the wall face, air side
        io.github.term4.minestommechanics.mechanics.explosion.ExplosionSystem.install(mm, Explosion.config());
        FireballEntity fb = fireball();
        fb.setExplosionPower(2.0f);
        fb.setBlockBreakAtContact(true);
        fb.setAerodynamics(new net.minestom.server.collision.Aerodynamics(0.0, 0.95, 0.95));
        fb.setInstance(inst, new Pos(wx + 1.5, wy + 0.5, wz - 3.5)).join(); // aimed at the wall a block beside the wood
        fb.setVelocityBt(new net.minestom.server.coordinate.Vec(0, 0, 1.0));
        for (int i = 0; i < 10 && !fb.isRemoved(); i++) fb.tick(i);
        assertTrue(fb.isRemoved(), "fireball hit the wall");
        assertTrue(inst.getBlock(wx, wy, wz - 1).isAir(), "protruding wood breaks off the near-miss");
        assertTrue(inst.getBlock(wx + 1, wy, wz).compare(Block.END_STONE), "the wall itself holds");
    }

    /** End stone is blast-proof TO fireballs, not just unbreakable: a 1-thick wall shields the wood set into it
     *  (user-observed on a 5x5 end-stone wall - the far-side fireball left the centre wood standing). */
    @Test
    void endStoneWallShieldsWoodFromFireball() {
        int wx = 740, wy = 66, wz = 740; // wall plane at z=wz, wood at the centre
        Instance inst = flatInstance(MechanicsProfile.builder().build());
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = -2; dy <= 2; dy++)
                inst.setBlock(wx + dx, wy + dy, wz, dx == 0 && dy == 0 ? Block.OAK_PLANKS : Block.END_STONE);
        ExplosionSystem sys = new ExplosionSystem(mm, Explosion.config());
        // blast on the far side, offset from the wood so its rays must cross end stone first
        sys.explode(inst, new Pos(wx + 1.5, wy + 0.5, wz + 0.9), 2.0f, fireball());
        assertTrue(inst.getBlock(wx, wy, wz).compare(Block.OAK_PLANKS), "wood behind the end stone survives a fireball");
        sys.explode(inst, new Pos(wx + 1.5, wy + 0.5, wz + 0.9), 2.0f, null);
        assertTrue(inst.getBlock(wx, wy, wz).isAir(), "the same blast from TNT reaches it");
    }
}
