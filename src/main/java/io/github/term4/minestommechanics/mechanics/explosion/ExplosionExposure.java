package io.github.term4.minestommechanics.mechanics.explosion;

import net.minestom.server.collision.BoundingBox;
import net.minestom.server.collision.CollisionUtils;
import net.minestom.server.collision.PhysicsResult;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.instance.Instance;

/**
 * Vanilla {@code getSeenPercent}: samples a grid of points across the entity's box and returns the fraction with an
 * unobstructed line of sight to the center (the block-occlusion term in the damage/knockback falloff). Both vanillas
 * share the grid; the RAY differs — 1.8 {@code World.rayTraceBlocks} vs the modern voxel-shape {@code level.clip} —
 * and the two disagree on boundary-riding/grazing rays (visible: TNT sheltering behind block edges).
 */
public final class ExplosionExposure {

    private ExplosionExposure() {}

    /** Which ray implementation samples the grid; {@code NONE} = full exposure for everyone in range. */
    public enum Rays { NONE, MODERN, LEGACY_1_8 }

    private static final BoundingBox RAY_BOX = new BoundingBox(Vec.ZERO, Vec.ZERO);
    // 1.8 Vec3D intermediate epsilon ((float) 1e-7 widened)
    private static final double EPSILON_1_8 = 1.0000000116860974E-7;

    /** Modern-flavoured exposure: the vanilla grid over Minestom's swept collision (approximates 26.1 {@code clip}). */
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

    /**
     * 1.8 exposure: the same grid through a faithful {@code World.rayTraceBlocks} port (paper 1.8.8). The 1.8 ray
     * differs from any swept/modern trace where it matters for explosions: a level ray riding a block seam travels
     * in the AIR row (flat ground reads full exposure), a DESCENDING ray from a boundary sample steps into the block
     * below (the {@code -0.0} quirk — block-edge shadows bite hard), and the endpoint's own block never blocks.
     * Capture-verified against MineMen ray-for-ray (every reading k/27).
     */
    public static float seenPercent18(Instance instance, Point center, Entity entity) {
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

        int clear = 0, total = 0;
        // float accumulators like 1.8 (the loop's sample count is part of the parity)
        for (float f = 0.0F; f <= 1.0F; f = (float) (f + xs)) {
            for (float f1 = 0.0F; f1 <= 1.0F; f1 = (float) (f1 + ys)) {
                for (float f2 = 0.0F; f2 <= 1.0F; f2 = (float) (f2 + zs)) {
                    double sx = minX + (maxX - minX) * f + xOffset;
                    double sy = minY + (maxY - minY) * f1;
                    double sz = minZ + (maxZ - minZ) * f2 + zOffset;
                    if (!rayHits18(instance, sx, sy, sz, center.x(), center.y(), center.z())) clear++;
                    total++;
                }
            }
        }
        return total == 0 ? 0.0f : (float) clear / total;
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

    // 1.8 World.rayTraceBlocks over full-cube solids: start-block check, boundary march with the -0.0 ->
    // -1e-4 quirk, terminates unchecked when the march reaches the end block's coordinates
    private static boolean rayHits18(Instance in, double ax, double ay, double az, double bx, double by, double bz) {
        int i = (int) Math.floor(bx), j = (int) Math.floor(by), k = (int) Math.floor(bz);
        int l = (int) Math.floor(ax), i1 = (int) Math.floor(ay), j1 = (int) Math.floor(az);
        if (solid(in, l, i1, j1) && cubeIntercept(l, i1, j1, ax, ay, az, bx, by, bz)) return true;
        int n = 200;
        while (n-- >= 0) {
            if (l == i && i1 == j && j1 == k) return false;
            boolean sx = true, sy = true, sz = true;
            double px = 999, py = 999, pz = 999;
            if (i > l) px = l + 1.0; else if (i < l) px = l; else sx = false;
            if (j > i1) py = i1 + 1.0; else if (j < i1) py = i1; else sy = false;
            if (k > j1) pz = j1 + 1.0; else if (k < j1) pz = j1; else sz = false;
            double tx = 999, ty = 999, tz = 999;
            double dx = bx - ax, dy = by - ay, dz = bz - az;
            if (sx) tx = (px - ax) / dx;
            if (sy) ty = (py - ay) / dy;
            if (sz) tz = (pz - az) / dz;
            if (tx == -0.0D) tx = -1.0E-4D;
            if (ty == -0.0D) ty = -1.0E-4D;
            if (tz == -0.0D) tz = -1.0E-4D;
            int face;
            if (tx < ty && tx < tz) { face = 0; ax = px; ay += dy * tx; az += dz * tx; }
            else if (ty < tz) { face = 1; ax += dx * ty; ay = py; az += dz * ty; }
            else { face = 2; ax += dx * tz; ay += dy * tz; az = pz; }
            l = (int) Math.floor(ax) - (face == 0 && i < l ? 1 : 0);
            i1 = (int) Math.floor(ay) - (face == 1 && j < i1 ? 1 : 0);
            j1 = (int) Math.floor(az) - (face == 2 && k < j1 ? 1 : 0);
            if (solid(in, l, i1, j1) && cubeIntercept(l, i1, j1, ax, ay, az, bx, by, bz)) return true;
        }
        return false;
    }

    private static boolean solid(Instance in, int x, int y, int z) {
        return in.getBlock(x, y, z).isSolid();
    }

    // 1.8 Block.collisionRayTrace vs the unit cube: face-plane intercepts with INCLUSIVE 2D bounds
    private static boolean cubeIntercept(int bx, int by, int bz, double ax, double ay, double az, double ex, double ey, double ez) {
        ax -= bx; ay -= by; az -= bz;
        ex -= bx; ey -= by; ez -= bz;
        return xFace(ax, ay, az, ex, ey, ez, 0.0) || xFace(ax, ay, az, ex, ey, ez, 1.0)
                || yFace(ax, ay, az, ex, ey, ez, 0.0) || yFace(ax, ay, az, ex, ey, ez, 1.0)
                || zFace(ax, ay, az, ex, ey, ez, 0.0) || zFace(ax, ay, az, ex, ey, ez, 1.0);
    }

    private static boolean xFace(double ax, double ay, double az, double ex, double ey, double ez, double x) {
        double d = ex - ax;
        if (d * d < EPSILON_1_8) return false;
        double t = (x - ax) / d;
        if (t < 0.0 || t > 1.0) return false;
        double y = ay + (ey - ay) * t, z = az + (ez - az) * t;
        return y >= 0.0 && y <= 1.0 && z >= 0.0 && z <= 1.0;
    }

    private static boolean yFace(double ax, double ay, double az, double ex, double ey, double ez, double y) {
        double d = ey - ay;
        if (d * d < EPSILON_1_8) return false;
        double t = (y - ay) / d;
        if (t < 0.0 || t > 1.0) return false;
        double x = ax + (ex - ax) * t, z = az + (ez - az) * t;
        return x >= 0.0 && x <= 1.0 && z >= 0.0 && z <= 1.0;
    }

    private static boolean zFace(double ax, double ay, double az, double ex, double ey, double ez, double z) {
        double d = ez - az;
        if (d * d < EPSILON_1_8) return false;
        double t = (z - az) / d;
        if (t < 0.0 || t > 1.0) return false;
        double x = ax + (ex - ax) * t, y = ay + (ey - ay) * t;
        return x >= 0.0 && x <= 1.0 && y >= 0.0 && y <= 1.0;
    }
}
