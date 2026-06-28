package io.github.term4.minestommechanics.mechanics.explosion;

import net.minestom.server.collision.BoundingBox;
import net.minestom.server.collision.CollisionUtils;
import net.minestom.server.collision.PhysicsResult;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.instance.Instance;

/**
 * Vanilla {@code Explosion.getSeenPercent}: samples a grid of points across the entity's box and returns the fraction
 * with an unobstructed line of sight to the center (the block-occlusion term in the damage/knockback falloff).
 */
public final class ExplosionExposure {

    private ExplosionExposure() {}

    private static final BoundingBox RAY_BOX = new BoundingBox(Vec.ZERO, Vec.ZERO);

    public static float seenPercent(Instance instance, Point center, Entity entity) {
        BoundingBox bb = entity.getBoundingBox();
        Point pos = entity.getPosition();
        double minX = pos.x() + bb.minX(), maxX = pos.x() + bb.maxX();
        double minY = pos.y() + bb.minY(), maxY = pos.y() + bb.maxY();
        double minZ = pos.z() + bb.minZ(), maxZ = pos.z() + bb.maxZ();

        double xs = 1.0 / ((maxX - minX) * 2.0 + 1.0);
        double ys = 1.0 / ((maxY - minY) * 2.0 + 1.0);
        double zs = 1.0 / ((maxZ - minZ) * 2.0 + 1.0);
        if (xs < 0.0 || ys < 0.0 || zs < 0.0) return 0.0f;
        double xOffset = (1.0 - Math.floor(1.0 / xs) * xs) / 2.0;
        double zOffset = (1.0 - Math.floor(1.0 / zs) * zs) / 2.0;

        int hits = 0, count = 0;
        for (double xx = 0.0; xx <= 1.0; xx += xs) {
            for (double yy = 0.0; yy <= 1.0; yy += ys) {
                for (double zz = 0.0; zz <= 1.0; zz += zs) {
                    Vec from = new Vec(lerp(xx, minX, maxX) + xOffset, lerp(yy, minY, maxY), lerp(zz, minZ, maxZ) + zOffset);
                    if (unobstructed(instance, from, center)) hits++;
                    count++;
                }
            }
        }
        return count == 0 ? 0.0f : (float) hits / count;
    }

    private static boolean unobstructed(Instance instance, Vec from, Point center) {
        Vec dir = new Vec(center.x() - from.x(), center.y() - from.y(), center.z() - from.z());
        if (dir.isZero()) return true;
        PhysicsResult result = CollisionUtils.handlePhysics(instance, null, RAY_BOX, from.asPos(), dir, null, false);
        return !result.hasCollision();
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }
}
