package test.presets.mmc18;

import io.github.term4.minestommechanics.api.event.ExplosionEvent;
import io.github.term4.minestommechanics.mechanics.explosion.ExplosionSystem;
import io.github.term4.minestommechanics.util.Directions;
import net.minestom.server.MinecraftServer;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.collision.CollisionUtils;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.Instance;
import test.presets.customItems.PrimedTnt;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MineMen TNT: fuse 52, feet detonation, the MINEMEN wire shape (see {@link PrimedTnt}), and the measured
 * blast-push table on TNT victims. NOT replicated: their full-block-only collision (MineMen TNT falls
 * through fences).
 */
public final class Tnt {

    private Tnt() {}

    public static final PrimedTnt.Config CONFIG = new PrimedTnt.Config(52, 4.0f, true, PrimedTnt.Wire.MINEMEN);

    // TNT-blast push on TNT victims: |dv| = 1.44 * (1 - d/(2*power)) * exposure(), feet-radial; the measured
    // flat 1.08 (= 1.44 * 3/4), the same-level 1.0818, the dy!=0 release (their far rows read 1.15-1.28) and
    // the zero-below-surface all emerge from the exposure ray rules
    private static final double SCALE = 1.44;
    private static final double FIREBALL_SCALE = 1.3167;      // fireball->TNT = their player push scale
    private static final double BELOW_CENTER_UP = 2.0 / 3.0;  // grounded victim under a mid-air center

    private static final AtomicBoolean pushRule = new AtomicBoolean();

    public static PrimedTnt spawn(ExplosionSystem explosion, Instance instance, Point tntBlock) {
        if (pushRule.compareAndSet(false, true))
            MinecraftServer.getGlobalEventHandler().addListener(ExplosionEvent.class, Tnt::retunePush);
        return PrimedTnt.spawn(explosion, instance, tntBlock, CONFIG);
    }

    /**
     * Blast push on TNT victims, replacing the profile's player-scaled push. TNT sources: 1.44 x falloff x
     * {@link #exposure} (the flat-ground 1.08 and the below-surface zero are ray effects, not state rules).
     * Fireball/other sources: the 1.3167 player scale x falloff x the profile's exposure (vanilla18 =
     * {@code LEGACY_1_8}, the real 1.8 rays - every pillar-capture value is k/27), pure radial in every
     * quadrant (downward delivered). The sole remaining special case is a grounded victim under a mid-air
     * TNT center - (2/3)F up + (2/3)F radial horizontal (probably the ground bouncing the downward radial;
     * the empirical numbers stay authoritative).
     */
    private static void retunePush(ExplosionEvent e) {
        boolean tntSource = e.source() instanceof PrimedTnt;
        Instance instance = e.instance();
        double r2 = e.power() * 2.0;
        for (ExplosionEvent.Target target : e.targets()) {
            if (!(target.entity() instanceof PrimedTnt victim)) continue;
            Pos feet = victim.getPosition();
            double exp = tntSource ? exposure(instance, e.center(), feet) : target.exposure();
            double F = (1 - target.distance() / r2) * exp;
            double dy = feet.y() - e.center().y();
            Vec u = F > 0 ? Directions.unit3D(feet.x() - e.center().x(), dy, feet.z() - e.center().z(), 1.0e-7) : null;
            if (u == null) {
                target.setKnockback(null);
            } else if (!tntSource) {
                target.setKnockback(u.mul(FIREBALL_SCALE * F));
            } else if (dy < -0.01 && victim.isOnGround()) { // grounded under the center: no downward delivery
                target.setKnockback(new Vec(u.x(), 0, u.z()).mul(BELOW_CENTER_UP * F).withY(BELOW_CENTER_UP * F));
            } else {
                target.setKnockback(u.mul(SCALE * F));
            }
        }
    }

    /**
     * Their TNT-blast exposure: the vanilla density grid over the FULL-BLOCK box (their TNT collision is
     * full-block-only - it falls through fences), with one deviation from vanilla rays: a ray riding a
     * block seam (level at integer y) counts as blocked. That single rule produces the grounded 3/4
     * (bottom sample layer, level with a feet-height center), keeps it for same-level pillar pairs,
     * releases it whenever dy != 0 (their far elevated rows read above 1.08), and leaves fireball
     * (elevated-center) pushes at full scale.
     */
    public static float exposure(Instance instance, Point center, Point feet) {
        int clear = 0, total = 0;
        for (int xi = 0; xi <= 3; xi++) {
            for (int yi = 0; yi <= 3; yi++) {
                for (int zi = 0; zi <= 3; zi++) {
                    total++;
                    Vec from = new Vec(feet.x() - 0.5 + xi / 3.0, feet.y() + yi / 3.0, feet.z() - 0.5 + zi / 3.0);
                    Vec dir = new Vec(center.x() - from.x(), center.y() - from.y(), center.z() - from.z());
                    if (dir.isZero()) { clear++; continue; }
                    if (Math.abs(dir.y()) < 1.0e-6 && Math.abs(from.y() - Math.rint(from.y())) < 1.0e-6) continue; // seam ray
                    if (!CollisionUtils.handlePhysics(instance, null, RAY_BOX, from.asPos(), dir, null, false).hasCollision()) clear++;
                }
            }
        }
        return (float) clear / total;
    }

    private static final BoundingBox RAY_BOX = new BoundingBox(Vec.ZERO, Vec.ZERO);
}
