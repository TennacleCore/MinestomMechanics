package io.github.term4.minestommechanics.util;

import net.minestom.server.coordinate.Vec;

/** Small horizontal (x/z) direction helpers using the Minecraft yaw convention. */
public final class Directions {

    private Directions() {}

    /**
     * Minecraft yaw -> horizontal facing unit vector, {@code (x, z) = (-sin(yaw), cos(yaw))}.
     * Convention: yaw 0 -> +Z, 90 -> -X, -90 -> +X, 180 -> -Z. The y component is always 0.
     */
    public static Vec fromYaw(double yawDeg) {
        double rad = Math.toRadians(yawDeg);
        return new Vec(-Math.sin(rad), 0, Math.cos(rad));
    }

    // TODO: Could make this 3D later?
    /**
     * Snaps a horizontal vector onto whichever cardinal axis carries the larger magnitude, returning a unit vector
     * with components in {@code {-1, 0, +1}}: e.g. {@code (0.9, 0.1) -> (+1, 0)}, {@code (0.1, -0.9) -> (0, -1)}.
     * Ties favour x; a zero vector snaps to {@code (0, 0)}.
     */
    public static Vec snapDominantAxis(Vec v) {
        return Math.abs(v.x()) >= Math.abs(v.z())
                ? new Vec(Math.signum(v.x()), 0, 0)
                : new Vec(0, 0, Math.signum(v.z()));
    }
}
